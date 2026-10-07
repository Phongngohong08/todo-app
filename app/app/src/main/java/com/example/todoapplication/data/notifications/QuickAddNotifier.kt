package com.example.todoapplication.data.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.todoapplication.R
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.utils.formatDueLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.todoapplication.i18n.str

/**
 * Thông báo cố định "Thêm việc nhanh": gõ ngay trên thanh thông báo (RemoteInput, như trả lời tin nhắn) —
 * câu được phân tích bằng chính bộ phân tích tạo nhanh ("Nộp báo cáo mai 3h !cao #Công việc @gấp") rồi lưu vào
 * Room (chạy được khi offline). Bật / tắt trong Thiết lập.
 */
object QuickAddNotifier {
    const val KEY_TEXT = "quick_add_text"

    fun refresh(context: Context, lastAdded: String? = null) {
        val app = context.applicationContext
        val enabled = ServiceLocator.localPrefs.quickAddNotification.value && ServiceLocator.sessionManager.isLoggedIn()
        if (!enabled) {
            NotificationManagerCompat.from(app).cancel(Notifications.ID_QUICK_ADD)
            return
        }
        Notifications.ensureChannels(app)
        if (!Notifications.canPost(app)) return

        val remoteInput = RemoteInput.Builder(KEY_TEXT)
            .setLabel(str(R.string.quick_add_notif_hint))
            .build()
        val reply = PendingIntent.getBroadcast(
            app, Notifications.ID_QUICK_ADD,
            Intent(app, QuickAddReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_shortcut_add, str(R.string.tile_quick_add), reply)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(app, Notifications.CHANNEL_QUICK_ADD)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(str(R.string.channel_quick_add))
            .setContentText(lastAdded?.let { str(R.string.task_added, it) } ?: str(R.string.quick_add_notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(Notifications.openApp(app, Notifications.ID_QUICK_ADD, AppIntents.OPEN_ADD))
            .addAction(action)
            .build()
        NotificationManagerCompat.from(app).notify(Notifications.ID_QUICK_ADD, notification)
    }
}

/** Nhận câu gõ trên thông báo "Thêm việc nhanh" → tạo việc. */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(QuickAddNotifier.KEY_TEXT)?.toString()?.trim()
        val app = context.applicationContext
        if (text.isNullOrEmpty()) {
            QuickAddNotifier.refresh(app)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                val task = ServiceLocator.sentenceTasks.create(listOf(text)).firstOrNull() ?: return@launch
                val label = task.title + (task.dueAt?.let { " · " + formatDueLabel(it, task.dueAllDay) } ?: "")
                // Phải đăng lại thông báo, nếu không hệ thống cứ hiện vòng quay "đang gửi"
                QuickAddNotifier.refresh(app, lastAdded = label)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
