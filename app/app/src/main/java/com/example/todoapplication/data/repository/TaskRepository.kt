package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.PostponeTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.notifications.ReminderScheduler

/** Kết quả tải danh sách task: kèm cờ offline để UI hiển thị banner. */
data class TaskListResult(val tasks: List<Task>, val isOffline: Boolean)

/**
 * Repository cho công việc — nguồn dữ liệu duy nhất cho tầng UI về task.
 * Bọc [ApiService], đồng bộ nhắc nhở, cache offline ([TaskCacheRepository]) và
 * ghi nhận gamification ([GamificationManager]) khi hoàn thành.
 */
class TaskRepository(
    private val api: ApiService,
    private val appContext: Context
) {
    /**
     * Tải danh sách task. Thành công → cache (khi không lọc). Lỗi mạng → đọc cache offline.
     * @throws Exception khi vừa lỗi mạng vừa không có cache.
     */
    suspend fun loadTasks(status: String?, query: String?): TaskListResult {
        return try {
            val response = api.listTasks(status = status, query = query)
            if (response.isSuccessful) {
                val fresh = response.body() ?: emptyList()
                ReminderScheduler.syncAll(appContext, fresh)
                if (status == null && query == null) {
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
        call { api.createTask(input) }.onSuccess { ReminderScheduler.schedule(appContext, it) }

    suspend fun updateTask(id: String, input: UpdateTaskInput): Result<Task> =
        call { api.updateTask(id, input) }.onSuccess { ReminderScheduler.schedule(appContext, it) }

    suspend fun deleteTask(id: String): Boolean {
        val resp = api.deleteTask(id)
        if (resp.isSuccessful) ReminderScheduler.cancel(appContext, id)
        return resp.isSuccessful
    }

    suspend fun startTask(id: String): Boolean = api.startTask(id).isSuccessful

    suspend fun cancelTask(id: String): Boolean = api.cancelTask(id).isSuccessful

    suspend fun postponeTask(id: String, input: PostponeTaskInput): Boolean =
        api.postponeTask(id, input).isSuccessful

    /**
     * Hoàn thành task: gọi API rồi ghi nhận gamification.
     * Trả về danh sách huy hiệu MỚI mở khóa (rỗng nếu thất bại hoặc không có huy hiệu mới).
     */
    suspend fun completeTask(task: Task): List<Badge> {
        val resp = api.completeTask(task.id)
        if (!resp.isSuccessful) return emptyList()
        return GamificationManager.recordCompletion(appContext, task)
    }

    private inline fun <T> call(block: () -> retrofit2.Response<T>): Result<T> = try {
        val resp = block()
        val body = resp.body()
        if (resp.isSuccessful && body != null) Result.success(body)
        else Result.failure(IllegalStateException("HTTP ${resp.code()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
