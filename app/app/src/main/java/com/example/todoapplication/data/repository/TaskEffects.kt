package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.data.sync.SyncScheduler
import com.example.todoapplication.data.sync.TaskChangeListener
import com.example.todoapplication.widget.TasksWidgetProvider

/**
 * [TẦNG DATA] Side effect của Android framework khi task thay đổi — gom về một chỗ:
 *  - đặt lại / hủy nhắc việc (WorkManager),
 *  - làm mới widget màn hình chính,
 *  - xin đồng bộ (chỉ với thay đổi trên máy; thay đổi từ server thì không cần gửi ngược lại).
 */
class TaskEffects(
    private val context: Context,
    private val db: AppDatabase,
    private val syncScheduler: SyncScheduler
) : LocalChangeEffects, TaskChangeListener {

    override fun afterLocalWrite(changed: List<TaskEntity>, removedIds: List<String>) {
        applyReminders(changed, removedIds)
        TasksWidgetProvider.refresh(context)
        syncScheduler.requestSync()
    }

    override suspend fun onTasksChanged(changedIds: Collection<String>, removedIds: Collection<String>) {
        val changed = changedIds.chunked(500).flatMap { db.taskDao().getByIds(it) }
        applyReminders(changed, removedIds)
        TasksWidgetProvider.refresh(context)
    }

    private fun applyReminders(changed: List<TaskEntity>, removedIds: Collection<String>) {
        changed.forEach { task ->
            if (task.isDeleted) ReminderScheduler.cancel(context, task.id)
            else ReminderScheduler.schedule(context, task.toDomain())
        }
        removedIds.forEach { ReminderScheduler.cancel(context, it) }
    }
}
