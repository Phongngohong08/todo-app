package memory

import (
	"context"
	"log"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

type MemoryClient interface {
	ExtractMemories(ctx context.Context, logs []*domain.TaskLog, messages []*domain.ChatMessage) ([]string, error)
}

type MemoryUseCase struct {
	taskRepo     domain.TaskRepository
	chatRepo     domain.ChatRepository
	memoryRepo   domain.MemoryRepository
	embedService domain.EmbeddingService
	aiClient     MemoryClient
}

func NewMemoryUseCase(
	taskRepo domain.TaskRepository,
	chatRepo domain.ChatRepository,
	memoryRepo domain.MemoryRepository,
	embedService domain.EmbeddingService,
	aiClient MemoryClient,
) *MemoryUseCase {
	return &MemoryUseCase{
		taskRepo:     taskRepo,
		chatRepo:     chatRepo,
		memoryRepo:   memoryRepo,
		embedService: embedService,
		aiClient:     aiClient,
	}
}

func (u *MemoryUseCase) ExtractAndStoreMemories(ctx context.Context, userID string) error {
	// 1. Fetch activities from the last 24 hours
	since := time.Now().Add(-24 * time.Hour)
	logs, err := u.taskRepo.ListLogs(ctx, userID, since)
	if err != nil {
		return err
	}

	// 2. Fetch latest chat history (up to 30 messages)
	messages, err := u.chatRepo.GetHistory(ctx, userID, 30)
	if err != nil {
		return err
	}

	// If no activities occurred, skip extraction
	if len(logs) == 0 && len(messages) == 0 {
		return nil
	}

	// 3. Extract insights using LLM
	insights, err := u.aiClient.ExtractMemories(ctx, logs, messages)
	if err != nil {
		return err
	}

	// 4. Create vector embedding and store each insight in Qdrant
	for _, insight := range insights {
		vector, err := u.embedService.CreateEmbedding(ctx, insight)
		if err != nil {
			log.Printf("Failed to generate embedding for memory insight '%s': %v", insight, err)
			continue
		}

		item := &domain.MemoryItem{
			ID:         uuid.New().String(),
			UserID:     userID,
			MemoryType: "observation",
			Content:    insight,
			Source:     "logs_analysis",
			CreatedAt:  time.Now(),
			Vector:     vector,
		}

		err = u.memoryRepo.Save(ctx, item)
		if err != nil {
			log.Printf("Failed to save memory item to Qdrant: %v", err)
			continue
		}
	}

	return nil
}
