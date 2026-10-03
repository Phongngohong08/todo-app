// Package focus lưu lịch sử các phiên tập trung (Pomodoro) để xem thống kê trên mọi thiết bị.
package focus

import (
	"context"
	"strings"
	"time"
	"todo-backend/internal/domain"
)

const (
	syncOverlap = 30 * time.Second
	// maxSessions: lần tải đầu chỉ lấy chừng này phiên gần nhất (đủ cho thống kê một năm).
	maxSessions = 2000
	// clockSkew: chấp nhận giờ bắt đầu hơi lệch về tương lai (đồng hồ điện thoại).
	clockSkew = 5 * time.Minute
)

type FocusUseCase struct {
	repo domain.FocusRepository
	now  func() time.Time
}

func NewFocusUseCase(repo domain.FocusRepository) *FocusUseCase {
	return &FocusUseCase{repo: repo, now: time.Now}
}

// SessionInput: một phiên tập trung đã xong, gửi từ app (có thể từ lúc offline).
type SessionInput struct {
	TaskID    *string   `json:"task_id" binding:"omitempty,uuid"`
	TaskTitle string    `json:"task_title" binding:"max=255"`
	StartedAt time.Time `json:"started_at" binding:"required"`
	Minutes   int       `json:"minutes" binding:"required,min=1,max=240"`
}

// Record lưu phiên [id] (gửi lại nhiều lần vẫn chỉ một bản).
func (u *FocusUseCase) Record(ctx context.Context, id, userID string, in SessionInput) (*domain.FocusSession, error) {
	now := u.now()
	if in.StartedAt.After(now.Add(clockSkew)) {
		return nil, domain.NewValidationError("started_at không thể ở tương lai")
	}
	s := &domain.FocusSession{
		ID:        id,
		UserID:    userID,
		TaskID:    in.TaskID,
		TaskTitle: strings.TrimSpace(in.TaskTitle),
		StartedAt: in.StartedAt,
		Minutes:   in.Minutes,
		CreatedAt: now,
	}
	if err := u.repo.Insert(ctx, s); err != nil {
		return nil, err
	}
	return s, nil
}

// Changes trả về các phiên server nhận được kể từ since (nil = các phiên gần nhất).
func (u *FocusUseCase) Changes(ctx context.Context, userID string, since *time.Time) (*domain.FocusChanges, error) {
	serverTime := u.now()
	var from *time.Time
	if since != nil {
		f := since.Add(-syncOverlap)
		from = &f
	}
	sessions, err := u.repo.ListCreatedSince(ctx, userID, from, maxSessions)
	if err != nil {
		return nil, err
	}
	return &domain.FocusChanges{Sessions: sessions, ServerTime: serverTime}, nil
}
