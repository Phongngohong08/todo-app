package db

import (
	"context"
	"database/sql"
	"todo-backend/internal/domain"
)

type PostgresChatRepository struct {
	db *sql.DB
}

func NewPostgresChatRepository(db *sql.DB) *PostgresChatRepository {
	return &PostgresChatRepository{db: db}
}

func (r *PostgresChatRepository) Save(ctx context.Context, message *domain.ChatMessage) error {
	query := `
		INSERT INTO chat_messages (id, user_id, role, content, created_at)
		VALUES ($1, $2, $3, $4, $5)
	`
	_, err := r.db.ExecContext(ctx, query,
		message.ID, message.UserID, message.Role, message.Content, message.CreatedAt,
	)
	return err
}

func (r *PostgresChatRepository) GetHistory(ctx context.Context, userID string, limit int) ([]*domain.ChatMessage, error) {
	query := `
		SELECT id, user_id, role, content, created_at
		FROM chat_messages
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`
	rows, err := r.db.QueryContext(ctx, query, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var messages []*domain.ChatMessage
	for rows.Next() {
		var msg domain.ChatMessage
		err := rows.Scan(&msg.ID, &msg.UserID, &msg.Role, &msg.Content, &msg.CreatedAt)
		if err != nil {
			return nil, err
		}
		// Since we ordered by DESC to get the latest, but we typically want it in chronological order:
		messages = append([]*domain.ChatMessage{&msg}, messages...)
	}

	return messages, nil
}
