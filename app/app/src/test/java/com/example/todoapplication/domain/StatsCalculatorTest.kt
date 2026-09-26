package com.example.todoapplication.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsCalculatorTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 7, 8)

    private fun at(d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(2026, 7, d, h, min, 0, 0, vn).toInstant().toEpochMilli()

    @Test
    fun `weekly buckets by calendar day, not by 24 hour windows`() {
        val times = listOf(
            at(8, 0, 30),   // hôm nay, sáng sớm
            at(7, 23, 50),  // hôm qua, trước nửa đêm vài phút → phải tính là hôm qua
            at(7, 9),
            at(2, 10),      // 6 ngày trước
            at(1, 10)       // 7 ngày trước → ngoài biểu đồ
        )
        assertEquals(listOf(1, 0, 0, 0, 0, 2, 1), StatsCalculator.weekly(times, today, vn))
    }

    @Test
    fun `yearly counts per day in the given year`() {
        val times = listOf(at(8, 9), at(8, 21), at(7, 9))
        assertEquals(mapOf("2026-07-08" to 2, "2026-07-07" to 1), StatsCalculator.yearly(times, 2026, vn))
    }
}
