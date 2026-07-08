package memory

import (
	"context"
	"log"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

// Ngưỡng tương đồng Cosine: insight mới có điểm giống cao hơn mức này so với trí nhớ đã có
// sẽ bị coi là trùng và bỏ qua (1.0 = giống hệt).
const duplicateSimilarityThreshold = 0.90

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

// ExtractionResult tóm tắt một lượt phân tích trí nhớ.
type ExtractionResult struct {
	Analyzed int // số bản ghi đầu vào (log hoạt động + tin nhắn chat) được đưa vào phân tích
	Saved    int // số trí nhớ mới (không trùng) thực sự được lưu
}

// ExtractAndStoreMemories là "vòng học thói quen": đọc hoạt động gần đây → LLM rút ra quan sát →
// embedding từng câu → KHỬ TRÙNG bằng vector → lưu vào Qdrant. Đây là nơi ghép mọi mảnh AI lại.
//
// Luồng 4 bước: (1) lấy log + chat trong lookback → (2) LLM ExtractMemories → (3) embedding mỗi câu →
// (4) MaxSimilarity < ngưỡng thì Save (nếu đã có câu rất giống thì bỏ qua để khỏi trùng).
//
// Tham số (một trường hợp minh họa):
//
//	userID   = "u1"
//	lookback = 30 * 24 * time.Hour   // phân tích hoạt động trong 30 ngày gần nhất
//
// Kết quả trả về:
//
//	ExtractionResult{Analyzed: 12, Saved: 2}
//	// đã xét 12 bản ghi (log + chat), rút ra vài quan sát nhưng chỉ 2 cái đủ mới để lưu
func (u *MemoryUseCase) ExtractAndStoreMemories(ctx context.Context, userID string, lookback time.Duration) (ExtractionResult, error) {
	// 1. Fetch activities within the lookback window
	since := time.Now().Add(-lookback)
	logs, err := u.taskRepo.ListLogs(ctx, userID, since)
	if err != nil {
		return ExtractionResult{}, err
	}

	// 2. Fetch latest chat history (up to 30 messages)
	messages, err := u.chatRepo.GetHistory(ctx, userID, 30)
	if err != nil {
		return ExtractionResult{}, err
	}

	analyzed := len(logs) + len(messages)

	// If no activities occurred, skip extraction
	if analyzed == 0 {
		return ExtractionResult{}, nil
	}

	// 3. Extract insights using LLM
	insights, err := u.aiClient.ExtractMemories(ctx, logs, messages)
	if err != nil {
		return ExtractionResult{Analyzed: analyzed}, err
	}

	// 4. Create vector embedding and store each insight in Qdrant (bỏ qua các quan sát trùng lặp)
	saved := 0
	for _, insight := range insights {
		vector, err := u.embedService.CreateEmbedding(ctx, insight)
		if err != nil {
			log.Printf("Failed to generate embedding for memory insight '%s': %v", insight, err)
			continue
		}

		// Khử trùng theo ngữ nghĩa: nếu đã tồn tại trí nhớ rất giống thì bỏ qua.
		// Lưu wait=true nên các insight vừa lưu trong cùng lượt cũng được tính (chống trùng nội bộ batch).
		similarity, simErr := u.memoryRepo.MaxSimilarity(ctx, userID, vector)
		if simErr != nil {
			// Không chặn việc lưu chỉ vì bước kiểm tra trùng lỗi (vd Qdrant tạm thời trục trặc)
			log.Printf("Similarity check failed for insight '%s': %v. Proceeding to save.", insight, simErr)
		} else if similarity >= duplicateSimilarityThreshold {
			log.Printf("Skipping duplicate memory insight (similarity %.3f): %s", similarity, insight)
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
		saved++
	}

	return ExtractionResult{Analyzed: analyzed, Saved: saved}, nil
}
