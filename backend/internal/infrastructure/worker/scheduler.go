package worker

import (
	"context"
	"log"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
)

type Scheduler struct {
	userRepo  domain.UserRepository
	planUC    *plan.PlanUseCase
	memoryUC  *memory.MemoryUseCase
}

func NewScheduler(userRepo domain.UserRepository, planUC *plan.PlanUseCase, memoryUC *memory.MemoryUseCase) *Scheduler {
	return &Scheduler{
		userRepo:  userRepo,
		planUC:    planUC,
		memoryUC:  memoryUC,
	}
}

func (s *Scheduler) Start(ctx context.Context) {
	log.Println("Background scheduler starting...")

	// Tick every hour to check if we need to run background tasks
	ticker := time.NewTicker(1 * time.Hour)
	defer ticker.Stop()

	// Run initial checks/runs on start if needed (optional)
	s.runDueJobs(ctx, time.Now())

	for {
		select {
		case <-ctx.Done():
			log.Println("Background scheduler stopping...")
			return
		case now := <-ticker.C:
			s.runDueJobs(ctx, now)
		}
	}
}

func (s *Scheduler) runDueJobs(ctx context.Context, now time.Time) {
	hour := now.Hour()

	// 1. Memory extraction job runs daily at 01:00 AM
	if hour == 1 {
		log.Println("Triggering nightly memory extraction job...")
		s.RunMemoryExtraction(ctx)
	}

	// 2. Daily planning job runs daily at 04:00 AM
	if hour == 4 {
		log.Println("Triggering nightly daily planning job...")
		s.RunDailyPlanning(ctx)
	}
}

func (s *Scheduler) RunMemoryExtraction(ctx context.Context) {
	userIDs, err := s.userRepo.ListAllUserIDs(ctx)
	if err != nil {
		log.Printf("Scheduler error listing users: %v", err)
		return
	}

	for _, userID := range userIDs {
		log.Printf("Extracting memories for user %s...", userID)
		// Job ngầm chạy mỗi đêm nên chỉ phân tích phần phát sinh trong 24h gần nhất
		res, err := s.memoryUC.ExtractAndStoreMemories(ctx, userID, 24*time.Hour)
		if err != nil {
			log.Printf("Error extracting memories for user %s: %v", userID, err)
		} else {
			log.Printf("Memory extraction for user %s: analyzed %d, saved %d", userID, res.Analyzed, res.Saved)
		}
	}
}

func (s *Scheduler) RunDailyPlanning(ctx context.Context) {
	userIDs, err := s.userRepo.ListAllUserIDs(ctx)
	if err != nil {
		log.Printf("Scheduler error listing users: %v", err)
		return
	}

	today := time.Now()
	for _, userID := range userIDs {
		log.Printf("Pre-generating daily plan for user %s...", userID)
		_, err := s.planUC.Generate(ctx, userID, today, "")
		if err != nil {
			log.Printf("Error generating daily plan for user %s: %v", userID, err)
		}
	}
}
