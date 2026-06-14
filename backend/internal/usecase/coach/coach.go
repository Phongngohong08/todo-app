package coach

import (
	"context"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

type CoachClient interface {
	GetCoachResponse(ctx context.Context, message string, tasks []*domain.Task, memories []*domain.MemoryItem, history []*domain.ChatMessage) (string, error)
}

type CoachUseCase struct {
	chatRepo     domain.ChatRepository
	taskRepo     domain.TaskRepository
	memoryRepo   domain.MemoryRepository
	embedService domain.EmbeddingService
	aiClient     CoachClient
}

func NewCoachUseCase(
	chatRepo domain.ChatRepository,
	taskRepo domain.TaskRepository,
	memoryRepo domain.MemoryRepository,
	embedService domain.EmbeddingService,
	aiClient CoachClient,
) *CoachUseCase {
	return &CoachUseCase{
		chatRepo:     chatRepo,
		taskRepo:     taskRepo,
		memoryRepo:   memoryRepo,
		embedService: embedService,
		aiClient:     aiClient,
	}
}

type ChatInput struct {
	Message string `json:"message" binding:"required"`
}

type ChatResponse struct {
	Reply string `json:"reply"`
}

func (u *CoachUseCase) Chat(ctx context.Context, userID string, messageText string) (string, error) {
	// 1. Save user message to database
	userMsg := &domain.ChatMessage{
		ID:        uuid.New().String(),
		UserID:    userID,
		Role:      "user",
		Content:   messageText,
		CreatedAt: time.Now(),
	}
	_ = u.chatRepo.Save(ctx, userMsg) // Ignore database insert error for resilience

	// 2. Fetch active tasks
	tasks, err := u.taskRepo.List(ctx, userID, domain.TaskFilter{})
	var activeTasks []*domain.Task
	if err == nil {
		for _, t := range tasks {
			if t.Status == domain.StatusTodo || t.Status == domain.StatusInProgress || t.Status == domain.StatusPostponed {
				activeTasks = append(activeTasks, t)
			}
		}
	}

	// 3. Retrieve relevant memories from Qdrant if memoryRepo and embedding service are set up
	var relevantMemories []*domain.MemoryItem
	if u.memoryRepo != nil && u.embedService != nil {
		vector, err := u.embedService.CreateEmbedding(ctx, messageText)
		if err == nil {
			relevantMemories, _ = u.memoryRepo.Search(ctx, userID, vector, 3)
		}
	}

	// 4. Retrieve chat history context (last 10 messages)
	history, _ := u.chatRepo.GetHistory(ctx, userID, 10)

	// 5. Query OpenAI Coach Client
	replyText, err := u.aiClient.GetCoachResponse(ctx, messageText, activeTasks, relevantMemories, history)
	if err != nil {
		return "", err
	}

	// 6. Save assistant message to database
	assistantMsg := &domain.ChatMessage{
		ID:        uuid.New().String(),
		UserID:    userID,
		Role:      "assistant",
		Content:   replyText,
		CreatedAt: time.Now(),
	}
	_ = u.chatRepo.Save(ctx, assistantMsg)

	return replyText, nil
}
