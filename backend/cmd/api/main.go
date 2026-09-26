package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
	_ "time/tzdata" // nhúng dữ liệu múi giờ: image alpine không có /usr/share/zoneinfo
	"todo-backend/internal/infrastructure/config"
	"todo-backend/internal/infrastructure/db"
	"todo-backend/internal/infrastructure/gemini"
	"todo-backend/internal/infrastructure/qdrant"
	"todo-backend/internal/infrastructure/router"
	"todo-backend/internal/infrastructure/worker"
	"todo-backend/internal/usecase/auth"
	"todo-backend/internal/usecase/category"
	"todo-backend/internal/usecase/coach"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
	"todo-backend/internal/usecase/quickadd"
	"todo-backend/internal/usecase/task"
	"todo-backend/migrations"
)

const shutdownTimeout = 20 * time.Second

func main() {
	// 0. Cấu hình sai (vd JWT_SECRET trống/mặc định) → dừng ngay, không chạy với cấu hình không an toàn
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Invalid configuration: %v", err)
	}

	// Hủy ctx khi nhận SIGINT/SIGTERM (docker stop) để tắt server và scheduler êm ái
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// 1. Initialize DB — bắt buộc: không có DB thì mọi API đều hỏng, nên dừng thay vì chạy tiếp
	pgDB, err := db.NewPostgresDB(ctx, cfg)
	if err != nil {
		log.Fatalf("Failed to connect to PostgreSQL: %v", err)
	}
	defer pgDB.Close()
	log.Println("Successfully connected to PostgreSQL database")

	if cfg.AutoMigrate {
		if err := db.RunMigrations(ctx, pgDB, migrations.FS); err != nil {
			log.Fatalf("Failed to apply database migrations: %v", err)
		}
		log.Println("Database schema is up to date")
	}

	// 2. Initialize Qdrant Vector DB (tùy chọn: thiếu thì tính năng trí nhớ chạy ở chế độ suy giảm)
	qdrantClient := qdrant.NewQdrantClient(cfg.QdrantHost, cfg.QdrantPort)
	initCtx, cancelInit := context.WithTimeout(ctx, 5*time.Second)
	err = qdrantClient.InitCollection(initCtx)
	cancelInit()
	if err != nil {
		log.Printf("Warning: Failed to connect or initialize Qdrant vector collection: %v. Memory features will operate in degraded mode.", err)
	} else {
		log.Println("Successfully initialized Qdrant vector database collection")
	}

	// 3. Initialize Gemini Client (tùy chọn: thiếu key thì các endpoint AI trả 503)
	geminiClient, err := gemini.NewGeminiClient(context.Background(), cfg.GeminiKey, cfg.GeminiModel)
	if err != nil {
		log.Printf("Warning: Failed to initialize Gemini Client: %v. AI features will return 503.", err)
	} else {
		log.Println("Successfully initialized Gemini AI Provider client")
	}

	// 4. Setup repositories
	userRepo := db.NewPostgresUserRepository(pgDB)
	taskRepo := db.NewPostgresTaskRepository(pgDB)
	planRepo := db.NewPostgresPlanRepository(pgDB)
	chatRepo := db.NewPostgresChatRepository(pgDB)
	statsRepo := db.NewPostgresStatsRepository(pgDB)
	categoryRepo := db.NewPostgresCategoryRepository(pgDB)

	// 5. Setup usecases
	authUC := auth.NewAuthUseCase(userRepo, cfg.JWTSecret, cfg.AccessTokenTTL, cfg.RefreshTokenTTL)
	taskUC := task.NewTaskUseCase(taskRepo, cfg.Timezone)
	planUC := plan.NewPlanUseCase(planRepo, taskRepo, userRepo, qdrantClient, geminiClient)
	coachUC := coach.NewCoachUseCase(chatRepo, taskRepo, qdrantClient, geminiClient, geminiClient)
	memoryUC := memory.NewMemoryUseCase(taskRepo, chatRepo, qdrantClient, geminiClient, geminiClient)
	quickAddUC := quickadd.NewQuickAddUseCase(geminiClient)
	categoryUC := category.NewCategoryUseCase(categoryRepo)

	// 6. Setup HTTP router
	routeManager := router.NewRouteManager(
		router.Dependencies{
			Auth:       authUC,
			Tasks:      taskUC,
			Plans:      planUC,
			Coach:      coachUC,
			Memory:     memoryUC,
			QuickAdd:   quickAddUC,
			Categories: categoryUC,
			MemoryRepo: qdrantClient,
			StatsRepo:  statsRepo,
			UserRepo:   userRepo,
		},
		router.Options{
			CORSAllowedOrigins: cfg.CORSAllowedOrigins,
			TrustedProxies:     cfg.TrustedProxies,
			Timezone:           cfg.Timezone,
			AIRatePerMinute:    cfg.AIRatePerMinute,
			AIDailyLimit:       cfg.AIDailyLimit,
			HealthCheck:        pgDB.PingContext,
		},
	)
	r, err := routeManager.SetupRouter()
	if err != nil {
		log.Fatalf("Failed to set up router: %v", err)
	}

	// 7. Khởi động scheduler chạy ngầm ngay trong tiến trình API
	//    (trích xuất trí nhớ 01:00, tạo sẵn lịch trình ngày 04:00 — theo APP_TIMEZONE)
	scheduler := worker.NewScheduler(userRepo, planUC, memoryUC, cfg.Timezone)
	go scheduler.Start(ctx)

	// Timeout để client chậm/treo không giữ kết nối mãi. WriteTimeout phải dài hơn thời gian gọi Gemini
	// (lập lịch/phân tích có thể mất 30–60s) và ngắn hơn proxy_read_timeout của Nginx (120s).
	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           r,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      90 * time.Second,
		IdleTimeout:       120 * time.Second,
		MaxHeaderBytes:    1 << 20,
	}

	serverErr := make(chan error, 1)
	go func() {
		log.Printf("Server is starting on port %s...", cfg.Port)
		serverErr <- srv.ListenAndServe()
	}()

	select {
	case err := <-serverErr:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Printf("Server error: %v", err)
		}
	case <-ctx.Done():
		log.Println("Shutdown signal received, draining in-flight requests...")
	}
	stop()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("Graceful shutdown did not complete: %v", err)
	}
	log.Println("Server stopped")
}
