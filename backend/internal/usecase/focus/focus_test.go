package focus

import (
	"context"
	"errors"
	"testing"
	"time"
	"todo-backend/internal/domain"
)

type fakeRepo struct{ sessions map[string]*domain.FocusSession }

func (f *fakeRepo) Insert(_ context.Context, s *domain.FocusSession) error {
	if old, ok := f.sessions[s.ID]; ok {
		if old.UserID != s.UserID {
			return domain.ErrNotFound
		}
		return nil
	}
	c := *s
	f.sessions[s.ID] = &c
	return nil
}

func (f *fakeRepo) ListCreatedSince(_ context.Context, userID string, since *time.Time, limit int) ([]*domain.FocusSession, error) {
	var out []*domain.FocusSession
	for _, s := range f.sessions {
		if s.UserID == userID && (since == nil || s.CreatedAt.After(*since)) && len(out) < limit {
			out = append(out, s)
		}
	}
	return out, nil
}

const sessionA = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

func TestRecord_IsIdempotentAndValidated(t *testing.T) {
	repo := &fakeRepo{sessions: map[string]*domain.FocusSession{}}
	uc := NewFocusUseCase(repo)
	now := time.Date(2026, 10, 2, 9, 0, 0, 0, time.UTC)
	uc.now = func() time.Time { return now }
	ctx := context.Background()

	in := SessionInput{TaskTitle: " Viết báo cáo ", StartedAt: now.Add(-25 * time.Minute), Minutes: 25}
	s, err := uc.Record(ctx, sessionA, "u", in)
	if err != nil {
		t.Fatal(err)
	}
	if s.TaskTitle != "Viết báo cáo" {
		t.Fatalf("title not trimmed: %q", s.TaskTitle)
	}
	if _, err := uc.Record(ctx, sessionA, "u", in); err != nil {
		t.Fatalf("re-sending must succeed: %v", err)
	}
	if len(repo.sessions) != 1 {
		t.Fatal("re-sending must not duplicate")
	}
	if _, err := uc.Record(ctx, sessionA, "other", in); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("other user's id: want ErrNotFound, got %v", err)
	}
	future := SessionInput{StartedAt: now.Add(time.Hour), Minutes: 25}
	if _, err := uc.Record(ctx, "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "u", future); err == nil {
		t.Fatal("future start must be rejected")
	}

	changes, _ := uc.Changes(ctx, "u", nil)
	if len(changes.Sessions) != 1 || !changes.ServerTime.Equal(now) {
		t.Fatalf("changes: %+v", changes)
	}
}
