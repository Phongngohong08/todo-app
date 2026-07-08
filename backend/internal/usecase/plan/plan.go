package plan

import (
	"context"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

type PlanningClient interface {
	GenerateDailyPlan(ctx context.Context, tasks []*domain.Task, prefs *domain.UserPreferences, memories []*domain.MemoryItem, localTime string) ([]domain.PlanSlot, error)
}

type PlanUseCase struct {
	planRepo   domain.PlanRepository
	taskRepo   domain.TaskRepository
	userRepo   domain.UserRepository
	memoryRepo domain.MemoryRepository
	aiClient   PlanningClient
}

func NewPlanUseCase(
	planRepo domain.PlanRepository,
	taskRepo domain.TaskRepository,
	userRepo domain.UserRepository,
	memoryRepo domain.MemoryRepository,
	aiClient PlanningClient,
) *PlanUseCase {
	return &PlanUseCase{
		planRepo:   planRepo,
		taskRepo:   taskRepo,
		userRepo:   userRepo,
		memoryRepo: memoryRepo,
		aiClient:   aiClient,
	}
}

// GetPlan chỉ đọc lại lịch ĐÃ lưu của một ngày (không gọi AI).
// Ví dụ: GetPlan(ctx, "u1", 2026-07-08)  → *DailyPlan đã tạo trước đó, hoặc nil nếu ngày đó chưa có.
func (u *PlanUseCase) GetPlan(ctx context.Context, userID string, date time.Time) (*domain.DailyPlan, error) {
	return u.planRepo.GetByDate(ctx, userID, date)
}

// Generate dựng lịch MỚI cho một ngày bằng AI rồi lưu lại.
// Năm bước: (1) lấy sở thích (thiếu thì dùng mặc định 08:00–18:00, khối 60') → (2) lấy task đang mở →
// (3) nạp trí nhớ/thói quen → (4) gọi LLM GenerateDailyPlan → (5) lưu kế hoạch.
//
// Tham số (một trường hợp minh họa):
//
//	userID    = "u1"
//	date      = 2026-07-08            // ngày cần lập lịch
//	localTime = "10:15"               // giờ hiện tại của user → không xếp việc vào quá khứ
//
// Kết quả trả về:
//
//	&domain.DailyPlan{PlanDate: 2026-07-08, PlanData: [ {Start:"10:15",End:"11:15",Title:"Viết báo cáo"}, ... ]}
func (u *PlanUseCase) Generate(ctx context.Context, userID string, date time.Time, localTime string) (*domain.DailyPlan, error) {
	// 1. Fetch preferences
	prefs, err := u.userRepo.GetPreferences(ctx, userID)
	if err != nil {
		return nil, err
	}
	if prefs == nil {
		// Provide default preferences
		prefs = &domain.UserPreferences{
			UserID:                 userID,
			MorningStartTime:       "08:00",
			EveningEndTime:         "18:00",
			WorkDurationPreference: 60,
		}
	}

	// 2. Fetch active tasks (TODO, IN_PROGRESS, POSTPONED)
	tasks, err := u.taskRepo.List(ctx, userID, domain.TaskFilter{})
	if err != nil {
		return nil, err
	}

	// Filter tasks to only active ones
	var activeTasks []*domain.Task
	for _, t := range tasks {
		if t.Status == domain.StatusTodo {
			activeTasks = append(activeTasks, t)
		}
	}

	// 3. Fetch memories if memoryRepo is configured
	var memories []*domain.MemoryItem
	if u.memoryRepo != nil {
		memories, _ = u.memoryRepo.List(ctx, userID) // Fallback silently on error for resilience
	}

	// 4. Ask OpenAI/Gemini to generate schedule
	slots, err := u.aiClient.GenerateDailyPlan(ctx, activeTasks, prefs, memories, localTime)
	if err != nil {
		return nil, err
	}

	// 5. Save the generated plan
	plan := &domain.DailyPlan{
		ID:        uuid.New().String(),
		UserID:    userID,
		PlanDate:  date,
		PlanData:  slots,
		CreatedAt: time.Now(),
	}

	err = u.planRepo.Save(ctx, plan)
	if err != nil {
		return nil, err
	}

	return plan, nil
}
