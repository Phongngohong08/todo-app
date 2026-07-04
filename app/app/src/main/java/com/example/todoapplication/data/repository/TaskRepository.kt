package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.notifications.ReminderScheduler

/** Kết quả tải danh sách task: kèm cờ offline để UI hiển thị banner. */
data class TaskListResult(val tasks: List<Task>, val isOffline: Boolean)

/**
 * Repository cho công việc — nguồn dữ liệu duy nhất cho tầng UI về task.
 * Bọc [ApiService], đồng bộ nhắc nhở và cache offline ([TaskCacheRepository]).
 */
class TaskRepository(
    private val api: ApiService,
    private val appContext: Context
) {
    /**
     * Tải danh sách task. Thành công → cache (khi không lọc). Lỗi mạng → đọc cache offline.
     * @throws Exception khi vừa lỗi mạng vừa không có cache.
     */
    suspend fun loadTasks(category: String?, query: String?): TaskListResult {
        return try {
            val response = api.listTasks(category = category, query = query)
            if (response.isSuccessful) {
                val fresh = response.body() ?: emptyList()
                ReminderScheduler.syncAll(appContext, fresh)
                if (category == null && query == null) {
                    TaskCacheRepository.cache(appContext, fresh)
                }
                TaskListResult(fresh, isOffline = false)
            } else {
                throw IllegalStateException("HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            val cached = TaskCacheRepository.getCached(appContext)
            if (cached.isNotEmpty()) TaskListResult(cached, isOffline = true)
            else throw e
        }
    }

    suspend fun getTask(id: String): Task? {
        val resp = api.getTask(id)
        return if (resp.isSuccessful) resp.body() else null
    }

    suspend fun createTask(input: CreateTaskInput): Result<Task> =
        safeApiCall { api.createTask(input) }.onSuccess { ReminderScheduler.schedule(appContext, it) }

    suspend fun updateTask(id: String, input: UpdateTaskInput): Result<Task> =
        safeApiCall { api.updateTask(id, input) }.onSuccess { ReminderScheduler.schedule(appContext, it) }

    suspend fun deleteTask(id: String): Boolean {
        val resp = api.deleteTask(id)
        if (resp.isSuccessful) ReminderScheduler.cancel(appContext, id)
        return resp.isSuccessful
    }

    /** Hoàn thành task: gọi API đánh dấu COMPLETED. */
    suspend fun completeTask(task: Task): Boolean = api.completeTask(task.id).isSuccessful
}
