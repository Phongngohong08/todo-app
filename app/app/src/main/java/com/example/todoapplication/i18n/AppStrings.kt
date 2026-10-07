package com.example.todoapplication.i18n

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Chuỗi giao diện cho code NGOÀI Compose (ViewModel, Worker, thông báo, widget, logic tạo nhãn).
 * Trong Composable dùng stringResource(...) như bình thường; ở đây đọc tài nguyên qua Context của Application
 * — luôn theo ngôn ngữ app đang dùng (xem [AppLanguage]).
 *
 * [source] thay được trong unit test (JVM không có Resources): test nạp thẳng values-vi/strings.xml.
 */
object AppStrings {
    interface Source {
        fun string(@StringRes id: Int, args: Array<out Any?>): String
        fun plural(@PluralsRes id: Int, count: Int, args: Array<out Any?>): String
        fun array(@ArrayRes id: Int): List<String>
    }

    @Volatile
    var source: Source? = null

    fun init(context: Context) {
        source = ResourceSource(context.applicationContext)
    }

    internal fun require(): Source = source ?: error("AppStrings.init() chưa được gọi")

    private class ResourceSource(private val context: Context) : Source {
        override fun string(id: Int, args: Array<out Any?>): String =
            if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

        override fun plural(id: Int, count: Int, args: Array<out Any?>): String =
            context.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf(count) else args))

        override fun array(id: Int): List<String> = context.resources.getStringArray(id).toList()
    }
}

/** Chuỗi theo ngôn ngữ hiện tại; [args] thay cho %1$s, %2$s… */
fun str(@StringRes id: Int, vararg args: Any?): String = AppStrings.require().string(id, args)

/** Chuỗi số nhiều ("1 task" / "3 tasks"); không truyền [args] thì dùng chính [count]. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any?): String = AppStrings.require().plural(id, count, args)

fun strArray(@ArrayRes id: Int): List<String> = AppStrings.require().array(id)

private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

/**
 * Định dạng ngày theo mẫu lấy từ tài nguyên (vd en "MMM d" → "Jul 10", vi "dd/MM" → "10/07"),
 * dùng Locale hiện tại cho tên tháng / thứ. Cache theo (mẫu, locale) vì DateTimeFormatter bất biến.
 */
fun localizedFormatter(@StringRes patternId: Int): DateTimeFormatter {
    val pattern = str(patternId)
    val locale = Locale.getDefault()
    return formatters.getOrPut("$pattern|${locale.toLanguageTag()}") { DateTimeFormatter.ofPattern(pattern, locale) }
}
