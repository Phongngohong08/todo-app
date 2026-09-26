package task

import (
	"testing"
	"time"
	"todo-backend/internal/domain"
)

func TestNextDueAfter(t *testing.T) {
	cases := []struct {
		name string
		due  time.Time
		rec  domain.Recurrence
		days string
		now  time.Time
		want time.Time
	}{
		{
			name: "daily completed on time",
			due:  time.Date(2026, 7, 8, 8, 0, 0, 0, vn),
			rec:  domain.RecurrenceDaily,
			now:  time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			want: time.Date(2026, 7, 9, 8, 0, 0, 0, vn),
		},
		{
			name: "daily overdue several days skips to the future",
			due:  time.Date(2026, 7, 1, 8, 0, 0, 0, vn),
			rec:  domain.RecurrenceDaily,
			now:  time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			want: time.Date(2026, 7, 8, 8, 0, 0, 0, vn),
		},
		{
			name: "weekly on chosen weekdays (Wed -> Fri)",
			due:  time.Date(2026, 7, 8, 18, 0, 0, 0, vn), // thứ Tư
			rec:  domain.RecurrenceWeekly,
			days: "MON,FRI",
			now:  time.Date(2026, 7, 8, 19, 0, 0, 0, vn),
			want: time.Date(2026, 7, 10, 18, 0, 0, 0, vn), // thứ Sáu
		},
		{
			// 06:00 giờ VN = 23:00 UTC hôm trước — thứ phải tính theo giờ VN
			name: "weekday is computed in the user's timezone, not UTC",
			due:  time.Date(2026, 7, 6, 6, 0, 0, 0, vn).UTC(), // thứ Hai giờ VN (Chủ nhật ở UTC)
			rec:  domain.RecurrenceWeekly,
			days: "MON",
			now:  time.Date(2026, 7, 6, 7, 0, 0, 0, vn),
			want: time.Date(2026, 7, 13, 6, 0, 0, 0, vn),
		},
		{
			name: "monthly clamps to the end of a shorter month",
			due:  time.Date(2026, 1, 31, 9, 0, 0, 0, vn),
			rec:  domain.RecurrenceMonthly,
			now:  time.Date(2026, 1, 31, 10, 0, 0, 0, vn),
			want: time.Date(2026, 2, 28, 9, 0, 0, 0, vn),
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := nextDueAfter(tc.due, tc.rec, tc.days, tc.now, vn)
			if !got.Equal(tc.want) {
				t.Fatalf("got %v, want %v", got.In(vn), tc.want)
			}
		})
	}
}

// Cùng vector với RecurrenceRulesTest.kt bên app — hai bên phải sinh ra đúng một id.
func TestNextOccurrenceID_MatchesAndroid(t *testing.T) {
	got := domain.NextOccurrenceID("11111111-1111-4111-8111-111111111111")
	want := nextOccurrenceIDVector
	if got != want {
		t.Fatalf("NextOccurrenceID = %s, want %s (must match the Android implementation)", got, want)
	}
}

// Tính độc lập: MD5(namespace 16 byte + "1111...") rồi đặt bit version 3 / variant RFC 4122.
const nextOccurrenceIDVector = "aeef035e-c62f-36af-8237-e4b2b4d9a73e"
