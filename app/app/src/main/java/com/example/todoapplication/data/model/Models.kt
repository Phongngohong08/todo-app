package com.example.todoapplication.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class AuthResponse(
    val token: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: User
)

data class Task(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val description: String?,
    val priority: String, // "LOW", "MEDIUM", "HIGH"
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("estimated_duration") val estimatedDuration: Int, // in minutes
    @SerializedName("preferred_time_start") val preferredTimeStart: String?,
    @SerializedName("preferred_time_end") val preferredTimeEnd: String?,
    val status: String, // "TODO", "IN_PROGRESS", "COMPLETED", "CANCELLED", "POSTPONED"
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

data class StatsSummary(
    @SerializedName("completed_tasks") val completedTasks: Int,
    @SerializedName("postponed_tasks") val postponedTasks: Int,
    @SerializedName("total_time_spent_mins") val totalTimeSpentMins: Int,
    @SerializedName("most_postponed_category") val mostPostponedCategory: String?,
    @SerializedName("postpone_reasons") val postponeReasons: Map<String, Int>?
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
    @SerializedName("estimated_duration") val estimatedDuration: Int,
    @SerializedName("preferred_time_start") val preferredTimeStart: String?,
    @SerializedName("preferred_time_end") val preferredTimeEnd: String?
)

data class UpdateTaskInput(
    val title: String,
    val description: String,
    val priority: String,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("estimated_duration") val estimatedDuration: Int,
    @SerializedName("preferred_time_start") val preferredTimeStart: String?,
    @SerializedName("preferred_time_end") val preferredTimeEnd: String?
)

data class PostponeTaskInput(
    @SerializedName("due_date") val dueDate: String,
    val reason: String
)

data class ChatInput(
    val message: String
)

data class ChatResponse(
    val reply: String
)

data class MemoryItem(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("memory_type") val memoryType: String,
    val content: String,
    val source: String,
    @SerializedName("created_at") val createdAt: String
)
