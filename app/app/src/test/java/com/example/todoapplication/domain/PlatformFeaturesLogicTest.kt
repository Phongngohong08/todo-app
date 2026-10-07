package com.example.todoapplication.domain

import com.example.todoapplication.data.backup.BackupCsv
import com.example.todoapplication.data.calendar.DeviceCalendar
import com.example.todoapplication.data.calendar.DeviceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class PlatformFeaturesLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = LocalDateTime.of(2026, 10, 7, 9, 0)
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    // ── Chụp ảnh tạo việc ──

    @Test
    fun `scanned text is split into clean task lines`() {
        val text = """
            VIỆC CẦN LÀM
            • Gọi cho khách hàng mai 9h
            - [ ] Nộp báo cáo !cao
            1. Mua sữa
            2) Mua sữa
            --
            12/10
            ☐ Đặt vé tàu
        """.trimIndent()
        assertEquals(
            listOf("VIỆC CẦN LÀM", "Gọi cho khách hàng mai 9h", "Nộp báo cáo !cao", "Mua sữa", "Đặt vé tàu"),
            ScanTextParser.taskLines(text)
        )
    }

    // ── Hoãn bằng câu trả lời trên thông báo ──

    @Test
    fun `snooze replies understand relative times and quick add phrases`() {
        assertEquals(millis(now.plusMinutes(30)), SnoozeText.parse("30 phút", now, zone))
        assertEquals(millis(now.plusHours(2)), SnoozeText.parse("2 tiếng nữa", now, zone))
        assertEquals(millis(now.plusHours(1)), SnoozeText.parse("1h", now, zone))
        assertEquals(millis(now.withHour(20).withMinute(0)), SnoozeText.parse("Tối nay", now, zone))
        // Chỉ có ngày → 8 giờ sáng ngày đó
        assertEquals(millis(now.plusDays(1).withHour(8).withMinute(0)), SnoozeText.parse("mai", now, zone))
        assertNull(SnoozeText.parse("không hiểu gì", now, zone))
        assertNull("mốc đã qua", SnoozeText.parse("7h sáng nay", now, zone))
    }

    // ── Xuất CSV ──

    @Test
    fun `csv values are escaped per RFC 4180`() {
        assertEquals("Mua sữa", BackupCsv.escape("Mua sữa"))
        assertEquals("\"a, b\"", BackupCsv.escape("a, b"))
        assertEquals("\"nói \"\"xin chào\"\"\"", BackupCsv.escape("nói \"xin chào\""))
        assertEquals("\"dòng 1\ndòng 2\"", BackupCsv.escape("dòng 1\ndòng 2"))
    }

    // ── Lịch của máy ──

    @Test
    fun `device events are placed on every day they cover`() {
        val meeting = DeviceEvent(1, "Họp", millis(now), millis(now.plusHours(1)), allDay = false, color = 0, calendarName = "")
        assertEquals(listOf(LocalDate.of(2026, 10, 7)), DeviceCalendar.daysOf(meeting, zone))

        // Sự kiện cả ngày lưu theo UTC: 7/10 00:00Z → 9/10 00:00Z = hai ngày 7 và 8
        val trip = DeviceEvent(
            2, "Công tác",
            LocalDate.of(2026, 10, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            LocalDate.of(2026, 10, 9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            allDay = true, color = 0, calendarName = ""
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8)), DeviceCalendar.daysOf(trip, zone))

        val byDay = DeviceCalendar.byDay(listOf(meeting, trip), zone)
        assertEquals(2, byDay["2026-10-07"]?.size)
        assertEquals(1, byDay["2026-10-08"]?.size)
    }
}
