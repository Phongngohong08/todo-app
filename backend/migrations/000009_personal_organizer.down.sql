DROP TABLE IF EXISTS focus_sessions;
DROP TABLE IF EXISTS habit_checkins;
DROP TABLE IF EXISTS habits;
DROP TABLE IF EXISTS list_settings;

ALTER TABLE tasks
    DROP COLUMN IF EXISTS extra_reminders,
    DROP COLUMN IF EXISTS start_at,
    DROP COLUMN IF EXISTS section,
    DROP COLUMN IF EXISTS tags;
