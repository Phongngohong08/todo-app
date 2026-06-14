package com.example.todoapplication.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoapplication.MainActivity

/**
 * Hiển thị một thông báo nhắc việc khi đến hạn (được WorkManager kích hoạt).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Công việc đến hạn"
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        showNotification(applicationContext, taskId, title)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val KEY_TITLE = "title"
        const val KEY_TASK_ID = "task_id"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc việc",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Thông báo khi công việc đến hạn" }
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }

        private fun showNotification(context: Context, taskId: String, title: String) {
            ensureChannel(context)

            // API 33+ cần quyền POST_NOTIFICATIONS; nếu chưa cấp thì bỏ qua (không crash)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, taskId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Đến hạn công việc")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }
    }
}
