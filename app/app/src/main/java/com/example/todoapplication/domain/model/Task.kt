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

/** Lần lặp kế tiếp tính từ đâu — xem RecurrenceRules. */
object RecurrenceMode {
    /** Theo lịch cố định, tính từ hạn cũ (họp mỗi thứ 2). */
    const val SCHEDULE = "SCHEDULE"
    /** Tính từ ngày hoàn thành (tưới cây 3 ngày sau lần tưới trước). */
    const val COMPLETION = "COMPLETION"
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
    val hasPendingSync: Boolean = false,      // có thay đổi trên máy chưa gửi lên server
    /** Hạn chỉ có ngày (dueAt = 23:59 giờ địa phương của ngày đó). */
    val dueAllDay: Boolean = false,
    /** "Ngày của tôi": ngày người dùng chọn làm việc này ("yyyy-MM-dd"); khác hôm nay = không còn hiệu lực. */
    val myDay: String? = null,
    val estimatedMinutes: Int = 0,
    val recurrenceInterval: Int = 1,
    val recurrenceMode: String = RecurrenceMode.SCHEDULE,
    val recurrenceUntil: Long? = null,
    /** Nhãn (đã chuẩn hóa: chữ thường, không khoảng trắng). */
    val tags: List<String> = emptyList(),
    /** Mục trong danh sách (cột Kanban), "" = chưa phân mục. */
    val section: String = "",
    /** Giờ bắt đầu — cùng [dueAt] tạo khung thời gian (09:00–10:30). */
    val startAt: Long? = null,
    /** Lời nhắc thêm (phút trước mốc nhắc), ngoài [reminderOffsetMinutes]. */
    val extraReminders: List<Int> = emptyList(),
    /** Bước con chưa xong có hạn sớm nhất ("yyyy-MM-dd") — chỉ có khi đọc qua observeTasks. */
    val nextStepDue: String? = null,
    val nextStepTitle: String? = null,
    /** Thời điểm bị xóa (chỉ có ý nghĩa với việc trong Thùng rác). */
    val deletedAt: Long? = null
) {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isRecurring: Boolean get() = recurrence != Recurrence.NONE

    /** Mọi lời nhắc (phút trước mốc nhắc), không trùng, tăng dần. */
    val reminderOffsets: List<Int> get() = (listOf(reminderOffsetMinutes) + extraReminders).distinct().sorted()

    /** Việc nằm trong "Ngày của tôi" của ngày [today] ("yyyy-MM-dd"). */
    fun isInMyDay(today: String): Boolean = myDay == today
}

data class Subtask(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val position: Int,
    /** Hạn của riêng bước này ("yyyy-MM-dd"). */
    val dueDate: String? = null
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
    val reminderOffsetMinutes: Int = 0,
    val dueAllDay: Boolean = false,
    val estimatedMinutes: Int = 0,
    val recurrenceInterval: Int = 1,
    val recurrenceMode: String = RecurrenceMode.SCHEDULE,
    val recurrenceUntil: Long? = null,
    val tags: List<String> = emptyList(),
    val section: String = "",
    val startAt: Long? = null,
    val extraReminders: List<Int> = emptyList()
)

/** Số liệu tổng hợp cho màn Thống kê (tính từ Room). */
data class StatsSummary(
    val completedTasks: Int,
    val pendingTasks: Int,
    val byCategory: Map<String, Int> = emptyMap()
)
