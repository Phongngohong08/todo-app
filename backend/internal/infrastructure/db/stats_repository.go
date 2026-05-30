package db

import (
	"context"
	"database/sql"
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
		PostponeReasons: make(map[string]int),
	}

	// 1. Completed tasks count
	queryCompleted := `
		SELECT COUNT(*)
		FROM tasks
		WHERE user_id = $1 AND status = 'COMPLETED'
	`
	err := r.db.QueryRowContext(ctx, queryCompleted, userID).Scan(&summary.CompletedTasks)
	if err != nil {
		return nil, err
	}

	// 2. Postponed tasks log count
	queryPostponed := `
		SELECT COUNT(*)
		FROM task_logs
		WHERE user_id = $1 AND action = 'POSTPONED'
	`
	err = r.db.QueryRowContext(ctx, queryPostponed, userID).Scan(&summary.PostponedTasks)
	if err != nil {
		return nil, err
	}

	// 3. Total duration of completed tasks
	queryDuration := `
		SELECT COALESCE(SUM(estimated_duration), 0)
		FROM tasks
		WHERE user_id = $1 AND status = 'COMPLETED'
	`
	err = r.db.QueryRowContext(ctx, queryDuration, userID).Scan(&summary.TotalTimeSpentMinutes)
	if err != nil {
		return nil, err
	}

	// 4. Postpone reasons breakdown
	queryReasons := `
		SELECT details, COUNT(*)
		FROM task_logs
		WHERE user_id = $1 AND action = 'POSTPONED' AND details IS NOT NULL AND details != ''
		GROUP BY details
		ORDER BY COUNT(*) DESC
	`
	rows, err := r.db.QueryContext(ctx, queryReasons, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var reason string
		var count int
		if err := rows.Scan(&reason, &count); err == nil {
			summary.PostponeReasons[reason] = count
		}
	}

	return summary, nil
}
