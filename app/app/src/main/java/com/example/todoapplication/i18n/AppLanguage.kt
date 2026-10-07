package com.example.todoapplication.i18n

import android.annotation.SuppressLint
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Ngôn ngữ riêng của app (per-app language), độc lập với ngôn ngữ của máy.
 *
 * - Android 13+: dùng LocaleManager của hệ thống — lựa chọn được lưu bởi hệ điều hành, hiện cả trong
 *   Cài đặt › Ứng dụng › Ngôn ngữ (nhờ res/xml/locales_config.xml), hệ thống tự tạo lại Activity.
 * - Android 10–12: tự lưu lựa chọn, bọc Context của Application / Activity bằng Configuration có locale đó
 *   ([wrap] trong attachBaseContext) rồi tạo lại Activity.
 *
 * Tài nguyên: values/ = tiếng Anh (mặc định), values-vi/ = tiếng Việt.
 */
object AppLanguage {
    /** Theo ngôn ngữ của máy. */
    const val SYSTEM = ""

    /** Mã ngôn ngữ → tên hiển thị bằng chính ngôn ngữ đó (không dịch). Giống res/xml/locales_config.xml. */
    val SUPPORTED = listOf("vi" to "Tiếng Việt", "en" to "English")

    /** Android 10–12: đổi ngôn ngữ không qua onConfigurationChanged của Application → báo trực tiếp. */
    var onChanged: (() -> Unit)? = null

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "" (theo máy), "vi" hoặc "en". */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) SYSTEM else locales[0].language
        } else {
            prefs(context).getString(KEY, SYSTEM).orEmpty()
        }

    @SuppressLint("ApplySharedPref")
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return
        }
        // commit (ghi ngay): Activity tạo lại ngay sau đây đọc lựa chọn này trong attachBaseContext
        prefs(activity).edit().putString(KEY, tag).commit()
        applyTo(activity.applicationContext.resources, tag)
        onChanged?.invoke()
        activity.recreate()
    }

    /** Android 10–12: gọi trong attachBaseContext của Application và mọi Activity. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, SYSTEM).orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** Đổi ngay ngôn ngữ của Resources đang dùng (thông báo, widget đọc chuỗi qua Context của Application). */
    @Suppress("DEPRECATION")
    private fun applyTo(resources: Resources, tag: String) {
        val locales = if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList(Locale.forLanguageTag(tag))
        Locale.setDefault(locales[0])
        val config = Configuration(resources.configuration).apply { setLocales(locales) }
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
