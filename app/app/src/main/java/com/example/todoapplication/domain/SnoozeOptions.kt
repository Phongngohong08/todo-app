package com.example.todoapplication.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/** Các mốc "Hoãn…" cho thông báo nhắc việc — chỉ đưa ra mốc còn ý nghĩa với giờ hiện tại. */
object SnoozeOptions {
    data class Option(val label: String, val atMillis: Long)

    private val EVENING = LocalTime.of(20, 0)
    private val MORNING = LocalTime.of(8, 0)

    fun options(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<Option> {
        fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        val list = mutableListOf(
            Option(str(R.string.snooze_15_min), millis(now.plusMinutes(15))),
            Option(str(R.string.snooze_1_hour), millis(now.plusHours(1))),
            Option(str(R.string.snooze_3_hours), millis(now.plusHours(3)))
        )
        // "Tối nay" chỉ khi còn cách 20:00 ít nhất 1 tiếng (không thì trùng "1 giờ nữa")
        if (now.toLocalTime().isBefore(EVENING.minusHours(1))) {
            list += Option(str(R.string.snooze_tonight), millis(now.toLocalDate().atTime(EVENING)))
        }
        list += Option(str(R.string.snooze_tomorrow_morning), millis(now.toLocalDate().plusDays(1).atTime(MORNING)))
        // Cuối tuần → gợi ý dời sang đầu tuần làm việc
        if (now.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            val monday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            list += Option(str(R.string.snooze_next_monday), millis(monday.atTime(MORNING)))
        }
        return list
    }
}

/**
 * Hiểu câu trả lời gõ ngay trên thông báo (RemoteInput) để hoãn nhắc việc:
 * "30 phút", "2 tiếng", "1h", "tối nay", "sáng mai", "thứ 2 9h", "15/10 8h"... và tiếng Anh: "in 30 min", "2 hours", "tomorrow 9am".
 * Số + đơn vị → tính từ bây giờ; còn lại dùng bộ phân tích tạo nhanh. Mốc đã qua / không hiểu → null.
 */
object SnoozeText {
    private val RELATIVE = Regex("""^\s*(?:in\s+)?(\d{1,3})\s*(p|ph|phut|m|mins?|minutes?|gio|tieng|h|g|hrs?|hours?)\s*(nua|later)?\s*$""")

    fun parse(text: String, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val plain = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "").replace('đ', 'd')
        RELATIVE.find(plain)?.let { m ->
            val n = m.groupValues[1].toLong()
            if (n <= 0) return null
            val minutes = m.groupValues[2].let { it.startsWith("p") || it.startsWith("m") }
            val at = if (minutes) now.plusMinutes(n) else now.plusHours(n)
            return at.atZone(zone).toInstant().toEpochMilli()
        }
        val parsed = QuickAddParser.parse(text, now, zone)
        val due = parsed.dueAt ?: return null
        // Chỉ có ngày ("mai", "thứ 2") → 8 giờ sáng ngày đó
        val at = if (parsed.allDay) {
            java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
        } else due
        return at.takeIf { it > now.atZone(zone).toInstant().toEpochMilli() }
    }
}
