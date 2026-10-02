package com.example.todoapplication.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.todoapplication.ui.navigation.AppIntents

/** Kênh thông báo + tiện ích dùng chung cho nhắc việc, tóm tắt buổi sáng, tổng kết tuần và hẹn giờ tập trung. */
object Notifications {
    const val CHANNEL_REMINDERS = "task_reminders"
    const val CHANNEL_DIGEST = "daily_digest"
    const val CHANNEL_FOCUS = "focus_timer"
    const val CHANNEL_ALARM = "task_alarms"

    const val ID_DIGEST = 7001
    const val ID_WEEKLY_REVIEW = 7002
    const val ID_FOCUS = 7003

    /** Mỗi việc một id thông báo báo thức riêng (khác id nhắc việc thường). */
    fun alarmId(taskId: String): Int = taskId.hashCode() xor 0x5A5A_0000

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
                    .apply { description = "Báo khi hết phiên tập trung hoặc giờ nghỉ" },
                // Kênh báo thức: âm báo thức của máy, phát theo luồng báo thức (kêu cả khi để im lặng thông báo)
                NotificationChannel(CHANNEL_ALARM, "Báo thức việc quan trọng", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Đổ chuông toàn màn hình khi việc ưu tiên cao đến hạn"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600)
                }
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
