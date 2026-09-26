package domain

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
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

// Subtask là một bước trong checklist. Nằm TRONG task (task là aggregate root): thêm/sửa/xóa bước con
// đều là cập nhật task, nên chỉ cần đồng bộ một loại bản ghi.
type Subtask struct {
	ID       string `json:"id" binding:"required,uuid"`
	Title    string `json:"title" binding:"required,max=255"`
	Done     bool   `json:"done"`
	Position int    `json:"position"`
}

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
	CompletedAt           *time.Time `json:"completed_at"`
	SortOrder             float64    `json:"sort_order"` // nhỏ hơn đứng trước
	Subtasks              []Subtask  `json:"subtasks"`
	SpawnedFrom           *string    `json:"spawned_from"` // id task lặp đã sinh ra lần này (nếu có)
	CreatedAt             time.Time  `json:"created_at"`
	UpdatedAt             time.Time  `json:"updated_at"` // do server đặt ở mỗi lần ghi — dùng làm con trỏ đồng bộ
	DeletedAt             *time.Time `json:"-"`
}

func (t *Task) IsDeleted() bool { return t.DeletedAt != nil }

// recurrenceNamespace cố định để sinh id cho lần lặp kế tiếp. App Android dùng CÙNG namespace và thuật toán
// (UUID v3 = MD5(namespace + parentID)) — nhờ vậy lần lặp do app tạo khi offline và lần lặp do server tạo
// có cùng id, không bị nhân đôi khi đồng bộ.
var recurrenceNamespace = uuid.MustParse("6f1c2c1e-6b1a-4b8e-9a3e-2d5f8f0c7a11")

// NextOccurrenceID trả về id tất định của lần lặp sinh ra từ task parentID.
func NextOccurrenceID(parentID string) string {
	return uuid.NewMD5(recurrenceNamespace, []byte(parentID)).String()
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
	Query         string   // tìm trong title/description (ILIKE)
	Category      Category // lọc theo một danh mục
	DueDateBefore *time.Time
}

// TaskChanges là kết quả một lần kéo dữ liệu đồng bộ.
type TaskChanges struct {
	Tasks      []*Task   `json:"tasks"`       // task được tạo/sửa (chưa bị xóa)
	DeletedIDs []string  `json:"deleted_ids"` // task đã bị xóa mềm
	ServerTime time.Time `json:"server_time"` // client gửi lại làm "since" ở lần sau
}

type TaskRepository interface {
	// Upsert ghi toàn bộ trạng thái task (tạo mới nếu chưa có). Caller bảo đảm quyền sở hữu.
	Upsert(ctx context.Context, task *Task) error
	// GetByID trả về cả task đã xóa mềm (DeletedAt != nil) để caller quyết định.
	GetByID(ctx context.Context, id string) (*Task, error)
	// List chỉ trả về task chưa bị xóa.
	List(ctx context.Context, userID string, filter TaskFilter) ([]*Task, error)
	// ListChangedSince trả về mọi task (kể cả đã xóa mềm) có updated_at > since; since nil = mọi task chưa xóa.
	ListChangedSince(ctx context.Context, userID string, since *time.Time) ([]*Task, error)
	SoftDelete(ctx context.Context, id string, at time.Time) error

	CreateLog(ctx context.Context, log *TaskLog) error
	ListLogs(ctx context.Context, userID string, since time.Time) ([]*TaskLog, error)
}
