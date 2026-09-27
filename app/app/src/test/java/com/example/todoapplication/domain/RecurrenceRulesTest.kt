package com.example.todoapplication.domain

import com.example.todoapplication.domain.RecurrenceRules.Spec
import com.example.todoapplication.domain.model.Recurrence
import com.example.todoapplication.domain.model.RecurrenceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cùng các vector với backend/internal/usecase/task/recurrence_test.go — hai bên phải tính giống hệt. */
class RecurrenceRulesTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, vn).toInstant().toEpochMilli()

    /** completed = now, giống các case của Go. */
    private fun next(due: Long, spec: Spec, completed: Long) =
        RecurrenceRules.nextDueForCompletion(due, spec, completed, completed, vn)

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
        assertEquals(at(2026, 7, 9, 8), next(at(2026, 7, 8, 8), Spec(Recurrence.DAILY), at(2026, 7, 8, 7)))
    }

    @Test
    fun `daily overdue several days skips to the future`() {
        assertEquals(at(2026, 7, 8, 8), next(at(2026, 7, 1, 8), Spec(Recurrence.DAILY), at(2026, 7, 8, 7)))
    }

    @Test
    fun `weekly on chosen weekdays goes from Wednesday to Friday`() {
        assertEquals(at(2026, 7, 10, 18), next(at(2026, 7, 8, 18), Spec(Recurrence.WEEKLY, "MON,FRI"), at(2026, 7, 8, 19)))
    }

    @Test
    fun `weekday is computed in the user's timezone`() {
        // 06:00 thứ Hai giờ VN = 23:00 Chủ nhật UTC
        assertEquals(at(2026, 7, 13, 6), next(at(2026, 7, 6, 6), Spec(Recurrence.WEEKLY, "MON"), at(2026, 7, 6, 7)))
    }

    @Test
    fun `monthly clamps to the end of a shorter month`() {
        assertEquals(at(2026, 2, 28, 9), next(at(2026, 1, 31, 9), Spec(Recurrence.MONTHLY), at(2026, 1, 31, 10)))
    }

    @Test
    fun `every 3 days`() {
        assertEquals(at(2026, 7, 11, 8), next(at(2026, 7, 8, 8), Spec(Recurrence.DAILY, interval = 3), at(2026, 7, 8, 7)))
    }

    @Test
    fun `every 2 weeks on Mon and Wed skips a week when wrapping`() {
        val spec = Spec(Recurrence.WEEKLY, "MON,WED", interval = 2)
        assertEquals(at(2026, 7, 20, 18), next(at(2026, 7, 8, 18), spec, at(2026, 7, 8, 19)))
        // Trong cùng tuần thì không bỏ tuần
        assertEquals(at(2026, 7, 22, 18), next(at(2026, 7, 20, 18), spec, at(2026, 7, 20, 19)))
    }

    @Test
    fun `every 2 months`() {
        assertEquals(at(2026, 3, 31, 9), next(at(2026, 1, 31, 9), Spec(Recurrence.MONTHLY, interval = 2), at(2026, 1, 31, 10)))
    }

    @Test
    fun `completion mode counts from the completion day and keeps the due time`() {
        val spec = Spec(Recurrence.DAILY, interval = 3, mode = RecurrenceMode.COMPLETION)
        assertEquals(at(2026, 7, 11, 8), next(at(2026, 7, 1, 8), spec, at(2026, 7, 8, 21)))
    }

    @Test
    fun `no occurrence after the end date`() {
        val spec = Spec(Recurrence.DAILY, until = at(2026, 7, 8, 23, 59))
        assertNull(next(at(2026, 7, 8, 8), spec, at(2026, 7, 8, 7)))
    }

    @Test
    fun `skip moves one step on the schedule even for completion based tasks`() {
        val spec = Spec(Recurrence.WEEKLY, "MON", mode = RecurrenceMode.COMPLETION)
        assertEquals(at(2026, 7, 13, 9), RecurrenceRules.skipDue(at(2026, 7, 6, 9), spec, at(2026, 7, 6, 8), vn))
    }

    @Test
    fun `projected occurrences stop at the horizon, respect the end date and skip non recurring tasks`() {
        val daily = RecurrenceRules.projectedOccurrences(at(2026, 7, 1, 8), Spec(Recurrence.DAILY), at(2026, 7, 5, 23), vn)
        assertEquals(listOf(at(2026, 7, 2, 8), at(2026, 7, 3, 8), at(2026, 7, 4, 8), at(2026, 7, 5, 8)), daily)

        val ending = RecurrenceRules.projectedOccurrences(
            at(2026, 7, 1, 8), Spec(Recurrence.DAILY, until = at(2026, 7, 3, 12)), at(2026, 12, 31, 0), vn
        )
        assertEquals(listOf(at(2026, 7, 2, 8), at(2026, 7, 3, 8)), ending)

        assertTrue(RecurrenceRules.projectedOccurrences(at(2026, 7, 1, 8), Spec(Recurrence.NONE), at(2027, 1, 1, 0), vn).isEmpty())
    }
}
