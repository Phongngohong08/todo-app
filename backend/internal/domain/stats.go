package domain

import (
	"context"
	"time"
)

// DailyCount là số việc hoàn thành trong một ngày (cho biểu đồ cột 7 ngày).
type DailyCount struct {
	Date      string `json:"date"` // YYYY-MM-DD
	Completed int    `json:"completed"`
}

type StatsSummary struct {
	CompletedTasks int            `json:"completed_tasks"`
	PendingTasks   int            `json:"pending_tasks"`
	ByCategory     map[string]int `json:"by_category"`     // category -> số việc chưa xong
	DailyCompleted []DailyCount   `json:"daily_completed"` // 7 ngày gần nhất
}

type StatsRepository interface {
	// loc: múi giờ của người dùng, dùng để gom số liệu theo "ngày" của họ.
	GetSummary(ctx context.Context, userID string, loc *time.Location) (*StatsSummary, error)
}
