package task

import (
	"context"
	"errors"
	"strings"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

type TaskUseCase struct {
	taskRepo domain.TaskRepository
}

func NewTaskUseCase(taskRepo domain.TaskRepository) *TaskUseCase {
	return &TaskUseCase{taskRepo: taskRepo}
}

type CreateTaskInput struct {
	Title       string     `json:"title" binding:"required"`
	Description string     `json:"description"`
	Priority    string     `json:"priority" binding:"required,oneof=LOW MEDIUM HIGH"`
	DueDate     *time.Time `json:"due_date"`
	Category    string     `json:"category"`
	Recurrence  string     `json:"recurrence"`
}

type UpdateTaskInput struct {
	Title       string     `json:"title" binding:"required"`
	Description string     `json:"description"`
	Priority    string     `json:"priority" binding:"required,oneof=LOW MEDIUM HIGH"`
	DueDate     *time.Time `json:"due_date"`
	Category    string     `json:"category"`
	Recurrence  string     `json:"recurrence"`
}

// normalizeRecurrence trả về giá trị recurrence hợp lệ, mặc định NONE.
func normalizeRecurrence(r string) domain.Recurrence {
	switch domain.Recurrence(r) {
	case domain.RecurrenceDaily, domain.RecurrenceWeekly, domain.RecurrenceMonthly:
		return domain.Recurrence(r)
	default:
		return domain.RecurrenceNone
	}
}

// normalizeCategory cho phép danh mục tự do; rỗng -> OTHER.
func normalizeCategory(c string) domain.Category {
	t := strings.TrimSpace(c)
	if t == "" {
		return domain.CategoryOther
	}
	return domain.Category(t)
}

func (u *TaskUseCase) Create(ctx context.Context, userID string, input CreateTaskInput) (*domain.Task, error) {
	now := time.Now()
	task := &domain.Task{
		ID:          uuid.New().String(),
		UserID:      userID,
		Title:       input.Title,
		Description: input.Description,
		Priority:    domain.Priority(input.Priority),
		DueDate:     input.DueDate,
		Status:      domain.StatusTodo,
		Category:    normalizeCategory(input.Category),
		Recurrence:  normalizeRecurrence(input.Recurrence),
		CreatedAt:   now,
		UpdatedAt:   now,
	}

	err := u.taskRepo.Create(ctx, task)
	if err != nil {
		return nil, err
	}

	// Create activity log
	log := &domain.TaskLog{
		ID:        uuid.New().String(),
		TaskID:    task.ID,
		UserID:    userID,
		Action:    domain.ActionCreated,
		Details:   "Task created",
		CreatedAt: now,
	}
	_ = u.taskRepo.CreateLog(ctx, log) // Non-blocking if it fails, or log it

	return task, nil
}

func (u *TaskUseCase) GetByID(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.taskRepo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if task.UserID != userID {
		return nil, errors.New("unauthorized access to task")
	}
	return task, nil
}

func (u *TaskUseCase) List(ctx context.Context, userID string, status string, dueDateBefore *time.Time, query string, category string) ([]*domain.Task, error) {
	return u.taskRepo.List(ctx, userID, domain.TaskFilter{
		Status:        domain.TaskStatus(status),
		DueDateBefore: dueDateBefore,
		Query:         query,
		Category:      domain.Category(category),
	})
}

func (u *TaskUseCase) Update(ctx context.Context, id string, userID string, input UpdateTaskInput) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	task.Title = input.Title
	task.Description = input.Description
	task.Priority = domain.Priority(input.Priority)
	task.DueDate = input.DueDate
	task.Category = normalizeCategory(input.Category)
	task.Recurrence = normalizeRecurrence(input.Recurrence)
	task.UpdatedAt = time.Now()

	err = u.taskRepo.Update(ctx, task)
	if err != nil {
		return nil, err
	}

	return task, nil
}

func (u *TaskUseCase) Delete(ctx context.Context, id string, userID string) error {
	_, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return err
	}
	return u.taskRepo.Delete(ctx, id)
}

func (u *TaskUseCase) Complete(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	if task.Status == domain.StatusCompleted {
		return nil, domain.ErrInvalidStatusTrans
	}

	task.Status = domain.StatusCompleted
	task.UpdatedAt = time.Now()

	err = u.taskRepo.Update(ctx, task)
	if err != nil {
		return nil, err
	}

	// Create activity log
	log := &domain.TaskLog{
		ID:        uuid.New().String(),
		TaskID:    task.ID,
		UserID:    userID,
		Action:    domain.ActionCompleted,
		Details:   "Task completed",
		CreatedAt: time.Now(),
	}
	_ = u.taskRepo.CreateLog(ctx, log)

	// Task lặp lại: sinh occurrence kế tiếp khi hoàn thành (cần có hạn chót để dời chu kỳ).
	u.spawnNextOccurrence(ctx, task)

	return task, nil
}

// spawnNextOccurrence tạo bản sao của task lặp lại với hạn chót dời theo chu kỳ.
// Lỗi không làm hỏng thao tác Complete (chỉ bỏ qua occurrence).
func (u *TaskUseCase) spawnNextOccurrence(ctx context.Context, completed *domain.Task) {
	if completed.Recurrence == domain.RecurrenceNone || completed.Recurrence == "" || completed.DueDate == nil {
		return
	}

	nextDue := nextOccurrenceDate(*completed.DueDate, completed.Recurrence)
	now := time.Now()
	next := &domain.Task{
		ID:          uuid.New().String(),
		UserID:      completed.UserID,
		Title:       completed.Title,
		Description: completed.Description,
		Priority:    completed.Priority,
		DueDate:     &nextDue,
		Status:      domain.StatusTodo,
		Category:    completed.Category,
		Recurrence:  completed.Recurrence,
		CreatedAt:   now,
		UpdatedAt:   now,
	}

	if err := u.taskRepo.Create(ctx, next); err != nil {
		return
	}
	_ = u.taskRepo.CreateLog(ctx, &domain.TaskLog{
		ID:        uuid.New().String(),
		TaskID:    next.ID,
		UserID:    next.UserID,
		Action:    domain.ActionCreated,
		Details:   "Recurring task occurrence created",
		CreatedAt: now,
	})
}

// nextOccurrenceDate dời mốc hạn chót theo chu kỳ lặp.
func nextOccurrenceDate(from time.Time, r domain.Recurrence) time.Time {
	switch r {
	case domain.RecurrenceDaily:
		return from.AddDate(0, 0, 1)
	case domain.RecurrenceWeekly:
		return from.AddDate(0, 0, 7)
	case domain.RecurrenceMonthly:
		return from.AddDate(0, 1, 0)
	default:
		return from
	}
}

