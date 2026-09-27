package com.example.todoapplication.domain

import com.example.todoapplication.domain.QuickAddParser.CategoryOption
import com.example.todoapplication.domain.QuickAddParser.Kind
import com.example.todoapplication.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickAddParserTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    /** Thứ Tư 08/07/2026, 10:00. */
    private val now = LocalDateTime.of(2026, 7, 8, 10, 0)
    private val categories = listOf(
        CategoryOption("PERSONAL", "Cá nhân"),
        CategoryOption("WORK", "Công việc"),
        CategoryOption("OTHER", "Khác"),
        CategoryOption("Học tiếng Anh", "Học tiếng Anh")
    )

    private fun parse(text: String) = QuickAddParser.parse(text, now, zone, categories)
    private fun at(m: Int, d: Int, h: Int, min: Int = 0, y: Int = 2026) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `full sentence with date, afternoon time, priority and category`() {
        val r = parse("Nộp báo cáo mai 3h chiều !cao #Công việc")
        assertEquals("Nộp báo cáo", r.title)
        assertEquals(at(7, 9, 15), r.dueAt)
        assertFalse(r.allDay)
        assertEquals("HIGH", r.priority)
        assertEquals("WORK", r.category)
        assertEquals(listOf(Kind.DATE, Kind.TIME, Kind.PRIORITY, Kind.CATEGORY), r.tokens.map { it.kind })
    }

    @Test
    fun `works without Vietnamese diacritics`() {
        val r = parse("nop bao cao mai 3h chieu")
        assertEquals("nop bao cao", r.title)
        assertEquals(at(7, 9, 15), r.dueAt)
    }

    @Test
    fun `date without time is all day`() {
        val r = parse("Gửi hồ sơ 15/10")
        assertEquals("Gửi hồ sơ", r.title)
        assertTrue(r.allDay)
        assertEquals(at(10, 15, 23, 59), r.dueAt)
    }

    @Test
    fun `past day-month without year means next year`() {
        assertEquals(at(1, 5, 23, 59, y = 2027), parse("Thanh toán 5/1").dueAt)
    }

    @Test
    fun `weekday next week with connector word and half hour`() {
        val r = parse("Họp team thứ 6 tuần sau lúc 9h30 sáng")
        assertEquals("Họp team", r.title)
        assertEquals(at(7, 17, 9, 30), r.dueAt)
    }

    @Test
    fun `weekday means the coming one and connector is removed`() {
        val r = parse("Họp vào thứ 6")
        assertEquals("Họp", r.title)
        assertEquals(at(7, 10, 23, 59), r.dueAt)
    }

    @Test
    fun `part of day with today`() {
        val r = parse("Đi chợ tối nay")
        assertEquals("Đi chợ", r.title)
        assertEquals(at(7, 8, 20), r.dueAt)
    }

    @Test
    fun `time only already passed today goes to tomorrow`() {
        val r = parse("Uống thuốc 7h sáng")
        assertEquals(at(7, 9, 7), r.dueAt)
    }

    @Test
    fun `bare small hour is read as afternoon`() {
        assertEquals(at(7, 8, 15), parse("Gọi khách 3h").dueAt)
    }

    @Test
    fun `in n days and p2 priority`() {
        val r = parse("Viết báo cáo 3 ngày nữa p2")
        assertEquals("Viết báo cáo", r.title)
        assertEquals(at(7, 11, 23, 59), r.dueAt)
        assertEquals("MEDIUM", r.priority)
    }

    @Test
    fun `weekly on chosen days starts at the nearest chosen day`() {
        val r = parse("Tưới cây mỗi thứ 2 và thứ 5")
        assertEquals("Tưới cây", r.title)
        assertEquals(Recurrence.WEEKLY, r.recurrence)
        assertEquals("MON,THU", r.recurrenceDays)
        assertEquals(at(7, 9, 23, 59), r.dueAt) // thứ Năm gần nhất
        assertTrue(r.allDay)
    }

    @Test
    fun `daily recurrence with time`() {
        val r = parse("Tập gym hằng ngày 18h")
        assertEquals("Tập gym", r.title)
        assertEquals(Recurrence.DAILY, r.recurrence)
        assertEquals(at(7, 8, 18), r.dueAt)
    }

    @Test
    fun `capitalised Mai in the middle is a name, not tomorrow`() {
        val r = parse("Gọi cho Mai")
        assertEquals("Gọi cho Mai", r.title)
        assertNull(r.dueAt)
        assertFalse(r.hasAnything)
    }

    @Test
    fun `category label with spaces and unknown category becomes a new one`() {
        assertEquals("Học tiếng Anh", parse("Ôn từ vựng #học tiếng anh").category)
        val r = parse("Mua quà #SinhNhật")
        assertEquals("SinhNhật", r.category)
        assertEquals("Mua quà", r.title)
    }

    @Test
    fun `plain text is left alone`() {
        val r = parse("Đọc sách")
        assertEquals("Đọc sách", r.title)
        assertNull(r.dueAt)
        assertNull(r.priority)
        assertNull(r.category)
    }

    @Test
    fun `normalize keeps length so token ranges map onto the original text`() {
        val s = "Đi chợ tối nay ư"
        assertEquals(s.length, QuickAddParser.normalize(s).length)
        assertEquals("di cho toi nay u", QuickAddParser.normalize(s))
    }
}
