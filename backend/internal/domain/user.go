package domain

import (
	"context"
	"time"
)

type User struct {
	ID           string    `json:"id"`
	Email        string    `json:"email"`
	PasswordHash string    `json:"-"`
	Name         string    `json:"name"`
	TokenVersion int       `json:"-"` // tăng khi đăng xuất → thu hồi mọi refresh token đã phát
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

type UserPreferences struct {
	UserID                  string    `json:"user_id"`
	MorningStartTime        string    `json:"morning_start_time"`         // e.g., "08:00"
	EveningEndTime          string    `json:"evening_end_time"`           // e.g., "18:00"
	WorkDurationPreference  int       `json:"work_duration_preference"`  // in minutes, e.g., 60
	DailyGoal               int       `json:"daily_goal"`                // số việc muốn hoàn thành mỗi ngày (chuỗi ngày đạt mục tiêu)
	DaysOff                 string    `json:"days_off"`                  // "SAT,SUN": ngày nghỉ không làm đứt chuỗi
	UpdatedAt               time.Time `json:"updated_at"`
}

type UserRepository interface {
	Create(ctx context.Context, user *User) error
	GetByID(ctx context.Context, id string) (*User, error)
	GetByEmail(ctx context.Context, email string) (*User, error)
	// ListActiveUserIDs trả về user có hoạt động (log task hoặc chat) kể từ since — job ngầm dùng
	// để không tốn lượt AI cho tài khoản bỏ không.
	ListActiveUserIDs(ctx context.Context, since time.Time) ([]string, error)
	// IncrementTokenVersion vô hiệu hóa mọi refresh token đã phát cho user.
	IncrementTokenVersion(ctx context.Context, userID string) error
	// RevokeRefreshToken thu hồi đúng một refresh token (đăng xuất một thiết bị). Gọi lại vẫn an toàn.
	RevokeRefreshToken(ctx context.Context, jti, userID string, expiresAt time.Time) error
	// IsRefreshTokenRevoked cho biết refresh token có jti này đã bị thu hồi chưa.
	IsRefreshTokenRevoked(ctx context.Context, jti string) (bool, error)
	// PurgeExpiredRevocations xóa bản ghi thu hồi của token đã tự hết hạn (không còn cần chặn).
	PurgeExpiredRevocations(ctx context.Context, now time.Time) error

	GetPreferences(ctx context.Context, userID string) (*UserPreferences, error)
	UpdatePreferences(ctx context.Context, prefs *UserPreferences) error
}
