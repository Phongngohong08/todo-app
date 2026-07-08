package quickadd

import (
	"context"
	"errors"
	"strings"
	"todo-backend/internal/domain"
)

// TaskParser được hiện thực bởi GeminiClient (mirror pattern MemoryClient trong usecase memory).
type TaskParser interface {
	ParseTask(ctx context.Context, text string, nowContext string) (*domain.ParsedTask, error)
}

type QuickAddUseCase struct {
	parser TaskParser
}

func NewQuickAddUseCase(parser TaskParser) *QuickAddUseCase {
	return &QuickAddUseCase{parser: parser}
}

// Parse chuyển câu ngôn ngữ tự nhiên thành task có cấu trúc (chỉ TRẢ VỀ, chưa lưu DB — app sẽ cho
// người dùng xác nhận rồi mới tạo). Chặn câu rỗng, còn lại ủy quyền cho LLM ParseTask.
//
// Tham số (một trường hợp minh họa):
//
//	text       = "nhắc tôi uống thuốc lúc 8h tối nay"
//	nowContext = "2026-07-08T09:00:00+07:00"
//
// Kết quả trả về:
//
//	&domain.ParsedTask{Title:"Uống thuốc", Priority:"MEDIUM",
//	                   DueDate:"2026-07-08T20:00:00+07:00", Category:"PERSONAL"}
func (u *QuickAddUseCase) Parse(ctx context.Context, text string, nowContext string) (*domain.ParsedTask, error) {
	if strings.TrimSpace(text) == "" {
		return nil, errors.New("text is empty")
	}
	return u.parser.ParseTask(ctx, text, nowContext)
}
