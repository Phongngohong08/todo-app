-- Thu hồi refresh token: mỗi refresh token mang claim "ver" = token_version của user lúc phát hành.
-- Đăng xuất tăng token_version -> mọi refresh token cũ của user bị từ chối.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 0;

-- Tăng tốc truy vấn job ngầm tìm người dùng có hoạt động gần đây.
CREATE INDEX IF NOT EXISTS idx_task_logs_user_created ON task_logs (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_messages_user_created ON chat_messages (user_id, created_at);
