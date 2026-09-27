package com.example.todoapplication

import android.app.Application
import com.example.todoapplication.data.repository.ThemeController
import com.example.todoapplication.data.notifications.DigestScheduler
import com.example.todoapplication.data.notifications.FocusSession
import com.example.todoapplication.data.notifications.Notifications
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Application gốc — khởi tạo ServiceLocator (manual DI) một lần khi app khởi động, và bật đồng bộ nền
 * nếu người dùng đã đăng nhập.
 */
class TodoApplication : Application() {

    /** Scope sống cùng tiến trình, cho các việc không thuộc về màn hình nào. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ThemeController.init(this)
        Notifications.ensureChannels(this)
        FocusSession.init(this)
        // Tóm tắt buổi sáng + tổng kết tuần (worker tự bỏ qua khi chưa đăng nhập). REPLACE → gọi mỗi lần mở app vẫn an toàn.
        DigestScheduler.scheduleAll(this)

        if (ServiceLocator.sessionManager.isLoggedIn()) {
            ServiceLocator.syncController.start()
        }

        // Có mạng trở lại → đồng bộ ngay các thay đổi làm lúc offline (bỏ giá trị đầu: trạng thái lúc khởi động)
        appScope.launch {
            ServiceLocator.connectivityObserver.isOnline
                .drop(1)
                .filter { it }
                .collect {
                    if (ServiceLocator.sessionManager.isLoggedIn()) ServiceLocator.syncController.requestSync()
                }
        }
    }
}
