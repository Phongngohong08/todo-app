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

func (c *capturingCoach) GetCoachResponse(ctx context.Context, message string, localTime string, tasks []*domain.Task, memories []*domain.MemoryItem, history []*domain.ChatMessage) (*domain.CoachReply, error) {
	c.message = message
	c.history = history
	return &domain.CoachReply{Reply: "reply"}, nil
}

func TestChat_CurrentMessageNotDuplicatedInHistory(t *testing.T) {
	chat := &memChatRepo{msgs: []*domain.ChatMessage{
		{UserID: "u1", Role: "user", Content: "tin cũ", CreatedAt: time.Now().Add(-time.Minute)},
		{UserID: "u1", Role: "assistant", Content: "trả lời cũ", CreatedAt: time.Now().Add(-time.Minute)},
	}}
	ai := &capturingCoach{}
	uc := NewCoachUseCase(chat, emptyTaskRepo{}, nil, nil, ai)

	if _, err := uc.Chat(context.Background(), "u1", "tin mới", ""); err != nil {
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

// LLM có thể bịa id hoặc trả hành động thiếu dữ liệu — chỉ giữ đề xuất áp dụng được lên việc của chính người dùng.
func TestSanitizeActions_DropsUnknownTasksAndInvalidActions(t *testing.T) {
	active := []*domain.Task{{ID: "t1"}, {ID: "t2"}}
	got := sanitizeActions([]domain.CoachAction{
		{Type: domain.CoachActionReschedule, Label: "Dời", TaskIDs: []string{"t1", "ghost"}, DueDate: "2026-07-13T09:00:00+07:00"},
		{Type: domain.CoachActionReschedule, Label: "Dời việc lạ", TaskIDs: []string{"ghost"}, DueDate: "2026-07-13T09:00:00+07:00"},
		{Type: domain.CoachActionSetPriority, Label: "Ưu tiên", TaskIDs: []string{"t2"}, Priority: "URGENT"},
		{Type: "DELETE_ALL", Label: "Xóa hết", TaskIDs: []string{"t1"}},
		{Type: domain.CoachActionAddSubtasks, Label: "Chia nhỏ", TaskIDs: []string{"t2"}, Subtasks: []string{" Bước 1 ", ""}},
		{Type: domain.CoachActionCreateTask, Label: "Tạo", Title: "Đi bộ 15 phút"},
	}, active)

	if len(got) != domain.MaxCoachActions {
		t.Fatalf("expected %d actions, got %d: %+v", domain.MaxCoachActions, len(got), got)
	}
	if got[0].Type != domain.CoachActionReschedule || len(got[0].TaskIDs) != 1 || got[0].TaskIDs[0] != "t1" {
		t.Fatalf("unknown task id should be stripped, got %+v", got[0])
	}
	if got[1].Type != domain.CoachActionAddSubtasks || len(got[1].Subtasks) != 1 || got[1].Subtasks[0] != "Bước 1" {
		t.Fatalf("subtasks should be trimmed and blanks dropped, got %+v", got[1])
	}
	if got[2].Type != domain.CoachActionCreateTask || got[2].Priority != "MEDIUM" {
		t.Fatalf("create task should default priority, got %+v", got[2])
	}
}
