// Package habit quản lý thói quen (việc lặp lại không có hạn chót) và việc đánh dấu theo ngày.
// Đồng bộ giống tasks: client sinh id, PUT ghi toàn bộ trạng thái (idempotent), GET /habits/sync?since kéo thay đổi.
package habit

import (
	"context"
	"errors"
	"regexp"
	"strings"
	"time"
	"todo-backend/internal/domain"
)

// syncOverlap: lùi mốc since một chút để không bỏ sót giao dịch commit muộn (xem task.syncOverlap).
const syncOverlap = 30 * time.Second

// futureCheckinDays: cho đánh dấu tới "ngày mai" theo giờ server — múi giờ người dùng có thể đi trước UTC.
const futureCheckinDays = 1

const dayLayout = "2006-01-02"

var (
	colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)
	weekdayCodes = map[string]bool{"MON": true, "TUE": true, "WED": true, "THU": true, "FRI": true, "SAT": true, "SUN": true}
	weekdayOrder = []string{"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}
)

type HabitUseCase struct {
	repo domain.HabitRepository
	now  func() time.Time
}

func NewHabitUseCase(repo domain.HabitRepository) *HabitUseCase {
	return &HabitUseCase{repo: repo, now: time.Now}
}

// HabitInput là toàn bộ trạng thái thói quen client gửi lên.
type HabitInput struct {
	Name          string   `json:"name" binding:"required,max=100"`
	Emoji         string   `json:"emoji" binding:"max=16"`
	Color         string   `json:"color" binding:"max=9"`
	Frequency     string   `json:"frequency" binding:"omitempty,oneof=DAILY WEEKDAYS WEEKLY"`
	Weekdays      string   `json:"weekdays" binding:"max=40"`
	TargetPerWeek int      `json:"target_per_week" binding:"min=0,max=7"`
	ReminderTime  string   `json:"reminder_time" binding:"max=5"`
	Position      *float64 `json:"position"`
	Archived      bool     `json:"archived"`
}

// CheckinInput: đánh dấu (done = true) hoặc bỏ đánh dấu một ngày.
type CheckinInput struct {
	Done bool `json:"done"`
}

// Save tạo mới hoặc ghi đè thói quen [id] của userID. Thói quen đã xóa mà được ghi lại sẽ sống lại (Hoàn tác).
func (u *HabitUseCase) Save(ctx context.Context, id, userID string, in HabitInput) (*domain.Habit, error) {
	now := u.now()
	existing, err := u.repo.GetByID(ctx, id)
	if err != nil && !errors.Is(err, domain.ErrNotFound) {
		return nil, err
	}
	if existing != nil && existing.UserID != userID {
		return nil, domain.ErrNotFound
	}

	h := &domain.Habit{ID: id, UserID: userID, CreatedAt: now, Position: -float64(now.UnixMilli())}
	if existing != nil {
		h.CreatedAt = existing.CreatedAt
		h.Position = existing.Position
	}
	if err := apply(h, in); err != nil {
		return nil, err
	}
	h.UpdatedAt = now
	h.DeletedAt = nil
	if err := u.repo.Upsert(ctx, h); err != nil {
		return nil, err
	}
	return h, nil
}

// apply kiểm tra và chép dữ liệu đầu vào vào thói quen.
func apply(h *domain.Habit, in HabitInput) error {
	h.Name = strings.TrimSpace(in.Name)
	if h.Name == "" {
		return domain.NewValidationError("Tên thói quen không được để trống")
	}
	h.Emoji = strings.TrimSpace(in.Emoji)
	h.Color = strings.TrimSpace(in.Color)
	if h.Color != "" && !colorPattern.MatchString(h.Color) {
		return domain.NewValidationError("color phải có dạng #RRGGBB")
	}

	h.Frequency = domain.HabitFrequency(in.Frequency)
	if h.Frequency == "" {
		h.Frequency = domain.HabitDaily
	}
	h.Weekdays = ""
	h.TargetPerWeek = 7
	switch h.Frequency {
	case domain.HabitWeekdays:
		days, err := NormalizeWeekdays(in.Weekdays)
		if err != nil {
			return err
		}
		if days == "" {
			return domain.NewValidationError("Chọn ít nhất một thứ trong tuần")
		}
		h.Weekdays = days
		h.TargetPerWeek = len(strings.Split(days, ","))
	case domain.HabitWeekly:
		if in.TargetPerWeek < 1 {
			return domain.NewValidationError("Số lần mỗi tuần phải từ 1 đến 7")
		}
		h.TargetPerWeek = in.TargetPerWeek
	}

	h.ReminderTime = strings.TrimSpace(in.ReminderTime)
	if h.ReminderTime != "" {
		t, err := time.Parse("15:04", h.ReminderTime)
		if err != nil {
			return domain.NewValidationError("reminder_time phải có dạng HH:mm")
		}
		h.ReminderTime = t.Format("15:04")
	}
	if in.Position != nil {
		h.Position = *in.Position
	}
	h.Archived = in.Archived
	return nil
}

// NormalizeWeekdays: "wed, mon" → "MON,WED" (theo thứ tự trong tuần, bỏ trùng). Mã lạ → lỗi.
func NormalizeWeekdays(raw string) (string, error) {
	seen := map[string]bool{}
	for _, p := range strings.Split(raw, ",") {
		code := strings.ToUpper(strings.TrimSpace(p))
		if code == "" {
			continue
		}
		if !weekdayCodes[code] {
			return "", domain.NewValidationError("weekdays chỉ gồm MON,TUE,WED,THU,FRI,SAT,SUN")
		}
		seen[code] = true
	}
	out := []string{}
	for _, code := range weekdayOrder {
		if seen[code] {
			out = append(out, code)
		}
	}
	return strings.Join(out, ","), nil
}

// Delete xóa mềm (giữ lại để thiết bị khác biết mà xóa theo). Xóa lại lần nữa vẫn thành công.
func (u *HabitUseCase) Delete(ctx context.Context, id, userID string) error {
	h, err := u.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if h.UserID != userID {
		return domain.ErrNotFound
	}
	if h.IsDeleted() {
		return nil
	}
	return u.repo.SoftDelete(ctx, id, u.now())
}

// SetCheckin đánh dấu / bỏ đánh dấu thói quen trong ngày [date] ("yyyy-MM-dd").
func (u *HabitUseCase) SetCheckin(ctx context.Context, userID, habitID, date string, done bool) (*domain.HabitCheckin, error) {
	day, err := time.Parse(dayLayout, strings.TrimSpace(date))
	if err != nil {
		return nil, domain.NewValidationError("Ngày phải có dạng yyyy-MM-dd")
	}
	now := u.now()
	latest := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC).AddDate(0, 0, futureCheckinDays)
	if day.After(latest) {
		return nil, domain.NewValidationError("Không thể đánh dấu cho ngày trong tương lai")
	}

	h, err := u.repo.GetByID(ctx, habitID)
	if err != nil {
		return nil, err
	}
	if h.UserID != userID || h.IsDeleted() {
		return nil, domain.ErrNotFound
	}
	c := domain.HabitCheckin{HabitID: habitID, Date: day.Format(dayLayout), Done: done, UpdatedAt: now}
	if err := u.repo.SetCheckin(ctx, userID, c); err != nil {
		return nil, err
	}
	return &c, nil
}

// Changes trả về thói quen và check-in thay đổi kể từ since (nil = lần đầu: toàn bộ).
func (u *HabitUseCase) Changes(ctx context.Context, userID string, since *time.Time) (*domain.HabitChanges, error) {
	serverTime := u.now()
	var from *time.Time
	if since != nil {
		f := since.Add(-syncOverlap)
		from = &f
	}
	habits, err := u.repo.ListChangedSince(ctx, userID, from)
	if err != nil {
		return nil, err
	}
	checkins, err := u.repo.CheckinsChangedSince(ctx, userID, from)
	if err != nil {
		return nil, err
	}

	res := &domain.HabitChanges{Habits: []*domain.Habit{}, DeletedIDs: []string{}, Checkins: checkins, ServerTime: serverTime}
	for _, h := range habits {
		if h.IsDeleted() {
			res.DeletedIDs = append(res.DeletedIDs, h.ID)
		} else {
			res.Habits = append(res.Habits, h)
		}
	}
	return res, nil
}
