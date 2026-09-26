package ratelimit

import (
	"testing"
	"time"
)

func TestLimiter_AllowsBurstThenBlocks(t *testing.T) {
	l := New(time.Minute, 3)

	for i := 0; i < 3; i++ {
		if ok, _ := l.Allow("k"); !ok {
			t.Fatalf("request %d within burst was blocked", i+1)
		}
	}
	ok, wait := l.Allow("k")
	if ok {
		t.Fatal("request beyond burst was allowed")
	}
	if wait <= 0 || wait > time.Minute {
		t.Fatalf("unexpected retry-after %v", wait)
	}
}

func TestLimiter_KeysAreIndependent(t *testing.T) {
	l := New(time.Hour, 1)
	if ok, _ := l.Allow("a"); !ok {
		t.Fatal("first request for a blocked")
	}
	if ok, _ := l.Allow("a"); ok {
		t.Fatal("second request for a allowed")
	}
	if ok, _ := l.Allow("b"); !ok {
		t.Fatal("key b should not be affected by key a")
	}
}

func TestLimiter_RejectedRequestDoesNotConsumeToken(t *testing.T) {
	l := New(50*time.Millisecond, 1)
	l.Allow("k")
	// Nhiều lần bị từ chối liên tiếp không được đẩy thời điểm hồi lượt ra xa hơn
	for i := 0; i < 5; i++ {
		l.Allow("k")
	}
	time.Sleep(80 * time.Millisecond)
	if ok, _ := l.Allow("k"); !ok {
		t.Fatal("token should have been refilled")
	}
}

func TestDailyQuota(t *testing.T) {
	q := NewDailyQuota(2, time.UTC)
	for i := 0; i < 2; i++ {
		if ok, _ := q.Allow("u1"); !ok {
			t.Fatalf("request %d within quota was blocked", i+1)
		}
	}
	ok, wait := q.Allow("u1")
	if ok {
		t.Fatal("request beyond daily quota was allowed")
	}
	if wait <= 0 || wait > 24*time.Hour {
		t.Fatalf("retry-after should be until midnight, got %v", wait)
	}
	if ok, _ := q.Allow("u2"); !ok {
		t.Fatal("other users must have their own quota")
	}
}

func TestDailyQuota_ZeroMeansUnlimited(t *testing.T) {
	q := NewDailyQuota(0, time.UTC)
	for i := 0; i < 1000; i++ {
		if ok, _ := q.Allow("u"); !ok {
			t.Fatal("limit 0 should be unlimited")
		}
	}
}
