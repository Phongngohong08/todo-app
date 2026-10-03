package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task

/** Chia việc của một danh sách thành các cột Kanban theo mục. */
object BoardLogic {

    /**
     * Cột đầu luôn là "" (Chưa phân mục), sau đó các mục theo thứ tự của danh sách. Việc mang tên mục không còn
     * tồn tại (mục đã bị xóa ở máy khác) rơi về cột đầu thay vì biến mất. Giữ nguyên thứ tự việc trong mỗi cột.
     */
    fun columns(sections: List<String>, tasks: List<Task>): List<Pair<String, List<Task>>> {
        val known = sections.toSet()
        val bySection = tasks.groupBy { if (it.section in known) it.section else "" }
        return (listOf("") + sections).map { it to bySection[it].orEmpty() }
    }
}
