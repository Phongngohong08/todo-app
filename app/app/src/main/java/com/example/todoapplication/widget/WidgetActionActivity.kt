package com.example.todoapplication.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nhận thao tác trên widget (Activity trong suốt, không có giao diện, đóng ngay):
 *  - ô tích → hoàn thành việc (ghi Room như mọi nơi khác → đồng bộ nền, widget tự làm mới);
 *  - bấm dòng → mở việc đó trong app.
 * Dùng Activity thay vì BroadcastReceiver vì từ Android 12 receiver không được tự mở Activity ("trampoline").
 */
class WidgetActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_COMPLETE -> if (taskId != null) {
                val app = applicationContext
                scope.launch {
                    val ok = ServiceLocator.taskRepository.setCompleted(taskId, completed = true)
                    if (ok) withContext(Dispatchers.Main) { Toast.makeText(app, "Đã hoàn thành ✓", Toast.LENGTH_SHORT).show() }
                }
            }
            ACTION_OPEN -> startActivity(AppIntents.main(this, taskId?.let(AppIntents::openTask) ?: AppIntents.OPEN_TODAY))
        }
        finish() // theme Translucent đã tắt hiệu ứng chuyển cảnh
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_TASK_ID = "widget_task_id"
        const val ACTION_COMPLETE = "complete"
        const val ACTION_OPEN = "open"

        /** Việc ghi database phải chạy xong dù Activity đã đóng. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun fillIn(action: String, taskId: String) = Intent().apply {
            putExtra(EXTRA_ACTION, action)
            putExtra(EXTRA_TASK_ID, taskId)
        }
    }
}
