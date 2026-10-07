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
 *  - v6: task có thêm "cả ngày", "Ngày của tôi", thời lượng ước tính, lặp mỗi N / theo ngày hoàn thành /
 *        ngày kết thúc lặp. Nâng cấp bằng [MIGRATION_5_6] (chỉ thêm cột có giá trị mặc định).
 *  - v7: nhãn, mục (Kanban), khung giờ, nhiều lời nhắc, hạn của bước con; bảng list_meta, habits,
 *        habit_checkins, focus_sessions; con trỏ đồng bộ thói quen / tập trung. Nâng cấp bằng [MIGRATION_6_7].
 *  - v8: tệp đính kèm, địa điểm đã lưu, nhắc theo địa điểm (chỉ trên máy). Nâng cấp bằng [MIGRATION_7_8].
 */
@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        CategoryEntity::class,
        ChatMessageEntity::class,
        SyncStateEntity::class,
        ListMetaEntity::class,
        HabitEntity::class,
        HabitCheckinEntity::class,
        FocusSessionEntity::class,
        AttachmentEntity::class,
        PlaceEntity::class,
        TaskLocationEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun chatDao(): ChatDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun listMetaDao(): ListMetaDao
    abstract fun habitDao(): HabitDao
    abstract fun focusDao(): FocusDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun placeDao(): PlaceDao

    companion object {
        const val NAME = "todo_local.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Chỉ các schema cũ (1-3, trước khi export schema) mới được xóa-tạo lại.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
