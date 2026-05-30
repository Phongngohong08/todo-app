package task

import (
	"context"
	"errors"
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
	Title              string     `json:"title" binding:"required"`
	Description        string     `json:"description"`
	Priority           string     `json:"priority" binding:"required,oneof=LOW MEDIUM HIGH"`
	DueDate            *time.Time `json:"due_date"`
	EstimatedDuration  int        `json:"estimated_duration"` // in minutes
	PreferredTimeStart *string    `json:"preferred_time_start"`
	PreferredTimeEnd   *string    `json:"preferred_time_end"`
}

type UpdateTaskInput struct {
	Title              string     `json:"title" binding:"required"`
	Description        string     `json:"description"`
	Priority           string     `json:"priority" binding:"required,oneof=LOW MEDIUM HIGH"`
	DueDate            *time.Time `json:"due_date"`
	EstimatedDuration  int        `json:"estimated_duration"`
	PreferredTimeStart *string    `json:"preferred_time_start"`
	PreferredTimeEnd   *string    `json:"preferred_time_end"`
}

type PostponeTaskInput struct {
	DueDate *time.Time `json:"due_date" binding:"required"`
	Reason  string     `json:"reason"`
}

func (u *TaskUseCase) Create(ctx context.Context, userID string, input CreateTaskInput) (*domain.Task, error) {
	now := time.Now()
	task := &domain.Task{
		ID:                 uuid.New().String(),
		UserID:             userID,
		Title:              input.Title,
		Description:        input.Description,
		Priority:           domain.Priority(input.Priority),
		DueDate:            input.DueDate,
		EstimatedDuration:  input.EstimatedDuration,
		PreferredTimeStart: input.PreferredTimeStart,
		PreferredTimeEnd:   input.PreferredTimeEnd,
		Status:             domain.StatusTodo,
		CreatedAt:          now,
		UpdatedAt:          now,
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

func (u *TaskUseCase) List(ctx context.Context, userID string, status string, dueDateBefore *time.Time) ([]*domain.Task, error) {
	return u.taskRepo.List(ctx, userID, domain.TaskStatus(status), dueDateBefore)
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
	task.EstimatedDuration = input.EstimatedDuration
	task.PreferredTimeStart = input.PreferredTimeStart
	task.PreferredTimeEnd = input.PreferredTimeEnd
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

func (u *TaskUseCase) Start(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	if task.Status != domain.StatusTodo && task.Status != domain.StatusPostponed {
		return nil, domain.ErrInvalidStatusTrans
	}

	task.Status = domain.StatusInProgress
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
		Action:    domain.ActionStarted,
		Details:   "Task started",
		CreatedAt: time.Now(),
	}
	_ = u.taskRepo.CreateLog(ctx, log)

	return task, nil
}

func (u *TaskUseCase) Complete(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	if task.Status == domain.StatusCompleted || task.Status == domain.StatusCancelled {
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

	return task, nil
}

func (u *TaskUseCase) Postpone(ctx context.Context, id string, userID string, input PostponeTaskInput) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	if task.Status == domain.StatusCompleted || task.Status == domain.StatusCancelled {
		return nil, domain.ErrInvalidStatusTrans
	}

	task.Status = domain.StatusPostponed
	task.DueDate = input.DueDate
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
		Action:    domain.ActionPostponed,
		Details:   input.Reason,
		CreatedAt: time.Now(),
	}
	_ = u.taskRepo.CreateLog(ctx, log)

	return task, nil
}

func (u *TaskUseCase) Cancel(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}

	if task.Status == domain.StatusCompleted || task.Status == domain.StatusCancelled {
		return nil, domain.ErrInvalidStatusTrans
	}

	task.Status = domain.StatusCancelled
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
		Action:    domain.ActionCancelled,
		Details:   "Task cancelled",
		CreatedAt: time.Now(),
	}
	_ = u.taskRepo.CreateLog(ctx, log)

	return task, nil
}

