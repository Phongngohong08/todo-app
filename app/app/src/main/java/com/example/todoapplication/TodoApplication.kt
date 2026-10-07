package com.example.todoapplication

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.example.todoapplication.i18n.AppLanguage
import com.example.todoapplication.i18n.AppStrings
import com.example.todoapplication.widget.WidgetUpdater
import com.example.todoapplication.data.repository.ThemeController
import com.example.todoapplication.data.notifications.DigestScheduler
import com.example.todoapplication.data.notifications.FocusSession
import com.example.todoapplication.data.notifications.HabitReminderScheduler
import com.example.todoapplication.data.notifications.QuickAddNotifier
import com.example.todoapplication.data.location.LocationReminders
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

    // Ngôn ngữ riêng của app trên Android 10–12 (Android 13+ hệ thống tự áp cho cả tiến trình)
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    private var lastLocales = ""

    /** Đổi ngôn ngữ (của app hoặc của máy); bỏ qua các thay đổi cấu hình khác (xoay màn, chế độ tối). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val tags = newConfig.locales.toLanguageTags()
        if (tags != lastLocales) {
            lastLocales = tags
            onLanguageChanged()
        }
    }

    /** Đặt lại tên kênh thông báo theo ngôn ngữ mới, vẽ lại widget và thông báo "Thêm việc nhanh". */
    private fun onLanguageChanged() {
        Notifications.ensureChannels(this)
        WidgetUpdater.refresh(this)
        if (ServiceLocator.sessionManager.isLoggedIn()) QuickAddNotifier.refresh(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
        lastLocales = resources.configuration.locales.toLanguageTags()
        AppLanguage.onChanged = ::onLanguageChanged
        ServiceLocator.init(this)
        ThemeController.init(this)
        Notifications.ensureChannels(this)
        FocusSession.init(this)
        // Tóm tắt buổi sáng + tổng kết tuần (worker tự bỏ qua khi chưa đăng nhập). REPLACE → gọi mỗi lần mở app vẫn an toàn.
        DigestScheduler.scheduleAll(this)

        if (ServiceLocator.sessionManager.isLoggedIn()) {
            ServiceLocator.syncController.start()
            HabitReminderScheduler.rescheduleAll(this)
            QuickAddNotifier.refresh(this)
            LocationReminders.sync(this, force = true)
            appScope.launch { ServiceLocator.attachmentRepository.cleanupOrphans() }
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
