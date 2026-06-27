-- Đơn giản hoá task: bỏ các trường ít dùng, thay tags (nhiều, tự do) bằng category (một, cố định),
-- và rút gọn status còn TODO/COMPLETED.

-- 1. Thêm cột category (cố định)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS category VARCHAR(10) NOT NULL DEFAULT 'OTHER'
    CHECK (category IN ('PERSONAL', 'WORK', 'OTHER'));

-- 2. Gộp các status cũ về TODO/COMPLETED trước khi siết CHECK
UPDATE tasks SET status = 'TODO' WHERE status IN ('IN_PROGRESS', 'POSTPONED');
UPDATE tasks SET status = 'COMPLETED' WHERE status = 'CANCELLED';

ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_status_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check CHECK (status IN ('TODO', 'COMPLETED'));

-- 3. Bỏ các action log cũ không còn dùng rồi siết CHECK
DELETE FROM task_logs WHERE action IN ('STARTED', 'PAUSED', 'POSTPONED', 'CANCELLED');

ALTER TABLE task_logs DROP CONSTRAINT IF EXISTS task_logs_action_check;
ALTER TABLE task_logs ADD CONSTRAINT task_logs_action_check CHECK (action IN ('CREATED', 'COMPLETED'));

-- 4. Bỏ các cột không còn dùng
DROP INDEX IF EXISTS idx_tasks_tags;
ALTER TABLE tasks
    DROP COLUMN IF EXISTS estimated_duration,
    DROP COLUMN IF EXISTS preferred_time_start,
    DROP COLUMN IF EXISTS preferred_time_end,
    DROP COLUMN IF EXISTS tags;
