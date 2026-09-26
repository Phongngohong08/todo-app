package config

import (
	"strings"
	"testing"
	"time"
)

func validConfig() *Config {
	return &Config{
		DBHost:          "localhost",
		DBUser:          "postgres",
		DBName:          "todo_db",
		JWTSecret:       strings.Repeat("a", 64),
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 720 * time.Hour,
		Timezone:        time.UTC,
		AIRatePerMinute: 6,
		AIDailyLimit:    30,
	}
}

func TestValidate_AcceptsValidConfig(t *testing.T) {
	if err := validConfig().Validate(); err != nil {
		t.Fatalf("expected valid config, got %v", err)
	}
}

func TestValidate_RejectsUnsafeJWTSecret(t *testing.T) {
	cases := map[string]string{
		"empty":       "",
		"whitespace":  "   ",
		"placeholder": "super_secret_key_change_me",
		"example":     "super_secret_key_change_me_in_production",
		"too short":   "short-secret",
	}
	for name, secret := range cases {
		t.Run(name, func(t *testing.T) {
			cfg := validConfig()
			cfg.JWTSecret = secret
			err := cfg.Validate()
			if err == nil || !strings.Contains(err.Error(), "JWT_SECRET") {
				t.Fatalf("expected JWT_SECRET error, got %v", err)
			}
		})
	}
}

func TestValidate_RejectsBadTTLAndLimits(t *testing.T) {
	cfg := validConfig()
	cfg.AccessTokenTTL = cfg.RefreshTokenTTL
	if err := cfg.Validate(); err == nil {
		t.Fatal("access TTL >= refresh TTL should be rejected")
	}

	cfg = validConfig()
	cfg.AIRatePerMinute = 0
	if err := cfg.Validate(); err == nil {
		t.Fatal("AI_RATE_PER_MINUTE=0 should be rejected")
	}

	cfg = validConfig()
	cfg.AIDailyLimit = -1
	if err := cfg.Validate(); err == nil {
		t.Fatal("negative AI_DAILY_LIMIT_PER_USER should be rejected")
	}
}
