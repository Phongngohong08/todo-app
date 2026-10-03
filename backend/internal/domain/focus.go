package domain

import (
	"context"
	"time"
)

// FocusSession là một phiên tập trung (Pomodoro) đã hoàn thành. Chỉ thêm, không sửa.
type FocusSession struct {
	ID        string    `json:"id"`
	UserID    string    `json:"user_id"`
	TaskID    *string   `json:"task_id"`
	TaskTitle string    `json:"task_title"`
	StartedAt time.Time `json:"started_at"`
	Minutes   int       `json:"minutes"`
	CreatedAt time.Time `json:"created_at"` // giờ server ghi nhận — dùng làm con trỏ đồng bộ
}

type FocusChanges struct {
	Sessions   []*FocusSession `json:"sessions"`
	ServerTime time.Time       `json:"server_time"`
}

type FocusRepository interface {
	// Insert bỏ qua nếu id đã có của cùng user (gửi lại do mất mạng); trả ErrNotFound nếu id thuộc user khác.
	Insert(ctx context.Context, s *FocusSession) error
	// ListCreatedSince: since nil = toàn bộ (giới hạn bởi limit, mới nhất trước).
	ListCreatedSince(ctx context.Context, userID string, since *time.Time, limit int) ([]*FocusSession, error)
}
