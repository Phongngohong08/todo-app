package com.example.todoapplication.ui.utils

/**
 * Bộ chuyển đổi giá trị enum từ backend sang nhãn tiếng Việt hiển thị cho người dùng.
 * Giữ giá trị enum gốc trong logic/API, chỉ map sang tiếng Việt ở tầng giao diện.
 */

fun statusLabel(status: String): String = when (status) {
    "ALL" -> "Tất cả"
    "TODO" -> "Cần làm"
    "COMPLETED" -> "Hoàn thành"
    else -> status
}

fun priorityLabel(priority: String): String = when (priority) {
    "HIGH" -> "Cao"
    "MEDIUM" -> "Trung bình"
    "LOW" -> "Thấp"
    else -> priority
}

fun recurrenceLabel(recurrence: String): String = when (recurrence) {
    "DAILY" -> "Hằng ngày"
    "WEEKLY" -> "Hằng tuần"
    "MONTHLY" -> "Hằng tháng"
    else -> "Không lặp"
}

fun categoryLabel(category: String): String = when (category) {
    "ALL" -> "Tất cả"
    "PERSONAL" -> "Cá nhân"
    "WORK" -> "Công việc"
    "OTHER" -> "Khác"
    else -> category
}

// Các lựa chọn lặp lại theo thứ tự hiển thị (value enum)
val RECURRENCE_OPTIONS = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")

// Các danh mục cố định (value enum)
val CATEGORY_OPTIONS = listOf("PERSONAL", "WORK", "OTHER")

// Bộ lọc danh mục ở đầu danh sách (gồm "Tất cả")
val CATEGORY_FILTERS = listOf("ALL", "PERSONAL", "WORK", "OTHER")
