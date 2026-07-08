package com.example.todoapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * [TẦNG DATA · ROOM] Điểm gom database cục bộ: khai báo các bảng (entities) và cung cấp DAO.
 * get() trả về một instance duy nhất toàn app (singleton) vì mở SQLite khá tốn kém.
 * version = số phiên bản schema; đổi cấu trúc bảng thì tăng version.
 */
@Database(
    entities = [TaskCacheEntity::class, SubtaskEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "todo_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
