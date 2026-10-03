package domain

import (
	"context"
	"time"
)

// HabitFrequency: thói quen cần làm vào những ngày nào.
type HabitFrequency string

const (
	HabitDaily    HabitFrequency = "DAILY"    // mỗi ngày
	HabitWeekdays HabitFrequency = "WEEKDAYS" // các thứ cố định (weekdays = "MON,WED,FRI")
	HabitWeekly   HabitFrequency = "WEEKLY"   // target_per_week lần mỗi tuần, ngày nào cũng được
)

// Habit là việc lặp lại không có hạn chót (uống nước, đọc sách 20 phút) — đánh dấu theo ngày, tính chuỗi.
type Habit struct {
	ID            string         `json:"id"`
	UserID        string         `json:"user_id"`
	Name          string         `json:"name"`
	Emoji         string         `json:"emoji"`
	Color         string         `json:"color"`
	Frequency     HabitFrequency `json:"frequency"`
	Weekdays      string         `json:"weekdays"`
	TargetPerWeek int            `json:"target_per_week"`
	ReminderTime  string         `json:"reminder_time"` // "HH:mm" hoặc "" (không nhắc)
	Position      float64        `json:"position"`
	Archived      bool           `json:"archived"`
	CreatedAt     time.Time      `json:"created_at"`
	UpdatedAt     time.Time      `json:"updated_at"`
	DeletedAt     *time.Time     `json:"-"`
}

func (h *Habit) IsDeleted() bool { return h.DeletedAt != nil }

// HabitCheckin: trạng thái của một thói quen trong một ngày. Done = false là "đã bỏ đánh dấu".
type HabitCheckin struct {
	HabitID   string    `json:"habit_id"`
	Date      string    `json:"date"` // "yyyy-MM-dd" theo lịch của người dùng
	Done      bool      `json:"done"`
	UpdatedAt time.Time `json:"updated_at"`
}

// HabitChanges là kết quả một lần kéo đồng bộ thói quen.
type HabitChanges struct {
	Habits     []*Habit       `json:"habits"`
	DeletedIDs []string       `json:"deleted_ids"`
	Checkins   []HabitCheckin `json:"checkins"`
	ServerTime time.Time      `json:"server_time"`
}

type HabitRepository interface {
	Upsert(ctx context.Context, habit *Habit) error
	// GetByID trả về cả thói quen đã xóa mềm.
	GetByID(ctx context.Context, id string) (*Habit, error)
	// ListChangedSince: mọi thói quen (kể cả đã xóa) có updated_at > since; since nil = mọi thói quen còn sống.
	ListChangedSince(ctx context.Context, userID string, since *time.Time) ([]*Habit, error)
	SoftDelete(ctx context.Context, id string, at time.Time) error

	SetCheckin(ctx context.Context, userID string, checkin HabitCheckin) error
	// CheckinsChangedSince: since nil = mọi check-in đang "done" của các thói quen còn sống.
	CheckinsChangedSince(ctx context.Context, userID string, since *time.Time) ([]HabitCheckin, error)
}
