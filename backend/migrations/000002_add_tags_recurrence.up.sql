-- Thêm phân loại (tags) và lặp lại (recurrence) cho task
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]';
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence VARCHAR(10) NOT NULL DEFAULT 'NONE'
    CHECK (recurrence IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY'));

-- Hỗ trợ lọc theo tag nhanh hơn
CREATE INDEX IF NOT EXISTS idx_tasks_tags ON tasks USING GIN (tags);
