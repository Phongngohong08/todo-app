package config

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Port            string
	DBHost          string
	DBPort          string
	DBUser          string
	DBPassword      string
	DBName          string
	QdrantHost      string
	QdrantPort      string
	GeminiKey       string
	GeminiModel     string
	JWTSecret       string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration

	// AutoMigrate: tự áp các migration còn thiếu khi khởi động (mặc định bật).
	AutoMigrate bool
	// Timezone: múi giờ mặc định của người dùng (lịch job ngầm, "hôm nay" khi client không gửi ngày).
	Timezone *time.Location
	// CORSAllowedOrigins: danh sách origin trình duyệt được phép; rỗng = không bật CORS (app mobile không cần).
	CORSAllowedOrigins []string
	// TrustedProxies: IP/CIDR của reverse proxy (Nginx) để Gin đọc IP thật của client từ X-Forwarded-For.
	TrustedProxies []string

	// Giới hạn tần suất gọi AI cho MỖI người dùng.
	AIRatePerMinute int // số lượt/phút (token bucket)
	AIDailyLimit    int // tổng lượt/ngày; 0 = không giới hạn
}

// Các giá trị mẫu từng xuất hiện trong code/.env.example — dùng chúng ở production là lộ khóa ký JWT.
var placeholderSecrets = map[string]bool{
	"super_secret_key_change_me":               true,
	"super_secret_key_change_me_in_production": true,
	"change_me": true,
}

const minJWTSecretLength = 32

func Load() (*Config, error) {
	// Load .env file if it exists
	loadEnv(".env")

	tzName := getEnv("APP_TIMEZONE", "Asia/Ho_Chi_Minh")
	loc, err := time.LoadLocation(tzName)
	if err != nil {
		return nil, fmt.Errorf("APP_TIMEZONE %q không hợp lệ: %w", tzName, err)
	}

	cfg := &Config{
		Port:            getEnv("PORT", "8080"),
		DBHost:          getEnv("DB_HOST", "localhost"),
		DBPort:          getEnv("DB_PORT", "5432"),
		DBUser:          getEnv("DB_USER", "postgres"),
		DBPassword:      getEnv("DB_PASSWORD", "postgrespassword"),
		DBName:          getEnv("DB_NAME", "todo_db"),
		QdrantHost:      getEnv("QDRANT_HOST", "localhost"),
		QdrantPort:      getEnv("QDRANT_PORT", "6333"),
		GeminiKey:       getEnv("GEMINI_API_KEY", ""),
		GeminiModel:     getEnv("GEMINI_MODEL", ""),
		JWTSecret:       getEnv("JWT_SECRET", ""),
		AccessTokenTTL:  getEnvDuration("ACCESS_TOKEN_TTL", 15*time.Minute),
		RefreshTokenTTL: getEnvDuration("REFRESH_TOKEN_TTL", 30*24*time.Hour),

		AutoMigrate:        getEnvBool("AUTO_MIGRATE", true),
		Timezone:           loc,
		CORSAllowedOrigins: getEnvList("CORS_ALLOWED_ORIGINS", ""),
		// Mặc định tin loopback + dải mạng Docker bridge (Nginx trên host đi vào container qua gateway 172.x.0.1)
		TrustedProxies: getEnvList("TRUSTED_PROXIES", "127.0.0.1,::1,172.16.0.0/12"),

		AIRatePerMinute: getEnvInt("AI_RATE_PER_MINUTE", 6),
		AIDailyLimit:    getEnvInt("AI_DAILY_LIMIT_PER_USER", 30),
	}

	if err := cfg.Validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// Validate chặn khởi động khi cấu hình không an toàn/không hợp lệ, thay vì chạy "nửa vời" rồi lỗi lúc runtime.
func (c *Config) Validate() error {
	var errs []error

	secret := strings.TrimSpace(c.JWTSecret)
	switch {
	case secret == "":
		errs = append(errs, errors.New("JWT_SECRET chưa được đặt"))
	case placeholderSecrets[secret]:
		errs = append(errs, errors.New("JWT_SECRET đang dùng giá trị mẫu công khai"))
	case len(secret) < minJWTSecretLength:
		errs = append(errs, fmt.Errorf("JWT_SECRET phải dài tối thiểu %d ký tự", minJWTSecretLength))
	}
	if len(errs) > 0 {
		errs = append(errs, errors.New("tạo khóa mới bằng: openssl rand -hex 32"))
	}

	if c.AccessTokenTTL >= c.RefreshTokenTTL {
		errs = append(errs, errors.New("ACCESS_TOKEN_TTL phải ngắn hơn REFRESH_TOKEN_TTL"))
	}
	if c.DBHost == "" || c.DBUser == "" || c.DBName == "" {
		errs = append(errs, errors.New("DB_HOST, DB_USER, DB_NAME không được để trống"))
	}
	if c.AIRatePerMinute <= 0 {
		errs = append(errs, errors.New("AI_RATE_PER_MINUTE phải > 0"))
	}
	if c.AIDailyLimit < 0 {
		errs = append(errs, errors.New("AI_DAILY_LIMIT_PER_USER phải >= 0 (0 = không giới hạn)"))
	}

	if len(errs) > 0 {
		msgs := make([]string, len(errs))
		for i, e := range errs {
			msgs[i] = e.Error()
		}
		return fmt.Errorf("cấu hình không hợp lệ:\n  - %s", strings.Join(msgs, "\n  - "))
	}
	return nil
}

// getEnvDuration đọc một biến môi trường dạng Go duration (vd "15m", "720h"), trả về fallback nếu trống/không hợp lệ.
func getEnvDuration(key string, fallback time.Duration) time.Duration {
	if value, ok := os.LookupEnv(key); ok && value != "" {
		if d, err := time.ParseDuration(value); err == nil {
			return d
		}
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if value, ok := os.LookupEnv(key); ok && value != "" {
		if n, err := strconv.Atoi(strings.TrimSpace(value)); err == nil {
			return n
		}
	}
	return fallback
}

func getEnvBool(key string, fallback bool) bool {
	if value, ok := os.LookupEnv(key); ok && value != "" {
		if b, err := strconv.ParseBool(strings.TrimSpace(value)); err == nil {
			return b
		}
	}
	return fallback
}

// getEnvList đọc danh sách phân tách bằng dấu phẩy, bỏ phần tử rỗng.
func getEnvList(key, fallback string) []string {
	raw := getEnv(key, fallback)
	var out []string
	for _, p := range strings.Split(raw, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func loadEnv(filename string) {
	file, err := os.Open(filename)
	if err != nil {
		return // Silently ignore if file doesn't exist
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}
		key := strings.TrimSpace(parts[0])
		val := strings.TrimSpace(parts[1])

		// Strip quotes if wrapped
		if (strings.HasPrefix(val, "\"") && strings.HasSuffix(val, "\"")) ||
			(strings.HasPrefix(val, "'") && strings.HasSuffix(val, "'")) {
			val = val[1 : len(val)-1]
		}

		// Only set if not already set by system env
		if os.Getenv(key) == "" {
			os.Setenv(key, val)
		}
	}
}
