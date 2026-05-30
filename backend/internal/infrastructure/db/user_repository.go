package db

import (
	"context"
	"database/sql"
	"errors"
	"todo-backend/internal/domain"
)

type PostgresUserRepository struct {
	db *sql.DB
}

func NewPostgresUserRepository(db *sql.DB) *PostgresUserRepository {
	return &PostgresUserRepository{db: db}
}

func (r *PostgresUserRepository) Create(ctx context.Context, user *domain.User) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	queryUser := `
		INSERT INTO users (id, email, password_hash, name, created_at, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6)
	`
	_, err = tx.ExecContext(ctx, queryUser, user.ID, user.Email, user.PasswordHash, user.Name, user.CreatedAt, user.UpdatedAt)
	if err != nil {
		return err
	}

	queryPrefs := `
		INSERT INTO user_preferences (user_id, morning_start_time, evening_end_time, work_duration_preference, updated_at)
		VALUES ($1, $2, $3, $4, $5)
	`
	_, err = tx.ExecContext(ctx, queryPrefs, user.ID, "08:00", "18:00", 60, user.UpdatedAt)
	if err != nil {
		return err
	}

	return tx.Commit()
}

func (r *PostgresUserRepository) GetByID(ctx context.Context, id string) (*domain.User, error) {
	query := `
		SELECT id, email, password_hash, name, created_at, updated_at
		FROM users
		WHERE id = $1
	`
	row := r.db.QueryRowContext(ctx, query, id)
	
	var user domain.User
	err := row.Scan(&user.ID, &user.Email, &user.PasswordHash, &user.Name, &user.CreatedAt, &user.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}

	return &user, nil
}

func (r *PostgresUserRepository) GetByEmail(ctx context.Context, email string) (*domain.User, error) {
	query := `
		SELECT id, email, password_hash, name, created_at, updated_at
		FROM users
		WHERE email = $1
	`
	row := r.db.QueryRowContext(ctx, query, email)
	
	var user domain.User
	err := row.Scan(&user.ID, &user.Email, &user.PasswordHash, &user.Name, &user.CreatedAt, &user.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}

	return &user, nil
}

func (r *PostgresUserRepository) ListAllUserIDs(ctx context.Context) ([]string, error) {
	query := `SELECT id FROM users`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var ids []string
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, nil
}

func (r *PostgresUserRepository) GetPreferences(ctx context.Context, userID string) (*domain.UserPreferences, error) {
	query := `
		SELECT user_id, morning_start_time, evening_end_time, work_duration_preference, updated_at
		FROM user_preferences
		WHERE user_id = $1
	`
	row := r.db.QueryRowContext(ctx, query, userID)
	
	var prefs domain.UserPreferences
	err := row.Scan(&prefs.UserID, &prefs.MorningStartTime, &prefs.EveningEndTime, &prefs.WorkDurationPreference, &prefs.UpdatedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}

	return &prefs, nil
}

func (r *PostgresUserRepository) UpdatePreferences(ctx context.Context, prefs *domain.UserPreferences) error {
	query := `
		UPDATE user_preferences
		SET morning_start_time = $1, evening_end_time = $2, work_duration_preference = $3, updated_at = $4
		WHERE user_id = $5
	`
	_, err := r.db.ExecContext(ctx, query, prefs.MorningStartTime, prefs.EveningEndTime, prefs.WorkDurationPreference, prefs.UpdatedAt, prefs.UserID)
	return err
}
