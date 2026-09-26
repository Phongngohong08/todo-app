package db

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"log"
	"sort"
	"strconv"
	"strings"
)

// Khóa advisory để hai tiến trình API khởi động cùng lúc không áp migration chồng lên nhau.
const migrationLockID = 727_001

// legacyBaselineVersion là version cuối cùng từng được áp THỦ CÔNG (docker cp + psql) trước khi có bảng
// schema_migrations. DB cũ đã có schema tới version này sẽ được đánh dấu là đã áp thay vì chạy lại.
const legacyBaselineVersion = 5

type migrationFile struct {
	version int
	name    string
}

// RunMigrations áp các file <version>_<tên>.up.sql trong fsys theo thứ tự version, mỗi file trong một transaction,
// và ghi lại version đã áp vào bảng schema_migrations. Gọi lại nhiều lần là an toàn (bỏ qua version đã áp).
func RunMigrations(ctx context.Context, db *sql.DB, fsys fs.FS) error {
	files, err := listMigrations(fsys)
	if err != nil {
		return err
	}

	// Advisory lock gắn với session → phải giữ nguyên một connection trong suốt quá trình.
	conn, err := db.Conn(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()

	if _, err := conn.ExecContext(ctx, `SELECT pg_advisory_lock($1)`, migrationLockID); err != nil {
		return fmt.Errorf("acquire migration lock: %w", err)
	}
	defer conn.ExecContext(context.Background(), `SELECT pg_advisory_unlock($1)`, migrationLockID)

	if _, err := conn.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version INT PRIMARY KEY,
			name TEXT NOT NULL,
			applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
		)`); err != nil {
		return fmt.Errorf("create schema_migrations: %w", err)
	}

	applied, err := appliedVersions(ctx, conn)
	if err != nil {
		return err
	}

	if len(applied) == 0 {
		if err := baselineLegacySchema(ctx, conn, files, applied); err != nil {
			return err
		}
	}

	for _, f := range files {
		if applied[f.version] {
			continue
		}
		body, err := fs.ReadFile(fsys, f.name)
		if err != nil {
			return err
		}

		tx, err := conn.BeginTx(ctx, nil)
		if err != nil {
			return err
		}
		// Không truyền tham số → lib/pq dùng simple query protocol, cho phép nhiều câu lệnh trong một file.
		if _, err := tx.ExecContext(ctx, string(body)); err != nil {
			tx.Rollback()
			return fmt.Errorf("migration %s failed: %w", f.name, err)
		}
		if _, err := tx.ExecContext(ctx,
			`INSERT INTO schema_migrations (version, name) VALUES ($1, $2)`, f.version, f.name); err != nil {
			tx.Rollback()
			return fmt.Errorf("record migration %s: %w", f.name, err)
		}
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit migration %s: %w", f.name, err)
		}
		log.Printf("Applied migration %s", f.name)
	}
	return nil
}

// baselineLegacySchema: DB tạo từ trước khi có bảng schema_migrations (áp tay tới 000005) sẽ có cột
// tasks.recurrence_days. Khi đó đánh dấu 1..legacyBaselineVersion là đã áp để không chạy lại các file cũ.
func baselineLegacySchema(ctx context.Context, conn *sql.Conn, files []migrationFile, applied map[int]bool) error {
	var exists bool
	err := conn.QueryRowContext(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM information_schema.columns
			WHERE table_schema = current_schema() AND table_name = 'tasks' AND column_name = 'recurrence_days'
		)`).Scan(&exists)
	if err != nil {
		return fmt.Errorf("detect legacy schema: %w", err)
	}
	if !exists {
		return nil
	}

	for _, f := range files {
		if f.version > legacyBaselineVersion {
			continue
		}
		if _, err := conn.ExecContext(ctx,
			`INSERT INTO schema_migrations (version, name) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
			f.version, f.name); err != nil {
			return err
		}
		applied[f.version] = true
	}
	log.Printf("Detected schema applied manually up to version %d; recorded as baseline", legacyBaselineVersion)
	return nil
}

func appliedVersions(ctx context.Context, conn *sql.Conn) (map[int]bool, error) {
	rows, err := conn.QueryContext(ctx, `SELECT version FROM schema_migrations`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	applied := make(map[int]bool)
	for rows.Next() {
		var v int
		if err := rows.Scan(&v); err != nil {
			return nil, err
		}
		applied[v] = true
	}
	return applied, rows.Err()
}

func listMigrations(fsys fs.FS) ([]migrationFile, error) {
	names, err := fs.Glob(fsys, "*.up.sql")
	if err != nil {
		return nil, err
	}

	seen := make(map[int]string)
	var files []migrationFile
	for _, name := range names {
		prefix, _, ok := strings.Cut(name, "_")
		if !ok {
			return nil, fmt.Errorf("invalid migration file name %q", name)
		}
		v, err := strconv.Atoi(prefix)
		if err != nil {
			return nil, fmt.Errorf("invalid migration version in %q: %w", name, err)
		}
		if other, dup := seen[v]; dup {
			return nil, fmt.Errorf("duplicate migration version %d: %s and %s", v, other, name)
		}
		seen[v] = name
		files = append(files, migrationFile{version: v, name: name})
	}
	sort.Slice(files, func(i, j int) bool { return files[i].version < files[j].version })
	return files, nil
}
