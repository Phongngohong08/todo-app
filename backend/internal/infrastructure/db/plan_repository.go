package db

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"
	"todo-backend/internal/domain"
)

type PostgresPlanRepository struct {
	db *sql.DB
}

func NewPostgresPlanRepository(db *sql.DB) *PostgresPlanRepository {
	return &PostgresPlanRepository{db: db}
}

func (r *PostgresPlanRepository) Save(ctx context.Context, plan *domain.DailyPlan) error {
	planDataJSON, err := json.Marshal(plan.PlanData)
	if err != nil {
		return err
	}

	query := `
		INSERT INTO daily_plans (id, user_id, plan_date, plan_data, created_at)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (user_id, plan_date)
		DO UPDATE SET plan_data = EXCLUDED.plan_data, created_at = EXCLUDED.created_at
	`
	_, err = r.db.ExecContext(ctx, query,
		plan.ID, plan.UserID, plan.PlanDate.Format("2006-01-02"), planDataJSON, plan.CreatedAt,
	)
	return err
}

func (r *PostgresPlanRepository) GetByDate(ctx context.Context, userID string, date time.Time) (*domain.DailyPlan, error) {
	query := `
		SELECT id, user_id, plan_date, plan_data, created_at
		FROM daily_plans
		WHERE user_id = $1 AND plan_date = $2
	`
	row := r.db.QueryRowContext(ctx, query, userID, date.Format("2006-01-02"))

	var id, uID string
	var planDate time.Time
	var planDataJSON []byte
	var createdAt time.Time

	err := row.Scan(&id, &uID, &planDate, &planDataJSON, &createdAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}

	var slots []domain.PlanSlot
	if err := json.Unmarshal(planDataJSON, &slots); err != nil {
		return nil, err
	}

	return &domain.DailyPlan{
		ID:        id,
		UserID:    uID,
		PlanDate:  planDate,
		PlanData:  slots,
		CreatedAt: createdAt,
	}, nil
}
