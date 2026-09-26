package com.example.todoapplication.ui.utils

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Parse thời gian backend trả về thành [Date].
 * Backend Go xuất RFC3339Nano (vd "2026-05-30T08:00:00.123456Z" hoặc "...+07:00") — phần lẻ giây
 * dài tùy ý nên KHÔNG dùng SimpleDateFormat ("SSS" sẽ đọc 123456 thành 123456 ms ≈ lệch 2 phút).
 * Chuỗi không có múi giờ ("2026-05-30 08:00:00") được hiểu là giờ máy.
 */
fun parseIso8601(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    val s = dateStr.trim()
    runCatching { return Date.from(OffsetDateTime.parse(s).toInstant()) }
    runCatching {
        val local = LocalDateTime.parse(s.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return Date.from(local.atZone(ZoneId.systemDefault()).toInstant())
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

/**
 * Ngày hôm nay theo lịch của máy, dạng "2026-07-08" (luôn chữ số ASCII, không phụ thuộc Locale).
 * Gửi cho backend để server không lấy nhầm ngày UTC — 0h–7h sáng giờ VN ở UTC vẫn là "hôm qua".
 */
fun todayLocalDate(): String = LocalDate.now().toString()

/** Giờ hiện tại theo máy, dạng "14:30". */
fun nowLocalClock(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * Múi giờ IANA của máy (vd "Asia/Ho_Chi_Minh") để server gom số liệu theo ngày của người dùng.
 * Trả null nếu máy chỉ có dạng offset ("GMT+07:00") — khi đó server dùng múi giờ mặc định (APP_TIMEZONE).
 */
fun deviceTimeZoneId(): String? = ZoneId.systemDefault().id.takeIf { it.contains('/') || it == "UTC" }

/** Chuỗi RFC3339 → epoch millis (null nếu trống/sai định dạng). */
fun parseIsoMillis(dateStr: String?): Long? = parseIso8601(dateStr)?.time

/** Epoch millis → chuỗi RFC3339 UTC (vd "2026-07-08T02:00:00Z") để gửi lên server. */
fun toIsoString(millis: Long): String = java.time.Instant.ofEpochMilli(millis).toString()

private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Hiển thị ngày giờ theo múi giờ máy. DateTimeFormatter bất biến và an toàn đa luồng nên tạo một lần
 * rồi dùng lại (SimpleDateFormat thì không) — tránh tạo đối tượng mới mỗi lần vẽ lại danh sách.
 */
fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME_FORMAT.format(java.time.Instant.ofEpochMilli(millis).atZone(zone))

fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TIME_FORMAT.format(java.time.Instant.ofEpochMilli(millis).atZone(zone))
