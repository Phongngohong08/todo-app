package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"
	"todo-backend/internal/infrastructure/config"
	"todo-backend/internal/infrastructure/db"
	"todo-backend/internal/infrastructure/gemini"
	"todo-backend/internal/infrastructure/qdrant"
	"todo-backend/internal/infrastructure/worker"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
)

func main() {
	cfg := config.Load()

	// 1. Initialize DB
	pgDB, err := db.NewPostgresDB(cfg)
	if err != nil {
		log.Printf("Warning: Worker failed to connect to Postgres: %v.", err)
	} else {
		defer pgDB.Close()
		log.Println("Worker successfully connected to PostgreSQL database")
	}

	// 2. Initialize Qdrant
	qdrantClient := qdrant.NewQdrantClient(cfg.QdrantHost, cfg.QdrantPort)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = qdrantClient.InitCollection(ctx)
	if err != nil {
		log.Printf("Warning: Worker failed to connect/init Qdrant: %v.", err)
	}

	// 3. Initialize Gemini Client
	geminiClient, err := gemini.NewGeminiClient(context.Background(), cfg.GeminiKey)
	if err != nil {
		log.Printf("Warning: Worker failed to initialize Gemini Client: %v. AI Features will fail at runtime.", err)
	}

	// 4. Setup repositories
	userRepo := db.NewPostgresUserRepository(pgDB)
	taskRepo := db.NewPostgresTaskRepository(pgDB)
	planRepo := db.NewPostgresPlanRepository(pgDB)
	chatRepo := db.NewPostgresChatRepository(pgDB)

	// 5. Setup usecases
	planUC := plan.NewPlanUseCase(planRepo, taskRepo, userRepo, qdrantClient, geminiClient)
	memoryUC := memory.NewMemoryUseCase(taskRepo, chatRepo, qdrantClient, geminiClient, geminiClient)

	// 6. Setup and Start Scheduler
	scheduler := worker.NewScheduler(userRepo, planUC, memoryUC)
	
	schedulerCtx, schedulerCancel := context.WithCancel(context.Background())
	defer schedulerCancel()

	go scheduler.Start(schedulerCtx)

	// Wait for termination signal
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan

	log.Println("Worker received shutdown signal. Stopping...")
	schedulerCancel()
	// Allow scheduler to shutdown gracefully
	time.Sleep(1 * time.Second)
	log.Println("Worker shutdown complete.")
}
