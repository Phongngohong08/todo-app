package domain

import (
	"context"
	"time"
)

type PlanSlot struct {
	StartTime string `json:"start"`   // e.g., "08:00"
	EndTime   string `json:"end"`     // e.g., "09:00"
	TaskID    string `json:"task_id"`
	Title     string `json:"title"`
}

type DailyPlan struct {
	ID        string     `json:"id"`
	UserID    string     `json:"user_id"`
	PlanDate  time.Time  `json:"plan_date"` // Date only
	PlanData  []PlanSlot `json:"plan_data"`
	CreatedAt time.Time  `json:"created_at"`
}

type PlanRepository interface {
	Save(ctx context.Context, plan *DailyPlan) error
	GetByDate(ctx context.Context, userID string, date time.Time) (*DailyPlan, error)
}
