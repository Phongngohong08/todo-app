package domain

import "context"

type StatsSummary struct {
	CompletedTasks        int            `json:"completed_tasks"`
	PostponedTasks        int            `json:"postponed_tasks"`
	TotalTimeSpentMinutes int            `json:"total_time_spent_mins"`
	MostPostponedCategory string         `json:"most_postponed_category,omitempty"`
	PostponeReasons       map[string]int `json:"postpone_reasons,omitempty"`
}

type StatsRepository interface {
	GetSummary(ctx context.Context, userID string) (*StatsSummary, error)
}
