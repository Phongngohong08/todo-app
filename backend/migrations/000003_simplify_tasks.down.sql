-- Khôi phục lại các cột và CHECK trước khi đơn giản hoá.

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS estimated_duration INT,
    ADD COLUMN IF NOT EXISTS preferred_time_start VARCHAR(5),
    ADD COLUMN IF NOT EXISTS preferred_time_end VARCHAR(5),
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]';

CREATE INDEX IF NOT EXISTS idx_tasks_tags ON tasks USING GIN (tags);

ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_status_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'POSTPONED'));

ALTER TABLE task_logs DROP CONSTRAINT IF EXISTS task_logs_action_check;
ALTER TABLE task_logs ADD CONSTRAINT task_logs_action_check
    CHECK (action IN ('CREATED', 'STARTED', 'PAUSED', 'COMPLETED', 'POSTPONED', 'CANCELLED'));

ALTER TABLE tasks DROP COLUMN IF EXISTS category;
