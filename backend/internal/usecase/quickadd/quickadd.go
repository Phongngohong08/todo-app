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

// Parse chuyển câu ngôn ngữ tự nhiên thành task có cấu trúc (chưa lưu DB).
func (u *QuickAddUseCase) Parse(ctx context.Context, text string, nowContext string) (*domain.ParsedTask, error) {
	if strings.TrimSpace(text) == "" {
		return nil, errors.New("text is empty")
	}
	return u.parser.ParseTask(ctx, text, nowContext)
}
