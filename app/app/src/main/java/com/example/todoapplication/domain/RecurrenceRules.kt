package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Recurrence
import com.example.todoapplication.domain.model.RecurrenceMode
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
    /** Khớp domain.MaxRecurrenceInterval của backend. */
    const val MAX_INTERVAL = 365

    fun isRecurring(recurrence: String): Boolean =
        recurrence == Recurrence.DAILY || recurrence == Recurrence.WEEKLY || recurrence == Recurrence.MONTHLY

    /** "Mỗi N" hợp lệ trong [1, MAX_INTERVAL]. */
    fun normalizeInterval(n: Int): Int = n.coerceIn(1, MAX_INTERVAL)

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

    /** Tham số lặp của một task (gom lại cho gọn chữ ký hàm). */
    data class Spec(
        val recurrence: String,
        val days: String = "",
        val interval: Int = 1,
        val mode: String = RecurrenceMode.SCHEDULE,
        val until: Long? = null
    )

    /**
     * Hạn của lần lặp sinh ra khi task (hạn [dueAt]) được hoàn thành lúc [completedAt]; null khi đã qua
     * ngày kết thúc lặp.
     *  - SCHEDULE:   bước tiếp theo sau hạn cũ; nếu vẫn chưa tới [now] thì nhảy tiếp — lần lặp mới luôn ở
     *    tương lai kể cả khi hoàn thành trễ nhiều ngày.
     *  - COMPLETION: lấy NGÀY hoàn thành, GIỜ của hạn cũ, rồi cộng một chu kỳ.
     */
    fun nextDueForCompletion(dueAt: Long, spec: Spec, completedAt: Long, now: Long, zone: ZoneId): Long? {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val from = if (spec.mode == RecurrenceMode.COMPLETION) {
            Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate().atTime(due.toLocalTime()).atZone(zone)
        } else {
            due
        }
        var next = next(from, spec)
        var steps = 0
        while (next.toInstant().toEpochMilli() <= now && steps < MAX_ADVANCE_STEPS) {
            next = next(next, spec)
            steps++
        }
        val millis = next.toInstant().toEpochMilli()
        return if (spec.until != null && millis > spec.until) null else millis
    }

    /**
     * Hạn khi "Bỏ qua lần này": dời đúng MỘT chu kỳ theo lịch (không phụ thuộc chế độ hoàn thành), nhảy tiếp
     * nếu vẫn ở quá khứ. null nếu lần kế tiếp vượt ngày kết thúc lặp.
     */
    fun skipDue(dueAt: Long, spec: Spec, now: Long, zone: ZoneId): Long? =
        nextDueForCompletion(dueAt, spec.copy(mode = RecurrenceMode.SCHEDULE), now, now, zone)

    /**
     * Dời mốc hạn chót đúng một chu kỳ (mỗi interval ngày/tuần/tháng).
     * WEEKLY có chọn thứ: ngày gần nhất sau [from] rơi vào một thứ được chọn; nếu phải sang tuần sau
     * (tuần bắt đầu thứ 2) thì bỏ qua thêm interval-1 tuần — "2 tuần một lần vào T2, T4".
     */
    fun next(from: ZonedDateTime, spec: Spec): ZonedDateTime {
        val interval = normalizeInterval(spec.interval).toLong()
        return when (spec.recurrence) {
            Recurrence.DAILY -> from.plusDays(interval)
            Recurrence.WEEKLY -> {
                val chosen = parseWeekdays(spec.days)
                if (chosen.isEmpty()) {
                    from.plusWeeks(interval)
                } else {
                    val cand = (1..7).map { from.plusDays(it.toLong()) }.first { it.dayOfWeek in chosen }
                    // DayOfWeek.value: thứ 2 = 1 ... CN = 7 → không lớn hơn thứ của from nghĩa là đã sang tuần mới
                    if (cand.dayOfWeek.value <= from.dayOfWeek.value) cand.plusWeeks(interval - 1) else cand
                }
            }
            // plusMonths kẹp ngày vào cuối tháng (31/1 → 28/2), backend làm y như vậy (addMonthsClamped)
            Recurrence.MONTHLY -> from.plusMonths(interval)
            else -> from
        }
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
     * Chỉ là dự báo để người dùng xem trước (tính theo lịch, kể cả với việc lặp theo ngày hoàn thành);
     * task thật của lần kế tiếp chỉ xuất hiện khi hoàn thành lần này.
     */
    fun projectedOccurrences(
        dueAt: Long,
        spec: Spec,
        untilMillis: Long,
        zone: ZoneId,
        maxCount: Int = 400
    ): List<Long> {
        if (!isRecurring(spec.recurrence)) return emptyList()
        val limit = spec.until?.let { minOf(it, untilMillis) } ?: untilMillis
        val result = mutableListOf<Long>()
        var cursor = Instant.ofEpochMilli(dueAt).atZone(zone)
        while (result.size < maxCount) {
            cursor = next(cursor, spec)
            val millis = cursor.toInstant().toEpochMilli()
            if (millis > limit) break
            result += millis
        }
        return result
    }
}

/** Tham số lặp của task. */
fun com.example.todoapplication.domain.model.Task.recurrenceSpec() =
    RecurrenceRules.Spec(recurrence, recurrenceDays, recurrenceInterval, recurrenceMode, recurrenceUntil)
