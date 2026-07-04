package com.example.todoapplication.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nhận sự kiện khi người dùng bấm nút trên thông báo nhắc việc:
 * - "Hoàn thành" → gọi API complete rồi tắt thông báo.
 * - "Hoãn 1 giờ" → đặt lại nhắc nhở sau 60 phút.
 *
 * Dùng `goAsync()` để giữ receiver sống trong lúc gọi mạng (coroutine ngắn).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, taskId.hashCode())
        val appContext = context.applicationContext

        when (intent.action) {
            ACTION_COMPLETE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ServiceLocator.apiService.completeTask(taskId)
                        ReminderScheduler.cancel(appContext, taskId)
                    } catch (_: Exception) {
                        // Mất mạng: bỏ qua, người dùng có thể hoàn thành lại trong app
                    } finally {
                        NotificationManagerCompat.from(appContext).cancel(notifId)
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                ReminderScheduler.snooze(appContext, taskId, title, 60)
                NotificationManagerCompat.from(appContext).cancel(notifId)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.example.todoapplication.action.COMPLETE"
        const val ACTION_SNOOZE = "com.example.todoapplication.action.SNOOZE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
