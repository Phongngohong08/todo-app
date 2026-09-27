package task

import (
	"strings"
	"time"
	"todo-backend/internal/domain"
)

// Quy tắc lặp PHẢI giống hệt bản Kotlin (app/.../domain/RecurrenceRules.kt): app tự sinh lần lặp kế tiếp
// khi offline với cùng id tất định, nên hai bên tính lệch hạn chót sẽ ghi đè nhau khi đồng bộ.

// maxAdvanceSteps chặn vòng lặp khi task quá hạn rất lâu (vd lặp hằng ngày quá hạn 3 năm).
const maxAdvanceSteps = 2000

// normalizeInterval: "mỗi N" hợp lệ trong [1, MaxRecurrenceInterval]; giá trị thiếu/sai coi là 1.
func normalizeInterval(n int) int {
	if n < 1 {
		return 1
	}
	if n > domain.MaxRecurrenceInterval {
		return domain.MaxRecurrenceInterval
	}
	return n
}

// nextDueForCompletion trả về hạn của lần lặp sinh ra khi task được hoàn thành lúc completedAt.
// ok = false khi không còn lần lặp nào (vượt recurrence_until).
//
//   - SCHEDULE:   bước tiếp theo sau hạn cũ; nếu vẫn chưa tới "now" thì nhảy tiếp — lần lặp mới luôn ở
//     tương lai kể cả khi hoàn thành trễ nhiều ngày.
//   - COMPLETION: lấy NGÀY hoàn thành, GIỜ của hạn cũ, rồi cộng một chu kỳ.
func nextDueForCompletion(t *domain.Task, completedAt, now time.Time, loc *time.Location) (time.Time, bool) {
	due := t.DueDate.In(loc)
	interval := normalizeInterval(t.RecurrenceInterval)

	from := due
	if t.RecurrenceMode == domain.RecurrenceModeCompletion {
		c := completedAt.In(loc)
		from = time.Date(c.Year(), c.Month(), c.Day(), due.Hour(), due.Minute(), due.Second(), due.Nanosecond(), loc)
	}

	next := nextOccurrenceDate(from, t.Recurrence, t.RecurrenceDays, interval)
	for i := 0; !next.After(now) && i < maxAdvanceSteps; i++ {
		next = nextOccurrenceDate(next, t.Recurrence, t.RecurrenceDays, interval)
	}
	if t.RecurrenceUntil != nil && next.After(*t.RecurrenceUntil) {
		return time.Time{}, false
	}
	return next, true
}

// nextOccurrenceDate dời mốc hạn chót đúng một chu kỳ (mỗi "interval" ngày/tuần/tháng).
// WEEKLY có chọn thứ (days != ""): ngày gần nhất sau "from" rơi vào một thứ được chọn; nếu phải sang tuần
// sau (tuần bắt đầu thứ 2) thì bỏ qua thêm interval-1 tuần — "2 tuần một lần vào T2, T4".
func nextOccurrenceDate(from time.Time, r domain.Recurrence, days string, interval int) time.Time {
	interval = normalizeInterval(interval)
	switch r {
	case domain.RecurrenceDaily:
		return from.AddDate(0, 0, interval)
	case domain.RecurrenceWeekly:
		if set := parseWeekdays(days); len(set) > 0 {
			for i := 1; i <= 7; i++ {
				cand := from.AddDate(0, 0, i)
				if set[cand.Weekday()] {
					if mondayIndex(cand.Weekday()) <= mondayIndex(from.Weekday()) {
						cand = cand.AddDate(0, 0, 7*(interval-1)) // đã sang tuần mới
					}
					return cand
				}
			}
		}
		return from.AddDate(0, 0, 7*interval)
	case domain.RecurrenceMonthly:
		return addMonthsClamped(from, interval)
	default:
		return from
	}
}

// mondayIndex: thứ 2 = 0 ... chủ nhật = 6 (tuần bắt đầu thứ 2, giống DayOfWeek.value - 1 của Java).
func mondayIndex(w time.Weekday) int {
	return (int(w) + 6) % 7
}

// addMonthsClamped cộng tháng nhưng giữ ngày trong phạm vi tháng đích (31/1 + 1 tháng = 28 hoặc 29/2),
// giống LocalDateTime.plusMonths của Java. (time.AddDate của Go sẽ tràn sang 3/3.)
func addMonthsClamped(t time.Time, months int) time.Time {
	firstOfTarget := time.Date(t.Year(), t.Month()+time.Month(months), 1, t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), t.Location())
	lastDay := firstOfTarget.AddDate(0, 1, -1).Day()
	day := t.Day()
	if day > lastDay {
		day = lastDay
	}
	return time.Date(firstOfTarget.Year(), firstOfTarget.Month(), day, t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), t.Location())
}

// parseWeekdays đổi chuỗi "MON,WED,FRI" thành tập time.Weekday.
func parseWeekdays(days string) map[time.Weekday]bool {
	if strings.TrimSpace(days) == "" {
		return nil
	}
	m := map[string]time.Weekday{
		"SUN": time.Sunday, "MON": time.Monday, "TUE": time.Tuesday,
		"WED": time.Wednesday, "THU": time.Thursday, "FRI": time.Friday, "SAT": time.Saturday,
	}
	set := make(map[time.Weekday]bool)
	for _, p := range strings.Split(days, ",") {
		if wd, ok := m[strings.ToUpper(strings.TrimSpace(p))]; ok {
			set[wd] = true
		}
	}
	return set
}
