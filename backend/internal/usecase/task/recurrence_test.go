package task

import (
	"testing"
	"time"
	"todo-backend/internal/domain"
)

// Các vector này PHẢI trùng với RecurrenceRulesTest.kt bên app — hai bên tính lệch là ghi đè nhau khi đồng bộ.
func TestNextDueForCompletion(t *testing.T) {
	until := time.Date(2026, 7, 8, 23, 59, 0, 0, vn)
	cases := []struct {
		name      string
		task      domain.Task
		completed time.Time
		now       time.Time
		want      time.Time
		wantNone  bool
	}{
		{
			name:      "daily completed on time",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 8, 8, 0, 0, 0, vn)), Recurrence: domain.RecurrenceDaily},
			completed: time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 9, 8, 0, 0, 0, vn),
		},
		{
			name:      "daily overdue several days skips to the future",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 1, 8, 0, 0, 0, vn)), Recurrence: domain.RecurrenceDaily},
			completed: time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 8, 8, 0, 0, 0, vn),
		},
		{
			name:      "weekly on chosen weekdays (Wed -> Fri)",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 8, 18, 0, 0, 0, vn)), Recurrence: domain.RecurrenceWeekly, RecurrenceDays: "MON,FRI"},
			completed: time.Date(2026, 7, 8, 19, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 10, 18, 0, 0, 0, vn),
		},
		{
			// 06:00 giờ VN = 23:00 UTC hôm trước — thứ phải tính theo giờ VN
			name:      "weekday is computed in the user's timezone, not UTC",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 6, 6, 0, 0, 0, vn).UTC()), Recurrence: domain.RecurrenceWeekly, RecurrenceDays: "MON"},
			completed: time.Date(2026, 7, 6, 7, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 13, 6, 0, 0, 0, vn),
		},
		{
			name:      "monthly clamps to the end of a shorter month",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 1, 31, 9, 0, 0, 0, vn)), Recurrence: domain.RecurrenceMonthly},
			completed: time.Date(2026, 1, 31, 10, 0, 0, 0, vn),
			want:      time.Date(2026, 2, 28, 9, 0, 0, 0, vn),
		},
		{
			name:      "every 3 days",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 8, 8, 0, 0, 0, vn)), Recurrence: domain.RecurrenceDaily, RecurrenceInterval: 3},
			completed: time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 11, 8, 0, 0, 0, vn),
		},
		{
			// T4 → T2 tuần sau là sang tuần mới → bỏ qua thêm 1 tuần
			name:      "every 2 weeks on Mon/Wed skips a week when wrapping",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 8, 18, 0, 0, 0, vn)), Recurrence: domain.RecurrenceWeekly, RecurrenceDays: "MON,WED", RecurrenceInterval: 2},
			completed: time.Date(2026, 7, 8, 19, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 20, 18, 0, 0, 0, vn),
		},
		{
			name:      "every 2 weeks stays in the same week before wrapping",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 20, 18, 0, 0, 0, vn)), Recurrence: domain.RecurrenceWeekly, RecurrenceDays: "MON,WED", RecurrenceInterval: 2},
			completed: time.Date(2026, 7, 20, 19, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 22, 18, 0, 0, 0, vn),
		},
		{
			name:      "every 2 months",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 1, 31, 9, 0, 0, 0, vn)), Recurrence: domain.RecurrenceMonthly, RecurrenceInterval: 2},
			completed: time.Date(2026, 1, 31, 10, 0, 0, 0, vn),
			want:      time.Date(2026, 3, 31, 9, 0, 0, 0, vn),
		},
		{
			// Hạn 1/7 nhưng làm xong tối 8/7 → lần sau là 11/7 (ngày hoàn thành + 3), giữ giờ 08:00 của hạn
			name:      "completion mode counts from the completion day",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 1, 8, 0, 0, 0, vn)), Recurrence: domain.RecurrenceDaily, RecurrenceInterval: 3, RecurrenceMode: domain.RecurrenceModeCompletion},
			completed: time.Date(2026, 7, 8, 21, 0, 0, 0, vn),
			want:      time.Date(2026, 7, 11, 8, 0, 0, 0, vn),
		},
		{
			name:      "no occurrence after recurrence_until",
			task:      domain.Task{DueDate: ptr(time.Date(2026, 7, 8, 8, 0, 0, 0, vn)), Recurrence: domain.RecurrenceDaily, RecurrenceUntil: &until},
			completed: time.Date(2026, 7, 8, 7, 0, 0, 0, vn),
			wantNone:  true,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			now := tc.now
			if now.IsZero() {
				now = tc.completed
			}
			got, ok := nextDueForCompletion(&tc.task, tc.completed, now, vn)
			if tc.wantNone {
				if ok {
					t.Fatalf("expected no next occurrence, got %v", got.In(vn))
				}
				return
			}
			if !ok || !got.Equal(tc.want) {
				t.Fatalf("got %v (ok=%v), want %v", got.In(vn), ok, tc.want)
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

func ptr[T any](v T) *T { return &v }
