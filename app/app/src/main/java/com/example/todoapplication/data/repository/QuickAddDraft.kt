package com.example.todoapplication.data.repository

import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.domain.allDayDue
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.utils.parseIsoMillis
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Giữ tạm nội dung điền sẵn cho màn Thêm Task (taskId = "new"): từ thanh tạo nhanh ("Chi tiết"), AI Quick Add,
 * mẫu nhiệm vụ, hay nội dung chia sẻ từ app khác. Dùng một lần rồi xóa (consume).
 */
object QuickAddDraft {

    data class Prefill(val draft: TaskDraft, val addToMyDay: Boolean = false)

    private var pending: Prefill? = null

    fun set(prefill: Prefill) {
        pending = prefill
    }

    /** Kết quả AI (hạn là chuỗi RFC3339). Hạn đúng 23:59 → coi là "cả ngày". */
    fun set(task: ParsedTask, zone: ZoneId = ZoneId.systemDefault()) {
        val due = parseIsoMillis(task.dueDate)
        val allDay = due != null && Instant.ofEpochMilli(due).atZone(zone).toLocalTime() == LocalTime.of(23, 59)
        pending = Prefill(
            TaskDraft(
                title = task.title,
                description = task.description,
                priority = task.priority.ifBlank { "MEDIUM" },
                dueAt = if (allDay) allDayDue(Instant.ofEpochMilli(due!!).atZone(zone).toLocalDate(), zone) else due,
                dueAllDay = allDay,
                category = task.category.ifBlank { "OTHER" }
            )
        )
    }

    /** Lấy draft (nếu có) và xóa khỏi holder để không prefill lại lần sau. */
    fun consume(): Prefill? {
        val p = pending
        pending = null
        return p
    }
}
