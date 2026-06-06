package domain

import (
	"context"
	"time"
)

type MemoryItem struct {
	ID         string    `json:"id"`
	UserID     string    `json:"user_id"`
	MemoryType string    `json:"memory_type"` // e.g., "habit", "preference", "observation"
	Content    string    `json:"content"`
	Source     string    `json:"source"`
	CreatedAt  time.Time `json:"created_at"`
	Vector     []float32 `json:"-"`
}

type MemoryRepository interface {
	Save(ctx context.Context, item *MemoryItem) error
	Search(ctx context.Context, userID string, vector []float32, limit int) ([]*MemoryItem, error)
	// MaxSimilarity trả về điểm tương đồng cao nhất giữa vector và các trí nhớ hiện có của user
	// (thang Cosine: càng gần 1 càng giống). Trả về 0 nếu user chưa có trí nhớ nào.
	MaxSimilarity(ctx context.Context, userID string, vector []float32) (float64, error)
	Delete(ctx context.Context, id string) error
	List(ctx context.Context, userID string) ([]*MemoryItem, error)
}

type EmbeddingService interface {
	CreateEmbedding(ctx context.Context, text string) ([]float32, error)
}
