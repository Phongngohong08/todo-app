DROP TABLE IF EXISTS user_categories;
DROP INDEX IF EXISTS idx_tasks_user_status;
DROP INDEX IF EXISTS idx_tasks_user_updated;
ALTER TABLE tasks
    DROP COLUMN IF EXISTS deleted_at,
    DROP COLUMN IF EXISTS spawned_from,
    DROP COLUMN IF EXISTS subtasks,
    DROP COLUMN IF EXISTS sort_order,
    DROP COLUMN IF EXISTS completed_at;
