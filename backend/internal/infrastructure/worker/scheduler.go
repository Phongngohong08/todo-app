package worker

import (
	"context"
	"log"
	"sync"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
)

// Chỉ tạo sẵn lịch cho người dùng có hoạt động trong khoảng này — tránh đốt lượt AI cho tài khoản bỏ không.
const planningActiveWindow = 3 * 24 * time.Hour

type Scheduler struct {
	userRepo domain.UserRepository
	planUC   *plan.PlanUseCase
	memoryUC *memory.MemoryUseCase
	loc      *time.Location // giờ chạy job (01:00, 04:00) tính theo múi giờ người dùng, không phải UTC của server

	mu      sync.Mutex
	lastRun map[string]string // job -> ngày đã chạy (YYYY-MM-DD), chống chạy lặp trong cùng một ngày
}

func NewScheduler(userRepo domain.UserRepository, planUC *plan.PlanUseCase, memoryUC *memory.MemoryUseCase, loc *time.Location) *Scheduler {
	if loc == nil {
		loc = time.UTC
	}
	return &Scheduler{
		userRepo: userRepo,
		planUC:   planUC,
		memoryUC: memoryUC,
		loc:      loc,
		lastRun:  make(map[string]string),
	}
}

func (s *Scheduler) Start(ctx context.Context) {
	log.Printf("Background scheduler starting (timezone %s)...", s.loc)

	// Tick every hour to check if we need to run background tasks
	ticker := time.NewTicker(1 * time.Hour)
	defer ticker.Stop()

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
	local := now.In(s.loc)

	// 1. Memory extraction job runs daily at 01:00
	if local.Hour() == 1 && s.markRun("memory", local) {
		log.Println("Triggering nightly memory extraction job...")
		s.RunMemoryExtraction(ctx)
	}

	// 2. Daily planning job runs daily at 04:00
	if local.Hour() == 4 && s.markRun("planning", local) {
		log.Println("Triggering nightly daily planning job...")
		s.RunDailyPlanning(ctx)
	}
}

// markRun trả true nếu job chưa chạy trong ngày local (và đánh dấu đã chạy).
func (s *Scheduler) markRun(job string, local time.Time) bool {
	day := local.Format("2006-01-02")
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.lastRun[job] == day {
		return false
	}
	s.lastRun[job] = day
	return true
}

func (s *Scheduler) RunMemoryExtraction(ctx context.Context) {
	// Job ngầm chạy mỗi đêm nên chỉ phân tích phần phát sinh trong 24h gần nhất
	lookback := 24 * time.Hour
	userIDs, err := s.userRepo.ListActiveUserIDs(ctx, time.Now().Add(-lookback))
	if err != nil {
		log.Printf("Scheduler error listing users: %v", err)
		return
	}

	for _, userID := range userIDs {
		if ctx.Err() != nil {
			return
		}
		res, err := s.memoryUC.ExtractAndStoreMemories(ctx, userID, lookback)
		if err != nil {
			log.Printf("Error extracting memories for user %s: %v", userID, err)
		} else {
			log.Printf("Memory extraction for user %s: analyzed %d, saved %d", userID, res.Analyzed, res.Saved)
		}
	}
}

func (s *Scheduler) RunDailyPlanning(ctx context.Context) {
	userIDs, err := s.userRepo.ListActiveUserIDs(ctx, time.Now().Add(-planningActiveWindow))
	if err != nil {
		log.Printf("Scheduler error listing users: %v", err)
		return
	}

	now := time.Now().In(s.loc)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, s.loc)
	for _, userID := range userIDs {
		if ctx.Err() != nil {
			return
		}
		// Đã có lịch hôm nay (người dùng tự tạo, hoặc job đã chạy trước khi server khởi động lại) → bỏ qua.
		existing, err := s.planUC.GetPlan(ctx, userID, today)
		if err != nil {
			log.Printf("Error checking daily plan for user %s: %v", userID, err)
			continue
		}
		if existing != nil {
			continue
		}
		// Generate tự bỏ qua (không gọi AI) nếu user không còn việc cần làm.
		if _, err := s.planUC.Generate(ctx, userID, today, ""); err != nil {
			log.Printf("Error generating daily plan for user %s: %v", userID, err)
		}
	}
}
