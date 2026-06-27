package com.example.todoapplication.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Quản lý danh mục công việc: 3 danh mục mặc định (mã enum) + các danh mục do người dùng tự thêm.
 * Danh mục tùy chỉnh lưu bền trong SharedPreferences; `custom` là state -> đổi sẽ recompose.
 * Mirror pattern singleton như [ThemeController].
 */
object CategoryStore {
    private const val PREF_NAME = "todo_category_prefs"
    private const val KEY_CUSTOM = "custom_categories"

    /** Danh mục mặc định (lưu bằng mã enum, hiển thị nhãn tiếng Việt qua categoryLabel). */
    val defaults = listOf("PERSONAL", "WORK", "OTHER")

    var custom by mutableStateOf<List<String>>(emptyList())
        private set

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        custom = prefs.getStringSet(KEY_CUSTOM, emptySet())?.toList()?.sorted() ?: emptyList()
    }

    /** Tất cả danh mục để người dùng chọn (mặc định + tùy chỉnh). */
    fun all(): List<String> = defaults + custom

    /** Thêm một danh mục mới (bỏ qua nếu rỗng/trùng). Trả về tên đã chuẩn hoá. */
    fun add(name: String): String {
        val n = name.trim()
        if (n.isEmpty()) return n
        val isDefault = defaults.any { it.equals(n, ignoreCase = true) }
        val exists = custom.any { it.equals(n, ignoreCase = true) }
        if (!isDefault && !exists) {
            custom = (custom + n).sorted()
            if (::appContext.isInitialized) {
                appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEY_CUSTOM, custom.toSet())
                    .apply()
            }
        }
        return n
    }
}
