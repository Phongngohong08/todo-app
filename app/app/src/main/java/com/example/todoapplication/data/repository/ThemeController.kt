package com.example.todoapplication.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Quản lý chế độ giao diện (Sáng/Tối/Theo hệ thống) ở cấp ứng dụng.
 * `mode` là state -> đổi giá trị sẽ recompose toàn app. Lưu bền trong SharedPreferences.
 * Mirror pattern holder singleton như [SessionEvents] / [QuickAddDraft].
 */
object ThemeController {
    private const val PREF_NAME = "todo_theme_prefs"
    private const val KEY_MODE = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Nạp chế độ đã lưu (gọi một lần khi khởi động app). */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        mode = runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    /** Đổi chế độ và lưu lại. */
    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, newMode.name)
            .apply()
    }
}
