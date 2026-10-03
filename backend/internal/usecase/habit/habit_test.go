package habit

import (
	"context"
	"errors"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type fakeRepo struct {
	habits   map[string]*domain.Habit
	checkins map[string]domain.HabitCheckin // habitID|date
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{habits: map[string]*domain.Habit{}, checkins: map[string]domain.HabitCheckin{}}
}

func (f *fakeRepo) Upsert(_ context.Context, h *domain.Habit) error {
	if old, ok := f.habits[h.ID]; ok && old.UserID != h.UserID {
		return domain.ErrNotFound
	}
	c := *h
	f.habits[h.ID] = &c
	return nil
}

func (f *fakeRepo) GetByID(_ context.Context, id string) (*domain.Habit, error) {
	h, ok := f.habits[id]
	if !ok {
		return nil, domain.ErrNotFound
	}
	c := *h
	return &c, nil
}

func (f *fakeRepo) ListChangedSince(_ context.Context, userID string, since *time.Time) ([]*domain.Habit, error) {
	var out []*domain.Habit
	for _, h := range f.habits {
		if h.UserID == userID && (since == nil && !h.IsDeleted() || since != nil && h.UpdatedAt.After(*since)) {
			c := *h
			out = append(out, &c)
		}
	}
	return out, nil
}

func (f *fakeRepo) SoftDelete(_ context.Context, id string, at time.Time) error {
	if h, ok := f.habits[id]; ok && h.DeletedAt == nil {
		h.DeletedAt = &at
		h.UpdatedAt = at
	}
	return nil
}

func (f *fakeRepo) SetCheckin(_ context.Context, _ string, c domain.HabitCheckin) error {
	f.checkins[c.HabitID+"|"+c.Date] = c
	return nil
}

func (f *fakeRepo) CheckinsChangedSince(_ context.Context, _ string, since *time.Time) ([]domain.HabitCheckin, error) {
	var out []domain.HabitCheckin
	for _, c := range f.checkins {
		if since == nil && c.Done || since != nil && c.UpdatedAt.After(*since) {
			out = append(out, c)
		}
	}
	return out, nil
}

const habitA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

func newUseCase(now time.Time) (*HabitUseCase, *time.Time) {
	uc := NewHabitUseCase(newFakeRepo())
	clock := now
	uc.now = func() time.Time { return clock }
	return uc, &clock
}

func TestSave_ValidatesAndNormalizesFrequency(t *testing.T) {
	uc, _ := newUseCase(time.Date(2026, 10, 2, 8, 0, 0, 0, time.UTC))
	ctx := context.Background()

	h, err := uc.Save(ctx, habitA, "u", HabitInput{Name: " Chạy bộ ", Frequency: "WEEKDAYS", Weekdays: "fri, mon,MON", ReminderTime: "6:30"})
	if err != nil {
		t.Fatal(err)
	}
	if h.Name != "Chạy bộ" || h.Weekdays != "MON,FRI" || h.TargetPerWeek != 2 || h.ReminderTime != "06:30" {
		t.Fatalf("not normalized: %+v", h)
	}

	cases := []HabitInput{
		{Name: "  "},
		{Name: "X", Frequency: "WEEKDAYS"},
		{Name: "X", Frequency: "WEEKDAYS", Weekdays: "MON,FUNDAY"},
		{Name: "X", Frequency: "WEEKLY", TargetPerWeek: 0},
		{Name: "X", ReminderTime: "25:00"},
		{Name: "X", Color: "blue"},
	}
	for i, in := range cases {
		var v *domain.ValidationError
		if _, err := uc.Save(ctx, habitA, "u", in); !errors.As(err, &v) {
			t.Fatalf("case %d: expected validation error, got %v", i, err)
		}
	}

	daily, _ := uc.Save(ctx, habitA, "u", HabitInput{Name: "Uống nước", Weekdays: "MON"})
	if daily.Frequency != domain.HabitDaily || daily.Weekdays != "" || daily.TargetPerWeek != 7 {
		t.Fatalf("daily habit must ignore weekdays: %+v", daily)
	}
}

func TestOtherUsersHabitLooksNotFound(t *testing.T) {
	uc, _ := newUseCase(time.Date(2026, 10, 2, 8, 0, 0, 0, time.UTC))
	ctx := context.Background()
	if _, err := uc.Save(ctx, habitA, "owner", HabitInput{Name: "Đọc sách"}); err != nil {
		t.Fatal(err)
	}
	if _, err := uc.Save(ctx, habitA, "intruder", HabitInput{Name: "Hack"}); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("save: want ErrNotFound, got %v", err)
	}
	if _, err := uc.SetCheckin(ctx, "intruder", habitA, "2026-10-02", true); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("checkin: want ErrNotFound, got %v", err)
	}
	if err := uc.Delete(ctx, habitA, "intruder"); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("delete: want ErrNotFound, got %v", err)
	}
}

func TestCheckinsAndDeleteAreReportedBySync(t *testing.T) {
	start := time.Date(2026, 10, 2, 8, 0, 0, 0, time.UTC)
	uc, clock := newUseCase(start)
	ctx := context.Background()
	uc.Save(ctx, habitA, "u", HabitInput{Name: "Thiền"})

	if _, err := uc.SetCheckin(ctx, "u", habitA, "2026-10-04", true); err == nil {
		t.Fatal("check-in two days ahead must be rejected")
	}
	if _, err := uc.SetCheckin(ctx, "u", habitA, "02/10/2026", true); err == nil {
		t.Fatal("bad date format must be rejected")
	}
	if _, err := uc.SetCheckin(ctx, "u", habitA, "2026-10-02", true); err != nil {
		t.Fatal(err)
	}

	first, _ := uc.Changes(ctx, "u", nil)
	if len(first.Habits) != 1 || len(first.Checkins) != 1 || !first.Checkins[0].Done {
		t.Fatalf("initial sync: %+v", first)
	}

	*clock = start.Add(time.Hour)
	uc.SetCheckin(ctx, "u", habitA, "2026-10-02", false) // bỏ đánh dấu
	uc.Delete(ctx, habitA, "u")
	if err := uc.Delete(ctx, habitA, "u"); err != nil {
		t.Fatalf("deleting twice must succeed: %v", err)
	}

	next, _ := uc.Changes(ctx, "u", &first.ServerTime)
	if len(next.DeletedIDs) != 1 || len(next.Habits) != 0 {
		t.Fatalf("delete must be reported: %+v", next)
	}
	if len(next.Checkins) != 1 || next.Checkins[0].Done {
		t.Fatalf("un-check must be reported: %+v", next.Checkins)
	}

	if _, err := uc.SetCheckin(ctx, "u", habitA, "2026-10-02", true); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("checking in a deleted habit: want ErrNotFound, got %v", err)
	}
	// Ghi lại (Hoàn tác xóa) → sống lại
	if _, err := uc.Save(ctx, habitA, "u", HabitInput{Name: "Thiền"}); err != nil {
		t.Fatal(err)
	}
	if again, _ := uc.Changes(ctx, "u", nil); len(again.Habits) != 1 {
		t.Fatal("re-saving must resurrect the habit")
	}
}
