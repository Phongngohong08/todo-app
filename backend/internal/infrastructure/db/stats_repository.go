package db

import (
	"context"
	"database/sql"
	"time"
	"todo-backend/internal/domain"
)

type PostgresStatsRepository struct {
	db *sql.DB
}

func NewPostgresStatsRepository(db *sql.DB) *PostgresStatsRepository {
	return &PostgresStatsRepository{db: db}
}

func (r *PostgresStatsRepository) GetSummary(ctx context.Context, userID string) (*domain.StatsSummary, error) {
	summary := &domain.StatsSummary{
		ByCategory:     make(map[string]int),
		DailyCompleted: make([]domain.DailyCount, 0, 7),
	}

	// 1. Số việc đã hoàn thành
	queryCompleted := `SELECT COUNT(*) FROM tasks WHERE user_id = $1 AND status = 'COMPLETED'`
	if err := r.db.QueryRowContext(ctx, queryCompleted, userID).Scan(&summary.CompletedTasks); err != nil {
		return nil, err
	}

	// 2. Số việc đang chờ (chưa xong)
	queryPending := `SELECT COUNT(*) FROM tasks WHERE user_id = $1 AND status = 'TODO'`
	if err := r.db.QueryRowContext(ctx, queryPending, userID).Scan(&summary.PendingTasks); err != nil {
		return nil, err
	}

	// 3. Phân bố việc chưa xong theo danh mục
	queryByCategory := `
		SELECT category, COUNT(*)
		FROM tasks
		WHERE user_id = $1 AND status = 'TODO'
		GROUP BY category
	`
	rows, err := r.db.QueryContext(ctx, queryByCategory, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var category string
		var count int
		if err := rows.Scan(&category, &count); err == nil {
			summary.ByCategory[category] = count
		}
	}

	// 4. Số việc hoàn thành theo từng ngày trong 7 ngày gần nhất (từ task_logs action=COMPLETED)
	dailyCounts := make(map[string]int)
	queryDaily := `
		SELECT to_char(created_at, 'YYYY-MM-DD') AS d, COUNT(*)
		FROM task_logs
		WHERE user_id = $1 AND action = 'COMPLETED' AND created_at >= $2
		GROUP BY d
	`
	since := time.Now().AddDate(0, 0, -6).Truncate(24 * time.Hour)
	dRows, err := r.db.QueryContext(ctx, queryDaily, userID, since)
	if err != nil {
		return nil, err
	}
	defer dRows.Close()
	for dRows.Next() {
		var d string
		var count int
		if err := dRows.Scan(&d, &count); err == nil {
			dailyCounts[d] = count
		}
	}

	// Trả về đủ 7 ngày (kể cả ngày không có việc) theo thứ tự tăng dần
	for i := 6; i >= 0; i-- {
		day := time.Now().AddDate(0, 0, -i).Format("2006-01-02")
		summary.DailyCompleted = append(summary.DailyCompleted, domain.DailyCount{
			Date:      day,
			Completed: dailyCounts[day],
		})
	}

	return summary, nil
}
