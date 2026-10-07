package com.example.todoapplication.data.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todoapplication.R
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.data.location.LocationReminders
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.screens.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Báo thức cho việc ưu tiên cao (bật trong Thiết lập → Nhắc hẹn). Dùng AlarmManager thay vì WorkManager vì
 * WorkManager có thể trễ nhiều phút khi máy ngủ sâu (Doze) — báo thức thì phải đúng giờ.
 *
 *  - Có quyền báo thức chính xác (Android 12+: người dùng cấp trong cài đặt) → setAlarmClock: đúng giờ,
 *    hiện biểu tượng đồng hồ trên thanh trạng thái.
 *  - Chưa có quyền → setAndAllowWhileIdle: vẫn kêu khi máy ngủ, có thể lệch vài phút.
 */
object AlarmScheduler {
    private const val ACTION_FIRE = "com.example.todoapplication.action.ALARM_FIRE"
    const val ACTION_SNOOZE = "com.example.todoapplication.action.ALARM_SNOOZE"
    const val EXTRA_TASK_ID = "alarm_task_id"
    const val EXTRA_TITLE = "alarm_title"
    const val SNOOZE_MINUTES = 10L

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun schedule(context: Context, taskId: String, title: String, atMillis: Long) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val fire = firePendingIntent(app, taskId, title)
        if (canScheduleExact(app)) {
            val show = Notifications.openApp(app, taskId.hashCode(), AppIntents.openTask(taskId))
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fire)
        }
    }

    fun cancel(context: Context, taskId: String) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val existing = PendingIntent.getBroadcast(
            app, taskId.hashCode(), fireIntent(app, taskId, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(existing)
        existing.cancel()
    }

    private fun firePendingIntent(context: Context, taskId: String, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, taskId.hashCode(), fireIntent(context, taskId, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** data = URI riêng theo task → mỗi việc một PendingIntent, không đè nhau dù trùng hashCode. */
    private fun fireIntent(context: Context, taskId: String, title: String) =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("taskflow://alarm/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }

    /** Thông báo báo thức: đổ chuông lặp lại tới khi tắt, bật màn hình khóa với [AlarmActivity]. */
    fun showAlarm(context: Context, taskId: String, title: String) {
        Notifications.ensureChannels(context)
        if (!Notifications.canPost(context)) return
        val notifId = Notifications.alarmId(taskId)
        val fullScreen = PendingIntent.getActivity(
            context, notifId, AlarmActivity.intent(context, taskId, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val complete = PendingIntent.getBroadcast(
            context, notifId + 1,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COMPLETE
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                data = Uri.parse("taskflow://alarm-snooze/$taskId")
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("⏰ Đến hạn việc quan trọng")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_save, "Hoàn thành", complete)
            .addAction(android.R.drawable.ic_menu_recent_history, "Hoãn $SNOOZE_MINUTES phút", snooze)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT } // chuông lặp tới khi tắt
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    fun dismiss(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(Notifications.alarmId(taskId))
    }
}

/** Nhận báo thức tới giờ (và nút "Hoãn 10 phút"). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE).orEmpty()
        val app = context.applicationContext
        if (intent.action == AlarmScheduler.ACTION_SNOOZE) {
            AlarmScheduler.dismiss(app, taskId)
            AlarmScheduler.schedule(app, taskId, title, System.currentTimeMillis() + AlarmScheduler.SNOOZE_MINUTES * 60_000L)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                // Việc đã xong / đã xóa trong lúc chờ (vd trên máy khác) → không kêu nữa
                val task = runCatching { ServiceLocator.taskRepository.getTask(taskId) }.getOrNull()
                if (task != null && !task.isCompleted) AlarmScheduler.showAlarm(app, taskId, task.title.ifBlank { title })
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Khởi động lại máy / cập nhật app: AlarmManager mất hết hẹn giờ → đặt lại toàn bộ nhắc việc. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (ServiceLocator.sessionManager.isLoggedIn()) ReminderScheduler.rescheduleAll(context)
                QuickAddNotifier.refresh(context)
                // Hệ thống xóa mọi geofence khi khởi động lại máy → đăng ký lại
                LocationReminders.sync(context, force = true)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
