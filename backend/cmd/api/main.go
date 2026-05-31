package main

import (
	"context"
	"log"
	"net/http"
	"time"
	"todo-backend/internal/infrastructure/config"
	"todo-backend/internal/infrastructure/db"
	"todo-backend/internal/infrastructure/gemini"
	"todo-backend/internal/infrastructure/qdrant"
	"todo-backend/internal/infrastructure/router"
	"todo-backend/internal/usecase/auth"
	"todo-backend/internal/usecase/coach"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
	"todo-backend/internal/usecase/task"
)

func main() {
	cfg := config.Load()

	// 1. Initialize DB
	pgDB, err := db.NewPostgresDB(cfg)
	if err != nil {
		log.Printf("Warning: Failed to connect to Postgres: %v. Running in mock/development mode if database is offline.", err)
	} else {
		defer pgDB.Close()
		log.Println("Successfully connected to PostgreSQL database")
	}

	// 2. Initialize Qdrant Vector DB
	qdrantClient := qdrant.NewQdrantClient(cfg.QdrantHost, cfg.QdrantPort)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = qdrantClient.InitCollection(ctx)
	if err != nil {
		log.Printf("Warning: Failed to connect or initialize Qdrant vector collection: %v. Memory features will operate in degraded mode.", err)
	} else {
		log.Println("Successfully initialized Qdrant vector database collection")
	}

	// 3. Initialize Gemini Client
	geminiClient, err := gemini.NewGeminiClient(context.Background(), cfg.GeminiKey)
	if err != nil {
		log.Printf("Warning: Failed to initialize Gemini Client: %v. AI Features will fail at runtime.", err)
	} else if cfg.GeminiKey == "" {
		log.Println("Warning: GEMINI_API_KEY is not set. AI Features will fail at runtime unless key is supplied.")
	} else {
		log.Println("Successfully initialized Gemini AI Provider client")
	}

	// 4. Setup repositories
	userRepo := db.NewPostgresUserRepository(pgDB)
	taskRepo := db.NewPostgresTaskRepository(pgDB)
	planRepo := db.NewPostgresPlanRepository(pgDB)
	chatRepo := db.NewPostgresChatRepository(pgDB)
	statsRepo := db.NewPostgresStatsRepository(pgDB)

	// 5. Setup usecases
	authUC := auth.NewAuthUseCase(userRepo, cfg.JWTSecret)
	taskUC := task.NewTaskUseCase(taskRepo)
	planUC := plan.NewPlanUseCase(planRepo, taskRepo, userRepo, qdrantClient, geminiClient)
	coachUC := coach.NewCoachUseCase(chatRepo, taskRepo, qdrantClient, geminiClient, geminiClient)
	memoryUC := memory.NewMemoryUseCase(taskRepo, chatRepo, qdrantClient, geminiClient, geminiClient)

	// 6. Setup HTTP router
	routeManager := router.NewRouteManager(
		authUC,
		taskUC,
		planUC,
		coachUC,
		memoryUC,
		qdrantClient,
		statsRepo,
		userRepo,
	)
	r := routeManager.SetupRouter()

	log.Printf("Server is starting on port %s...", cfg.Port)
	if err := http.ListenAndServe(":"+cfg.Port, r); err != nil {
		log.Fatalf("Failed to run server: %v", err)
	}
}
