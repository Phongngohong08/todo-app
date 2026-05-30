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
		INSERT INTO tasks (id, user_id, title, description, priority, due_date, estimated_duration, preferred_time_start, preferred_time_end, status, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
	`
	_, err := r.db.ExecContext(ctx, query,
		task.ID, task.UserID, task.Title, task.Description,
		task.Priority, task.DueDate, task.EstimatedDuration,
		task.PreferredTimeStart, task.PreferredTimeEnd,
		task.Status, task.CreatedAt, task.UpdatedAt,
	)
	return err
}

func (r *PostgresTaskRepository) GetByID(ctx context.Context, id string) (*domain.Task, error) {
	query := `
		SELECT id, user_id, title, description, priority, due_date, estimated_duration, preferred_time_start, preferred_time_end, status, created_at, updated_at
		FROM tasks
		WHERE id = $1
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var task domain.Task
	err := row.Scan(
		&task.ID, &task.UserID, &task.Title, &task.Description,
		&task.Priority, &task.DueDate, &task.EstimatedDuration,
		&task.PreferredTimeStart, &task.PreferredTimeEnd,
		&task.Status, &task.CreatedAt, &task.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrTaskNotFound
		}
		return nil, err
	}
	return &task, nil
}

func (r *PostgresTaskRepository) List(ctx context.Context, userID string, status domain.TaskStatus, dueDateBefore *time.Time) ([]*domain.Task, error) {
	query := `
		SELECT id, user_id, title, description, priority, due_date, estimated_duration, preferred_time_start, preferred_time_end, status, created_at, updated_at
		FROM tasks
		WHERE user_id = $1
	`
	args := []interface{}{userID}
	argCount := 1

	if status != "" {
		argCount++
		query += fmt.Sprintf(" AND status = $%d", argCount)
		args = append(args, status)
	}

	if dueDateBefore != nil {
		argCount++
		query += fmt.Sprintf(" AND due_date <= $%d", argCount)
		args = append(args, *dueDateBefore)
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
			&task.Priority, &task.DueDate, &task.EstimatedDuration,
			&task.PreferredTimeStart, &task.PreferredTimeEnd,
			&task.Status, &task.CreatedAt, &task.UpdatedAt,
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
		    estimated_duration = $5, preferred_time_start = $6, preferred_time_end = $7, status = $8, updated_at = $9
		WHERE id = $10
	`
	res, err := r.db.ExecContext(ctx, query,
		task.Title, task.Description, task.Priority, task.DueDate,
		task.EstimatedDuration, task.PreferredTimeStart, task.PreferredTimeEnd,
		task.Status, task.UpdatedAt, task.ID,
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
