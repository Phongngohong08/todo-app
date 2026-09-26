package router

import (
	"fmt"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"
	"todo-backend/internal/infrastructure/ratelimit"
	"todo-backend/internal/usecase/auth"

	"github.com/gin-gonic/gin"
)

// maxBodyBytes giới hạn kích thước body mỗi request (JSON của app chỉ vài KB).
const maxBodyBytes = 1 << 20 // 1 MB

func AuthMiddleware(authUC *auth.AuthUseCase) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization header is required"})
			return
		}

		parts := strings.SplitN(authHeader, " ", 2)
		if !(len(parts) == 2 && parts[0] == "Bearer") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization header format must be Bearer <token>"})
			return
		}

		userID, err := authUC.VerifyToken(parts[1])
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid or expired token"})
			return
		}

		c.Set("userID", userID)
		c.Next()
	}
}

// BodyLimitMiddleware chặn body quá lớn (đọc quá giới hạn → ShouldBindJSON trả lỗi → 400).
func BodyLimitMiddleware(limit int64) gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.Body != nil {
			c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, limit)
		}
		c.Next()
	}
}

// CORSMiddleware chỉ cho các origin trình duyệt được liệt kê (CORS_ALLOWED_ORIGINS). App Android không
// dùng CORS nên mặc định danh sách rỗng và middleware không được gắn. Xác thực dùng Bearer token (không cookie)
// nên không bật Allow-Credentials.
func CORSMiddleware(allowed []string) gin.HandlerFunc {
	allowAll := false
	set := make(map[string]bool, len(allowed))
	for _, o := range allowed {
		if o == "*" {
			allowAll = true
		}
		set[strings.TrimRight(o, "/")] = true
	}

	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		if origin != "" && (allowAll || set[origin]) {
			h := c.Writer.Header()
			h.Set("Access-Control-Allow-Origin", origin)
			h.Add("Vary", "Origin")
			h.Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
			h.Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			h.Set("Access-Control-Max-Age", "600")
			if c.Request.Method == http.MethodOptions {
				c.AbortWithStatus(http.StatusNoContent)
				return
			}
		}
		c.Next()
	}
}

// keyFunc chọn khóa đếm lượt cho một request (IP với route công khai, userID với route đã đăng nhập).
type keyFunc func(c *gin.Context) string

func byClientIP(c *gin.Context) string { return "ip:" + c.ClientIP() }
func byUserID(c *gin.Context) string   { return "user:" + c.GetString("userID") }

// RateLimitMiddleware trả 429 + Retry-After khi khóa vượt tốc độ cho phép.
func RateLimitMiddleware(l *ratelimit.Limiter, key keyFunc) gin.HandlerFunc {
	return func(c *gin.Context) {
		if ok, wait := l.Allow(key(c)); !ok {
			abortTooManyRequests(c, wait, fmt.Sprintf(
				"Bạn thao tác quá nhanh. Vui lòng thử lại sau %s.", humanizeWait(wait)))
			return
		}
		c.Next()
	}
}

// aiGuard gom hai lớp giới hạn cho tính năng AI của từng người dùng: tốc độ (mỗi phút) và tổng lượt mỗi ngày.
type aiGuard struct {
	perMinute *ratelimit.Limiter
	daily     *ratelimit.DailyQuota
}

// allow kiểm tra và, nếu vượt hạn mức, tự ghi phản hồi 429. Trả về false khi request đã bị chặn.
func (g *aiGuard) allow(c *gin.Context) bool {
	key := byUserID(c)
	if ok, wait := g.perMinute.Allow(key); !ok {
		abortTooManyRequests(c, wait, fmt.Sprintf(
			"Bạn gửi yêu cầu AI quá nhanh. Vui lòng thử lại sau %s.", humanizeWait(wait)))
		return false
	}
	if ok, wait := g.daily.Allow(key); !ok {
		abortTooManyRequests(c, wait, fmt.Sprintf(
			"Bạn đã dùng hết %d lượt AI hôm nay. Hạn mức sẽ được làm mới lúc 0h.", g.daily.Limit()))
		return false
	}
	return true
}

func (g *aiGuard) middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		if !g.allow(c) {
			return
		}
		c.Next()
	}
}

func abortTooManyRequests(c *gin.Context, wait time.Duration, msg string) {
	secs := int(math.Ceil(wait.Seconds()))
	if secs < 1 {
		secs = 1
	}
	c.Header("Retry-After", strconv.Itoa(secs))
	c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{"error": msg})
}

func humanizeWait(d time.Duration) string {
	secs := int(math.Ceil(d.Seconds()))
	if secs < 1 {
		secs = 1
	}
	if secs < 60 {
		return fmt.Sprintf("%d giây", secs)
	}
	return fmt.Sprintf("%d phút", int(math.Ceil(float64(secs)/60)))
}
