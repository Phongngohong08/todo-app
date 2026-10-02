package assist

import (
	"context"
	"errors"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type fakeClient struct {
	steps    []string
	minutes  int
	examples []domain.EstimateExample
}

func (f *fakeClient) BreakdownTask(ctx context.Context, title, description string) ([]string, int, error) {
	return f.steps, f.minutes, nil
}

func (f *fakeClient) EstimateTask(ctx context.Context, title, description string, examples []domain.EstimateExample) (int, error) {
	f.examples = examples
	return f.minutes, nil
}

type listRepo struct {
	domain.TaskRepository
	tasks []*domain.Task
}

func (r listRepo) List(ctx context.Context, userID string, f domain.TaskFilter) ([]*domain.Task, error) {
	return r.tasks, nil
}

func TestBreakdown_CleansAndLimitsSteps(t *testing.T) {
	steps := []string{"1. Tìm tài liệu", " - Viết dàn ý ", "", "tìm tài liệu", "• Viết mở bài", "2) 5 phút khởi động"}
	for i := 0; i < 20; i++ {
		steps = append(steps, "Bước phụ "+string(rune('A'+i)))
	}
	uc := NewAssistUseCase(&fakeClient{steps: steps, minutes: 93}, nil)

	got, err := uc.Breakdown(context.Background(), "Viết tiểu luận", "")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Subtasks) != MaxSubtasks {
		t.Fatalf("expected %d steps, got %d: %v", MaxSubtasks, len(got.Subtasks), got.Subtasks)
	}
	if got.Subtasks[0] != "Tìm tài liệu" || got.Subtasks[1] != "Viết dàn ý" || got.Subtasks[2] != "Viết mở bài" {
		t.Fatalf("numbering/bullets should be stripped and duplicates dropped, got %v", got.Subtasks[:3])
	}
	if got.Subtasks[3] != "5 phút khởi động" {
		t.Fatalf("a meaningful leading number must be kept, got %q", got.Subtasks[3])
	}
	if got.EstimatedMinutes != 95 {
		t.Fatalf("estimate should snap to 5 minutes, got %d", got.EstimatedMinutes)
	}
}

func TestBreakdown_RejectsEmptyTitleAndEmptyResult(t *testing.T) {
	uc := NewAssistUseCase(&fakeClient{steps: []string{"  ", ""}}, nil)
	var ve *domain.ValidationError
	if _, err := uc.Breakdown(context.Background(), "  ", ""); !errors.As(err, &ve) {
		t.Fatalf("empty title must be a validation error, got %v", err)
	}
	if _, err := uc.Breakdown(context.Background(), "Dọn nhà", ""); !errors.As(err, &ve) {
		t.Fatalf("no usable steps must be a validation error, got %v", err)
	}
}

func TestEstimate_UsesRecentEstimatedTasksAsExamples(t *testing.T) {
	now := time.Now()
	repo := listRepo{tasks: []*domain.Task{
		{Title: "Không có ước tính"},
		{Title: "Cũ", EstimatedMinutes: 30, UpdatedAt: now.Add(-time.Hour)},
		{Title: "Mới", EstimatedMinutes: 45, UpdatedAt: now},
	}}
	client := &fakeClient{minutes: 2000}
	uc := NewAssistUseCase(client, repo)

	got, err := uc.Estimate(context.Background(), "u1", "Viết báo cáo", "")
	if err != nil {
		t.Fatal(err)
	}
	if got != maxEstimate {
		t.Fatalf("estimate should be clamped to %d, got %d", maxEstimate, got)
	}
	if len(client.examples) != 2 || client.examples[0].Title != "Mới" {
		t.Fatalf("examples should be estimated tasks, newest first, got %+v", client.examples)
	}
}
