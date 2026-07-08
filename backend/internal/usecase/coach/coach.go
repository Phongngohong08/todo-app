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

// Chat xử lý một lượt trò chuyện với AI Coach theo đúng mô hình RAG (Retrieval-Augmented Generation):
// LẤY ngữ cảnh liên quan (task đang mở + trí nhớ tìm bằng vector + lịch sử) rồi mới nhờ LLM trả lời.
//
// Sáu bước: (1) lưu tin của user → (2) lấy task đang mở → (3) embedding câu hỏi rồi Search 3 trí nhớ
// giống nhất (đây là bước "Retrieval") → (4) lấy 10 tin gần nhất → (5) gọi LLM → (6) lưu lại câu trả lời.
//
// Tham số (một trường hợp minh họa):
//
//	userID      = "u1"
//	messageText = "Tôi hay trì hoãn, làm sao khắc phục?"
//
// Kết quả trả về (chuỗi trả lời đã được cá nhân hóa nhờ trí nhớ):
//
//	"Mình thấy bạn hay hoãn việc viết báo cáo. Thử quy tắc 15 phút: chỉ cần bắt đầu..."
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
			if t.Status == domain.StatusTodo {
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
