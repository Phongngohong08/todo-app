package domain

import "context"

// ListSettings là cài đặt hiển thị của một danh sách (danh mục) — kể cả 3 danh sách mặc định.
type ListSettings struct {
	Name     string   `json:"name"`
	Color    string   `json:"color"`    // "#RRGGBB" hoặc "" (màu mặc định)
	Emoji    string   `json:"emoji"`    // "" = không có
	Sections []string `json:"sections"` // các mục (cột Kanban) theo thứ tự
}

type ListSettingsRepository interface {
	ListSettings(ctx context.Context, userID string) ([]ListSettings, error)
	// ReplaceSettings thay toàn bộ cài đặt danh sách của user.
	ReplaceSettings(ctx context.Context, userID string, settings []ListSettings) error
}
