package db

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"time"
	"todo-backend/internal/infrastructure/config"

	_ "github.com/lib/pq"
)

const (
	connectAttempts = 10
	connectBackoff  = 2 * time.Second
)

// NewPostgresDB mở pool kết nối và chờ Postgres sẵn sàng (thử lại vài lần vì container DB có thể khởi động chậm hơn API).
// Trả lỗi nếu hết số lần thử — caller nên dừng tiến trình thay vì chạy tiếp với DB nil.
func NewPostgresDB(ctx context.Context, cfg *config.Config) (*sql.DB, error) {
	connStr := fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		cfg.DBHost, cfg.DBPort, cfg.DBUser, cfg.DBPassword, cfg.DBName,
	)

	db, err := sql.Open("postgres", connStr)
	if err != nil {
		return nil, fmt.Errorf("error opening db: %w", err)
	}

	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(30 * time.Minute)
	db.SetConnMaxIdleTime(5 * time.Minute)

	for attempt := 1; ; attempt++ {
		pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
		err = db.PingContext(pingCtx)
		cancel()
		if err == nil {
			return db, nil
		}
		if attempt >= connectAttempts {
			db.Close()
			return nil, fmt.Errorf("error connecting to db after %d attempts: %w", attempt, err)
		}
		log.Printf("Postgres not ready (attempt %d/%d): %v", attempt, connectAttempts, err)
		select {
		case <-ctx.Done():
			db.Close()
			return nil, ctx.Err()
		case <-time.After(connectBackoff):
		}
	}
}
