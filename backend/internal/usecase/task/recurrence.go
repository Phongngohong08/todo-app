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

// nextDueAfter trả về hạn của lần lặp kế tiếp: bước tiếp theo sau "due", và nếu vẫn chưa tới "now"
// thì tiếp tục nhảy — lần lặp mới luôn ở tương lai kể cả khi hoàn thành trễ nhiều ngày.
// Tính theo giờ địa phương loc để "thứ trong tuần" và "cùng ngày tháng sau" đúng với người dùng.
func nextDueAfter(due time.Time, r domain.Recurrence, days string, now time.Time, loc *time.Location) time.Time {
	next := nextOccurrenceDate(due.In(loc), r, days)
	for i := 0; !next.After(now) && i < maxAdvanceSteps; i++ {
		next = nextOccurrenceDate(next, r, days)
	}
	return next
}

// nextOccurrenceDate dời mốc hạn chót đúng một chu kỳ.
// WEEKLY có chọn thứ (days != ""): ngày gần nhất sau "from" rơi vào một thứ được chọn.
func nextOccurrenceDate(from time.Time, r domain.Recurrence, days string) time.Time {
	switch r {
	case domain.RecurrenceDaily:
		return from.AddDate(0, 0, 1)
	case domain.RecurrenceWeekly:
		if set := parseWeekdays(days); len(set) > 0 {
			for i := 1; i <= 7; i++ {
				cand := from.AddDate(0, 0, i)
				if set[cand.Weekday()] {
					return cand
				}
			}
		}
		return from.AddDate(0, 0, 7)
	case domain.RecurrenceMonthly:
		return addMonthsClamped(from, 1)
	default:
		return from
	}
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
