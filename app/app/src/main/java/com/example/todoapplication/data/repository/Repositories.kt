package com.example.todoapplication.data.repository

import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.model.AuthResponse
import com.example.todoapplication.data.model.ChatInput
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.model.LoginInput
import com.example.todoapplication.data.model.MemoryExtractionResult
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.model.ParseTaskInput
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.model.RegisterInput
import com.example.todoapplication.data.model.StatsSummary
import com.example.todoapplication.data.model.User
import com.example.todoapplication.data.model.UserPreferences

private inline fun <T> safeCall(block: () -> retrofit2.Response<T>): Result<T> = try {
    val resp = block()
    val body = resp.body()
    if (resp.isSuccessful && body != null) Result.success(body)
    else Result.failure(IllegalStateException("HTTP ${resp.code()}"))
} catch (e: Exception) {
    Result.failure(e)
}

/** Đăng nhập / đăng ký — lưu phiên qua [SessionManager]. */
class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        val result = safeCall { api.login(LoginInput(email, password)) }
        result.getOrNull()?.let { auth ->
            sessionManager.saveTokens(auth.token, auth.refreshToken)
            sessionManager.saveUser(auth.user.id, auth.user.email, auth.user.name)
        }
        return result
    }

    suspend fun register(name: String, email: String, password: String): Result<User> =
        safeCall { api.register(RegisterInput(email, password, name)) }
}

/** Cấu hình cá nhân cho lập lịch AI. */
class PreferencesRepository(private val api: ApiService) {
    suspend fun get(): Result<UserPreferences> = safeCall { api.getPreferences() }
    suspend fun update(prefs: UserPreferences): Result<UserPreferences> =
        safeCall { api.updatePreferences(prefs) }
}

/** Lịch trình hằng ngày do AI tạo. */
class PlanRepository(private val api: ApiService) {
    suspend fun getDaily(date: String?, localTime: String?): Result<DailyPlan> =
        safeCall { api.getDailyPlan(date, localTime) }

    suspend fun generateDaily(localTime: String?): Result<DailyPlan> =
        safeCall { api.generateDailyPlan(localTime) }
}

/** AI Coach, Quick Add (parse) và trí nhớ dài hạn. */
class AiRepository(private val api: ApiService) {
    suspend fun chat(message: String): Result<String> {
        val r = safeCall { api.chat(ChatInput(message)) }
        return r.map { it.reply }
    }

    suspend fun parseTask(text: String, localTime: String): Result<ParsedTask> =
        safeCall { api.parseTask(ParseTaskInput(text, localTime)) }

    suspend fun listMemories(): Result<List<MemoryItem>> = safeCall { api.listMemories() }

    suspend fun triggerExtraction(): Result<MemoryExtractionResult> =
        safeCall { api.triggerMemoryExtraction() }

    suspend fun deleteMemory(id: String): Boolean = api.deleteMemory(id).isSuccessful
}

/** Thống kê & dữ liệu biểu đồ. */
class StatsRepository(private val api: ApiService) {
    suspend fun summary(): Result<StatsSummary> = safeCall { api.getStatsSummary() }

    /** Tải task đã hoàn thành để dựng biểu đồ năng suất tuần. */
    suspend fun completedTasks(): Result<List<com.example.todoapplication.data.model.Task>> =
        safeCall { api.listTasks(status = "COMPLETED") }
}
