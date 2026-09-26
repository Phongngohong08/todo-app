package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Recurrence
import java.nio.ByteBuffer
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * [TẦNG DOMAIN] Quy tắc việc lặp lại — PHẢI giống hệt backend (backend/internal/usecase/task/recurrence.go).
 *
 * App tự sinh lần lặp kế tiếp ngay khi người dùng bấm hoàn thành (kể cả lúc offline) để giao diện phản hồi
 * tức thì. Server cũng sinh lần lặp khi nhận trạng thái hoàn thành. Hai bên dùng CÙNG id tất định
 * ([nextOccurrenceId]) nên khi đồng bộ chỉ còn một bản, không bị nhân đôi.
 */
object RecurrenceRules {

    // Namespace chung với backend (domain.recurrenceNamespace)
    private val NAMESPACE: UUID = UUID.fromString("6f1c2c1e-6b1a-4b8e-9a3e-2d5f8f0c7a11")
    private const val MAX_ADVANCE_STEPS = 2000

    fun isRecurring(recurrence: String): Boolean =
        recurrence == Recurrence.DAILY || recurrence == Recurrence.WEEKLY || recurrence == Recurrence.MONTHLY

    /**
     * UUID v3 = MD5(namespace + parentId) — tương đương uuid.NewMD5 của Go.
     * (UUID.nameUUIDFromBytes băm đúng mảng byte truyền vào, nên ghép namespace vào trước.)
     */
    fun nextOccurrenceId(parentId: String): String {
        val ns = ByteBuffer.allocate(16)
            .putLong(NAMESPACE.mostSignificantBits)
            .putLong(NAMESPACE.leastSignificantBits)
            .array()
        return UUID.nameUUIDFromBytes(ns + parentId.toByteArray(Charsets.UTF_8)).toString()
    }

    /**
     * Hạn của lần lặp kế tiếp: bước tiếp theo sau [dueAt]; nếu vẫn chưa tới [now] thì nhảy tiếp,
     * để lần lặp mới luôn nằm ở tương lai kể cả khi người dùng hoàn thành trễ nhiều ngày.
     */
    fun nextDueAfter(dueAt: Long, recurrence: String, days: String, now: Long, zone: ZoneId): Long {
        var next = next(Instant.ofEpochMilli(dueAt).atZone(zone), recurrence, days)
        var steps = 0
        while (next.toInstant().toEpochMilli() <= now && steps < MAX_ADVANCE_STEPS) {
            next = next(next, recurrence, days)
            steps++
        }
        return next.toInstant().toEpochMilli()
    }

    /** Dời đúng một chu kỳ. WEEKLY có chọn thứ: ngày gần nhất sau [from] rơi vào một thứ đã chọn. */
    fun next(from: ZonedDateTime, recurrence: String, days: String): ZonedDateTime = when (recurrence) {
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.WEEKLY -> {
            val chosen = parseWeekdays(days)
            if (chosen.isEmpty()) from.plusWeeks(1)
            else (1..7).map { from.plusDays(it.toLong()) }.first { it.dayOfWeek in chosen }
        }
        // plusMonths kẹp ngày vào cuối tháng (31/1 → 28/2), backend làm y như vậy (addMonthsClamped)
        Recurrence.MONTHLY -> from.plusMonths(1)
        else -> from
    }

    fun parseWeekdays(days: String): Set<DayOfWeek> = days.split(",")
        .mapNotNull { WEEKDAY_CODES[it.trim().uppercase()] }
        .toSet()

    private val WEEKDAY_CODES = mapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY
    )

    /**
     * Các lần lặp DỰ KIẾN sau [dueAt] cho tới [untilMillis] (không gồm chính dueAt) — dùng cho màn Lịch.
     * Chỉ là dự báo để người dùng xem trước; task thật của lần kế tiếp chỉ xuất hiện khi hoàn thành lần này.
     */
    fun projectedOccurrences(
        dueAt: Long,
        recurrence: String,
        days: String,
        untilMillis: Long,
        zone: ZoneId,
        maxCount: Int = 400
    ): List<Long> {
        if (!isRecurring(recurrence)) return emptyList()
        val result = mutableListOf<Long>()
        var cursor = Instant.ofEpochMilli(dueAt).atZone(zone)
        while (result.size < maxCount) {
            cursor = next(cursor, recurrence, days)
            val millis = cursor.toInstant().toEpochMilli()
            if (millis > untilMillis) break
            result += millis
        }
        return result
    }
}
