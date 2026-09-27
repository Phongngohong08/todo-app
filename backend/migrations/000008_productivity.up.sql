-- Tính năng năng suất (đợt cải thiện trải nghiệm người dùng):
--  * due_all_day:         hạn chỉ có NGÀY, không có giờ ("cả ngày"). App lưu hạn ở 23:59 giờ địa phương để
--                         mọi phép so sánh "quá hạn" vẫn đúng; cờ này quyết định cách hiển thị và giờ nhắc.
--  * my_day:              "Ngày của tôi" — ngày (yyyy-MM-dd, giờ địa phương) người dùng chọn làm việc này.
--                         Tự hết hiệu lực khi sang ngày khác, không cần job dọn.
--  * estimated_minutes:   thời lượng ước tính (0 = chưa ước tính) — AI dùng để xếp lịch trong ngày.
--  * recurrence_interval: lặp mỗi N ngày/tuần/tháng (mặc định 1).
--  * recurrence_mode:     SCHEDULE = theo lịch cố định (họp mỗi thứ 2);
--                         COMPLETION = tính từ ngày hoàn thành (tưới cây 3 ngày sau lần tưới trước).
--  * recurrence_until:    không sinh lần lặp nào có hạn sau mốc này (NULL = lặp mãi).
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS due_all_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS my_day VARCHAR(10),
    ADD COLUMN IF NOT EXISTS estimated_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS recurrence_interval INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS recurrence_mode VARCHAR(12) NOT NULL DEFAULT 'SCHEDULE',
    ADD COLUMN IF NOT EXISTS recurrence_until TIMESTAMP WITH TIME ZONE;

-- Mục tiêu hằng ngày (số việc) và các ngày nghỉ không làm đứt chuỗi ("SAT,SUN").
ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS daily_goal INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS days_off VARCHAR(40) NOT NULL DEFAULT '';

-- Đăng xuất MỘT thiết bị: thu hồi đúng refresh token của thiết bị đó (theo jti).
-- "Đăng xuất mọi thiết bị" vẫn dùng users.token_version như trước.
-- Chỉ cần giữ tới khi token hết hạn tự nhiên; job định kỳ xóa các dòng đã hết hạn.
CREATE TABLE IF NOT EXISTS revoked_refresh_tokens (
    jti        UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_revoked_refresh_tokens_expires ON revoked_refresh_tokens (expires_at);
