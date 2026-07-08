package com.example.todoapplication.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/*
 * [TẦNG DATA · ROOM/DAO] Tập truy vấn database cục bộ (SQLite). Ta chỉ viết interface + câu SQL,
 * Room sinh phần hiện thực lúc build và KIỂM TRA SQL ngay khi biên dịch (sai cột là báo lỗi liền).
 * suspend = chạy nền, không chặn UI.
 */

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM task_cache ORDER BY cachedAt ASC")
    suspend fun getAll(): List<TaskCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskCacheEntity>)

    @Query("DELETE FROM task_cache")
    suspend fun clear()
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtask WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getForTask(taskId: String): List<SubtaskEntity>

    @Query("SELECT * FROM subtask")
    suspend fun getAll(): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SubtaskEntity)

    @Update
    suspend fun update(item: SubtaskEntity)

    @Delete
    suspend fun delete(item: SubtaskEntity)

    @Query("DELETE FROM subtask WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}
