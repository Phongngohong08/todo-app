// Package ratelimit giới hạn tần suất theo khóa (IP hoặc userID) trong bộ nhớ tiến trình.
// Phù hợp khi chạy MỘT instance API (như docker-compose.prod.yml); bộ đếm reset khi khởi động lại.
// Chạy nhiều instance thì cần chuyển sang kho dùng chung (vd Redis).
package ratelimit

import (
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// idleTTL: khóa không hoạt động quá lâu sẽ bị dọn để map không phình mãi.
const idleTTL = 30 * time.Minute

type visitor struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// Limiter là token bucket riêng cho từng khóa: cho phép "burst" request dồn dập rồi hồi dần theo "every".
type Limiter struct {
	mu          sync.Mutex
	visitors    map[string]*visitor
	every       time.Duration
	burst       int
	lastCleanup time.Time
}

// New tạo limiter hồi 1 lượt sau mỗi khoảng every, chứa tối đa burst lượt.
// Ví dụ New(12*time.Second, 5): dồn được 5 request, sau đó trung bình 5 request/phút.
func New(every time.Duration, burst int) *Limiter {
	return &Limiter{
		visitors:    make(map[string]*visitor),
		every:       every,
		burst:       burst,
		lastCleanup: time.Now(),
	}
}

// Allow trả về true nếu khóa còn lượt; nếu hết, trả kèm thời gian cần chờ trước khi thử lại.
func (l *Limiter) Allow(key string) (bool, time.Duration) {
	now := time.Now()

	l.mu.Lock()
	if now.Sub(l.lastCleanup) > idleTTL {
		for k, v := range l.visitors {
			if now.Sub(v.lastSeen) > idleTTL {
				delete(l.visitors, k)
			}
		}
		l.lastCleanup = now
	}
	v, ok := l.visitors[key]
	if !ok {
		v = &visitor{limiter: rate.NewLimiter(rate.Every(l.every), l.burst)}
		l.visitors[key] = v
	}
	v.lastSeen = now
	l.mu.Unlock()

	res := v.limiter.ReserveN(now, 1)
	if !res.OK() {
		return false, l.every
	}
	if delay := res.DelayFrom(now); delay > 0 {
		// Không "đặt chỗ" cho request bị từ chối, để lượt đó còn cho lần thử sau.
		res.CancelAt(now)
		return false, delay
	}
	return true, 0
}

// DailyQuota đếm số lượt mỗi khóa được dùng trong một ngày (theo múi giờ loc), reset lúc 0h.
type DailyQuota struct {
	mu     sync.Mutex
	limit  int
	loc    *time.Location
	day    string
	counts map[string]int
}

// NewDailyQuota tạo hạn mức limit lượt/ngày cho mỗi khóa; limit <= 0 nghĩa là không giới hạn.
func NewDailyQuota(limit int, loc *time.Location) *DailyQuota {
	return &DailyQuota{limit: limit, loc: loc, counts: make(map[string]int)}
}

// Allow trừ một lượt nếu còn; nếu đã hết, trả về thời gian tới lúc reset (0h hôm sau).
func (q *DailyQuota) Allow(key string) (bool, time.Duration) {
	if q.limit <= 0 {
		return true, 0
	}
	now := time.Now().In(q.loc)
	today := now.Format("2006-01-02")

	q.mu.Lock()
	defer q.mu.Unlock()
	if today != q.day {
		q.day = today
		q.counts = make(map[string]int)
	}
	if q.counts[key] >= q.limit {
		midnight := time.Date(now.Year(), now.Month(), now.Day()+1, 0, 0, 0, 0, q.loc)
		return false, midnight.Sub(now)
	}
	q.counts[key]++
	return true, 0
}

// Limit trả về hạn mức mỗi ngày (để hiển thị trong thông báo lỗi).
func (q *DailyQuota) Limit() int { return q.limit }
