package plan

import (
	"context"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type stubTaskRepo struct {
	domain.TaskRepository
	tasks []*domain.Task
}

func (s stubTaskRepo) List(ctx context.Context, userID string, f domain.TaskFilter) ([]*domain.Task, error) {
	var out []*domain.Task
	for _, t := range s.tasks {
		if f.Status == "" || t.Status == f.Status {
			out = append(out, t)
		}
	}
	return out, nil
}

type stubUserRepo struct{ domain.UserRepository }

func (stubUserRepo) GetPreferences(ctx context.Context, userID string) (*domain.UserPreferences, error) {
	return nil, nil
}

type countingPlanRepo struct{ saved int }

func (r *countingPlanRepo) Save(ctx context.Context, p *domain.DailyPlan) error {
	r.saved++
	return nil
}
func (r *countingPlanRepo) GetByDate(ctx context.Context, userID string, d time.Time) (*domain.DailyPlan, error) {
	return nil, nil
}

type countingAI struct{ calls int }

func (a *countingAI) GenerateDailyPlan(ctx context.Context, tasks []*domain.Task, prefs *domain.UserPreferences, memories []*domain.MemoryItem, localTime string) ([]domain.PlanSlot, error) {
	a.calls++
	return []domain.PlanSlot{{StartTime: "09:00", EndTime: "10:00", TaskID: tasks[0].ID, Title: tasks[0].Title}}, nil
}

func TestGenerate_NoActiveTasksSkipsAI(t *testing.T) {
	tasks := stubTaskRepo{tasks: []*domain.Task{{ID: "t1", Status: domain.StatusCompleted}}}
	plans := &countingPlanRepo{}
	ai := &countingAI{}
	uc := NewPlanUseCase(plans, tasks, stubUserRepo{}, nil, ai)

	plan, err := uc.Generate(context.Background(), "u1", time.Now(), "")
	if err != nil {
		t.Fatalf("generate: %v", err)
	}
	if ai.calls != 0 {
		t.Fatal("AI must not be called when there are no active tasks")
	}
	if plans.saved != 0 {
		t.Fatal("empty plan must not be saved (so it can be generated later when tasks exist)")
	}
	if plan.PlanData == nil || len(plan.PlanData) != 0 {
		t.Fatalf("expected empty (non-nil) plan data, got %#v", plan.PlanData)
	}

	has, err := uc.HasActiveTasks(context.Background(), "u1")
	if err != nil || has {
		t.Fatalf("HasActiveTasks = %v, %v; want false, nil", has, err)
	}
}

func TestGenerate_WithActiveTasksCallsAIAndSaves(t *testing.T) {
	tasks := stubTaskRepo{tasks: []*domain.Task{{ID: "t1", Title: "Viết báo cáo", Status: domain.StatusTodo}}}
	plans := &countingPlanRepo{}
	ai := &countingAI{}
	uc := NewPlanUseCase(plans, tasks, stubUserRepo{}, nil, ai)

	if _, err := uc.Generate(context.Background(), "u1", time.Now(), "10:00"); err != nil {
		t.Fatalf("generate: %v", err)
	}
	if ai.calls != 1 || plans.saved != 1 {
		t.Fatalf("expected 1 AI call and 1 save, got %d and %d", ai.calls, plans.saved)
	}
}
