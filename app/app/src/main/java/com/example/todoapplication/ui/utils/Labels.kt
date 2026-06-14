package com.example.todoapplication.ui.utils

/**
 * Bộ chuyển đổi giá trị enum từ backend sang nhãn tiếng Việt hiển thị cho người dùng.
 * Giữ giá trị enum gốc trong logic/API, chỉ map sang tiếng Việt ở tầng giao diện.
 */

fun statusLabel(status: String): String = when (status) {
    "ALL" -> "Tất cả"
    "TODO" -> "Cần làm"
    "IN_PROGRESS" -> "Đang làm"
    "COMPLETED" -> "Hoàn thành"
    "CANCELLED" -> "Đã hủy"
    "POSTPONED" -> "Đã hoãn"
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

// Các lựa chọn lặp lại theo thứ tự hiển thị (value enum)
val RECURRENCE_OPTIONS = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")
