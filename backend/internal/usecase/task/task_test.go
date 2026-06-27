package task

import (
	"context"
	"errors"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type MockTaskRepository struct {
	tasks map[string]*domain.Task
	logs  []*domain.TaskLog
}

func NewMockTaskRepository() *MockTaskRepository {
	return &MockTaskRepository{
		tasks: make(map[string]*domain.Task),
		logs:  make([]*domain.TaskLog, 0),
	}
}

func (m *MockTaskRepository) Create(ctx context.Context, task *domain.Task) error {
	m.tasks[task.ID] = task
	return nil
}

func (m *MockTaskRepository) GetByID(ctx context.Context, id string) (*domain.Task, error) {
	t, ok := m.tasks[id]
	if !ok {
		return nil, domain.ErrTaskNotFound
	}
	return t, nil
}

func (m *MockTaskRepository) List(ctx context.Context, userID string, filter domain.TaskFilter) ([]*domain.Task, error) {
	var result []*domain.Task
	for _, t := range m.tasks {
		if t.UserID == userID {
			if filter.Status != "" && t.Status != filter.Status {
				continue
			}
			result = append(result, t)
		}
	}
	return result, nil
}

func (m *MockTaskRepository) Update(ctx context.Context, task *domain.Task) error {
	if _, ok := m.tasks[task.ID]; !ok {
		return domain.ErrTaskNotFound
	}
	m.tasks[task.ID] = task
	return nil
}

func (m *MockTaskRepository) Delete(ctx context.Context, id string) error {
	if _, ok := m.tasks[id]; !ok {
		return domain.ErrTaskNotFound
	}
	delete(m.tasks, id)
	return nil
}

func (m *MockTaskRepository) CreateLog(ctx context.Context, log *domain.TaskLog) error {
	m.logs = append(m.logs, log)
	return nil
}

func (m *MockTaskRepository) ListLogs(ctx context.Context, userID string, since time.Time) ([]*domain.TaskLog, error) {
	var result []*domain.TaskLog
	for _, l := range m.logs {
		if l.UserID == userID && l.CreatedAt.After(since) {
			result = append(result, l)
		}
	}
	return result, nil
}

func TestTaskUseCase_Create(t *testing.T) {
	repo := NewMockTaskRepository()
	uc := NewTaskUseCase(repo)

	ctx := context.Background()
	input := CreateTaskInput{
		Title:    "Learn Go",
		Priority: "HIGH",
	}

	task, err := uc.Create(ctx, "user-1", input)
	if err != nil {
		t.Fatalf("unexpected error creating task: %v", err)
	}

	if task.Title != "Learn Go" {
		t.Errorf("expected title 'Learn Go', got '%s'", task.Title)
	}

	if task.Status != domain.StatusTodo {
		t.Errorf("expected status 'TODO', got '%s'", task.Status)
	}

	// Verify log was created
	if len(repo.logs) != 1 {
		t.Errorf("expected 1 log, got %d", len(repo.logs))
	} else if repo.logs[0].Action != domain.ActionCreated {
		t.Errorf("expected log action 'CREATED', got '%s'", repo.logs[0].Action)
	}
}

func TestTaskUseCase_Complete(t *testing.T) {
	repo := NewMockTaskRepository()
	uc := NewTaskUseCase(repo)

	ctx := context.Background()
	// Seed a task
	taskSeed := &domain.Task{
		ID:        "task-1",
		UserID:    "user-1",
		Title:     "Write Unit Tests",
		Status:    domain.StatusTodo,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
	_ = repo.Create(ctx, taskSeed)

	// Complete task
	completedTask, err := uc.Complete(ctx, "task-1", "user-1")
	if err != nil {
		t.Fatalf("unexpected error completing task: %v", err)
	}
	if completedTask.Status != domain.StatusCompleted {
		t.Errorf("expected status 'COMPLETED', got '%s'", completedTask.Status)
	}
}

func TestTaskUseCase_InvalidTransition(t *testing.T) {
	repo := NewMockTaskRepository()
	uc := NewTaskUseCase(repo)

	ctx := context.Background()
	// Seed a completed task
	taskSeed := &domain.Task{
		ID:        "task-1",
		UserID:    "user-1",
		Title:     "Completed Task",
		Status:    domain.StatusCompleted,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
	_ = repo.Create(ctx, taskSeed)

	// Completing an already-completed task is invalid
	_, err := uc.Complete(ctx, "task-1", "user-1")
	if !errors.Is(err, domain.ErrInvalidStatusTrans) {
		t.Errorf("expected invalid status transition error, got: %v", err)
	}
}
