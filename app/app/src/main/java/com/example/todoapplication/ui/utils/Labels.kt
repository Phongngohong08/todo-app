package com.example.todoapplication.ui.utils

import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.strArray

/**
 * Bộ chuyển đổi giá trị enum từ backend sang nhãn hiển thị theo ngôn ngữ của app (values/ tiếng Anh, values-vi/ tiếng Việt).
 * Giữ giá trị enum gốc trong logic/API, chỉ map sang chữ ở tầng giao diện.
 */

fun statusLabel(status: String): String = when (status) {
    "ALL" -> str(R.string.status_all)
    "TODO" -> str(R.string.status_todo)
    "COMPLETED" -> str(R.string.status_completed)
    else -> status
}

fun priorityLabel(priority: String): String = when (priority) {
    "HIGH" -> str(R.string.priority_high)
    "MEDIUM" -> str(R.string.priority_medium)
    "LOW" -> str(R.string.priority_low)
    else -> priority
}

fun recurrenceLabel(recurrence: String): String = when (recurrence) {
    "DAILY" -> str(R.string.recurrence_daily)
    "WEEKLY" -> str(R.string.recurrence_weekly)
    "MONTHLY" -> str(R.string.recurrence_monthly)
    else -> str(R.string.recurrence_none)
}

/** Danh sách có sẵn được dịch; danh sách người dùng tự đặt tên giữ nguyên. */
fun categoryLabel(category: String): String = when (category) {
    "ALL" -> str(R.string.category_all)
    "PERSONAL" -> str(R.string.category_personal)
    "WORK" -> str(R.string.category_work)
    "OTHER" -> str(R.string.category_inbox)
    else -> category
}

// Các lựa chọn lặp lại theo thứ tự hiển thị (value enum)
val RECURRENCE_OPTIONS = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")

// Các danh mục cố định (value enum)
val CATEGORY_OPTIONS = listOf("PERSONAL", "WORK", "OTHER")

// Bộ lọc danh mục ở đầu danh sách (gồm "Tất cả")
val CATEGORY_FILTERS = listOf("ALL", "PERSONAL", "WORK", "OTHER")

/** "Mỗi 2 tuần (T2, T4)", "Hằng ngày", "Mỗi 3 ngày · tính từ lúc xong" (en: "Every 2 weeks (Mon, Wed)"…). */
fun recurrenceDescription(recurrence: String, days: String, interval: Int, mode: String): String {
    if (recurrence == "NONE") return str(R.string.recurrence_none)
    val base = if (interval <= 1) recurrenceLabel(recurrence) else when (recurrence) {
        "DAILY" -> str(R.string.recurrence_every_n_days, interval)
        "WEEKLY" -> str(R.string.recurrence_every_n_weeks, interval)
        else -> str(R.string.recurrence_every_n_months, interval)
    }
    val labels = weekdayShortLabels()
    val dayList = days.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { labels[it] ?: it }
    val withDays = if (recurrence == "WEEKLY" && dayList.isNotEmpty()) "$base (${dayList.joinToString(", ")})" else base
    return if (mode == "COMPLETION") str(R.string.recurrence_from_completion, withDays) else withDays
}

/** Mã thứ trong tuần dùng với backend, thứ tự từ thứ Hai. */
val WEEKDAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** "MON" → "T2" (vi) / "Mon" (en), theo thứ tự [WEEKDAY_CODES]. */
fun weekdayShortLabels(): Map<String, String> = WEEKDAY_CODES.zip(strArray(R.array.weekdays_short)).toMap(LinkedHashMap())
