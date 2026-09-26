package worker

import (
	"testing"
	"time"
)

func TestMarkRun_OncePerLocalDay(t *testing.T) {
	loc, err := time.LoadLocation("Asia/Ho_Chi_Minh")
	if err != nil {
		t.Skip("tzdata not available")
	}
	s := NewScheduler(nil, nil, nil, loc)

	day1 := time.Date(2026, 7, 8, 4, 5, 0, 0, loc)
	if !s.markRun("planning", day1) {
		t.Fatal("first run of the day should be allowed")
	}
	if s.markRun("planning", day1.Add(30*time.Minute)) {
		t.Fatal("second run on the same day should be skipped")
	}
	if !s.markRun("memory", day1) {
		t.Fatal("different jobs are tracked separately")
	}
	if !s.markRun("planning", day1.AddDate(0, 0, 1)) {
		t.Fatal("next day should run again")
	}
}
