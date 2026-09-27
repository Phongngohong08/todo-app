package com.example.todoapplication.data.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoapplication.R
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.screens.SnoozeActivity

/**
 * Hiển thị một thông báo nhắc việc khi đến hạn (được WorkManager kích hoạt).
 * Nút: "Hoàn thành" · "Hoãn 1 giờ" · "Hoãn…" (15 phút / tối nay / sáng mai — như TickTick, Google Calendar).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Công việc đến hạn"
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        // Đã xong / đã xóa (vd trên thiết bị khác, vừa đồng bộ về) → không làm phiền nữa
        val task = runCatching { ServiceLocator.taskRepository.getTask(taskId) }.getOrNull()
        if (task == null || task.isCompleted) return Result.success()
        showNotification(applicationContext, taskId, task.title.ifBlank { title })
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_TASK_ID = "task_id"

        private fun showNotification(context: Context, taskId: String, title: String) {
            Notifications.ensureChannels(context)
            if (!Notifications.canPost(context)) return

            val notifId = taskId.hashCode()

            // Nút "Hoàn thành" và "Hoãn 1 giờ" → NotificationActionReceiver
            fun actionIntent(action: String, requestOffset: Int): PendingIntent {
                val i = Intent(context, NotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                }
                return PendingIntent.getBroadcast(
                    context, notifId + requestOffset, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // "Hoãn…" mở hộp chọn nhỏ (Activity trong suốt) — broadcast không được tự mở Activity trên Android 12+
            val snoozeOptions = PendingIntent.getActivity(
                context, notifId + 3,
                SnoozeActivity.intent(context, taskId, title, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_task)
                .setContentTitle("Đến hạn công việc")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(Notifications.openApp(context, notifId, AppIntents.openTask(taskId)))
                .addAction(
                    android.R.drawable.ic_menu_save, "Hoàn thành",
                    actionIntent(NotificationActionReceiver.ACTION_COMPLETE, 1)
                )
                .addAction(
                    android.R.drawable.ic_menu_recent_history, "Hoãn 1 giờ",
                    actionIntent(NotificationActionReceiver.ACTION_SNOOZE, 2)
                )
                .addAction(android.R.drawable.ic_menu_more, "Hoãn…", snoozeOptions)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}
