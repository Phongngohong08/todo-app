package com.example.todoapplication.domain.model

/*
 * [TẦNG DOMAIN · MODEL] Mô hình dữ liệu mà UI và logic nghiệp vụ dùng — KHÔNG phụ thuộc Room hay JSON.
 * Thời gian lưu dạng epoch millis (Long): so sánh/sắp xếp nhanh, không phải parse chuỗi ISO mỗi lần vẽ lại.
 * Ba lớp dữ liệu tách biệt: TaskDto (JSON từ API) ↔ TaskEntity (bảng Room) ↔ Task (domain) — xem data/local/Mappers.kt.
 */

object TaskStatus {
    const val TODO = "TODO"
    const val COMPLETED = "COMPLETED"
}

object Recurrence {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
}

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM",
    val dueAt: Long? = null,
    val status: String = TaskStatus.TODO,
    val category: String = "OTHER",
    val recurrence: String = Recurrence.NONE,
    val recurrenceDays: String = "",          // "MON,WED,FRI" khi lặp hằng tuần
    val reminderOffsetMinutes: Int = 0,       // nhắc trước hạn bao nhiêu phút
    val completedAt: Long? = null,
    val sortOrder: Double = 0.0,              // thứ tự kéo-thả: nhỏ hơn đứng trước
    val spawnedFrom: String? = null,          // lần lặp này sinh ra từ task nào
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val subtaskDone: Int = 0,
    val subtaskTotal: Int = 0,
    val hasPendingSync: Boolean = false       // có thay đổi trên máy chưa gửi lên server
) {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isRecurring: Boolean get() = recurrence != Recurrence.NONE
}

data class Subtask(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val position: Int
)

/** Phần nội dung người dùng nhập trong form (không gồm trạng thái hay thông tin đồng bộ). */
data class TaskDraft(
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM",
    val dueAt: Long? = null,
    val category: String = "OTHER",
    val recurrence: String = Recurrence.NONE,
    val recurrenceDays: String = "",
    val reminderOffsetMinutes: Int = 0
)

/** Số liệu tổng hợp cho màn Thống kê (tính từ Room). */
data class StatsSummary(
    val completedTasks: Int,
    val pendingTasks: Int,
    val byCategory: Map<String, Int> = emptyMap()
)
