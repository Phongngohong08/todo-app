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
	StatusTodo      TaskStatus = "TODO"
	StatusCompleted TaskStatus = "COMPLETED"
)

type Category string

const (
	CategoryPersonal Category = "PERSONAL"
	CategoryWork     Category = "WORK"
	CategoryOther    Category = "OTHER"
)

type Recurrence string

const (
	RecurrenceNone    Recurrence = "NONE"
	RecurrenceDaily   Recurrence = "DAILY"
	RecurrenceWeekly  Recurrence = "WEEKLY"
	RecurrenceMonthly Recurrence = "MONTHLY"
)

type Task struct {
	ID                    string     `json:"id"`
	UserID                string     `json:"user_id"`
	Title                 string     `json:"title"`
	Description           string     `json:"description"`
	Priority              Priority   `json:"priority"`
	DueDate               *time.Time `json:"due_date"`
	Status                TaskStatus `json:"status"`
	Category              Category   `json:"category"`
	Recurrence            Recurrence `json:"recurrence"`
	RecurrenceDays        string     `json:"recurrence_days"`         // "MON,WED,FRI" khi recurrence = WEEKLY
	ReminderOffsetMinutes int        `json:"reminder_offset_minutes"` // số phút nhắc trước hạn (0 = đúng giờ)
	CreatedAt             time.Time  `json:"created_at"`
	UpdatedAt             time.Time  `json:"updated_at"`
}

type TaskLogAction string

const (
	ActionCreated   TaskLogAction = "CREATED"
	ActionCompleted TaskLogAction = "COMPLETED"
)

type TaskLog struct {
	ID        string        `json:"id"`
	TaskID    string        `json:"task_id"`
	UserID    string        `json:"user_id"`
	Action    TaskLogAction `json:"action"`
	Details   string        `json:"details,omitempty"`
	CreatedAt time.Time     `json:"created_at"`
}

// ParsedTask là kết quả tách task từ câu ngôn ngữ tự nhiên (AI Quick Add).
type ParsedTask struct {
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Priority    string     `json:"priority"`
	DueDate     *time.Time `json:"due_date"`
	Category    string     `json:"category"`
}

var (
	ErrTaskNotFound       = errors.New("task not found")
	ErrInvalidStatusTrans = errors.New("invalid status transition")
)

// TaskFilter gom các điều kiện lọc danh sách task.
type TaskFilter struct {
	Status        TaskStatus
	Query         string // tìm trong title/description (ILIKE)
	Category      Category // lọc theo một danh mục
	DueDateBefore *time.Time
}

type TaskRepository interface {
	Create(ctx context.Context, task *Task) error
	GetByID(ctx context.Context, id string) (*Task, error)
	List(ctx context.Context, userID string, filter TaskFilter) ([]*Task, error)
	Update(ctx context.Context, task *Task) error
	Delete(ctx context.Context, id string) error

	CreateLog(ctx context.Context, log *TaskLog) error
	ListLogs(ctx context.Context, userID string, since time.Time) ([]*TaskLog, error)
}
