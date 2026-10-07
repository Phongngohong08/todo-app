package com.example.todoapplication.i18n

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todoapplication.R
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ngôn ngữ riêng của app (Android 13+, LocaleManager): đổi ngôn ngữ → tài nguyên của cả tiến trình đổi theo,
 * nên chuỗi đọc ngoài Compose (str: thông báo, widget, ViewModel) cũng ra đúng ngôn ngữ mới.
 */
@RunWith(AndroidJUnit4::class)
class AppLanguageTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    /** Hệ thống đổi cấu hình của tiến trình bất đồng bộ → chờ tới khi chuỗi đọc được đã ở ngôn ngữ mới. */
    private fun waitForLanguage(tag: String, today: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (str(R.string.day_today) != today && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertEquals(tag, app.resources.configuration.locales[0].language)
    }

    @Test
    fun perAppLanguageSwitchesStringsOutsideCompose() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val manager = app.getSystemService(LocaleManager::class.java)
        val before = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("vi")
            waitForLanguage("vi", "Hôm nay")
            assertEquals("vi", AppLanguage.current(app))
            assertEquals("Hôm nay", str(R.string.day_today))
            assertEquals("Quá hạn 2 ngày", plural(R.plurals.reason_overdue_days, 2))

            manager.applicationLocales = LocaleList.forLanguageTags("en")
            waitForLanguage("en", "Today")
            assertEquals("Today", str(R.string.day_today))
            assertEquals("Overdue by 1 day", plural(R.plurals.reason_overdue_days, 1))
        } finally {
            manager.applicationLocales = before
        }
    }
}
