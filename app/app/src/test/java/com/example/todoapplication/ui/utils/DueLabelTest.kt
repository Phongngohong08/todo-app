package com.example.todoapplication.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DueLabelTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 7, 8) // thứ Tư

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `relative days and all day tasks hide the time`() {
        assertEquals("Hôm nay", formatDueLabel(at(2026, 7, 8, 23, 59), allDay = true, today = today, zone = zone))
        assertEquals("Ngày mai 15:00", formatDueLabel(at(2026, 7, 9, 15), allDay = false, today = today, zone = zone))
        assertEquals("Hôm qua 09:30", formatDueLabel(at(2026, 7, 7, 9, 30), allDay = false, today = today, zone = zone))
        assertEquals("T6 10/07", formatDueLabel(at(2026, 7, 10, 23, 59), allDay = true, today = today, zone = zone))
        assertEquals("CN 12/07", formatDueLabel(at(2026, 7, 12, 23, 59), allDay = true, today = today, zone = zone))
        assertEquals("T2 04/01/2027 08:00", formatDueLabel(at(2027, 1, 4, 8), allDay = false, today = today, zone = zone))
    }

    @Test
    fun `durations and recurrence descriptions`() {
        assertEquals("25 phút", formatDuration(25))
        assertEquals("1 giờ", formatDuration(60))
        assertEquals("1 giờ 30 phút", formatDuration(90))
        assertEquals("Hằng tuần (T2, T5)", recurrenceDescription("WEEKLY", "MON,THU", 1, "SCHEDULE"))
        assertEquals("Mỗi 3 ngày · tính từ lúc xong", recurrenceDescription("DAILY", "", 3, "COMPLETION"))
        assertEquals("Không lặp", recurrenceDescription("NONE", "", 1, "SCHEDULE"))
    }
}
