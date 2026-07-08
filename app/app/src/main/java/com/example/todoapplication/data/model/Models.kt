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

data class Task(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val description: String?,
    val priority: String, // "LOW", "MEDIUM", "HIGH"
    @SerializedName("due_date") val dueDate: String?,
    val status: String, // "TODO", "COMPLETED"
    val category: String = "OTHER", // "PERSONAL", "WORK", "OTHER" hoặc danh mục tự do
    val recurrence: String = "NONE", // "NONE", "DAILY", "WEEKLY", "MONTHLY"
    @SerializedName("recurrence_days") val recurrenceDays: String = "", // "MON,WED,FRI" khi WEEKLY
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int = 0, // phút nhắc trước hạn
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class TaskLog(
    val id: String,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("user_id") val userId: String,
    val action: String,
    val details: String?,
    @SerializedName("created_at") val createdAt: String
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

data class DailyCount(
    val date: String,
    val completed: Int
)

data class StatsSummary(
    @SerializedName("completed_tasks") val completedTasks: Int,
    @SerializedName("pending_tasks") val pendingTasks: Int,
    @SerializedName("by_category") val byCategory: Map<String, Int> = emptyMap(),
    @SerializedName("daily_completed") val dailyCompleted: List<DailyCount> = emptyList()
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

data class CreateTaskInput(
    val title: String,
    val description: String,
    val priority: String,
    @SerializedName("due_date") val dueDate: String?,
    val category: String = "OTHER",
    val recurrence: String = "NONE",
    @SerializedName("recurrence_days") val recurrenceDays: String = "",
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int = 0
)

data class UpdateTaskInput(
    val title: String,
    val description: String,
    val priority: String,
    @SerializedName("due_date") val dueDate: String?,
    val category: String = "OTHER",
    val recurrence: String = "NONE",
    @SerializedName("recurrence_days") val recurrenceDays: String = "",
    @SerializedName("reminder_offset_minutes") val reminderOffsetMinutes: Int = 0
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
