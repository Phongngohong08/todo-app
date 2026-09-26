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
    @SerializedName("updated_at") val updatedAt: String? = null
)

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
    val message: String
)

data class ChatResponse(
    val reply: String
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
    @SerializedName("updated_at") val updatedAt: String
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
    @SerializedName("spawned_from") val spawnedFrom: String?
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
