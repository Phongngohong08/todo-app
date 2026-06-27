package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskCacheEntity
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.widget.TasksWidgetProvider

/**
 * Cache đọc offline cho danh sách task. Mỗi lần tải thành công từ API sẽ ghi đè cache;
 * khi mất mạng, đọc lại từ Room để vẫn xem được công việc (offline-first read).
 */
object TaskCacheRepository {

    suspend fun cache(context: Context, tasks: List<Task>) {
        val dao = AppDatabase.get(context.applicationContext).taskCacheDao()
        val now = System.currentTimeMillis()
        dao.clear()
        dao.insertAll(tasks.map { it.toEntity(now) })
        // Cập nhật widget màn hình chính sau khi cache thay đổi
        TasksWidgetProvider.refresh(context.applicationContext)
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
        status = status,
        category = category,
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
        status = status,
        category = category,
        recurrence = recurrence,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
