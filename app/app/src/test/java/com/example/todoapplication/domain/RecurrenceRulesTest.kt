package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cùng các trường hợp với backend/internal/usecase/task/recurrence_test.go — hai bên phải tính giống hệt. */
class RecurrenceRulesTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, vn).toInstant().toEpochMilli()

    @Test
    fun `next occurrence id matches the Go backend`() {
        // Vector tính độc lập: MD5(namespace + "1111...") + bit version 3 (xem recurrence_test.go)
        assertEquals(
            "aeef035e-c62f-36af-8237-e4b2b4d9a73e",
            RecurrenceRules.nextOccurrenceId("11111111-1111-4111-8111-111111111111")
        )
    }

    @Test
    fun `daily completed on time moves to the next day`() {
        val next = RecurrenceRules.nextDueAfter(at(2026, 7, 8, 8), Recurrence.DAILY, "", at(2026, 7, 8, 7), vn)
        assertEquals(at(2026, 7, 9, 8), next)
    }

    @Test
    fun `daily overdue several days skips to the future`() {
        val next = RecurrenceRules.nextDueAfter(at(2026, 7, 1, 8), Recurrence.DAILY, "", at(2026, 7, 8, 7), vn)
        assertEquals(at(2026, 7, 8, 8), next)
    }

    @Test
    fun `weekly on chosen weekdays goes from Wednesday to Friday`() {
        val next = RecurrenceRules.nextDueAfter(at(2026, 7, 8, 18), Recurrence.WEEKLY, "MON,FRI", at(2026, 7, 8, 19), vn)
        assertEquals(at(2026, 7, 10, 18), next)
    }

    @Test
    fun `weekday is computed in the user's timezone`() {
        // 06:00 thứ Hai giờ VN = 23:00 Chủ nhật UTC
        val next = RecurrenceRules.nextDueAfter(at(2026, 7, 6, 6), Recurrence.WEEKLY, "MON", at(2026, 7, 6, 7), vn)
        assertEquals(at(2026, 7, 13, 6), next)
    }

    @Test
    fun `monthly clamps to the end of a shorter month`() {
        val next = RecurrenceRules.nextDueAfter(at(2026, 1, 31, 9), Recurrence.MONTHLY, "", at(2026, 1, 31, 10), vn)
        assertEquals(at(2026, 2, 28, 9), next)
    }

    @Test
    fun `projected occurrences stop at the horizon and skip non recurring tasks`() {
        val daily = RecurrenceRules.projectedOccurrences(at(2026, 7, 1, 8), Recurrence.DAILY, "", at(2026, 7, 5, 23), vn)
        assertEquals(listOf(at(2026, 7, 2, 8), at(2026, 7, 3, 8), at(2026, 7, 4, 8), at(2026, 7, 5, 8)), daily)
        assertTrue(RecurrenceRules.projectedOccurrences(at(2026, 7, 1, 8), Recurrence.NONE, "", at(2027, 1, 1, 0), vn).isEmpty())
    }
}
