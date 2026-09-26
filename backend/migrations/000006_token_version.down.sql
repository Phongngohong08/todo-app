DROP INDEX IF EXISTS idx_chat_messages_user_created;
DROP INDEX IF EXISTS idx_task_logs_user_created;
ALTER TABLE users DROP COLUMN IF EXISTS token_version;
