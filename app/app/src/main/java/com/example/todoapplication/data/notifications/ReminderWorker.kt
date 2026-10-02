package com.example.todoapplication.data.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoapplication.R
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.QuietHours
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.screens.SnoozeActivity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Hiển thị một thông báo nhắc việc khi đến hạn (được WorkManager kích hoạt).
 * Nút: "Hoàn thành" · "Hoãn 1 giờ" · "Hoãn…" (15 phút / tối nay / sáng mai — như TickTick, Google Calendar).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Công việc đến hạn"
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        // Đã xong / đã xóa (vd trên thiết bị khác, vừa đồng bộ về) → không làm phiền nữa
        val task = runCatching { ServiceLocator.taskRepository.getTask(taskId) }.getOrNull()
        if (task == null || task.isCompleted) return Result.success()
        val name = task.title.ifBlank { title }
        val prefs = ServiceLocator.localPrefs

        // Giờ yên tĩnh: dời tới lúc kết thúc thay vì đánh thức người dùng
        if (prefs.quietEnabled.value) {
            val start = parseClock(prefs.quietStart.value, LocalTime.of(22, 0))
            val end = parseClock(prefs.quietEnd.value, LocalTime.of(7, 0))
            QuietHours.deferUntil(LocalDateTime.now(), start, end)?.let { until ->
                val at = until.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                ReminderScheduler.deferForQuietHours(applicationContext, taskId, name, at, attempt)
                return Result.success()
            }
        }

        showNotification(applicationContext, taskId, name, attempt)

        // Nhắc dai: hẹn lần kế tiếp — dừng khi việc xong (TaskEffects hủy job) hoặc đủ số lần tối đa
        val nagMinutes = prefs.nagMinutes.value
        if (nagMinutes > 0 && attempt < LocalPrefs.MAX_NAGS && (!prefs.nagHighOnly.value || task.priority == "HIGH")) {
            ReminderScheduler.scheduleNag(applicationContext, taskId, name, nagMinutes, attempt + 1)
        }
        return Result.success()
    }

    private fun parseClock(value: String, fallback: LocalTime): LocalTime =
        runCatching { LocalTime.parse(value) }.getOrDefault(fallback)

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_TASK_ID = "task_id"
        /** 0 = lần nhắc đầu; > 0 = lần nhắc dai thứ mấy. */
        const val KEY_ATTEMPT = "attempt"

        private fun showNotification(context: Context, taskId: String, title: String, attempt: Int) {
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
                .setContentTitle(if (attempt > 0) "Nhắc lại (lần $attempt): việc chưa xong" else "Đến hạn công việc")
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
