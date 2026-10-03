package db

import (
	"context"
	"database/sql"
	"time"
	"todo-backend/internal/domain"
)

type PostgresFocusRepository struct {
	db *sql.DB
}

func NewPostgresFocusRepository(db *sql.DB) *PostgresFocusRepository {
	return &PostgresFocusRepository{db: db}
}

func (r *PostgresFocusRepository) Insert(ctx context.Context, s *domain.FocusSession) error {
	// Gửi lại cùng id (mất mạng giữa chừng) → bỏ qua. id trùng của người khác → báo không tìm thấy.
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO focus_sessions (id, user_id, task_id, task_title, started_at, minutes, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		ON CONFLICT (id) DO NOTHING`,
		s.ID, s.UserID, s.TaskID, s.TaskTitle, s.StartedAt, s.Minutes, s.CreatedAt)
	if err != nil {
		return err
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		var owner string
		if err := r.db.QueryRowContext(ctx, `SELECT user_id FROM focus_sessions WHERE id = $1`, s.ID).Scan(&owner); err != nil {
			return err
		}
		if owner != s.UserID {
			return domain.ErrNotFound
		}
	}
	return nil
}

func (r *PostgresFocusRepository) ListCreatedSince(ctx context.Context, userID string, since *time.Time, limit int) ([]*domain.FocusSession, error) {
	const columns = `id, user_id, task_id, task_title, started_at, minutes, created_at`
	var (
		rows *sql.Rows
		err  error
	)
	if since == nil {
		rows, err = r.db.QueryContext(ctx,
			`SELECT `+columns+` FROM focus_sessions WHERE user_id = $1 ORDER BY started_at DESC LIMIT $2`,
			userID, limit)
	} else {
		rows, err = r.db.QueryContext(ctx,
			`SELECT `+columns+` FROM focus_sessions WHERE user_id = $1 AND created_at > $2 ORDER BY started_at DESC LIMIT $3`,
			userID, *since, limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []*domain.FocusSession{}
	for rows.Next() {
		var (
			s      domain.FocusSession
			taskID sql.NullString
		)
		if err := rows.Scan(&s.ID, &s.UserID, &taskID, &s.TaskTitle, &s.StartedAt, &s.Minutes, &s.CreatedAt); err != nil {
			return nil, err
		}
		if taskID.Valid {
			s.TaskID = &taskID.String
		}
		out = append(out, &s)
	}
	return out, rows.Err()
}
