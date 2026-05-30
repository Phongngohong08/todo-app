package com.example.todoapplication.ui.utils

import java.text.SimpleDateFormat
import java.util.*

fun parseIso8601(dateStr: String?): Date? {
    if (dateStr.isNullOrEmpty()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",      // e.g. 2026-05-30T15:00:00+07:00
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // e.g. 2026-05-30T15:00:00.123+07:00
        "yyyy-MM-dd'T'HH:mm:ss'Z'",      // e.g. 2026-05-30T08:00:00Z
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",  // e.g. 2026-05-30T08:00:00.123Z
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            if (pattern.endsWith("Z")) {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(dateStr)
            if (date != null) return date
        } catch (e: Exception) {
            // Try next pattern
        }
    }
    return null
}

fun formatUtcToLocal(utcDateStr: String?): String {
    if (utcDateStr.isNullOrEmpty()) return ""
    val date = parseIso8601(utcDateStr) ?: return try {
        utcDateStr.substring(0, 16).replace("T", " ")
    } catch (e: Exception) {
        utcDateStr
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(date)
}
