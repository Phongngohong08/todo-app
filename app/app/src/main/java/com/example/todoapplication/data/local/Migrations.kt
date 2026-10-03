package com.example.todoapplication.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: chuyển từ "cache chỉ-đọc" sang offline-first mà KHÔNG mất dữ liệu trên máy.
 *
 *  - task_cache → tasks: đổi chuỗi thời gian ISO sang epoch millis ngay trong SQL (strftime của SQLite
 *    hiểu được "2026-05-30T08:00:00.123Z"); thêm các cột đồng bộ.
 *  - subtask → subtasks: thêm khóa ngoại ON DELETE CASCADE (SQLite không thêm khóa ngoại vào bảng có sẵn
 *    được, nên phải tạo bảng mới rồi chép). Bước con trước đây CHỈ có trên máy → đánh dấu task cha là
 *    "dirty" để lần đồng bộ đầu tiên đẩy checklist lên server. Bước con mồ côi (task đã mất) bị bỏ.
 *  - Tạo mới categories, chat_messages, sync_state (sync_state rỗng → lần đồng bộ đầu tải toàn bộ).
 *
 * Câu CREATE phải khớp từng ký tự với schema Room sinh ra (app/schemas/.../5.json) — MigrationTest kiểm tra.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `priority` TEXT NOT NULL, `dueAt` INTEGER, `status` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `recurrence` TEXT NOT NULL, `recurrenceDays` TEXT NOT NULL, " +
                "`reminderOffsetMinutes` INTEGER NOT NULL, `completedAt` INTEGER, `sortOrder` REAL NOT NULL, " +
                "`spawnedFrom` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`isDirty` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `localVersion` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_isDeleted_sortOrder` ON `tasks` (`isDeleted`, `sortOrder`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_completedAt` ON `tasks` (`completedAt`)")

        db.execSQL(
            """
            INSERT INTO tasks (id, title, description, priority, dueAt, status, category, recurrence,
                recurrenceDays, reminderOffsetMinutes, completedAt, sortOrder, spawnedFrom, createdAt, updatedAt,
                isDirty, isDeleted, localVersion)
            SELECT id, title, COALESCE(description, ''), priority,
                CAST(strftime('%s', dueDate) AS INTEGER) * 1000,
                status, category, recurrence, recurrenceDays, reminderOffsetMinutes,
                CASE WHEN status = 'COMPLETED' THEN CAST(strftime('%s', updatedAt) AS INTEGER) * 1000 END,
                -COALESCE(CAST(strftime('%s', createdAt) AS INTEGER) * 1000.0, 0),
                NULL,
                COALESCE(CAST(strftime('%s', createdAt) AS INTEGER) * 1000, 0),
                COALESCE(CAST(strftime('%s', updatedAt) AS INTEGER) * 1000, 0),
                CASE WHEN id IN (SELECT taskId FROM subtask) THEN 1 ELSE 0 END,
                0,
                CASE WHEN id IN (SELECT taskId FROM subtask) THEN 1 ELSE 0 END
            FROM task_cache
            """.trimIndent()
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subtasks` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")
        db.execSQL(
            """
            INSERT INTO subtasks (id, taskId, title, isDone, position)
            SELECT id, taskId, title, isDone, position FROM subtask
            WHERE taskId IN (SELECT id FROM tasks)
            """.trimIndent()
        )

        db.execSQL("DROP TABLE subtask")
        db.execSQL("DROP TABLE task_cache")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`name`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isPending` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `tasksCursor` TEXT, " +
                "`lastSyncedAt` INTEGER, `categoriesDirty` INTEGER NOT NULL, `categoriesVersion` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v5 → v6: thêm các trường của đợt cải thiện trải nghiệm (cả ngày, Ngày của tôi, thời lượng, lặp nâng cao).
 * Chỉ ADD COLUMN có DEFAULT → dữ liệu cũ giữ nguyên, task cũ = "có giờ", lặp mỗi 1 chu kỳ theo lịch.
 * Câu lệnh phải khớp schema Room sinh ra (app/schemas/.../6.json) — MigrationTest kiểm tra.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `dueAllDay` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `myDay` TEXT")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `estimatedMinutes` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceInterval` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceMode` TEXT NOT NULL DEFAULT 'SCHEDULE'")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceUntil` INTEGER")
    }
}

/**
 * v6 → v7 (Giai đoạn 2 — sắp xếp công việc cá nhân): nhãn, mục, khung giờ, nhiều lời nhắc cho task; hạn của
 * bước con; bảng mới cho cài đặt danh sách, thói quen, lịch sử tập trung. Chỉ thêm cột / thêm bảng.
 * Câu lệnh phải khớp schema Room sinh ra (app/schemas/.../7.json) — MigrationTest kiểm tra.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `section` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `startAt` INTEGER")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `extraReminders` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `subtasks` ADD COLUMN `dueDate` TEXT")
        db.execSQL("ALTER TABLE `sync_state` ADD COLUMN `habitsCursor` TEXT")
        db.execSQL("ALTER TABLE `sync_state` ADD COLUMN `focusCursor` TEXT")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `list_meta` (`name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, `sections` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
                "`color` TEXT NOT NULL, `frequency` TEXT NOT NULL, `weekdays` TEXT NOT NULL, " +
                "`targetPerWeek` INTEGER NOT NULL, `reminderTime` TEXT NOT NULL, `position` REAL NOT NULL, " +
                "`archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`isDirty` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `localVersion` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habits_isDeleted_position` ON `habits` (`isDeleted`, `position`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_checkins` (`habitId` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `date`), " +
                "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_checkins_date` ON `habit_checkins` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` TEXT NOT NULL, `taskId` TEXT, " +
                "`taskTitle` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, " +
                "`isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
    }
}
