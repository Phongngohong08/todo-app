package domain

import (
	"context"
	"time"
)

type ChatMessage struct {
	ID        string    `json:"id"`
	UserID    string    `json:"user_id"`
	Role      string    `json:"role"` // "user", "assistant"
	Content   string    `json:"content"`
	CreatedAt time.Time `json:"created_at"`
}

type ChatRepository interface {
	Save(ctx context.Context, message *ChatMessage) error
	GetHistory(ctx context.Context, userID string, limit int) ([]*ChatMessage, error)
}
