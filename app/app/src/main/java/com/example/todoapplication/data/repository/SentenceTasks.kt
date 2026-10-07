package com.example.todoapplication.data.repository

import com.example.todoapplication.domain.QuickAddParser
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.utils.categoryLabel
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Tạo việc từ các câu tự nhiên (dòng chữ quét từ ảnh, câu gõ trên thông báo…) bằng CÙNG bộ phân tích của thanh
 * tạo nhanh: "Nộp báo cáo mai 3h !cao #Công việc @gấp". Danh sách mới gõ sau "#" được tạo luôn.
 */
class SentenceTasks(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now
) {
    suspend fun create(sentences: List<String>, myDay: String? = null): List<Task> {
        val known = categories.observeAll().first().toMutableList()
        return sentences.map { it.trim() }.filter { it.isNotEmpty() }.map { text ->
            val options = known.map { QuickAddParser.CategoryOption(it, categoryLabel(it)) }
            val parsed = QuickAddParser.parse(text, now(), categories = options)
            val category = parsed.category ?: "OTHER"
            if (category !in known) {
                categories.add(category).takeIf { it.isNotEmpty() }?.let { known += it }
            }
            tasks.create(
                TaskDraft(
                    title = parsed.title.ifBlank { text },
                    priority = parsed.priority ?: "MEDIUM",
                    dueAt = parsed.dueAt,
                    dueAllDay = parsed.allDay,
                    category = category,
                    recurrence = parsed.recurrence ?: "NONE",
                    recurrenceDays = parsed.recurrenceDays,
                    tags = parsed.tags
                ),
                myDay = myDay
            )
        }
    }
}
