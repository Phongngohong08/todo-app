package com.example.todoapplication.data.repository

import androidx.room.withTransaction
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.CategoryEntity
import com.example.todoapplication.data.local.ChatMessageEntity
import com.example.todoapplication.data.local.toEntity
import com.example.todoapplication.data.model.AuthResponse
import com.example.todoapplication.data.model.ChatInput
import com.example.todoapplication.data.model.ChatResponse
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.model.LoginInput
import com.example.todoapplication.data.model.MemoryExtractionResult
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.model.ParseTaskInput
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.model.LogoutInput
import com.example.todoapplication.data.model.PlanSlot
import com.example.todoapplication.data.model.SavePlanInput
import com.example.todoapplication.data.model.RegisterInput
import com.example.todoapplication.data.model.User
import com.example.todoapplication.data.model.UserPreferences
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.domain.model.StatsSummary
import com.example.todoapplication.ui.utils.deviceTimeZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/*
 * [TẦNG DATA · REPOSITORY] Các repository còn lại. Hai loại:
 *  - Chỉ online (Auth, Preferences, Plan, AI): bọc ApiService qua safeApiCall{...} (trả Result, không ném exception).
 *  - Offline-first (Category, Chat, Stats): đọc Room qua Flow, ghi Room trước rồi mới đồng bộ.
 * ViewModel chỉ nói chuyện với repository, không đụng thẳng ApiService hay DAO.
 */

/** Đăng nhập / đăng ký — lưu phiên qua [SessionManager]. */
class AuthRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager,
    private val sync: SyncController
) {
    // Scope cấp ứng dụng: lời gọi thu hồi phải chạy xong kể cả khi màn gọi logout đã bị hủy.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        val result = safeApiCall { api.login(LoginInput(email, password)) }
        result.getOrNull()?.let { auth ->
            sessionManager.saveTokens(auth.token, auth.refreshToken)
            sessionManager.saveUser(auth.user.id, auth.user.email, auth.user.name)
            sync.start() // tải dữ liệu của tài khoản về máy + lên lịch đồng bộ định kỳ
        }
        return result
    }

    suspend fun register(name: String, email: String, password: String): Result<User> =
        safeApiCall { api.register(RegisterInput(email, password, name)) }

    /**
     * Đăng xuất: xóa phiên + dữ liệu cục bộ NGAY (UI không phải chờ mạng), rồi báo server thu hồi
     * refresh token ở nền. Mất mạng thì bỏ qua — refresh token vẫn tự hết hạn theo TTL.
     * Caller nên đồng bộ trước (xem MeViewModel.requestLogout) để không mất thay đổi chưa gửi.
     *
     * [allDevices] = false (mặc định): chỉ thu hồi phiên của máy này — các máy khác vẫn đăng nhập.
     */
    fun logout(allDevices: Boolean = false) {
        val refreshToken = sessionManager.getRefreshToken()
        sync.stop()
        sessionManager.logout()
        if (!refreshToken.isNullOrEmpty()) {
            backgroundScope.launch {
                runCatching { api.logout(LogoutInput(refreshToken, allDevices)) }
            }
        }
    }
}

/**
 * Cấu hình cá nhân (lập lịch AI + mục tiêu ngày). Server là bản gốc; mục tiêu/ngày nghỉ được chép vào
 * [LocalPrefs] để chuỗi ngày và thông báo tính được cả khi offline.
 */
class PreferencesRepository(private val api: ApiService, private val local: LocalPrefs) {
    suspend fun get(): Result<UserPreferences> = safeApiCall { api.getPreferences() }.onSuccess(::cacheGoal)

    suspend fun update(prefs: UserPreferences): Result<UserPreferences> =
        safeApiCall { api.updatePreferences(prefs) }.onSuccess(::cacheGoal)

    private fun cacheGoal(prefs: UserPreferences) {
        prefs.dailyGoal?.let { local.setDailyGoal(it) }
        prefs.daysOff?.let { local.setDaysOff(it) }
    }
}

/** Lịch trình hằng ngày do AI tạo. */
class PlanRepository(private val api: ApiService) {
    suspend fun getDaily(date: String?, localTime: String?): Result<DailyPlan> =
        safeApiCall { api.getDailyPlan(date, localTime, deviceTimeZoneId()) }

    suspend fun generateDaily(date: String?, localTime: String?): Result<DailyPlan> =
        safeApiCall { api.generateDailyPlan(date, localTime, deviceTimeZoneId()) }

    /** Lưu lịch người dùng chỉnh tay (đổi giờ / bỏ khung) — không gọi AI. */
    suspend fun saveEdited(date: String, slots: List<PlanSlot>): Result<DailyPlan> =
        safeApiCall { api.saveDailyPlan(date, deviceTimeZoneId(), SavePlanInput(slots)) }
}

/** Quick Add (parse) và trí nhớ dài hạn của AI. */
class AiRepository(private val api: ApiService) {
    suspend fun parseTask(text: String, localTime: String): Result<ParsedTask> =
        safeApiCall { api.parseTask(ParseTaskInput(text, localTime)) }

    suspend fun listMemories(): Result<List<MemoryItem>> = safeApiCall { api.listMemories() }

    suspend fun triggerExtraction(): Result<MemoryExtractionResult> =
        safeApiCall { api.triggerMemoryExtraction() }

    suspend fun deleteMemory(id: String): Boolean =
        runCatching { api.deleteMemory(id).isSuccessful }.getOrDefault(false)
}

/**
 * Danh mục: 3 danh mục mặc định (mã cố định) + danh mục người dùng tự tạo (lưu Room, đồng bộ lên server
 * theo tài khoản — trước đây chỉ nằm trong SharedPreferences của máy).
 */
class CategoryRepository(
    private val db: AppDatabase,
    private val sync: SyncController
) {
    private val categoryDao = db.categoryDao()
    private val syncStateDao = db.syncStateDao()

    fun observeAll(): Flow<List<String>> = categoryDao.observeNames().map { DEFAULTS + it }

    /** Thêm danh mục; trả về tên đã chuẩn hoá (rỗng nếu không hợp lệ). */
    suspend fun add(name: String): String {
        val clean = name.trim().take(MAX_LENGTH).trim()
        if (clean.isEmpty()) return ""
        DEFAULTS.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }
        val existing = categoryDao.getNames().firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existing != null) return existing

        db.withTransaction {
            categoryDao.insert(CategoryEntity(clean, categoryDao.maxPosition() + 1))
            syncStateDao.markCategoriesDirty()
        }
        sync.requestSync(delayMillis = 1_000)
        return clean
    }

    suspend fun remove(name: String) {
        if (DEFAULTS.contains(name)) return
        db.withTransaction {
            categoryDao.delete(name)
            syncStateDao.markCategoriesDirty()
        }
        sync.requestSync(delayMillis = 1_000)
    }

    companion object {
        /** Danh mục mặc định (lưu bằng mã, hiển thị nhãn tiếng Việt qua categoryLabel). */
        val DEFAULTS = listOf("PERSONAL", "WORK", "OTHER")

        /** Khớp giới hạn của backend (cột tasks.category VARCHAR(50)). */
        const val MAX_LENGTH = 50
    }
}

/**
 * Lịch sử chat với AI Coach. Server là bản gốc (AI cần lịch sử để trả lời theo ngữ cảnh);
 * Room giữ bản đệm để mở màn là thấy ngay cuộc trò chuyện cũ, kể cả khi offline.
 */
class ChatRepository(
    private val api: ApiService,
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val chatDao = db.chatDao()

    fun observeMessages(): Flow<List<ChatMessageEntity>> = chatDao.observeAll()

    /** Tải lại lịch sử từ server (thay toàn bộ bản đệm). */
    suspend fun refresh(): Result<Unit> =
        safeApiCall { api.chatHistory(HISTORY_LIMIT) }.map { messages ->
            chatDao.replaceAll(messages.map { it.toEntity() })
        }

    /**
     * Gửi tin: hiện tin của người dùng ngay (lạc quan), chờ AI trả lời rồi đồng bộ lại với server.
     * Kết quả kèm các hành động AI đề xuất (không lưu — chỉ áp dụng khi người dùng xác nhận).
     */
    suspend fun send(text: String, localTime: String): Result<ChatResponse> {
        val pending = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text,
            createdAt = clock(),
            isPending = true
        )
        chatDao.upsert(pending)

        val result = safeApiCall { api.chat(ChatInput(text, localTime)) }
        result.onSuccess { response ->
            chatDao.upsert(pending.copy(isPending = false))
            chatDao.upsert(ChatMessageEntity(UUID.randomUUID().toString(), "assistant", response.reply, clock()))
            refresh() // lấy id thật từ server; thất bại cũng không sao — bản đệm đã đúng nội dung
        }.onFailure {
            chatDao.upsert(pending.copy(isPending = false))
        }
        return result
    }

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}

/** Thống kê tính trực tiếp từ Room → cập nhật tức thì khi hoàn thành việc, xem được cả khi offline. */
class StatsRepository(db: AppDatabase) {
    private val taskDao = db.taskDao()

    fun observeSummary(): Flow<StatsSummary> =
        combine(taskDao.observeStatusCounts(), taskDao.observePendingByCategory()) { counts, byCategory ->
            StatsSummary(
                completedTasks = counts.completed,
                pendingTasks = counts.pending,
                byCategory = byCategory.associate { it.category to it.count }
            )
        }

    /** Thời điểm hoàn thành (epoch millis) của các việc xong từ [sinceMillis] trở đi. */
    fun observeCompletedTimes(sinceMillis: Long): Flow<List<Long>> = taskDao.observeCompletedTimes(sinceMillis)
}
