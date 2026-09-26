package db

import (
	"context"
	"database/sql"
	"todo-backend/internal/domain"

	"github.com/lib/pq"
)

type PostgresCategoryRepository struct {
	db *sql.DB
}

func NewPostgresCategoryRepository(db *sql.DB) *PostgresCategoryRepository {
	return &PostgresCategoryRepository{db: db}
}

func (r *PostgresCategoryRepository) List(ctx context.Context, userID string) ([]string, error) {
	// Danh mục đã lưu + danh mục xuất hiện trong task (vd tạo từ bản app cũ chỉ lưu danh mục trên máy)
	query := `
		SELECT name FROM (
			SELECT name, position, 0 AS src FROM user_categories WHERE user_id = $1
			UNION ALL
			SELECT DISTINCT category, 2147483647, 1 FROM tasks
			WHERE user_id = $1 AND deleted_at IS NULL AND NOT (category = ANY($2))
			  AND category NOT IN (SELECT name FROM user_categories WHERE user_id = $1)
		) c
		ORDER BY src, position, name
	`
	rows, err := r.db.QueryContext(ctx, query, userID, pq.Array(domain.DefaultCategories))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	names := []string{}
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			return nil, err
		}
		names = append(names, name)
	}
	return names, rows.Err()
}

func (r *PostgresCategoryRepository) Replace(ctx context.Context, userID string, names []string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, `DELETE FROM user_categories WHERE user_id = $1`, userID); err != nil {
		return err
	}
	for i, name := range names {
		if _, err := tx.ExecContext(ctx,
			`INSERT INTO user_categories (user_id, name, position) VALUES ($1, $2, $3)`, userID, name, i); err != nil {
			return err
		}
	}
	return tx.Commit()
}
