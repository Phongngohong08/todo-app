package com.example.todoapplication.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.ui.utils.parseIso8601
import java.util.concurrent.TimeUnit

/**
 * Lập lịch local notification cho task theo hạn chót bằng WorkManager.
 * WorkManager tự khôi phục job sau khi khởi động lại máy, dùng REPLACE theo task id để đồng bộ khi sửa/xoá.
 */
object ReminderScheduler {

    private fun uniqueName(taskId: String) = "reminder_$taskId"

    /** Đặt (hoặc cập nhật) nhắc nhở cho task. Tự huỷ nếu không có hạn, đã xong/huỷ, hoặc đã quá hạn. */
    fun schedule(context: Context, task: Task) {
        val due = task.dueDate?.let { parseIso8601(it) }
        if (due == null || task.status == "COMPLETED" || task.status == "CANCELLED") {
            cancel(context, task.id)
            return
        }

        val delay = due.time - System.currentTimeMillis()
        if (delay <= 0) {
            // Đã quá hạn -> không nhắc lại
            cancel(context, task.id)
            return
        }

        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TITLE to task.title,
                    ReminderWorker.KEY_TASK_ID to task.id
                )
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName(task.id), ExistingWorkPolicy.REPLACE, work)
    }

    /** Đặt lại nhắc nhở cho cả danh sách (gọi khi load TaskList để đồng bộ). */
    fun syncAll(context: Context, tasks: List<Task>) {
        tasks.forEach { schedule(context, it) }
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(taskId))
    }
}
