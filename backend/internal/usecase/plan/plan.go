package plan

import (
	"context"
	"sort"
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

// MaxPlanSlots giới hạn số khung giờ người dùng lưu tay (một ngày không cần nhiều hơn).
const MaxPlanSlots = 48

// SaveEdited lưu lịch người dùng đã chỉnh tay (đổi giờ, bỏ khung). Khung giờ phải hợp lệ và không chồng nhau;
// được sắp lại theo giờ bắt đầu.
func (u *PlanUseCase) SaveEdited(ctx context.Context, userID string, date time.Time, slots []domain.PlanSlot) (*domain.DailyPlan, error) {
	if len(slots) > MaxPlanSlots {
		return nil, domain.NewValidationError("Lịch trình có quá nhiều khung giờ")
	}
	clean, err := normalizeSlots(slots)
	if err != nil {
		return nil, err
	}
	existing, err := u.planRepo.GetByDate(ctx, userID, date)
	if err != nil {
		return nil, err
	}
	plan := &domain.DailyPlan{
		ID:        uuid.New().String(),
		UserID:    userID,
		PlanDate:  date,
		PlanData:  clean,
		CreatedAt: time.Now(),
	}
	if existing != nil {
		plan.ID = existing.ID
	}
	if err := u.planRepo.Save(ctx, plan); err != nil {
		return nil, err
	}
	return plan, nil
}

// normalizeSlots kiểm tra "HH:mm", start < end, không chồng nhau; trả về bản đã sắp theo giờ bắt đầu.
func normalizeSlots(slots []domain.PlanSlot) ([]domain.PlanSlot, error) {
	out := make([]domain.PlanSlot, 0, len(slots))
	for _, s := range slots {
		start, errS := time.Parse("15:04", s.StartTime)
		end, errE := time.Parse("15:04", s.EndTime)
		if errS != nil || errE != nil {
			return nil, domain.NewValidationError("Giờ trong lịch trình phải có dạng HH:mm")
		}
		if !end.After(start) {
			return nil, domain.NewValidationError("Giờ kết thúc phải sau giờ bắt đầu")
		}
		if len(s.Title) > 255 {
			return nil, domain.NewValidationError("Tên khung giờ quá dài")
		}
		s.StartTime, s.EndTime = start.Format("15:04"), end.Format("15:04")
		out = append(out, s)
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].StartTime < out[j].StartTime })
	for i := 1; i < len(out); i++ {
		if out[i].StartTime < out[i-1].EndTime {
			return nil, domain.NewValidationError("Các khung giờ bị chồng lên nhau")
		}
	}
	return out, nil
}

// GetPlan chỉ đọc lại lịch ĐÃ lưu của một ngày (không gọi AI).
// Ví dụ: GetPlan(ctx, "u1", 2026-07-08)  → *DailyPlan đã tạo trước đó, hoặc nil nếu ngày đó chưa có.
func (u *PlanUseCase) GetPlan(ctx context.Context, userID string, date time.Time) (*domain.DailyPlan, error) {
	return u.planRepo.GetByDate(ctx, userID, date)
}

// HasActiveTasks cho biết user còn việc chưa xong không — tức là Generate có thực sự gọi AI hay không.
// Handler dùng để chỉ trừ hạn mức AI khi cần.
func (u *PlanUseCase) HasActiveTasks(ctx context.Context, userID string) (bool, error) {
	tasks, err := u.taskRepo.List(ctx, userID, domain.TaskFilter{Status: domain.StatusTodo})
	if err != nil {
		return false, err
	}
	return len(tasks) > 0, nil
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

	// Người dùng đã chọn "Ngày của tôi" cho ngày này → chỉ xếp lịch những việc đó (cam kết của họ),
	// thay vì dồn mọi việc đang mở vào một ngày.
	myDay, err := u.taskRepo.ListMyDay(ctx, userID, date.Format("2006-01-02"))
	if err != nil {
		return nil, err
	}
	if len(myDay) > 0 {
		activeTasks = myDay
	}

	// Không có việc cần xếp → trả lịch rỗng, KHÔNG gọi AI (tiết kiệm lượt) và KHÔNG lưu, để khi người dùng
	// thêm việc sau đó thì lần mở màn tiếp theo vẫn tự tạo được lịch.
	if len(activeTasks) == 0 {
		return &domain.DailyPlan{
			UserID:    userID,
			PlanDate:  date,
			PlanData:  []domain.PlanSlot{},
			CreatedAt: time.Now(),
		}, nil
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
	if slots == nil {
		slots = []domain.PlanSlot{}
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
