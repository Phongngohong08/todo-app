-- Hỗ trợ app offline-first:
--  * completed_at: thời điểm hoàn thành thật (thống kê không còn dựa vào updated_at)
--  * sort_order:   thứ tự kéo-thả (fractional indexing — nhỏ hơn đứng trước; mặc định -epoch ms = mới nhất lên đầu)
--  * subtasks:     checklist nằm TRONG task (aggregate) → đồng bộ cùng task, không cần API riêng
--  * spawned_from: lần lặp kế tiếp sinh từ task nào (để "mở lại" có thể thu hồi lần lặp đó)
--  * deleted_at:   xóa mềm → thiết bị khác biết task đã bị xóa khi đồng bộ
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS sort_order DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS subtasks JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS spawned_from UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE tasks SET completed_at = updated_at WHERE status = 'COMPLETED' AND completed_at IS NULL;
UPDATE tasks SET sort_order = -EXTRACT(EPOCH FROM created_at) * 1000 WHERE sort_order = 0;

-- Truy vấn nào cũng lọc theo user; đồng bộ lọc theo updated_at
CREATE INDEX IF NOT EXISTS idx_tasks_user_updated ON tasks (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks (user_id, status) WHERE deleted_at IS NULL;

-- Danh mục do người dùng tự tạo (trước đây chỉ lưu trên máy)
CREATE TABLE IF NOT EXISTS user_categories (
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     VARCHAR(50) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, name)
);
