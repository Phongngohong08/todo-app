DROP TABLE IF EXISTS revoked_refresh_tokens;

ALTER TABLE user_preferences
    DROP COLUMN IF EXISTS days_off,
    DROP COLUMN IF EXISTS daily_goal;

ALTER TABLE tasks
    DROP COLUMN IF EXISTS recurrence_until,
    DROP COLUMN IF EXISTS recurrence_mode,
    DROP COLUMN IF EXISTS recurrence_interval,
    DROP COLUMN IF EXISTS estimated_minutes,
    DROP COLUMN IF EXISTS my_day,
    DROP COLUMN IF EXISTS due_all_day;
