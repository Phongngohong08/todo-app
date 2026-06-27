package db

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
	"todo-backend/internal/domain"
)

type PostgresTaskRepository struct {
	db *sql.DB
}

func NewPostgresTaskRepository(db *sql.DB) *PostgresTaskRepository {
	return &PostgresTaskRepository{db: db}
}

func (r *PostgresTaskRepository) Create(ctx context.Context, task *domain.Task) error {
	query := `
		INSERT INTO tasks (id, user_id, title, description, priority, due_date, status, category, recurrence, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
	`
	_, err := r.db.ExecContext(ctx, query,
		task.ID, task.UserID, task.Title, task.Description,
		task.Priority, task.DueDate,
		task.Status, task.Category, task.Recurrence,
		task.CreatedAt, task.UpdatedAt,
	)
	return err
}

func (r *PostgresTaskRepository) GetByID(ctx context.Context, id string) (*domain.Task, error) {
	query := `
		SELECT id, user_id, title, description, priority, due_date, status, category, recurrence, created_at, updated_at
		FROM tasks
		WHERE id = $1
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var task domain.Task
	err := row.Scan(
		&task.ID, &task.UserID, &task.Title, &task.Description,
		&task.Priority, &task.DueDate,
		&task.Status, &task.Category, &task.Recurrence, &task.CreatedAt, &task.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrTaskNotFound
		}
		return nil, err
	}
	return &task, nil
}

func (r *PostgresTaskRepository) List(ctx context.Context, userID string, filter domain.TaskFilter) ([]*domain.Task, error) {
	query := `
		SELECT id, user_id, title, description, priority, due_date, status, category, recurrence, created_at, updated_at
		FROM tasks
		WHERE user_id = $1
	`
	args := []any{userID}
	argCount := 1

	if filter.Status != "" {
		argCount++
		query += fmt.Sprintf(" AND status = $%d", argCount)
		args = append(args, filter.Status)
	}

	if filter.DueDateBefore != nil {
		argCount++
		query += fmt.Sprintf(" AND due_date <= $%d", argCount)
		args = append(args, *filter.DueDateBefore)
	}

	if filter.Query != "" {
		argCount++
		query += fmt.Sprintf(" AND (title ILIKE '%%' || $%d || '%%' OR description ILIKE '%%' || $%d || '%%')", argCount, argCount)
		args = append(args, filter.Query)
	}

	if filter.Category != "" {
		argCount++
		query += fmt.Sprintf(" AND category = $%d", argCount)
		args = append(args, filter.Category)
	}

	query += " ORDER BY due_date ASC, created_at DESC"

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tasks []*domain.Task
	for rows.Next() {
		var task domain.Task
		err := rows.Scan(
			&task.ID, &task.UserID, &task.Title, &task.Description,
			&task.Priority, &task.DueDate,
			&task.Status, &task.Category, &task.Recurrence, &task.CreatedAt, &task.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		tasks = append(tasks, &task)
	}

	return tasks, nil
}

func (r *PostgresTaskRepository) Update(ctx context.Context, task *domain.Task) error {
	query := `
		UPDATE tasks
		SET title = $1, description = $2, priority = $3, due_date = $4,
		    status = $5, category = $6, recurrence = $7, updated_at = $8
		WHERE id = $9
	`
	res, err := r.db.ExecContext(ctx, query,
		task.Title, task.Description, task.Priority, task.DueDate,
		task.Status, task.Category, task.Recurrence, task.UpdatedAt, task.ID,
	)
	if err != nil {
		return err
	}
	
	rows, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return domain.ErrTaskNotFound
	}
	return nil
}

func (r *PostgresTaskRepository) Delete(ctx context.Context, id string) error {
	query := `DELETE FROM tasks WHERE id = $1`
	res, err := r.db.ExecContext(ctx, query, id)
	if err != nil {
		return err
	}

	rows, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return domain.ErrTaskNotFound
	}
	return nil
}

func (r *PostgresTaskRepository) CreateLog(ctx context.Context, log *domain.TaskLog) error {
	query := `
		INSERT INTO task_logs (id, task_id, user_id, action, details, created_at)
		VALUES ($1, $2, $3, $4, $5, $6)
	`
	_, err := r.db.ExecContext(ctx, query,
		log.ID, log.TaskID, log.UserID, log.Action, log.Details, log.CreatedAt,
	)
	return err
}

func (r *PostgresTaskRepository) ListLogs(ctx context.Context, userID string, since time.Time) ([]*domain.TaskLog, error) {
	query := `
		SELECT id, task_id, user_id, action, details, created_at
		FROM task_logs
		WHERE user_id = $1 AND created_at >= $2
		ORDER BY created_at ASC
	`
	rows, err := r.db.QueryContext(ctx, query, userID, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []*domain.TaskLog
	for rows.Next() {
		var log domain.TaskLog
		err := rows.Scan(&log.ID, &log.TaskID, &log.UserID, &log.Action, &log.Details, &log.CreatedAt)
		if err != nil {
			return nil, err
		}
		logs = append(logs, &log)
	}

	return logs, nil
}
