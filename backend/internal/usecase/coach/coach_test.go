package coach

import (
	"context"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type memChatRepo struct{ msgs []*domain.ChatMessage }

func (r *memChatRepo) Save(ctx context.Context, m *domain.ChatMessage) error {
	r.msgs = append(r.msgs, m)
	return nil
}
func (r *memChatRepo) GetHistory(ctx context.Context, userID string, limit int) ([]*domain.ChatMessage, error) {
	out := append([]*domain.ChatMessage(nil), r.msgs...)
	if len(out) > limit {
		out = out[len(out)-limit:]
	}
	return out, nil
}

type emptyTaskRepo struct{ domain.TaskRepository }

func (emptyTaskRepo) List(ctx context.Context, userID string, f domain.TaskFilter) ([]*domain.Task, error) {
	return nil, nil
}

type capturingCoach struct {
	message string
	history []*domain.ChatMessage
}

func (c *capturingCoach) GetCoachResponse(ctx context.Context, message string, tasks []*domain.Task, memories []*domain.MemoryItem, history []*domain.ChatMessage) (string, error) {
	c.message = message
	c.history = history
	return "reply", nil
}

func TestChat_CurrentMessageNotDuplicatedInHistory(t *testing.T) {
	chat := &memChatRepo{msgs: []*domain.ChatMessage{
		{UserID: "u1", Role: "user", Content: "tin cũ", CreatedAt: time.Now().Add(-time.Minute)},
		{UserID: "u1", Role: "assistant", Content: "trả lời cũ", CreatedAt: time.Now().Add(-time.Minute)},
	}}
	ai := &capturingCoach{}
	uc := NewCoachUseCase(chat, emptyTaskRepo{}, nil, nil, ai)

	if _, err := uc.Chat(context.Background(), "u1", "tin mới"); err != nil {
		t.Fatalf("chat: %v", err)
	}

	if ai.message != "tin mới" {
		t.Fatalf("expected current message to be sent, got %q", ai.message)
	}
	for _, h := range ai.history {
		if h.Content == "tin mới" {
			t.Fatal("current message must not also appear in history (prompt duplication)")
		}
	}
	if len(ai.history) != 2 {
		t.Fatalf("expected the 2 previous messages as history, got %d", len(ai.history))
	}
	if len(chat.msgs) != 4 {
		t.Fatalf("expected user + assistant messages to be saved, got %d total", len(chat.msgs))
	}
}
