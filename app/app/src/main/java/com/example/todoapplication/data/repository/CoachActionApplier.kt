package com.example.todoapplication.data.repository

import com.example.todoapplication.data.model.CoachAction
import com.example.todoapplication.domain.allDayDue
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.utils.parseIsoMillis
import java.time.Instant
import java.time.ZoneId

/**
 * Áp dụng một hành động AI Coach đề xuất, SAU KHI người dùng bấm xác nhận. Đi qua [TaskRepository] như mọi
 * thao tác khác (ghi Room → đồng bộ nền), nên hoạt động cả khi vừa mất mạng sau khi nhận câu trả lời.
 * Trả về câu thông báo kết quả, hoặc null nếu hành động không áp dụng được (dữ liệu thiếu/sai).
 */
class CoachActionApplier(
    private val repo: TaskRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    suspend fun apply(action: CoachAction): String? {
        val ids = action.taskIds.orEmpty()
        return when (action.type) {
            CoachAction.RESCHEDULE -> {
                val due = dueOf(action) ?: return null
                if (ids.isEmpty() || !repo.reschedule(ids, due, action.allDay)) null
                else "Đã dời ${ids.size} việc"
            }
            CoachAction.ADD_SUBTASKS -> {
                val taskId = ids.singleOrNull() ?: return null
                val steps = action.subtasks.orEmpty()
                if (!repo.addSubtasks(taskId, steps)) null else "Đã thêm ${steps.count { it.isNotBlank() }} bước con"
            }
            CoachAction.CREATE_TASK -> {
                val title = action.title?.trim().orEmpty()
                if (title.isEmpty()) return null
                val created = repo.create(
                    TaskDraft(
                        title = title,
                        priority = action.priority ?: "MEDIUM",
                        dueAt = dueOf(action),
                        dueAllDay = action.allDay
                    )
                )
                "Đã tạo: ${created.title}"
            }
            CoachAction.SET_PRIORITY -> {
                val p = action.priority?.takeIf { it in setOf("LOW", "MEDIUM", "HIGH") } ?: return null
                if (ids.isEmpty() || !repo.setPriority(ids, p)) null else "Đã đổi độ ưu tiên"
            }
            CoachAction.ADD_TO_MY_DAY -> {
                val today = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toString()
                if (ids.isEmpty() || !repo.setMyDay(ids, today)) null else "Đã thêm ${ids.size} việc vào Ngày của tôi"
            }
            else -> null
        }
    }

    /** Hạn trong hành động; "cả ngày" → 23:59 giờ địa phương của ngày đó (quy ước chung của app). */
    private fun dueOf(action: CoachAction): Long? {
        val millis = parseIsoMillis(action.dueDate) ?: return null
        return if (action.allDay) allDayDue(Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate(), zone()) else millis
    }
}
