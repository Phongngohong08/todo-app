-- Lời nhắc tách riêng (số phút nhắc trước hạn) và lặp lại theo thứ trong tuần.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS reminder_offset_minutes INT NOT NULL DEFAULT 0;
-- recurrence_days: danh sách thứ viết tắt cách nhau bởi dấu phẩy, vd "MON,WED,FRI" (chỉ dùng khi recurrence = WEEKLY)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_days VARCHAR(40) NOT NULL DEFAULT '';
