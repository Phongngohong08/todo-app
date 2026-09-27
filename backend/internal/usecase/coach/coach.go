package coach

import (
	"context"
	"strings"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

type CoachClient interface {
	GetCoachResponse(ctx context.Context, message string, localTime string, tasks []*domain.Task, memories []*domain.MemoryItem, history []*domain.ChatMessage) (*domain.CoachReply, error)
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
	Message string `json:"message" binding:"required,max=4000"`
	// LocalTime: giờ hiện tại của máy (RFC3339 kèm offset) để AI quy "thứ 2 tuần sau" ra ngày cụ thể.
	LocalTime string `json:"local_time" binding:"max=40"`
}

type ChatResponse = domain.CoachReply

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
func (u *CoachUseCase) Chat(ctx context.Context, userID string, messageText string, localTime string) (*domain.CoachReply, error) {
	// Lấy lịch sử TRƯỚC khi lưu tin mới: tin hiện tại được gửi riêng cho LLM, nếu lấy sau khi lưu
	// thì nó nằm cả trong history lẫn message → prompt bị lặp.
	history, _ := u.chatRepo.GetHistory(ctx, userID, 10)

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

	// 4. Chat history (10 tin gần nhất) đã lấy ở đầu hàm

	// 5. Query OpenAI Coach Client
	reply, err := u.aiClient.GetCoachResponse(ctx, messageText, localTime, activeTasks, relevantMemories, history)
	if err != nil {
		return nil, err
	}
	if reply == nil {
		reply = &domain.CoachReply{}
	}
	reply.Actions = sanitizeActions(reply.Actions, activeTasks)
	replyText := reply.Reply

	// 6. Save assistant message to database
	assistantMsg := &domain.ChatMessage{
		ID:        uuid.New().String(),
		UserID:    userID,
		Role:      "assistant",
		Content:   replyText,
		CreatedAt: time.Now(),
	}
	_ = u.chatRepo.Save(ctx, assistantMsg)

	return reply, nil
}

// History trả về tối đa limit tin nhắn gần nhất theo thứ tự thời gian (cũ → mới) để app hiển thị lại cuộc trò chuyện.
func (u *CoachUseCase) History(ctx context.Context, userID string, limit int) ([]*domain.ChatMessage, error) {
	messages, err := u.chatRepo.GetHistory(ctx, userID, limit)
	if err != nil {
		return nil, err
	}
	if messages == nil {
		messages = []*domain.ChatMessage{}
	}
	return messages, nil
}

// sanitizeActions bỏ các đề xuất không dùng được: loại lạ, thiếu dữ liệu bắt buộc, hoặc nhắc tới task không
// nằm trong danh sách việc đang mở của CHÍNH người dùng (LLM có thể bịa id). Giữ tối đa MaxCoachActions.
func sanitizeActions(actions []domain.CoachAction, active []*domain.Task) []domain.CoachAction {
	known := make(map[string]bool, len(active))
	for _, t := range active {
		known[t.ID] = true
	}
	validPriority := map[string]bool{"LOW": true, "MEDIUM": true, "HIGH": true}
	validDate := func(s string) bool {
		_, err := time.Parse(time.RFC3339, s)
		return err == nil
	}

	out := []domain.CoachAction{}
	for _, a := range actions {
		if len(out) == domain.MaxCoachActions {
			break
		}
		ids := make([]string, 0, len(a.TaskIDs))
		for _, id := range a.TaskIDs {
			if known[id] {
				ids = append(ids, id)
			}
		}
		a.TaskIDs = ids
		a.Label = strings.TrimSpace(a.Label)
		if a.Label == "" {
			continue
		}

		ok := false
		switch a.Type {
		case domain.CoachActionReschedule:
			ok = len(ids) > 0 && validDate(a.DueDate)
		case domain.CoachActionAddSubtasks:
			clean := make([]string, 0, len(a.Subtasks))
			for _, s := range a.Subtasks {
				if s = strings.TrimSpace(s); s != "" && len(clean) < 10 {
					clean = append(clean, s)
				}
			}
			a.Subtasks = clean
			ok = len(ids) == 1 && len(clean) > 0
		case domain.CoachActionCreateTask:
			a.Title = strings.TrimSpace(a.Title)
			if a.Priority == "" {
				a.Priority = "MEDIUM"
			}
			ok = a.Title != "" && validPriority[a.Priority] && (a.DueDate == "" || validDate(a.DueDate))
		case domain.CoachActionSetPriority:
			ok = len(ids) > 0 && validPriority[a.Priority]
		case domain.CoachActionAddToMyDay:
			ok = len(ids) > 0
		}
		if ok {
			out = append(out, a)
		}
	}
	return out
}
