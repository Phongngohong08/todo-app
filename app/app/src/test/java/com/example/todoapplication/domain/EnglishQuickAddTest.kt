package com.example.todoapplication.domain

import com.example.todoapplication.domain.QuickAddParser.CategoryOption
import com.example.todoapplication.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Thêm nhanh bằng tiếng Anh (hiểu được bất kể ngôn ngữ app) — câu tiếng Việt vẫn do QuickAddParserTest giữ. */
class EnglishQuickAddTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    /** Thứ Tư 08/07/2026, 10:00. */
    private val now = LocalDateTime.of(2026, 7, 8, 10, 0)
    private val categories = listOf(CategoryOption("PERSONAL", "Cá nhân"), CategoryOption("WORK", "Công việc"), CategoryOption("OTHER", "Hộp thư đến"))

    private fun parse(text: String) = QuickAddParser.parse(text, now, zone, categories)
    private fun at(m: Int, d: Int, h: Int, min: Int = 0, y: Int = 2026) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `english sentence with date, pm time, priority and built-in list name`() {
        val r = parse("Submit report tomorrow 3pm !high #Work")
        assertEquals("Submit report", r.title)
        assertEquals(at(7, 9, 15), r.dueAt)
        assertFalse(r.allDay)
        assertEquals("HIGH", r.priority)
        assertEquals("WORK", r.category) // "Work" khớp danh sách WORK dù nhãn đang là "Công việc"
    }

    @Test
    fun `weekdays, next week and relative days`() {
        assertEquals(at(7, 10, 23, 59), parse("Call mom on friday").dueAt)
        assertEquals("Call mom", parse("Call mom on friday").title)
        assertEquals(at(7, 17, 23, 59), parse("Call mom next friday").dueAt)
        assertEquals(at(7, 11, 23, 59), parse("Pay rent in 3 days").dueAt)
        assertEquals(at(7, 10, 23, 59), parse("Gym day after tomorrow").dueAt)
        assertEquals(at(7, 13, 23, 59), parse("Plan sprint next week").dueAt)
    }

    @Test
    fun `parts of the day, clock times and month names`() {
        assertEquals(at(7, 8, 20), parse("Read tonight").dueAt)
        assertEquals(at(7, 9, 9), parse("Gym tomorrow morning").dueAt)
        assertEquals(at(7, 8, 12), parse("Lunch with Lan at noon").dueAt)
        assertEquals(at(7, 8, 21, 30), parse("Call back 9:30 pm").dueAt)
        assertEquals(at(10, 15, 23, 59), parse("Dentist oct 15").dueAt)
        assertEquals(at(10, 15, 23, 59), parse("Dentist 10/15").dueAt) // kiểu Mỹ khi dd/mm không hợp lệ
        assertEquals(at(5, 5, 23, 59, y = 2027), parse("Renew passport May 5").dueAt)
        // "máy 5" (tiếng Việt) không phải tháng 5
        assertNull(parse("Sửa máy 5").dueAt)
    }

    @Test
    fun `recurrence in english`() {
        val r = parse("Water plants every monday and thursday")
        assertEquals("Water plants", r.title)
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals("MON,THU", r.recurrenceDays)
        assertEquals(Recurrence.DAILY, parse("Stretch daily").recurrence)
        assertEquals(Recurrence.MONTHLY, parse("Pay bills every month").recurrence)
    }

    @Test
    fun `vietnamese words that look like english abbreviations stay in the title`() {
        val r = parse("Thu tiền nhà")
        assertNull(r.dueAt)
        assertEquals("Thu tiền nhà", r.title)
        assertTrue(parse("Mua món quà").title.contains("món"))
    }

    @Test
    fun `snooze replies in english`() {
        fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        assertEquals(millis(now.plusMinutes(30)), SnoozeText.parse("in 30 min", now, zone))
        assertEquals(millis(now.plusHours(2)), SnoozeText.parse("2 hours", now, zone))
        assertEquals(millis(now.withHour(20)), SnoozeText.parse("Tonight", now, zone))
        assertEquals(millis(now.plusDays(1).withHour(9)), SnoozeText.parse("Tomorrow morning", now, zone))
        assertEquals(millis(now.plusDays(1).withHour(9)), SnoozeText.parse("tomorrow 9am", now, zone))
    }
}
