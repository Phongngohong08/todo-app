package domain

import (
	"context"
	"errors"
	"time"
)

type Priority string

const (
	PriorityLow    Priority = "LOW"
	PriorityMedium Priority = "MEDIUM"
	PriorityHigh   Priority = "HIGH"
)

type TaskStatus string

const (
	StatusTodo       TaskStatus = "TODO"
	StatusInProgress TaskStatus = "IN_PROGRESS"
	StatusCompleted  TaskStatus = "COMPLETED"
	StatusCancelled  TaskStatus = "CANCELLED"
	StatusPostponed  TaskStatus = "POSTPONED"
)

type Task struct {
	ID                string     `json:"id"`
	UserID            string     `json:"user_id"`
	Title             string     `json:"title"`
	Description       string     `json:"description"`
	Priority          Priority   `json:"priority"`
	DueDate           *time.Time `json:"due_date"`
	EstimatedDuration int        `json:"estimated_duration"` // in minutes
	PreferredTimeStart *string    `json:"preferred_time_start"`
	PreferredTimeEnd   *string    `json:"preferred_time_end"`
	Status            TaskStatus `json:"status"`
	CreatedAt         time.Time  `json:"created_at"`
	UpdatedAt         time.Time  `json:"updated_at"`
}

type TaskLogAction string

const (
	ActionCreated   TaskLogAction = "CREATED"
	ActionStarted   TaskLogAction = "STARTED"
	ActionPaused    TaskLogAction = "PAUSED"
	ActionCompleted TaskLogAction = "COMPLETED"
	ActionPostponed TaskLogAction = "POSTPONED"
	ActionCancelled TaskLogAction = "CANCELLED"
)

type TaskLog struct {
	ID        string        `json:"id"`
	TaskID    string        `json:"task_id"`
	UserID    string        `json:"user_id"`
	Action    TaskLogAction `json:"action"`
	Details   string        `json:"details,omitempty"`
	CreatedAt time.Time     `json:"created_at"`
}

var (
	ErrTaskNotFound       = errors.New("task not found")
	ErrInvalidStatusTrans = errors.New("invalid status transition")
)

type TaskRepository interface {
	Create(ctx context.Context, task *Task) error
	GetByID(ctx context.Context, id string) (*Task, error)
	List(ctx context.Context, userID string, status TaskStatus, dueDateBefore *time.Time) ([]*Task, error)
	Update(ctx context.Context, task *Task) error
	Delete(ctx context.Context, id string) error
	
	CreateLog(ctx context.Context, log *TaskLog) error
	ListLogs(ctx context.Context, userID string, since time.Time) ([]*TaskLog, error)
}
