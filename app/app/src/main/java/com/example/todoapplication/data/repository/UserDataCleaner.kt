package com.example.todoapplication.data.repository

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.data.sync.SyncScheduler
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.widget.TasksWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Xóa mọi dữ liệu cục bộ gắn với tài khoản khi đăng xuất: database Room (task, bước con, danh mục, chat),
 * công việc nền (đồng bộ, nhắc việc), thông báo đang hiện và widget.
 * Dữ liệu đã nằm trên server nên đăng nhập lại sẽ tải về đầy đủ.
 */
object UserDataCleaner {
    // Scope cấp ứng dụng: việc dọn phải chạy xong kể cả khi màn/ViewModel gọi logout đã bị hủy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun clear(context: Context) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        workManager.cancelAllWorkByTag(SyncScheduler.TAG)
        workManager.cancelAllWorkByTag(ReminderScheduler.TAG)
        NotificationManagerCompat.from(app).cancelAll()

        scope.launch {
            // Chờ lần đồng bộ đang chạy (nếu có) kết thúc để nó không ghi dữ liệu cũ vào DB vừa xóa
            ServiceLocator.syncEngine.exclusive {
                val db = AppDatabase.get(app)
                db.taskDao().getAllIds().forEach { ReminderScheduler.cancel(app, it) }
                db.clearAllTables()
            }
            TasksWidgetProvider.refresh(app)
        }
    }
}
