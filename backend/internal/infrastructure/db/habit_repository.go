package db

import (
	"context"
	"database/sql"
	"errors"
	"time"
	"todo-backend/internal/domain"
)

type PostgresHabitRepository struct {
	db *sql.DB
}

func NewPostgresHabitRepository(db *sql.DB) *PostgresHabitRepository {
	return &PostgresHabitRepository{db: db}
}

const habitColumns = `id, user_id, name, emoji, color, frequency, weekdays, target_per_week, reminder_time,
	position, archived, created_at, updated_at, deleted_at`

func scanHabit(row rowScanner) (*domain.Habit, error) {
	var h domain.Habit
	err := row.Scan(&h.ID, &h.UserID, &h.Name, &h.Emoji, &h.Color, &h.Frequency, &h.Weekdays, &h.TargetPerWeek,
		&h.ReminderTime, &h.Position, &h.Archived, &h.CreatedAt, &h.UpdatedAt, &h.DeletedAt)
	if err != nil {
		return nil, err
	}
	return &h, nil
}

func (r *PostgresHabitRepository) Upsert(ctx context.Context, h *domain.Habit) error {
	// Như tasks: chỉ ghi đè khi cùng chủ sở hữu.
	query := `
		INSERT INTO habits (` + habitColumns + `)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
		ON CONFLICT (id) DO UPDATE SET
			name = EXCLUDED.name, emoji = EXCLUDED.emoji, color = EXCLUDED.color,
			frequency = EXCLUDED.frequency, weekdays = EXCLUDED.weekdays,
			target_per_week = EXCLUDED.target_per_week, reminder_time = EXCLUDED.reminder_time,
			position = EXCLUDED.position, archived = EXCLUDED.archived,
			updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at
		WHERE habits.user_id = EXCLUDED.user_id
	`
	res, err := r.db.ExecContext(ctx, query,
		h.ID, h.UserID, h.Name, h.Emoji, h.Color, h.Frequency, h.Weekdays, h.TargetPerWeek, h.ReminderTime,
		h.Position, h.Archived, h.CreatedAt, h.UpdatedAt, h.DeletedAt,
	)
	if err != nil {
		return err
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *PostgresHabitRepository) GetByID(ctx context.Context, id string) (*domain.Habit, error) {
	h, err := scanHabit(r.db.QueryRowContext(ctx, `SELECT `+habitColumns+` FROM habits WHERE id = $1`, id))
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	return h, err
}

func (r *PostgresHabitRepository) ListChangedSince(ctx context.Context, userID string, since *time.Time) ([]*domain.Habit, error) {
	var (
		rows *sql.Rows
		err  error
	)
	if since == nil {
		rows, err = r.db.QueryContext(ctx,
			`SELECT `+habitColumns+` FROM habits WHERE user_id = $1 AND deleted_at IS NULL`, userID)
	} else {
		rows, err = r.db.QueryContext(ctx,
			`SELECT `+habitColumns+` FROM habits WHERE user_id = $1 AND updated_at > $2`, userID, *since)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	habits := []*domain.Habit{}
	for rows.Next() {
		h, err := scanHabit(rows)
		if err != nil {
			return nil, err
		}
		habits = append(habits, h)
	}
	return habits, rows.Err()
}

func (r *PostgresHabitRepository) SoftDelete(ctx context.Context, id string, at time.Time) error {
	_, err := r.db.ExecContext(ctx,
		`UPDATE habits SET deleted_at = $1, updated_at = $1 WHERE id = $2 AND deleted_at IS NULL`, at, id)
	return err
}

func (r *PostgresHabitRepository) SetCheckin(ctx context.Context, userID string, c domain.HabitCheckin) error {
	query := `
		INSERT INTO habit_checkins (habit_id, user_id, day, done, updated_at)
		VALUES ($1, $2, $3::date, $4, $5)
		ON CONFLICT (habit_id, day) DO UPDATE SET done = EXCLUDED.done, updated_at = EXCLUDED.updated_at
		WHERE habit_checkins.user_id = EXCLUDED.user_id
	`
	_, err := r.db.ExecContext(ctx, query, c.HabitID, userID, c.Date, c.Done, c.UpdatedAt)
	return err
}

func (r *PostgresHabitRepository) CheckinsChangedSince(ctx context.Context, userID string, since *time.Time) ([]domain.HabitCheckin, error) {
	var (
		rows *sql.Rows
		err  error
	)
	if since == nil {
		rows, err = r.db.QueryContext(ctx, `
			SELECT c.habit_id, c.day, c.done, c.updated_at FROM habit_checkins c
			JOIN habits h ON h.id = c.habit_id
			WHERE c.user_id = $1 AND c.done AND h.deleted_at IS NULL`, userID)
	} else {
		rows, err = r.db.QueryContext(ctx, `
			SELECT habit_id, day, done, updated_at FROM habit_checkins
			WHERE user_id = $1 AND updated_at > $2`, userID, *since)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []domain.HabitCheckin{}
	for rows.Next() {
		var (
			c   domain.HabitCheckin
			day time.Time
		)
		if err := rows.Scan(&c.HabitID, &day, &c.Done, &c.UpdatedAt); err != nil {
			return nil, err
		}
		c.Date = day.Format("2006-01-02")
		out = append(out, c)
	}
	return out, rows.Err()
}
