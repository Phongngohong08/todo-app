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

// GetSummary tổng hợp số liệu; biểu đồ 7 ngày được gom theo ngày ở múi giờ loc của người dùng
// (server/DB chạy UTC nên nếu gom bằng ngày UTC, việc xong lúc 0h–7h sáng giờ VN sẽ bị tính sang hôm trước).
func (r *PostgresStatsRepository) GetSummary(ctx context.Context, userID string, loc *time.Location) (*domain.StatsSummary, error) {
	summary := &domain.StatsSummary{
		ByCategory:     make(map[string]int),
		DailyCompleted: make([]domain.DailyCount, 0, 7),
	}

	// 1. Số việc đã hoàn thành
	queryCompleted := `SELECT COUNT(*) FROM tasks WHERE user_id = $1 AND status = 'COMPLETED' AND deleted_at IS NULL`
	if err := r.db.QueryRowContext(ctx, queryCompleted, userID).Scan(&summary.CompletedTasks); err != nil {
		return nil, err
	}

	// 2. Số việc đang chờ (chưa xong)
	queryPending := `SELECT COUNT(*) FROM tasks WHERE user_id = $1 AND status = 'TODO' AND deleted_at IS NULL`
	if err := r.db.QueryRowContext(ctx, queryPending, userID).Scan(&summary.PendingTasks); err != nil {
		return nil, err
	}

	// 3. Phân bố việc chưa xong theo danh mục
	if err := r.countByCategory(ctx, userID, summary.ByCategory); err != nil {
		return nil, err
	}

	// 4. Số việc hoàn thành theo từng ngày trong 7 ngày gần nhất (theo tasks.completed_at)
	now := time.Now().In(loc)
	startOfToday := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, loc)
	since := startOfToday.AddDate(0, 0, -6)

	dailyCounts, err := r.completedPerLocalDay(ctx, userID, since, loc)
	if err != nil {
		return nil, err
	}

	// Trả về đủ 7 ngày (kể cả ngày không có việc) theo thứ tự tăng dần
	for i := 6; i >= 0; i-- {
		day := startOfToday.AddDate(0, 0, -i).Format("2006-01-02")
		summary.DailyCompleted = append(summary.DailyCompleted, domain.DailyCount{
			Date:      day,
			Completed: dailyCounts[day],
		})
	}

	return summary, nil
}

func (r *PostgresStatsRepository) countByCategory(ctx context.Context, userID string, out map[string]int) error {
	query := `
		SELECT category, COUNT(*)
		FROM tasks
		WHERE user_id = $1 AND status = 'TODO' AND deleted_at IS NULL
		GROUP BY category
	`
	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var category string
		var count int
		if err := rows.Scan(&category, &count); err != nil {
			return err
		}
		out[category] = count
	}
	return rows.Err()
}

// completedPerLocalDay đếm việc đã hoàn thành theo ngày (YYYY-MM-DD) ở múi giờ loc. Dữ liệu tối đa 7 ngày
// của một người nên gom trong Go là đủ nhẹ và không phụ thuộc bảng múi giờ của Postgres.
func (r *PostgresStatsRepository) completedPerLocalDay(ctx context.Context, userID string, since time.Time, loc *time.Location) (map[string]int, error) {
	query := `
		SELECT completed_at
		FROM tasks
		WHERE user_id = $1 AND status = 'COMPLETED' AND deleted_at IS NULL AND completed_at >= $2
	`
	rows, err := r.db.QueryContext(ctx, query, userID, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	counts := make(map[string]int)
	for rows.Next() {
		var completedAt time.Time
		if err := rows.Scan(&completedAt); err != nil {
			return nil, err
		}
		counts[completedAt.In(loc).Format("2006-01-02")]++
	}
	return counts, rows.Err()
}
