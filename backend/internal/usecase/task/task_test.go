package task

import (
	"context"
	"errors"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

// MockTaskRepository mô phỏng Postgres: lưu bản sao để caller không sửa trực tiếp dữ liệu "trong DB".
type MockTaskRepository struct {
	tasks map[string]*domain.Task
	logs  []*domain.TaskLog
}

func NewMockTaskRepository() *MockTaskRepository {
	return &MockTaskRepository{tasks: make(map[string]*domain.Task)}
}

func clone(t *domain.Task) *domain.Task {
	c := *t
	c.Subtasks = append([]domain.Subtask(nil), t.Subtasks...)
	return &c
}

func (m *MockTaskRepository) Upsert(ctx context.Context, task *domain.Task) error {
	if old, ok := m.tasks[task.ID]; ok && old.UserID != task.UserID {
		return domain.ErrTaskNotFound
	}
	m.tasks[task.ID] = clone(task)
	return nil
}

func (m *MockTaskRepository) GetByID(ctx context.Context, id string) (*domain.Task, error) {
	t, ok := m.tasks[id]
	if !ok {
		return nil, domain.ErrTaskNotFound
	}
	return clone(t), nil
}

func (m *MockTaskRepository) List(ctx context.Context, userID string, filter domain.TaskFilter) ([]*domain.Task, error) {
	var result []*domain.Task
	for _, t := range m.tasks {
		if t.UserID == userID && !t.IsDeleted() && (filter.Status == "" || t.Status == filter.Status) {
			result = append(result, clone(t))
		}
	}
	return result, nil
}

func (m *MockTaskRepository) ListChangedSince(ctx context.Context, userID string, since *time.Time) ([]*domain.Task, error) {
	var result []*domain.Task
	for _, t := range m.tasks {
		if t.UserID != userID {
			continue
		}
		if since == nil && !t.IsDeleted() || since != nil && t.UpdatedAt.After(*since) {
			result = append(result, clone(t))
		}
	}
	return result, nil
}

func (m *MockTaskRepository) ListMyDay(ctx context.Context, userID string, day string) ([]*domain.Task, error) {
	var result []*domain.Task
	for _, t := range m.tasks {
		if t.UserID == userID && !t.IsDeleted() && t.Status == domain.StatusTodo && t.MyDay != nil && *t.MyDay == day {
			result = append(result, clone(t))
		}
	}
	return result, nil
}

func (m *MockTaskRepository) SoftDelete(ctx context.Context, id string, at time.Time) error {
	if t, ok := m.tasks[id]; ok && t.DeletedAt == nil {
		t.DeletedAt = &at
		t.UpdatedAt = at
	}
	return nil
}

func (m *MockTaskRepository) CreateLog(ctx context.Context, log *domain.TaskLog) error {
	m.logs = append(m.logs, log)
	return nil
}

func (m *MockTaskRepository) ListLogs(ctx context.Context, userID string, since time.Time) ([]*domain.TaskLog, error) {
	var result []*domain.TaskLog
	for _, l := range m.logs {
		if l.UserID == userID && l.CreatedAt.After(since) {
			result = append(result, l)
		}
	}
	return result, nil
}

var vn = mustLoad("Asia/Ho_Chi_Minh")

func mustLoad(name string) *time.Location {
	loc, err := time.LoadLocation(name)
	if err != nil {
		return time.FixedZone("ICT", 7*3600)
	}
	return loc
}

// newTestUseCase trả về usecase với đồng hồ điều khiển được.
func newTestUseCase(now time.Time) (*TaskUseCase, *MockTaskRepository, *time.Time) {
	repo := NewMockTaskRepository()
	uc := NewTaskUseCase(repo, vn)
	clock := now
	uc.now = func() time.Time { return clock }
	return uc, repo, &clock
}

const (
	taskA = "11111111-1111-4111-8111-111111111111"
	taskB = "22222222-2222-4222-8222-222222222222"
)

func TestCreate_WithClientIDIsIdempotent(t *testing.T) {
	uc, repo, _ := newTestUseCase(time.Date(2026, 7, 8, 9, 0, 0, 0, vn))
	ctx := context.Background()
	in := TaskInput{ID: taskA, Title: "Learn Go", Priority: "HIGH"}

	first, err := uc.Create(ctx, "user-1", in)
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if first.ID != taskA || first.Status != domain.StatusTodo {
		t.Fatalf("unexpected task %+v", first)
	}
	// Gửi lại (vd mất mạng sau khi server đã ghi) không tạo bản thứ hai
	if _, err := uc.Create(ctx, "user-1", in); err != nil {
		t.Fatalf("retry create: %v", err)
	}
	if len(repo.tasks) != 1 {
		t.Fatalf("expected 1 task after retry, got %d", len(repo.tasks))
	}
	created := 0
	for _, l := range repo.logs {
		if l.Action == domain.ActionCreated {
			created++
		}
	}
	if created != 1 {
		t.Fatalf("expected exactly 1 CREATED log, got %d", created)
	}
}

func TestSave_KeepsFieldsOldClientsDoNotSend(t *testing.T) {
	uc, _, _ := newTestUseCase(time.Now())
	ctx := context.Background()
	order := 42.0
	_, err := uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Có checklist", Priority: "LOW", SortOrder: &order,
		Subtasks: []domain.Subtask{{ID: taskB, Title: "Bước 1"}},
	})
	if err != nil {
		t.Fatal(err)
	}

	// Client cũ chỉ gửi các trường cơ bản
	updated, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Đổi tên", Priority: "HIGH"})
	if err != nil {
		t.Fatal(err)
	}
	if updated.SortOrder != 42 || len(updated.Subtasks) != 1 {
		t.Fatalf("sort order / subtasks must be preserved, got %v / %d", updated.SortOrder, len(updated.Subtasks))
	}
}

func TestSave_CompleteUsesClientTimestampAndReopenClearsIt(t *testing.T) {
	now := time.Date(2026, 7, 8, 12, 0, 0, 0, vn)
	uc, _, _ := newTestUseCase(now)
	ctx := context.Background()
	uc.Save(ctx, taskA, "u", TaskInput{Title: "Việc", Priority: "LOW"})

	offline := now.Add(-3 * time.Hour) // hoàn thành lúc offline, 3 giờ trước khi đồng bộ
	done, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Việc", Priority: "LOW", Status: "COMPLETED", CompletedAt: &offline})
	if err != nil {
		t.Fatal(err)
	}
	if done.CompletedAt == nil || !done.CompletedAt.Equal(offline) {
		t.Fatalf("completed_at should be the device time, got %v", done.CompletedAt)
	}

	reopened, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Việc", Priority: "LOW", Status: "TODO"})
	if err != nil {
		t.Fatal(err)
	}
	if reopened.Status != domain.StatusTodo || reopened.CompletedAt != nil {
		t.Fatalf("reopen should clear completion, got %s %v", reopened.Status, reopened.CompletedAt)
	}
}

func TestSave_FutureCompletedAtIsClampedToNow(t *testing.T) {
	now := time.Date(2026, 7, 8, 12, 0, 0, 0, vn)
	uc, _, _ := newTestUseCase(now)
	future := now.Add(24 * time.Hour)
	done, _ := uc.Save(context.Background(), taskA, "u", TaskInput{Title: "x", Priority: "LOW", Status: "COMPLETED", CompletedAt: &future})
	if !done.CompletedAt.Equal(now) {
		t.Fatalf("far-future completed_at should become server now, got %v", done.CompletedAt)
	}
}

func TestComplete_RecurringSpawnsNextOccurrenceInFuture(t *testing.T) {
	now := time.Date(2026, 7, 8, 10, 0, 0, 0, vn) // thứ Tư
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 7, 5, 8, 0, 0, 0, vn) // quá hạn 3 ngày
	uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Uống thuốc", Priority: "HIGH", DueDate: &due, Recurrence: "DAILY",
		Subtasks: []domain.Subtask{{ID: taskB, Title: "Sáng", Done: true}},
	})

	if _, err := uc.Complete(ctx, taskA, "u"); err != nil {
		t.Fatal(err)
	}

	child, ok := repo.tasks[domain.NextOccurrenceID(taskA)]
	if !ok {
		t.Fatal("next occurrence was not created")
	}
	want := time.Date(2026, 7, 9, 8, 0, 0, 0, vn) // 8h sáng mai — không phải 6/7 (vẫn ở quá khứ)
	if !child.DueDate.Equal(want) {
		t.Fatalf("next due = %v, want %v", child.DueDate.In(vn), want)
	}
	if child.SpawnedFrom == nil || *child.SpawnedFrom != taskA {
		t.Fatal("child must remember its parent")
	}
	if len(child.Subtasks) != 1 || child.Subtasks[0].Done || child.Subtasks[0].ID != domain.NextOccurrenceID(taskB) {
		t.Fatalf("checklist should be copied unchecked with deterministic ids, got %+v", child.Subtasks)
	}

	// Hoàn thành lại (gửi lại request) không sinh thêm bản
	if _, err := uc.Complete(ctx, taskA, "u"); !errors.Is(err, domain.ErrInvalidStatusTrans) {
		t.Fatalf("completing twice should be rejected, got %v", err)
	}
	if len(repo.tasks) != 2 {
		t.Fatalf("expected parent + 1 child, got %d tasks", len(repo.tasks))
	}
}

func TestComplete_DoesNotDuplicateOccurrenceCreatedOffline(t *testing.T) {
	now := time.Date(2026, 7, 8, 10, 0, 0, 0, vn)
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 7, 8, 8, 0, 0, 0, vn)
	uc.Save(ctx, taskA, "u", TaskInput{Title: "Tập gym", Priority: "LOW", DueDate: &due, Recurrence: "DAILY"})

	// App đã tự sinh lần lặp (cùng id tất định) và đẩy lên TRƯỚC task cha
	childID := domain.NextOccurrenceID(taskA)
	parent := taskA
	clientDue := time.Date(2026, 7, 9, 8, 0, 0, 0, vn)
	uc.Save(ctx, childID, "u", TaskInput{Title: "Tập gym (sửa trên máy)", Priority: "LOW", DueDate: &clientDue, Recurrence: "DAILY", SpawnedFrom: &parent})

	uc.Save(ctx, taskA, "u", TaskInput{Title: "Tập gym", Priority: "LOW", DueDate: &due, Recurrence: "DAILY", Status: "COMPLETED"})

	if len(repo.tasks) != 2 {
		t.Fatalf("expected no duplicate occurrence, got %d tasks", len(repo.tasks))
	}
	if repo.tasks[childID].Title != "Tập gym (sửa trên máy)" {
		t.Fatal("server must not overwrite the occurrence the app already created")
	}
}

func TestReopen_RetractsUntouchedOccurrence(t *testing.T) {
	now := time.Date(2026, 7, 8, 10, 0, 0, 0, vn)
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := now.Add(time.Hour)
	in := TaskInput{Title: "Đọc sách", Priority: "LOW", DueDate: &due, Recurrence: "WEEKLY", RecurrenceDays: "MON,FRI"}
	uc.Save(ctx, taskA, "u", in)

	in.Status = "COMPLETED"
	uc.Save(ctx, taskA, "u", in)
	child := domain.NextOccurrenceID(taskA)
	if repo.tasks[child].IsDeleted() {
		t.Fatal("child should exist after completion")
	}

	in.Status = "TODO"
	uc.Save(ctx, taskA, "u", in)
	if !repo.tasks[child].IsDeleted() {
		t.Fatal("reopening should remove the occurrence that was spawned by mistake")
	}

	// Hoàn thành lần nữa → lần lặp được khôi phục (cùng id), không nhân đôi
	in.Status = "COMPLETED"
	uc.Save(ctx, taskA, "u", in)
	if repo.tasks[child].IsDeleted() || len(repo.tasks) != 2 {
		t.Fatal("completing again should bring back the same occurrence")
	}
}

func TestOtherUsersTaskLooksNotFound(t *testing.T) {
	uc, repo, _ := newTestUseCase(time.Now())
	ctx := context.Background()
	uc.Create(ctx, "owner", TaskInput{ID: taskA, Title: "Private", Priority: "LOW"})

	if _, err := uc.GetByID(ctx, taskA, "intruder"); !errors.Is(err, domain.ErrTaskNotFound) {
		t.Fatalf("expected ErrTaskNotFound, got %v", err)
	}
	if _, err := uc.Save(ctx, taskA, "intruder", TaskInput{Title: "Hijack", Priority: "LOW"}); !errors.Is(err, domain.ErrTaskNotFound) {
		t.Fatalf("PUT on another user's id must not overwrite it, got %v", err)
	}
	if err := uc.Delete(ctx, taskA, "intruder"); !errors.Is(err, domain.ErrTaskNotFound) {
		t.Fatalf("expected ErrTaskNotFound when deleting, got %v", err)
	}
	if repo.tasks[taskA].Title != "Private" || repo.tasks[taskA].IsDeleted() {
		t.Fatal("task must be untouched")
	}
}

func TestDeleteIsSoftAndIdempotent_AndChangesReportIt(t *testing.T) {
	start := time.Date(2026, 7, 8, 9, 0, 0, 0, vn)
	uc, _, clock := newTestUseCase(start)
	ctx := context.Background()
	uc.Save(ctx, taskA, "u", TaskInput{Title: "A", Priority: "LOW"})
	uc.Save(ctx, taskB, "u", TaskInput{Title: "B", Priority: "LOW"})

	first, err := uc.Changes(ctx, "u", nil)
	if err != nil || len(first.Tasks) != 2 || len(first.DeletedIDs) != 0 {
		t.Fatalf("initial sync: %+v, %v", first, err)
	}

	*clock = start.Add(time.Minute)
	if err := uc.Delete(ctx, taskA, "u"); err != nil {
		t.Fatal(err)
	}
	if err := uc.Delete(ctx, taskA, "u"); err != nil {
		t.Fatalf("deleting again should succeed (retry), got %v", err)
	}
	if _, err := uc.GetByID(ctx, taskA, "u"); !errors.Is(err, domain.ErrTaskNotFound) {
		t.Fatal("deleted task should look not found")
	}

	*clock = start.Add(2 * time.Minute)
	next, err := uc.Changes(ctx, "u", &first.ServerTime)
	if err != nil {
		t.Fatal(err)
	}
	if len(next.DeletedIDs) != 1 || next.DeletedIDs[0] != taskA {
		t.Fatalf("expected deletion of A in changes, got %+v", next.DeletedIDs)
	}
}

func TestSave_ResurrectsDeletedTask(t *testing.T) {
	uc, repo, _ := newTestUseCase(time.Now())
	ctx := context.Background()
	uc.Save(ctx, taskA, "u", TaskInput{Title: "A", Priority: "LOW"})
	uc.Delete(ctx, taskA, "u")

	// "Hoàn tác" xóa trên máy khác, hoặc sửa sau khi xóa: ghi sau cùng thắng
	if _, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "A", Priority: "LOW"}); err != nil {
		t.Fatal(err)
	}
	if repo.tasks[taskA].IsDeleted() {
		t.Fatal("saving a deleted task should restore it")
	}
}

// Client cũ (chưa biết các trường mới) sửa task không được xóa mất "cả ngày", "Ngày của tôi", lặp nâng cao...
func TestSave_ExtendedFieldsSurviveOldClientsAndCanBeCleared(t *testing.T) {
	uc, _, _ := newTestUseCase(time.Date(2026, 7, 8, 10, 0, 0, 0, vn))
	ctx := context.Background()
	allDay, myDay, est, interval, mode, until := true, "2026-07-08", 45, 2, "COMPLETION", "2026-12-31T23:59:00+07:00"
	if _, err := uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Tưới cây", Priority: "LOW", Recurrence: "DAILY",
		DueAllDay: &allDay, MyDay: &myDay, EstimatedMinutes: &est,
		RecurrenceInterval: &interval, RecurrenceMode: &mode, RecurrenceUntil: &until,
	}); err != nil {
		t.Fatal(err)
	}

	kept, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Tưới cây ban công", Priority: "LOW", Recurrence: "DAILY"})
	if err != nil {
		t.Fatal(err)
	}
	if !kept.DueAllDay || kept.MyDay == nil || *kept.MyDay != myDay || kept.EstimatedMinutes != 45 ||
		kept.RecurrenceInterval != 2 || kept.RecurrenceMode != domain.RecurrenceModeCompletion || kept.RecurrenceUntil == nil {
		t.Fatalf("extended fields must be preserved, got %+v", kept)
	}

	empty := ""
	cleared, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Tưới cây", Priority: "LOW", MyDay: &empty, RecurrenceUntil: &empty})
	if err != nil {
		t.Fatal(err)
	}
	if cleared.MyDay != nil || cleared.RecurrenceUntil != nil {
		t.Fatalf(`"" must clear my_day and recurrence_until, got %v / %v`, cleared.MyDay, cleared.RecurrenceUntil)
	}

	bad := "08/07/2026"
	if _, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "x", Priority: "LOW", MyDay: &bad}); err == nil {
		t.Fatal("malformed my_day must be rejected")
	}
}

// Lần lặp mới giữ các thiết lập lặp + "cả ngày", nhưng KHÔNG thừa hưởng "Ngày của tôi" của lần trước.
func TestComplete_SpawnCopiesRecurrenceSettingsButNotMyDay(t *testing.T) {
	now := time.Date(2026, 7, 8, 21, 0, 0, 0, vn)
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 7, 1, 23, 59, 0, 0, vn)
	allDay, myDay, interval, mode := true, "2026-07-08", 3, "COMPLETION"
	uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Tưới cây", Priority: "LOW", DueDate: &due, Recurrence: "DAILY",
		DueAllDay: &allDay, MyDay: &myDay, RecurrenceInterval: &interval, RecurrenceMode: &mode,
	})

	if _, err := uc.Complete(ctx, taskA, "u"); err != nil {
		t.Fatal(err)
	}
	child := repo.tasks[domain.NextOccurrenceID(taskA)]
	if child == nil {
		t.Fatal("next occurrence was not created")
	}
	want := time.Date(2026, 7, 11, 23, 59, 0, 0, vn) // ngày hoàn thành 8/7 + 3 ngày
	if !child.DueDate.Equal(want) {
		t.Fatalf("next due = %v, want %v", child.DueDate.In(vn), want)
	}
	if !child.DueAllDay || child.RecurrenceInterval != 3 || child.RecurrenceMode != domain.RecurrenceModeCompletion {
		t.Fatalf("recurrence settings must be copied, got %+v", child)
	}
	if child.MyDay != nil {
		t.Fatal("the new occurrence must not inherit My Day")
	}
}

// Hết ngày kết thúc lặp → hoàn thành lần cuối không sinh thêm lần nào.
func TestComplete_NoSpawnAfterRecurrenceUntil(t *testing.T) {
	now := time.Date(2026, 7, 8, 9, 0, 0, 0, vn)
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 7, 8, 8, 0, 0, 0, vn)
	until := "2026-07-08T23:59:00+07:00"
	uc.Save(ctx, taskA, "u", TaskInput{Title: "Khóa học", Priority: "LOW", DueDate: &due, Recurrence: "DAILY", RecurrenceUntil: &until})

	if _, err := uc.Complete(ctx, taskA, "u"); err != nil {
		t.Fatal(err)
	}
	if _, ok := repo.tasks[domain.NextOccurrenceID(taskA)]; ok {
		t.Fatal("no occurrence should be spawned after recurrence_until")
	}
}
