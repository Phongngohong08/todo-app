package com.example.todoapplication.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SnoozeOptionsTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `morning on a weekday offers tonight but not next Monday`() {
        val labels = SnoozeOptions.options(LocalDateTime.of(2026, 7, 8, 10, 0), zone).map { it.label }
        assertEquals(listOf("15 phút nữa", "1 giờ nữa", "3 giờ nữa", "Tối nay 20:00", "Sáng mai 08:00"), labels)
    }

    @Test
    fun `late friday evening skips tonight and offers next Monday`() {
        val labels = SnoozeOptions.options(LocalDateTime.of(2026, 7, 10, 19, 30), zone).map { it.label }
        assertEquals(listOf("15 phút nữa", "1 giờ nữa", "3 giờ nữa", "Sáng mai 08:00", "Thứ 2 tuần sau 08:00"), labels)
    }
}
