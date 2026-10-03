package task

import (
	"context"
	"reflect"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

func TestNormalizeTags(t *testing.T) {
	got := NormalizeTags([]string{" @Nhà ", "nhà", "#đi chợ", "", "a,b", "  "})
	want := []string{"nhà", "đi-chợ", "ab"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v, want %v", got, want)
	}
	many := make([]string, 15)
	for i := range many {
		many[i] = string(rune('a' + i))
	}
	if n := len(NormalizeTags(many)); n != maxTags {
		t.Fatalf("tags must be capped at %d, got %d", maxTags, n)
	}
}

func TestNormalizeReminders(t *testing.T) {
	got := NormalizeReminders([]int{1440, 60, -5, 60, 20000, 0, 10, 30, 120})
	want := []int{0, 10, 30, 60, 120} // bỏ âm, quá 7 ngày, trùng; sắp tăng; tối đa 5
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v, want %v", got, want)
	}
}

func TestSave_OrganizerFieldsKeptForOldClientsAndValidated(t *testing.T) {
	now := time.Date(2026, 10, 2, 8, 0, 0, 0, vn)
	uc, _, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 10, 3, 10, 30, 0, 0, vn)
	tags := []string{"Nhà"}
	section := "Đang làm"
	start := "2026-10-03T09:00:00+07:00"
	extra := []int{1440, 60}
	subDue := "2026-10-02"
	bad := "hôm nay"
	task, err := uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Họp", Priority: "MEDIUM", DueDate: &due,
		Tags: &tags, Section: &section, StartAt: &start, ExtraReminders: &extra,
		Subtasks: []domain.Subtask{
			{ID: "s1", Title: "Chuẩn bị slide", DueDate: &subDue},
			{ID: "s2", Title: "Gửi lịch", Position: 1, DueDate: &bad},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(task.Tags, []string{"nhà"}) || task.Section != "Đang làm" ||
		!reflect.DeepEqual(task.ExtraReminders, []int{60, 1440}) || task.StartAt == nil {
		t.Fatalf("organizer fields not saved: %+v", task)
	}
	if task.Subtasks[0].DueDate == nil || *task.Subtasks[0].DueDate != "2026-10-02" || task.Subtasks[1].DueDate != nil {
		t.Fatalf("subtask due dates not normalized: %+v", task.Subtasks)
	}

	// Client cũ không gửi các trường mới → giữ nguyên
	kept, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Họp nhóm", Priority: "MEDIUM", DueDate: &due})
	if err != nil {
		t.Fatal(err)
	}
	if len(kept.Tags) != 1 || kept.Section != "Đang làm" || kept.StartAt == nil || len(kept.ExtraReminders) != 2 {
		t.Fatalf("fields must survive old clients: %+v", kept)
	}

	// Bỏ hạn → khung giờ không còn nghĩa
	noDue, _ := uc.Save(ctx, taskA, "u", TaskInput{Title: "Họp", Priority: "MEDIUM"})
	if noDue.StartAt != nil {
		t.Fatal("start_at must be dropped when there is no due date")
	}

	// Bắt đầu sau hạn → lỗi
	late := "2026-10-03T11:00:00+07:00"
	if _, err := uc.Save(ctx, taskA, "u", TaskInput{Title: "Họp", Priority: "MEDIUM", DueDate: &due, StartAt: &late}); err == nil {
		t.Fatal("start after due must be rejected")
	}
}

func TestComplete_SpawnCopiesOrganizerFieldsAndShiftsTimeBlock(t *testing.T) {
	now := time.Date(2026, 10, 5, 11, 0, 0, 0, vn)
	uc, repo, _ := newTestUseCase(now)
	ctx := context.Background()
	due := time.Date(2026, 10, 5, 10, 30, 0, 0, vn)
	start := "2026-10-05T09:00:00+07:00"
	tags := []string{"họp"}
	section := "Định kỳ"
	extra := []int{60}
	if _, err := uc.Save(ctx, taskA, "u", TaskInput{
		Title: "Họp tuần", Priority: "MEDIUM", DueDate: &due, Recurrence: "WEEKLY",
		StartAt: &start, Tags: &tags, Section: &section, ExtraReminders: &extra,
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := uc.Complete(ctx, taskA, "u"); err != nil {
		t.Fatal(err)
	}
	child := repo.tasks[domain.NextOccurrenceID(taskA)]
	if child == nil {
		t.Fatal("next occurrence was not created")
	}
	wantStart := time.Date(2026, 10, 12, 9, 0, 0, 0, vn)
	if child.StartAt == nil || !child.StartAt.Equal(wantStart) {
		t.Fatalf("start = %v, want %v", child.StartAt, wantStart)
	}
	if !reflect.DeepEqual(child.Tags, tags) || child.Section != section || !reflect.DeepEqual(child.ExtraReminders, extra) {
		t.Fatalf("organizer fields must be copied: %+v", child)
	}
}
