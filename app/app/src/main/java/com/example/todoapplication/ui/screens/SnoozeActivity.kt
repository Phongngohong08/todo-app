package com.example.todoapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.core.app.NotificationManagerCompat
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.domain.SnoozeOptions
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import java.time.LocalDateTime

/**
 * Hộp chọn "Hoãn…" mở từ nút trên thông báo nhắc việc (Activity trong suốt, chỉ có hộp thoại).
 * Chọn một mốc → đặt lại nhắc việc, tắt thông báo, đóng ngay.
 */
class SnoozeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, taskId.hashCode())

        setContent {
            TodoApplicationTheme {
                val options = remember { SnoozeOptions.options(LocalDateTime.now()) }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Nhắc lại sau") },
                    text = {
                        Column {
                            if (title.isNotBlank()) {
                                Text(title)
                                HorizontalDivider()
                            }
                            options.forEach { option ->
                                ListItem(
                                    headlineContent = { Text(option.label) },
                                    modifier = Modifier.clickable {
                                        ReminderScheduler.snoozeUntil(applicationContext, taskId, title, option.atMillis)
                                        NotificationManagerCompat.from(applicationContext).cancel(notifId)
                                        finish()
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Hủy") } }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_NOTIF_ID = "notif_id"

        fun intent(context: Context, taskId: String, title: String, notifId: Int): Intent =
            Intent(context, SnoozeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
    }
}
