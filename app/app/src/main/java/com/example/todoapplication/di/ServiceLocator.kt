package com.example.todoapplication.di

import android.content.Context
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.PlanRepository
import com.example.todoapplication.data.repository.PreferencesRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.StatsRepository
import com.example.todoapplication.data.repository.SubtaskRepository
import com.example.todoapplication.data.repository.TaskRepository

/**
 * Manual Dependency Injection (ServiceLocator pattern).
 *
 * Vì Hilt Gradle plugin chưa tương thích với AGP 9 (lỗi "Android BaseExtension not found"),
 * ta tự cung cấp các phụ thuộc dạng singleton lười (lazy). Khởi tạo một lần trong
 * [com.example.todoapplication.TodoApplication]. Các ViewModel lấy repository từ đây qua Factory.
 */
object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────
    val apiService: ApiService by lazy { NetworkClient.getApiService(appContext) }
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val sessionManager: SessionManager by lazy { SessionManager(appContext) }

    // ── Repository ───────────────────────────────────────────────────────────
    val taskRepository: TaskRepository by lazy { TaskRepository(apiService, appContext) }
    val authRepository: AuthRepository by lazy { AuthRepository(apiService, sessionManager) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(apiService) }
    val planRepository: PlanRepository by lazy { PlanRepository(apiService) }
    val aiRepository: AiRepository by lazy { AiRepository(apiService) }
    val statsRepository: StatsRepository by lazy { StatsRepository(apiService) }
    val subtaskRepository: SubtaskRepository by lazy { SubtaskRepository(appContext) }
}
