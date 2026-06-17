package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskCacheEntity
import com.example.todoapplication.data.model.Task

/**
 * Cache đọc offline cho danh sách task. Mỗi lần tải thành công từ API sẽ ghi đè cache;
 * khi mất mạng, đọc lại từ Room để vẫn xem được công việc (offline-first read).
 */
object TaskCacheRepository {
    private const val TAG_SEP = "" // unit separator — tránh đụng ký tự thường trong tag

    suspend fun cache(context: Context, tasks: List<Task>) {
        val dao = AppDatabase.get(context.applicationContext).taskCacheDao()
        val now = System.currentTimeMillis()
        dao.clear()
        dao.insertAll(tasks.map { it.toEntity(now) })
    }

    suspend fun getCached(context: Context): List<Task> {
        val dao = AppDatabase.get(context.applicationContext).taskCacheDao()
        return dao.getAll().map { it.toTask() }
    }

    private fun Task.toEntity(cachedAt: Long) = TaskCacheEntity(
        id = id,
        userId = userId,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        estimatedDuration = estimatedDuration,
        preferredTimeStart = preferredTimeStart,
        preferredTimeEnd = preferredTimeEnd,
        status = status,
        tagsCsv = tags.joinToString(TAG_SEP),
        recurrence = recurrence,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cachedAt = cachedAt
    )

    private fun TaskCacheEntity.toTask() = Task(
        id = id,
        userId = userId,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        estimatedDuration = estimatedDuration,
        preferredTimeStart = preferredTimeStart,
        preferredTimeEnd = preferredTimeEnd,
        status = status,
        tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(TAG_SEP),
        recurrence = recurrence,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
