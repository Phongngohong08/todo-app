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
