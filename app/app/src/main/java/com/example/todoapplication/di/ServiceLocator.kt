package com.example.todoapplication.di

import android.content.Context
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.ChatRepository
import com.example.todoapplication.data.repository.PlanRepository
import com.example.todoapplication.data.repository.PreferencesRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.StatsRepository
import com.example.todoapplication.data.repository.TaskEffects
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.ConnectivityObserver
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.data.sync.SyncEngine
import com.example.todoapplication.data.sync.SyncScheduler

/**
 * Manual Dependency Injection (ServiceLocator pattern).
 *
 * Vì Hilt Gradle plugin chưa tương thích với AGP 9 (lỗi "Android BaseExtension not found"),
 * ta tự cung cấp các phụ thuộc dạng singleton lười (lazy). Khởi tạo một lần trong
 * [com.example.todoapplication.TodoApplication]. Các ViewModel lấy repository từ đây qua Factory
 * (nhận qua constructor → unit test truyền bản giả được).
 *
 * Đồ thị phụ thuộc (mũi tên = "dùng"):
 *   ViewModel → Repository → { AppDatabase (Room), ApiService (Retrofit) }
 *   SyncWorker → SyncEngine → { AppDatabase, ApiService }
 *   TaskRepository → TaskEffects → { ReminderScheduler, Widget, SyncScheduler }
 */
object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────
    val sessionManager: SessionManager by lazy { SessionManager(appContext) }
    val apiService: ApiService by lazy { NetworkClient.getApiService(sessionManager) }
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    // ── Đồng bộ ──────────────────────────────────────────────────────────────
    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }
    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
    private val taskEffects: TaskEffects by lazy { TaskEffects(appContext, database, syncScheduler) }
    val syncEngine: SyncEngine by lazy {
        SyncEngine(database, apiService, currentUserId = { sessionManager.getUserId() }, listener = taskEffects)
    }
    val syncController: SyncController by lazy {
        SyncController(syncEngine, syncScheduler, connectivityObserver, database.syncStateDao())
    }

    // ── Repository ───────────────────────────────────────────────────────────
    val taskRepository: TaskRepository by lazy { TaskRepository(database, taskEffects) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database, syncController) }
    val chatRepository: ChatRepository by lazy { ChatRepository(apiService, database) }
    val statsRepository: StatsRepository by lazy { StatsRepository(database) }
    val authRepository: AuthRepository by lazy { AuthRepository(apiService, sessionManager, syncController) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(apiService) }
    val planRepository: PlanRepository by lazy { PlanRepository(apiService) }
    val aiRepository: AiRepository by lazy { AiRepository(apiService) }
}
