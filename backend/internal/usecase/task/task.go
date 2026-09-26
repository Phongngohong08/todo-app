package task

import (
	"context"
	"errors"
	"sort"
	"strings"
	"time"
	"todo-backend/internal/domain"

	"github.com/google/uuid"
)

// syncOverlap: khi client kéo thay đổi "từ mốc since", server lùi mốc lại một chút. Giao dịch ghi bắt đầu
// trước lần kéo nhưng commit sau có updated_at nhỏ hơn server_time — thiếu phần chồng lấn này sẽ bị bỏ sót.
// Bản ghi trùng lặp không hại gì vì client upsert theo id.
const syncOverlap = 30 * time.Second

// completedAtTolerance: chấp nhận completed_at do client gửi (hoàn thành lúc offline) nếu không vượt
// quá giờ server quá mức này — đồng hồ điện thoại có thể lệch vài phút.
const completedAtTolerance = 5 * time.Minute

type TaskUseCase struct {
	taskRepo domain.TaskRepository
	loc      *time.Location   // múi giờ người dùng, dùng để tính lần lặp kế tiếp
	now      func() time.Time // thay được trong test
}

func NewTaskUseCase(taskRepo domain.TaskRepository, loc *time.Location) *TaskUseCase {
	if loc == nil {
		loc = time.UTC
	}
	return &TaskUseCase{taskRepo: taskRepo, loc: loc, now: time.Now}
}

// TaskInput là toàn bộ trạng thái task mà client gửi lên (POST tạo mới, PUT ghi đè/tạo nếu chưa có).
// Các trường con trỏ/slice để trống (nil) nghĩa là "giữ nguyên giá trị hiện có" — client cũ không gửi chúng.
type TaskInput struct {
	// ID do client sinh (UUID) để tạo task khi offline; gửi lại nhiều lần vẫn chỉ tạo một task.
	ID                    string     `json:"id" binding:"omitempty,uuid"`
	Title                 string     `json:"title" binding:"required,max=255"`
	Description           string     `json:"description" binding:"max=10000"`
	Priority              string     `json:"priority" binding:"required,oneof=LOW MEDIUM HIGH"`
	DueDate               *time.Time `json:"due_date"`
	Category              string     `json:"category" binding:"max=50"`
	Recurrence            string     `json:"recurrence" binding:"omitempty,oneof=NONE DAILY WEEKLY MONTHLY"`
	RecurrenceDays        string     `json:"recurrence_days" binding:"max=40"`
	ReminderOffsetMinutes int        `json:"reminder_offset_minutes" binding:"min=0,max=10080"` // tối đa nhắc trước 7 ngày

	Status      string           `json:"status" binding:"omitempty,oneof=TODO COMPLETED"`
	CompletedAt *time.Time       `json:"completed_at"` // thời điểm hoàn thành trên máy (khi chuyển sang COMPLETED)
	SortOrder   *float64         `json:"sort_order"`
	Subtasks    []domain.Subtask `json:"subtasks" binding:"omitempty,max=100,dive"`
	SpawnedFrom *string          `json:"spawned_from" binding:"omitempty,uuid"`
}

// Giữ tên cũ cho các lời gọi hiện có.
type (
	CreateTaskInput = TaskInput
	UpdateTaskInput = TaskInput
)

// normalizeRecurrence trả về giá trị recurrence hợp lệ, mặc định NONE.
func normalizeRecurrence(r string) domain.Recurrence {
	switch domain.Recurrence(r) {
	case domain.RecurrenceDaily, domain.RecurrenceWeekly, domain.RecurrenceMonthly:
		return domain.Recurrence(r)
	default:
		return domain.RecurrenceNone
	}
}

// normalizeCategory cho phép danh mục tự do; rỗng -> OTHER.
func normalizeCategory(c string) domain.Category {
	t := strings.TrimSpace(c)
	if t == "" {
		return domain.CategoryOther
	}
	return domain.Category(t)
}

// normalizeSubtasks bỏ bước rỗng/trùng id, sắp theo position rồi đánh lại 0..n-1.
func normalizeSubtasks(in []domain.Subtask) []domain.Subtask {
	out := make([]domain.Subtask, 0, len(in))
	seen := make(map[string]bool, len(in))
	for _, s := range in {
		s.Title = strings.TrimSpace(s.Title)
		if s.Title == "" || seen[s.ID] {
			continue
		}
		seen[s.ID] = true
		out = append(out, s)
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Position < out[j].Position })
	for i := range out {
		out[i].Position = i
	}
	return out
}

// Create tạo task; nếu client gửi kèm id đã tồn tại (gửi lại do mất mạng) thì coi như Save — idempotent.
func (u *TaskUseCase) Create(ctx context.Context, userID string, input TaskInput) (*domain.Task, error) {
	id := input.ID
	if id == "" {
		id = uuid.New().String()
	}
	return u.Save(ctx, id, userID, input)
}

// Save ghi toàn bộ trạng thái task (tạo mới nếu chưa có — PUT idempotent, phục vụ đồng bộ offline).
// Đây là NƠI DUY NHẤT xử lý chuyển trạng thái:
//   - TODO → COMPLETED: đặt completed_at, sinh lần lặp kế tiếp (nếu là việc lặp)
//   - COMPLETED → TODO (mở lại): xóa completed_at, thu hồi lần lặp đã sinh nếu nó chưa được làm
//
// Task đã xóa mềm mà được ghi lại sẽ sống lại (last-write-wins) — cho phép "Hoàn tác" sau khi xóa.
func (u *TaskUseCase) Save(ctx context.Context, id, userID string, input TaskInput) (*domain.Task, error) {
	now := u.now()

	existing, err := u.taskRepo.GetByID(ctx, id)
	if err != nil && !errors.Is(err, domain.ErrTaskNotFound) {
		return nil, err
	}
	if existing != nil && existing.UserID != userID {
		return nil, domain.ErrTaskNotFound
	}

	task := &domain.Task{
		ID:        id,
		UserID:    userID,
		Status:    domain.StatusTodo,
		SortOrder: -float64(now.UnixMilli()), // mới nhất lên đầu
		Subtasks:  []domain.Subtask{},
		CreatedAt: now,
	}
	prevStatus := domain.StatusTodo
	if existing != nil {
		task.CreatedAt = existing.CreatedAt
		task.Status = existing.Status
		task.CompletedAt = existing.CompletedAt
		task.SortOrder = existing.SortOrder
		task.Subtasks = existing.Subtasks
		task.SpawnedFrom = existing.SpawnedFrom
		prevStatus = existing.Status
	}

	task.Title = strings.TrimSpace(input.Title)
	task.Description = input.Description
	task.Priority = domain.Priority(input.Priority)
	task.DueDate = input.DueDate
	task.Category = normalizeCategory(input.Category)
	task.Recurrence = normalizeRecurrence(input.Recurrence)
	task.RecurrenceDays = input.RecurrenceDays
	task.ReminderOffsetMinutes = input.ReminderOffsetMinutes
	if input.Status != "" {
		task.Status = domain.TaskStatus(input.Status)
	}
	if input.SortOrder != nil {
		task.SortOrder = *input.SortOrder
	}
	if input.Subtasks != nil {
		task.Subtasks = normalizeSubtasks(input.Subtasks)
	}
	if input.SpawnedFrom != nil {
		task.SpawnedFrom = input.SpawnedFrom
	}
	task.UpdatedAt = now
	task.DeletedAt = nil

	completedNow := prevStatus != domain.StatusCompleted && task.Status == domain.StatusCompleted
	reopened := prevStatus == domain.StatusCompleted && task.Status == domain.StatusTodo
	switch {
	case completedNow, task.Status == domain.StatusCompleted && task.CompletedAt == nil:
		task.CompletedAt = u.completionTime(input.CompletedAt, now)
	case reopened:
		task.CompletedAt = nil
	}

	if err := u.taskRepo.Upsert(ctx, task); err != nil {
		return nil, err
	}

	if existing == nil {
		u.log(ctx, task, domain.ActionCreated, "Task created", now)
	}
	if completedNow {
		u.log(ctx, task, domain.ActionCompleted, "Task completed", now)
		u.spawnNextOccurrence(ctx, task, now)
	}
	if reopened {
		u.retractNextOccurrence(ctx, task, now)
	}
	return task, nil
}

// completionTime dùng thời điểm hoàn thành client gửi (có thể từ lúc offline), trừ khi nó ở tương lai.
func (u *TaskUseCase) completionTime(fromClient *time.Time, now time.Time) *time.Time {
	if fromClient != nil && !fromClient.After(now.Add(completedAtTolerance)) {
		t := *fromClient
		if t.After(now) {
			t = now
		}
		return &t
	}
	return &now
}

func (u *TaskUseCase) GetByID(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.taskRepo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	// Task của người khác (hoặc đã xóa) trả về như "không tồn tại" để không lộ việc id đó có thật.
	if task.UserID != userID || task.IsDeleted() {
		return nil, domain.ErrTaskNotFound
	}
	return task, nil
}

func (u *TaskUseCase) List(ctx context.Context, userID string, status string, dueDateBefore *time.Time, query string, category string) ([]*domain.Task, error) {
	return u.taskRepo.List(ctx, userID, domain.TaskFilter{
		Status:        domain.TaskStatus(status),
		DueDateBefore: dueDateBefore,
		Query:         query,
		Category:      domain.Category(category),
	})
}

// Update giữ cho API cũ: chỉ sửa task đang tồn tại (không tạo mới như Save).
func (u *TaskUseCase) Update(ctx context.Context, id string, userID string, input TaskInput) (*domain.Task, error) {
	if _, err := u.GetByID(ctx, id, userID); err != nil {
		return nil, err
	}
	return u.Save(ctx, id, userID, input)
}

// Delete xóa mềm. Xóa lại task đã xóa trả về thành công (client gửi lại khi mất mạng).
func (u *TaskUseCase) Delete(ctx context.Context, id string, userID string) error {
	task, err := u.taskRepo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if task.UserID != userID {
		return domain.ErrTaskNotFound
	}
	if task.IsDeleted() {
		return nil
	}
	return u.taskRepo.SoftDelete(ctx, id, u.now())
}

// Complete giữ cho API cũ và nút "Hoàn thành" trên thông báo.
func (u *TaskUseCase) Complete(ctx context.Context, id string, userID string) (*domain.Task, error) {
	task, err := u.GetByID(ctx, id, userID)
	if err != nil {
		return nil, err
	}
	if task.Status == domain.StatusCompleted {
		return nil, domain.ErrInvalidStatusTrans
	}
	input := inputFromTask(task)
	input.Status = string(domain.StatusCompleted)
	return u.Save(ctx, id, userID, input)
}

// Changes trả về những gì thay đổi kể từ "since" (nil = lần đồng bộ đầu tiên: mọi task còn sống).
func (u *TaskUseCase) Changes(ctx context.Context, userID string, since *time.Time) (*domain.TaskChanges, error) {
	serverTime := u.now() // lấy TRƯỚC khi truy vấn: thay đổi xảy ra trong lúc truy vấn sẽ vào lần sau

	var from *time.Time
	if since != nil {
		f := since.Add(-syncOverlap)
		from = &f
	}
	tasks, err := u.taskRepo.ListChangedSince(ctx, userID, from)
	if err != nil {
		return nil, err
	}

	res := &domain.TaskChanges{Tasks: []*domain.Task{}, DeletedIDs: []string{}, ServerTime: serverTime}
	for _, t := range tasks {
		if t.IsDeleted() {
			res.DeletedIDs = append(res.DeletedIDs, t.ID)
		} else {
			res.Tasks = append(res.Tasks, t)
		}
	}
	return res, nil
}

// spawnNextOccurrence tạo lần lặp kế tiếp với id tất định. Nếu app đã tạo sẵn lần đó khi offline
// (cùng id) thì bỏ qua — nhờ vậy không bao giờ có hai bản.
func (u *TaskUseCase) spawnNextOccurrence(ctx context.Context, parent *domain.Task, now time.Time) {
	if parent.Recurrence == domain.RecurrenceNone || parent.Recurrence == "" || parent.DueDate == nil {
		return
	}

	childID := domain.NextOccurrenceID(parent.ID)
	existing, err := u.taskRepo.GetByID(ctx, childID)
	if err != nil && !errors.Is(err, domain.ErrTaskNotFound) {
		return
	}
	if existing != nil && !existing.IsDeleted() {
		return
	}

	nextDue := nextDueAfter(*parent.DueDate, parent.Recurrence, parent.RecurrenceDays, now, u.loc)
	parentID := parent.ID
	child := &domain.Task{
		ID:                    childID,
		UserID:                parent.UserID,
		Title:                 parent.Title,
		Description:           parent.Description,
		Priority:              parent.Priority,
		DueDate:               &nextDue,
		Status:                domain.StatusTodo,
		Category:              parent.Category,
		Recurrence:            parent.Recurrence,
		RecurrenceDays:        parent.RecurrenceDays,
		ReminderOffsetMinutes: parent.ReminderOffsetMinutes,
		SortOrder:             parent.SortOrder,
		Subtasks:              nextOccurrenceSubtasks(parent.Subtasks),
		SpawnedFrom:           &parentID,
		CreatedAt:             now,
		UpdatedAt:             now,
	}
	if err := u.taskRepo.Upsert(ctx, child); err != nil {
		return
	}
	u.log(ctx, child, domain.ActionCreated, "Recurring task occurrence created", now)
}

// nextOccurrenceSubtasks chép checklist sang lần lặp mới (chưa tích), id tất định giống bên app.
func nextOccurrenceSubtasks(parent []domain.Subtask) []domain.Subtask {
	out := make([]domain.Subtask, len(parent))
	for i, s := range parent {
		out[i] = domain.Subtask{ID: domain.NextOccurrenceID(s.ID), Title: s.Title, Position: s.Position}
	}
	return out
}

// retractNextOccurrence: mở lại việc đã hoàn thành → xóa lần lặp đã sinh nếu nó chưa được làm,
// tránh hai bản cùng tồn tại khi người dùng bấm hoàn thành nhầm rồi hoàn tác.
func (u *TaskUseCase) retractNextOccurrence(ctx context.Context, parent *domain.Task, now time.Time) {
	child, err := u.taskRepo.GetByID(ctx, domain.NextOccurrenceID(parent.ID))
	if err != nil || child.IsDeleted() || child.Status != domain.StatusTodo {
		return
	}
	_ = u.taskRepo.SoftDelete(ctx, child.ID, now)
}

func (u *TaskUseCase) log(ctx context.Context, task *domain.Task, action domain.TaskLogAction, details string, now time.Time) {
	_ = u.taskRepo.CreateLog(ctx, &domain.TaskLog{
		ID:        uuid.New().String(),
		TaskID:    task.ID,
		UserID:    task.UserID,
		Action:    action,
		Details:   details,
		CreatedAt: now,
	})
}

func inputFromTask(t *domain.Task) TaskInput {
	sortOrder := t.SortOrder
	return TaskInput{
		Title:                 t.Title,
		Description:           t.Description,
		Priority:              string(t.Priority),
		DueDate:               t.DueDate,
		Category:              string(t.Category),
		Recurrence:            string(t.Recurrence),
		RecurrenceDays:        t.RecurrenceDays,
		ReminderOffsetMinutes: t.ReminderOffsetMinutes,
		Status:                string(t.Status),
		SortOrder:             &sortOrder,
		Subtasks:              t.Subtasks,
		SpawnedFrom:           t.SpawnedFrom,
	}
}
