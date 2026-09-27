package com.example.todoapplication.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Kiểm tra MIGRATION_4_5 và MIGRATION_5_6 trên SQLite thật của thiết bị: dựng DB cũ từ schema JSON đã export,
 * thêm dữ liệu, chạy migration, rồi so schema kết quả với file JSON của version đích (runMigrationsAndValidate)
 * và kiểm tra dữ liệu.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun seedVersion4() {
        helper.createDatabase(DB_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO task_cache (id, userId, title, description, priority, dueDate, status, category, recurrence,
                    recurrenceDays, reminderOffsetMinutes, createdAt, updatedAt, cachedAt)
                VALUES ('t1', 'u1', 'Viết báo cáo', NULL, 'HIGH', '2026-07-08T02:00:00.123456Z', 'TODO', 'WORK', 'WEEKLY',
                    'MON,FRI', 30, '2026-07-01T00:00:00Z', '2026-07-02T00:00:00Z', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO task_cache (id, userId, title, description, priority, dueDate, status, category, recurrence,
                    recurrenceDays, reminderOffsetMinutes, createdAt, updatedAt, cachedAt)
                VALUES ('t2', 'u1', 'Đã xong', 'mô tả', 'LOW', NULL, 'COMPLETED', 'OTHER', 'NONE',
                    '', 0, '2026-07-01T00:00:00Z', '2026-07-03T08:30:00Z', 0)
                """.trimIndent()
            )
            execSQL("INSERT INTO subtask (id, taskId, title, isDone, position) VALUES ('s1', 't1', 'Bước 1', 1, 0)")
            execSQL("INSERT INTO subtask (id, taskId, title, isDone, position) VALUES ('s2', 'ghost', 'Mồ côi', 0, 0)")
            close()
        }
    }

    @Test
    fun migrate4To5_convertsTimesAndKeepsLocalOnlyData() {
        seedVersion4()

        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)

        db.query("SELECT dueAt, description, completedAt, isDirty, sortOrder, recurrenceDays FROM tasks WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(millis("2026-07-08T02:00:00Z"), c.getLong(0))
            assertEquals("", c.getString(1))
            assertTrue(c.isNull(2))
            // Có bước con (trước đây chỉ nằm trên máy) → phải được đánh dấu để đẩy lên server
            assertEquals(1, c.getInt(3))
            assertEquals(-millis("2026-07-01T00:00:00Z").toDouble(), c.getDouble(4), 0.0)
            assertEquals("MON,FRI", c.getString(5))
        }
        db.query("SELECT completedAt, isDirty, description FROM tasks WHERE id = 't2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(millis("2026-07-03T08:30:00Z"), c.getLong(0))
            assertEquals(0, c.getInt(1))
            assertEquals("mô tả", c.getString(2))
        }
        db.query("SELECT id FROM subtasks").use { c ->
            assertEquals("bước con mồ côi phải bị bỏ", 1, c.count)
            c.moveToFirst()
            assertEquals("s1", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('task_cache', 'subtask')").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun roomOpensMigratedDatabase() {
        seedVersion4()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()
        try {
            // Mở DB kích hoạt migration + kiểm tra khớp schema của Room
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tasks").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate5To6_addsColumnsWithDefaultsAndKeepsData() {
        helper.createDatabase(DB_NAME, 5).apply {
            execSQL(
                """
                INSERT INTO tasks (id, title, description, priority, dueAt, status, category, recurrence, recurrenceDays,
                    reminderOffsetMinutes, completedAt, sortOrder, spawnedFrom, createdAt, updatedAt, isDirty, isDeleted, localVersion)
                VALUES ('t1', 'Tưới cây', '', 'LOW', 1000, 'TODO', 'PERSONAL', 'DAILY', '', 0, NULL, 1.0, NULL, 1, 2, 0, 0, 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 6, true, MIGRATION_5_6)
        db.query("SELECT title, dueAllDay, myDay, estimatedMinutes, recurrenceInterval, recurrenceMode, recurrenceUntil FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Tưới cây", c.getString(0))
            assertEquals(0, c.getInt(1))            // task cũ = có giờ
            assertTrue(c.isNull(2))                 // chưa ở "Ngày của tôi"
            assertEquals(0, c.getInt(3))
            assertEquals(1, c.getInt(4))            // lặp mỗi 1 chu kỳ
            assertEquals("SCHEDULE", c.getString(5))
            assertTrue(c.isNull(6))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
