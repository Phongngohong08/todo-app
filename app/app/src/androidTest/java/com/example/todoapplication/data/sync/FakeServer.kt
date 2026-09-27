package com.example.todoapplication.data.sync

import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.model.*
import com.example.todoapplication.ui.utils.toIsoString
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/**
 * Server giả trong bộ nhớ, mô phỏng đúng hợp đồng đồng bộ của backend Go:
 * PUT idempotent (tạo nếu chưa có), DELETE mềm, GET /tasks/sync trả thay đổi sau mốc server_time.
 */
class FakeServer : ApiService {
    private data class Row(val dto: TaskDto, val updatedAt: Long, val deletedAt: Long? = null)

    private val rows = linkedMapOf<String, Row>()
    private var clock = 1_780_000_000_000L
    var categories: List<String> = emptyList()
        private set

    /** true → mọi request ném IOException như khi mất mạng. */
    var offline = false

    /** Chạy trong lúc server đang xử lý PUT — để mô phỏng người dùng sửa tiếp khi request đang bay. */
    var onSave: (suspend (String) -> Unit)? = null

    val tasks: Map<String, TaskDto> get() = rows.filterValues { it.deletedAt == null }.mapValues { it.value.dto }
    fun isDeleted(id: String) = rows[id]?.deletedAt != null

    private fun tick(): Long = ++clock
    private fun iso(millis: Long) = toIsoString(millis)
    private fun checkOnline() {
        if (offline) throw IOException("offline")
    }

    /** Thay đổi đến từ một thiết bị khác của cùng tài khoản. */
    fun putFromOtherDevice(id: String, title: String) {
        val now = tick()
        rows[id] = Row(
            TaskDto(id, "u1", title, "", "MEDIUM", null, "TODO", "OTHER", "NONE", "", 0, null, -now.toDouble(),
                emptyList(), null, iso(now), iso(now)),
            updatedAt = now
        )
    }

    fun deleteFromOtherDevice(id: String) {
        val now = tick()
        rows[id] = rows.getValue(id).copy(updatedAt = now, deletedAt = now)
    }

    override suspend fun saveTask(id: String, input: TaskInputDto): Response<TaskDto> {
        checkOnline()
        onSave?.invoke(id)
        val now = tick()
        val existing = rows[id]
        val completedAt = when {
            input.status != "COMPLETED" -> null
            existing?.dto?.status == "COMPLETED" -> existing.dto.completedAt
            else -> input.completedAt ?: iso(now)
        }
        val dto = TaskDto(
            id, "u1", input.title, input.description, input.priority, input.dueDate, input.status, input.category,
            input.recurrence, input.recurrenceDays, input.reminderOffsetMinutes, completedAt, input.sortOrder,
            input.subtasks, input.spawnedFrom ?: existing?.dto?.spawnedFrom,
            existing?.dto?.createdAt ?: iso(now), iso(now),
            // Trường mới: giống server thật — "" nghĩa là xóa giá trị
            dueAllDay = input.dueAllDay,
            myDay = input.myDay.ifEmpty { null },
            estimatedMinutes = input.estimatedMinutes,
            recurrenceInterval = input.recurrenceInterval,
            recurrenceMode = input.recurrenceMode,
            recurrenceUntil = input.recurrenceUntil.ifEmpty { null }
        )
        rows[id] = Row(dto, updatedAt = now)
        return Response.success(dto)
    }

    override suspend fun deleteTask(id: String): Response<Unit> {
        checkOnline()
        val row = rows[id] ?: return Response.error(404, "".toResponseBody())
        if (row.deletedAt == null) {
            val now = tick()
            rows[id] = row.copy(updatedAt = now, deletedAt = now)
        }
        return Response.success(Unit)
    }

    override suspend fun syncTasks(since: String?): Response<TaskChangesDto> {
        checkOnline()
        val serverTime = tick()
        val sinceMs = since?.let { Instant.parse(it).toEpochMilli() }
        val changed = rows.values.filter { sinceMs == null || it.updatedAt > sinceMs }
        return Response.success(
            TaskChangesDto(
                tasks = changed.filter { it.deletedAt == null }.map { it.dto },
                deletedIds = if (sinceMs == null) emptyList() else changed.filter { it.deletedAt != null }.map { it.dto.id },
                serverTime = iso(serverTime)
            )
        )
    }

    override suspend fun getCategories(): Response<CategoriesDto> {
        checkOnline()
        return Response.success(CategoriesDto(categories))
    }

    override suspend fun replaceCategories(input: CategoriesDto): Response<CategoriesDto> {
        checkOnline()
        categories = input.categories
        return Response.success(input)
    }

    // ── Không dùng trong test đồng bộ ──
    override suspend fun register(input: RegisterInput): Response<User> = unsupported()
    override suspend fun login(input: LoginInput): Response<AuthResponse> = unsupported()
    override fun refreshToken(input: RefreshTokenInput): Call<AuthResponse> = unsupported()
    override suspend fun logout(input: LogoutInput): Response<Unit> = unsupported()
    override suspend fun getPreferences(): Response<UserPreferences> = unsupported()
    override suspend fun updatePreferences(prefs: UserPreferences): Response<UserPreferences> = unsupported()
    override suspend fun getDailyPlan(date: String?, localTime: String?, timezone: String?): Response<DailyPlan> = unsupported()
    override suspend fun generateDailyPlan(date: String?, localTime: String?, timezone: String?): Response<DailyPlan> = unsupported()
    override suspend fun saveDailyPlan(date: String, timezone: String?, input: SavePlanInput): Response<DailyPlan> = unsupported()
    override suspend fun chat(input: ChatInput): Response<ChatResponse> = unsupported()
    override suspend fun chatHistory(limit: Int): Response<List<ChatMessage>> = unsupported()
    override suspend fun parseTask(input: ParseTaskInput): Response<ParsedTask> = unsupported()
    override suspend fun listMemories(): Response<List<MemoryItem>> = unsupported()
    override suspend fun triggerMemoryExtraction(): Response<MemoryExtractionResult> = unsupported()
    override suspend fun deleteMemory(id: String): Response<Unit> = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not needed by sync tests")
}
