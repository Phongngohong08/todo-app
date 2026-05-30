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
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

type UserPreferences struct {
	UserID                  string    `json:"user_id"`
	MorningStartTime        string    `json:"morning_start_time"`         // e.g., "08:00"
	EveningEndTime          string    `json:"evening_end_time"`           // e.g., "18:00"
	WorkDurationPreference  int       `json:"work_duration_preference"`  // in minutes, e.g., 60
	UpdatedAt               time.Time `json:"updated_at"`
}

type UserRepository interface {
	Create(ctx context.Context, user *User) error
	GetByID(ctx context.Context, id string) (*User, error)
	GetByEmail(ctx context.Context, email string) (*User, error)
	ListAllUserIDs(ctx context.Context) ([]string, error)
	
	GetPreferences(ctx context.Context, userID string) (*UserPreferences, error)
	UpdatePreferences(ctx context.Context, prefs *UserPreferences) error
}
