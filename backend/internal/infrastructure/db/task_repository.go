package db

import (
	"context"
	"database/sql"
	"encoding/json"
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

const taskColumns = `id, user_id, title, description, priority, due_date, status, category, recurrence,
	recurrence_days, reminder_offset_minutes, completed_at, sort_order, subtasks, spawned_from,
	created_at, updated_at, deleted_at, due_all_day, my_day, estimated_minutes,
	recurrence_interval, recurrence_mode, recurrence_until`

type rowScanner interface {
	Scan(dest ...any) error
}

func scanTask(row rowScanner) (*domain.Task, error) {
	var (
		task        domain.Task
		description sql.NullString
		subtasks    []byte
		spawnedFrom sql.NullString
		myDay       sql.NullString
	)
	err := row.Scan(
		&task.ID, &task.UserID, &task.Title, &description, &task.Priority, &task.DueDate,
		&task.Status, &task.Category, &task.Recurrence, &task.RecurrenceDays, &task.ReminderOffsetMinutes,
		&task.CompletedAt, &task.SortOrder, &subtasks, &spawnedFrom,
		&task.CreatedAt, &task.UpdatedAt, &task.DeletedAt, &task.DueAllDay, &myDay, &task.EstimatedMinutes,
		&task.RecurrenceInterval, &task.RecurrenceMode, &task.RecurrenceUntil,
	)
	if err != nil {
		return nil, err
	}
	task.Description = description.String
	if spawnedFrom.Valid {
		task.SpawnedFrom = &spawnedFrom.String
	}
	if myDay.Valid && myDay.String != "" {
		task.MyDay = &myDay.String
	}
	task.Subtasks = []domain.Subtask{}
	if len(subtasks) > 0 {
		if err := json.Unmarshal(subtasks, &task.Subtasks); err != nil {
			return nil, fmt.Errorf("decode subtasks of task %s: %w", task.ID, err)
		}
	}
	return &task, nil
}

func scanTasks(rows *sql.Rows) ([]*domain.Task, error) {
	defer rows.Close()
	var tasks []*domain.Task
	for rows.Next() {
		task, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		tasks = append(tasks, task)
	}
	return tasks, rows.Err()
}

func (r *PostgresTaskRepository) Upsert(ctx context.Context, task *domain.Task) error {
	subtasks := task.Subtasks
	if subtasks == nil {
		subtasks = []domain.Subtask{}
	}
	subtasksJSON, err := json.Marshal(subtasks)
	if err != nil {
		return err
	}

	// ON CONFLICT chỉ ghi đè khi cùng chủ sở hữu — chặn ở tầng DB phòng khi caller quên kiểm tra.
	query := `
		INSERT INTO tasks (` + taskColumns + `)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18,
			$19, $20, $21, $22, $23, $24)
		ON CONFLICT (id) DO UPDATE SET
			title = EXCLUDED.title, description = EXCLUDED.description, priority = EXCLUDED.priority,
			due_date = EXCLUDED.due_date, status = EXCLUDED.status, category = EXCLUDED.category,
			recurrence = EXCLUDED.recurrence, recurrence_days = EXCLUDED.recurrence_days,
			reminder_offset_minutes = EXCLUDED.reminder_offset_minutes, completed_at = EXCLUDED.completed_at,
			sort_order = EXCLUDED.sort_order, subtasks = EXCLUDED.subtasks, spawned_from = EXCLUDED.spawned_from,
			updated_at = EXCLUDED.updated_at, deleted_at = EXCLUDED.deleted_at,
			due_all_day = EXCLUDED.due_all_day, my_day = EXCLUDED.my_day,
			estimated_minutes = EXCLUDED.estimated_minutes, recurrence_interval = EXCLUDED.recurrence_interval,
			recurrence_mode = EXCLUDED.recurrence_mode, recurrence_until = EXCLUDED.recurrence_until
		WHERE tasks.user_id = EXCLUDED.user_id
	`
	interval := task.RecurrenceInterval
	if interval < 1 {
		interval = 1
	}
	mode := task.RecurrenceMode
	if mode == "" {
		mode = domain.RecurrenceModeSchedule
	}
	res, err := r.db.ExecContext(ctx, query,
		task.ID, task.UserID, task.Title, task.Description, task.Priority, task.DueDate,
		task.Status, task.Category, task.Recurrence, task.RecurrenceDays, task.ReminderOffsetMinutes,
		task.CompletedAt, task.SortOrder, subtasksJSON, task.SpawnedFrom,
		task.CreatedAt, task.UpdatedAt, task.DeletedAt,
		task.DueAllDay, task.MyDay, task.EstimatedMinutes, interval, mode, task.RecurrenceUntil,
	)
	if err != nil {
		return err
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		return domain.ErrTaskNotFound // id đã thuộc về người khác
	}
	return nil
}

func (r *PostgresTaskRepository) GetByID(ctx context.Context, id string) (*domain.Task, error) {
	query := `SELECT ` + taskColumns + ` FROM tasks WHERE id = $1`
	task, err := scanTask(r.db.QueryRowContext(ctx, query, id))
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrTaskNotFound
	}
	return task, err
}

func (r *PostgresTaskRepository) List(ctx context.Context, userID string, filter domain.TaskFilter) ([]*domain.Task, error) {
	query := `SELECT ` + taskColumns + ` FROM tasks WHERE user_id = $1 AND deleted_at IS NULL`
	args := []any{userID}

	if filter.Status != "" {
		args = append(args, filter.Status)
		query += fmt.Sprintf(" AND status = $%d", len(args))
	}
	if filter.DueDateBefore != nil {
		args = append(args, *filter.DueDateBefore)
		query += fmt.Sprintf(" AND due_date <= $%d", len(args))
	}
	if filter.Query != "" {
		args = append(args, filter.Query)
		n := len(args)
		query += fmt.Sprintf(" AND (title ILIKE '%%' || $%d || '%%' OR description ILIKE '%%' || $%d || '%%')", n, n)
	}
	if filter.Category != "" {
		args = append(args, filter.Category)
		query += fmt.Sprintf(" AND category = $%d", len(args))
	}

	query += " ORDER BY sort_order ASC, created_at DESC"

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	return scanTasks(rows)
}

func (r *PostgresTaskRepository) ListChangedSince(ctx context.Context, userID string, since *time.Time) ([]*domain.Task, error) {
	var (
		rows *sql.Rows
		err  error
	)
	if since == nil {
		query := `SELECT ` + taskColumns + ` FROM tasks WHERE user_id = $1 AND deleted_at IS NULL`
		rows, err = r.db.QueryContext(ctx, query, userID)
	} else {
		query := `SELECT ` + taskColumns + ` FROM tasks WHERE user_id = $1 AND updated_at > $2`
		rows, err = r.db.QueryContext(ctx, query, userID, *since)
	}
	if err != nil {
		return nil, err
	}
	return scanTasks(rows)
}

func (r *PostgresTaskRepository) ListMyDay(ctx context.Context, userID string, day string) ([]*domain.Task, error) {
	query := `SELECT ` + taskColumns + ` FROM tasks
		WHERE user_id = $1 AND deleted_at IS NULL AND status = 'TODO' AND my_day = $2
		ORDER BY sort_order ASC, created_at DESC`
	rows, err := r.db.QueryContext(ctx, query, userID, day)
	if err != nil {
		return nil, err
	}
	return scanTasks(rows)
}

func (r *PostgresTaskRepository) SoftDelete(ctx context.Context, id string, at time.Time) error {
	query := `UPDATE tasks SET deleted_at = $1, updated_at = $1 WHERE id = $2 AND deleted_at IS NULL`
	_, err := r.db.ExecContext(ctx, query, at, id)
	return err
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
		var details sql.NullString
		if err := rows.Scan(&log.ID, &log.TaskID, &log.UserID, &log.Action, &details, &log.CreatedAt); err != nil {
			return nil, err
		}
		log.Details = details.String
		logs = append(logs, &log)
	}

	return logs, rows.Err()
}
