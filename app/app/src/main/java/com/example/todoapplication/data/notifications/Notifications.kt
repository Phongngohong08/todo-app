package com.example.todoapplication.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.todoapplication.ui.navigation.AppIntents

/** Kênh thông báo + tiện ích dùng chung cho nhắc việc, tóm tắt buổi sáng, tổng kết tuần và hẹn giờ tập trung. */
object Notifications {
    const val CHANNEL_REMINDERS = "task_reminders"
    const val CHANNEL_DIGEST = "daily_digest"
    const val CHANNEL_FOCUS = "focus_timer"

    const val ID_DIGEST = 7001
    const val ID_WEEKLY_REVIEW = 7002
    const val ID_FOCUS = 7003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Nhắc việc", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Thông báo khi công việc đến hạn" },
                NotificationChannel(CHANNEL_DIGEST, "Tóm tắt & tổng kết", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Tóm tắt việc cần làm buổi sáng và tổng kết cuối tuần" },
                NotificationChannel(CHANNEL_FOCUS, "Hẹn giờ tập trung", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Báo khi hết phiên tập trung hoặc giờ nghỉ" }
            )
        )
    }

    /** Android 13+ cần quyền POST_NOTIFICATIONS; chưa cấp thì bỏ qua (không crash). */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Bấm thông báo → mở app ở màn [open] (xem [AppIntents]). */
    fun openApp(context: Context, requestCode: Int, open: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, AppIntents.main(context, open),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
