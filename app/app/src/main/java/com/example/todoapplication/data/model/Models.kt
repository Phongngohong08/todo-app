package com.example.todoapplication.data.model

import com.google.gson.annotations.SerializedName

/*
 * [TẦNG DATA · MODEL] Các "khuôn dữ liệu" (DTO) để chuyển JSON ↔ object Kotlin.
 * Gson đọc/ghi tự động; @SerializedName("snake_case") nối tên JSON của backend với tên camelCase ở đây
 * (giống struct tag `json:"..."` trong Go). Xxx = dữ liệu nhận về, XxxInput = dữ liệu gửi đi.
 */

data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class AuthResponse(
    val token: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: User
)

data class RefreshTokenInput(
    @SerializedName("refresh_token") val refreshToken: String
)

data class PlanSlot(
    val start: String, // e.g., "08:00"
    val end: String,   // e.g., "09:00"
    @SerializedName("task_id") val taskId: String,
    val title: String
)

data class DailyPlan(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("plan_date") val planDate: String,
    @SerializedName("plan_data") val planData: List<PlanSlot>,
    @SerializedName("created_at") val createdAt: String
)

data class ChatMessage(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val role: String, // "user", "assistant"
    val content: String,
    @SerializedName("created_at") val createdAt: String
)

data class UserPreferences(
    @SerializedName("user_id") val userId: String,
    @SerializedName("morning_start_time") val morningStartTime: String,
    @SerializedName("evening_end_time") val eveningEndTime: String,
    @SerializedName("work_duration_preference") val workDurationPreference: Int,
    @SerializedName("updated_at") val updatedAt: String? = null,
    /** Số việc muốn hoàn thành mỗi ngày (chuỗi ngày đạt mục tiêu). null = server cũ. */
    @SerializedName("daily_goal") val dailyGoal: Int? = null,
    /** "SAT,SUN": ngày nghỉ không làm đứt chuỗi. */
    @SerializedName("days_off") val daysOff: String? = null
)

data class LogoutInput(
    @SerializedName("refresh_token") val refreshToken: String,
    /** false = chỉ đăng xuất máy này. */
    @SerializedName("all_devices") val allDevices: Boolean
)

data class SavePlanInput(
    @SerializedName("plan_data") val planData: List<PlanSlot>
)

/**
 * Hành động AI Coach đề xuất. App chỉ áp dụng khi người dùng bấm xác nhận — qua TaskRepository như mọi thao tác
 * khác (ghi Room → đồng bộ), nên dùng được cả khi vừa mất mạng sau khi nhận câu trả lời.
 */
data class CoachAction(
    val type: String,
    val label: String,
    @SerializedName("task_ids") val taskIds: List<String>? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("all_day") val allDay: Boolean = false,
    val priority: String? = null,
    val title: String? = null,
    val subtasks: List<String>? = null
) {
    companion object {
        const val RESCHEDULE = "RESCHEDULE"
        const val ADD_SUBTASKS = "ADD_SUBTASKS"
        const val CREATE_TASK = "CREATE_TASK"
        const val SET_PRIORITY = "SET_PRIORITY"
        const val ADD_TO_MY_DAY = "ADD_TO_MY_DAY"
    }
}

// Request inputs
data class RegisterInput(
    val email: String,
    val password: String,
    val name: String
)

data class LoginInput(
    val email: String,
    val password: String
)

data class ParseTaskInput(
    val text: String,
    @SerializedName("local_time") val localTime: String
)

data class ParsedTask(
    val title: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",
    @SerializedName("due_date") val dueDate: String? = null,
    val category: String = "OTHER"
)

data class ChatInput(
    val message: String,
    /** Giờ hiện tại của máy (RFC3339 kèm offset) để AI quy "thứ 2 tuần sau" ra ngày cụ thể. */
    @SerializedName("local_time") val localTime: String? = null
)

data class ChatResponse(
    val reply: String,
    /** Server cũ không gửi → null. */
    val actions: List<CoachAction>? = null
)

data class MemoryExtractionResult(
    val message: String? = null,
    val analyzed: Int = 0,
    val extracted: Int = 0
)

data class MemoryItem(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("memory_type") val memoryType: String,
    val content: String,
    val source: String,
    @SerializedName("created_at") val createdAt: String
)

// ── Công việc & đồng bộ offline ──

data class SubtaskDto(
    val id: String,
    val title: String,
    val done: Boolean,
    val position: Int
)

/** Task như server trả về. Thời gian là chuỗi RFC3339; mapper đổi sang epoch millis cho Room. */
data class TaskDto(
    val id: String,
    @SerializedName("user_id") val userId: String?,
    val title: String,
    val description: String?,
    val priority: String,
    @SerializedName("due_date") val dueDate: String?,
    val status: String,
    val category: String?,
    val recurrence: String?,
    @SerializedName("recurrence_days") val recurrenceDays: String?,
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("sort_order") val sortOrder: Double?,
    val subtasks: List<SubtaskDto>?,
    @SerializedName("spawned_from") val spawnedFrom: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    // Thêm ở migration 000008 — server cũ không gửi → null → dùng mặc định
    @SerializedName("due_all_day") val dueAllDay: Boolean? = null,
    @SerializedName("my_day") val myDay: String? = null,
    @SerializedName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerializedName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerializedName("recurrence_mode") val recurrenceMode: String? = null,
    @SerializedName("recurrence_until") val recurrenceUntil: String? = null
)

/** Toàn bộ trạng thái task gửi lên bằng PUT /tasks/{id} (server tạo mới nếu chưa có — idempotent). */
data class TaskInputDto(
    val id: String,
    val title: String,
    val description: String,
    val priority: String,
    @SerializedName("due_date") val dueDate: String?,
    val category: String,
    val recurrence: String,
    @SerializedName("recurrence_days") val recurrenceDays: String,
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int,
    val status: String,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("sort_order") val sortOrder: Double,
    val subtasks: List<SubtaskDto>,
    @SerializedName("spawned_from") val spawnedFrom: String?,
    @SerializedName("due_all_day") val dueAllDay: Boolean,
    /** "" = bỏ khỏi "Ngày của tôi" (server hiểu null là "giữ nguyên" để tương thích client cũ). */
    @SerializedName("my_day") val myDay: String,
    @SerializedName("estimated_minutes") val estimatedMinutes: Int,
    @SerializedName("recurrence_interval") val recurrenceInterval: Int,
    @SerializedName("recurrence_mode") val recurrenceMode: String,
    /** "" = lặp mãi. */
    @SerializedName("recurrence_until") val recurrenceUntil: String
)

/** Kết quả GET /tasks/sync?since=... */
data class TaskChangesDto(
    val tasks: List<TaskDto>?,
    @SerializedName("deleted_ids") val deletedIds: List<String>?,
    @SerializedName("server_time") val serverTime: String
)

data class CategoriesDto(
    val categories: List<String>
)
