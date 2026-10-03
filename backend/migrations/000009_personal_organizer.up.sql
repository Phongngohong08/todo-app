-- Giai đoạn 2 — sắp xếp công việc cá nhân:
--  * tasks.tags:            nhãn tự do, lưu "a,b,c" (đã chuẩn hóa: chữ thường, không khoảng trắng). API trả mảng.
--  * tasks.section:         "mục" trong danh sách (cột Kanban), rỗng = chưa phân mục.
--  * tasks.start_at:        giờ bắt đầu — cùng due_date tạo thành khung thời gian (09:00–10:30).
--  * tasks.extra_reminders: các lời nhắc thêm ngoài reminder_offset_minutes, dạng "60,1440" (số phút trước hạn).
--  * Bước con (JSONB subtasks) có thêm due_date "yyyy-MM-dd" — không cần đổi schema.
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS tags TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS section VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS start_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS extra_reminders VARCHAR(100) NOT NULL DEFAULT '';

-- Cài đặt hiển thị của từng danh sách (kể cả 3 danh sách mặc định): màu, emoji, các mục (Kanban).
CREATE TABLE IF NOT EXISTS list_settings (
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     VARCHAR(50) NOT NULL,
    color    VARCHAR(9) NOT NULL DEFAULT '',
    emoji    VARCHAR(16) NOT NULL DEFAULT '',
    sections JSONB NOT NULL DEFAULT '[]',
    PRIMARY KEY (user_id, name)
);

-- Thói quen: lặp lại không có hạn chót, đánh dấu theo ngày.
--  frequency: DAILY | WEEKDAYS (các thứ trong weekdays) | WEEKLY (target_per_week lần mỗi tuần, ngày nào cũng được)
CREATE TABLE IF NOT EXISTS habits (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    emoji           VARCHAR(16) NOT NULL DEFAULT '',
    color           VARCHAR(9) NOT NULL DEFAULT '',
    frequency       VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    weekdays        VARCHAR(40) NOT NULL DEFAULT '',
    target_per_week INT NOT NULL DEFAULT 7,
    reminder_time   VARCHAR(5) NOT NULL DEFAULT '',
    position        DOUBLE PRECISION NOT NULL DEFAULT 0,
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_habits_user_updated ON habits (user_id, updated_at);

-- Một dòng cho mỗi (thói quen, ngày). Bỏ đánh dấu = done FALSE (giữ dòng để thiết bị khác biết mà đồng bộ).
CREATE TABLE IF NOT EXISTS habit_checkins (
    habit_id   UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day        DATE NOT NULL,
    done       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (habit_id, day)
);
CREATE INDEX IF NOT EXISTS idx_habit_checkins_user_updated ON habit_checkins (user_id, updated_at);

-- Lịch sử tập trung (Pomodoro): chỉ thêm, không sửa. task_id không có khóa ngoại — việc bị xóa vẫn giữ lịch sử.
CREATE TABLE IF NOT EXISTS focus_sessions (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id    UUID,
    task_title VARCHAR(255) NOT NULL DEFAULT '',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    minutes    INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_focus_sessions_user_created ON focus_sessions (user_id, created_at);
