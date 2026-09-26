package com.example.todoapplication.ui.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class DateTimeUtilsTest {
    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(originalTz)

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `parses UTC with and without fraction`() {
        assertEquals(millis("2026-05-30T08:00:00Z"), parseIso8601("2026-05-30T08:00:00Z")?.time)
        assertEquals(millis("2026-05-30T08:00:00.123Z"), parseIso8601("2026-05-30T08:00:00.123Z")?.time)
    }

    @Test
    fun `microsecond fraction from Go does not shift the time`() {
        // SimpleDateFormat "SSS" từng đọc 123456 thành 123456 ms (lệch ~2 phút)
        assertEquals(millis("2026-05-30T08:00:00.123456Z"), parseIso8601("2026-05-30T08:00:00.123456Z")?.time)
        assertEquals(millis("2026-05-30T08:00:00.123456789Z"), parseIso8601("2026-05-30T08:00:00.123456789Z")?.time)
    }

    @Test
    fun `respects explicit offset`() {
        assertEquals(millis("2026-05-30T08:00:00Z"), parseIso8601("2026-05-30T15:00:00+07:00")?.time)
        assertEquals(millis("2026-05-30T08:00:00.5Z"), parseIso8601("2026-05-30T15:00:00.5+07:00")?.time)
    }

    @Test
    fun `string without offset is treated as device local time`() {
        // Giờ máy = UTC+7 → 15:00 local = 08:00 UTC
        assertEquals(millis("2026-05-30T08:00:00Z"), parseIso8601("2026-05-30 15:00:00")?.time)
        assertEquals(millis("2026-05-30T08:00:00Z"), parseIso8601("2026-05-30T15:00:00")?.time)
    }

    @Test
    fun `invalid or empty input returns null`() {
        assertNull(parseIso8601(null))
        assertNull(parseIso8601(""))
        assertNull(parseIso8601("not a date"))
    }

    @Test
    fun `formatUtcToLocal converts to device time zone`() {
        assertEquals("2026-05-30 15:00", formatUtcToLocal("2026-05-30T08:00:00.123456Z"))
    }
}
