package com.example.todoapplication.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Các mốc "Hoãn…" cho thông báo nhắc việc — chỉ đưa ra mốc còn ý nghĩa với giờ hiện tại. */
object SnoozeOptions {
    data class Option(val label: String, val atMillis: Long)

    private val EVENING = LocalTime.of(20, 0)
    private val MORNING = LocalTime.of(8, 0)

    fun options(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<Option> {
        fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        val list = mutableListOf(
            Option("15 phút nữa", millis(now.plusMinutes(15))),
            Option("1 giờ nữa", millis(now.plusHours(1))),
            Option("3 giờ nữa", millis(now.plusHours(3)))
        )
        // "Tối nay" chỉ khi còn cách 20:00 ít nhất 1 tiếng (không thì trùng "1 giờ nữa")
        if (now.toLocalTime().isBefore(EVENING.minusHours(1))) {
            list += Option("Tối nay 20:00", millis(now.toLocalDate().atTime(EVENING)))
        }
        list += Option("Sáng mai 08:00", millis(now.toLocalDate().plusDays(1).atTime(MORNING)))
        // Cuối tuần → gợi ý dời sang đầu tuần làm việc
        if (now.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            val monday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            list += Option("Thứ 2 tuần sau 08:00", millis(monday.atTime(MORNING)))
        }
        return list
    }
}
