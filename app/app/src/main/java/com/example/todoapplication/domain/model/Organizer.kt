package com.example.todoapplication.domain.model

/*
 * [TẦNG DOMAIN · MODEL] Các mô hình của Giai đoạn 2: danh sách (có mục/Kanban), thói quen, lịch sử tập trung.
 */

/** Một danh sách (danh mục) cùng cài đặt hiển thị. [name] là mã lưu trong task.category. */
data class ListInfo(
    val name: String,
    val color: String = "",
    val emoji: String = "",
    /** Các mục (cột Kanban) theo thứ tự. */
    val sections: List<String> = emptyList()
)

object HabitFrequency {
    /** Mỗi ngày. */
    const val DAILY = "DAILY"
    /** Các thứ cố định trong tuần (weekdays = "MON,WED,FRI"). */
    const val WEEKDAYS = "WEEKDAYS"
    /** targetPerWeek lần mỗi tuần, ngày nào cũng được. */
    const val WEEKLY = "WEEKLY"
}

data class Habit(
    val id: String,
    val name: String,
    val emoji: String = "",
    val color: String = "",
    val frequency: String = HabitFrequency.DAILY,
    val weekdays: String = "",
    val targetPerWeek: Int = 7,
    /** "HH:mm" hoặc "" (không nhắc). */
    val reminderTime: String = "",
    val position: Double = 0.0,
    val archived: Boolean = false,
    val createdAt: Long = 0
)

/** Nội dung người dùng nhập khi tạo/sửa thói quen. */
data class HabitDraft(
    val name: String,
    val emoji: String = "",
    val color: String = "",
    val frequency: String = HabitFrequency.DAILY,
    val weekdays: String = "",
    val targetPerWeek: Int = 7,
    val reminderTime: String = "",
    val archived: Boolean = false
)

/** Một phiên tập trung đã xong. */
data class FocusRecord(
    val id: String,
    val taskId: String?,
    val taskTitle: String,
    val startedAt: Long,
    val minutes: Int
)
