package com.example.todoapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * [TẦNG DATA · ROOM] Điểm gom database cục bộ: khai báo các bảng (entities) và cung cấp DAO.
 * get() trả về một instance duy nhất toàn app (singleton) vì mở SQLite khá tốn kém.
 *
 * Lịch sử schema (file JSON trong app/schemas/, được kiểm tra bởi MigrationTest):
 *  - v1–3: chưa export schema → cho phép xóa-tạo lại.
 *  - v4: bảng task_cache (bản sao chỉ-đọc) + subtask (chỉ có trên máy).
 *  - v5: offline-first — bảng tasks là nguồn dữ liệu chính (có cờ đồng bộ), subtasks có khóa ngoại,
 *        thêm categories, chat_messages, sync_state. Nâng cấp bằng [MIGRATION_4_5], giữ nguyên dữ liệu.
 */
@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        CategoryEntity::class,
        ChatMessageEntity::class,
        SyncStateEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun chatDao(): ChatDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "todo_local.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_4_5)
                    // Chỉ các schema cũ (1-3, trước khi export schema) mới được xóa-tạo lại.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
