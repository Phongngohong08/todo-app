package com.example.todoapplication.data.repository

import com.example.todoapplication.data.model.ParsedTask

/**
 * Giữ tạm kết quả AI Quick Add để màn Thêm Task (taskId = "new") điền sẵn form.
 * Dùng một lần rồi xóa (consume). Mirror pattern holder singleton như [SessionEvents].
 */
object QuickAddDraft {
    private var pending: ParsedTask? = null

    fun set(task: ParsedTask) {
        pending = task
    }

    /** Lấy draft (nếu có) và xóa khỏi holder để không prefill lại lần sau. */
    fun consume(): ParsedTask? {
        val task = pending
        pending = null
        return task
    }
}
