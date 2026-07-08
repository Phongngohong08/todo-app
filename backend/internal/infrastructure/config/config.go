package config

import (
	"bufio"
	"os"
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
}

func Load() *Config {
	// Load .env file if it exists
	loadEnv(".env")

	return &Config{
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
		JWTSecret:       getEnv("JWT_SECRET", "super_secret_key_change_me"),
		AccessTokenTTL:  getEnvDuration("ACCESS_TOKEN_TTL", 15*time.Minute),
		RefreshTokenTTL: getEnvDuration("REFRESH_TOKEN_TTL", 30*24*time.Hour),
	}
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
