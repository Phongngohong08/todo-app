package com.example.todoapplication.domain

import com.example.todoapplication.domain.Eisenhower.Quadrant
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulingTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    /** Thứ Tư 08/07/2026. */
    private val today = LocalDate.of(2026, 7, 8)

    private fun at(m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(2026, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    // ── Giờ yên tĩnh ──

    @Test
    fun `quiet hours across midnight defer to the morning`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertEquals(LocalDateTime.of(2026, 7, 9, 7, 0), QuietHours.deferUntil(LocalDateTime.of(2026, 7, 8, 23, 30), start, end))
        assertEquals(LocalDateTime.of(2026, 7, 8, 7, 0), QuietHours.deferUntil(LocalDateTime.of(2026, 7, 8, 5, 0), start, end))
        assertNull(QuietHours.deferUntil(LocalDateTime.of(2026, 7, 8, 12, 0), start, end))
        // Mốc kết thúc không còn là giờ yên tĩnh
        assertNull(QuietHours.deferUntil(LocalDateTime.of(2026, 7, 8, 7, 0), start, end))
    }

    @Test
    fun `quiet hours within a day and empty range`() {
        assertTrue(QuietHours.isQuiet(LocalTime.of(13, 30), LocalTime.of(13, 0), LocalTime.of(14, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime.of(14, 0), LocalTime.of(13, 0), LocalTime.of(14, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime.of(3, 0), LocalTime.of(8, 0), LocalTime.of(8, 0)))
    }

    // ── Dời ngày ──

    @Test
    fun `moving keeps the time of day, all day stays all day`() {
        val target = LocalDate.of(2026, 7, 10)
        assertEquals(at(7, 10, 15, 30) to false, Reschedule.moveToDate(at(7, 8, 15, 30), false, target, zone))
        assertEquals(at(7, 10, 23, 59) to true, Reschedule.moveToDate(at(7, 8, 23, 59), true, target, zone))
        assertEquals(at(7, 10, 23, 59) to true, Reschedule.moveToDate(null, false, target, zone))
    }

    @Test
    fun `postpone options skip duplicates and past days`() {
        // Thứ Tư: Ngày mai (T5), Cuối tuần (T7), Thứ 2 tuần sau, 1 tuần nữa (T4 sau)
        assertEquals(
            listOf("Ngày mai", "Cuối tuần", "Thứ 2 tuần sau", "1 tuần nữa"),
            Reschedule.postponeOptions(today).map { it.label }
        )
        // Thứ Sáu: "Ngày mai" và "Cuối tuần" cùng là thứ Bảy → chỉ giữ một
        val friday = LocalDate.of(2026, 7, 10)
        assertEquals(listOf("Ngày mai", "Thứ 2 tuần sau", "1 tuần nữa"), Reschedule.postponeOptions(friday).map { it.label })
    }

    // ── Eisenhower ──

    private fun task(id: String, priority: String = "MEDIUM", dueAt: Long? = null, allDay: Boolean = false, done: Boolean = false) =
        Task(id = id, title = id, priority = priority, dueAt = dueAt, dueAllDay = allDay,
            status = if (done) TaskStatus.COMPLETED else TaskStatus.TODO)

    @Test
    fun `tasks fall into the right quadrant`() {
        val groups = Eisenhower.group(
            listOf(
                task("high-overdue", "HIGH", at(7, 7, 9)),
                task("high-tomorrow", "HIGH", at(7, 9, 23, 59), allDay = true),
                task("high-nextweek", "HIGH", at(7, 15, 9)),
                task("low-today", "LOW", at(7, 8, 18)),
                task("medium-nodate"),
                task("done", "HIGH", at(7, 8, 9), done = true)
            ),
            today, zone
        )
        assertEquals(listOf("high-overdue", "high-tomorrow"), groups[Quadrant.DO_FIRST]!!.map { it.id })
        assertEquals(listOf("high-nextweek"), groups[Quadrant.SCHEDULE]!!.map { it.id })
        assertEquals(listOf("low-today"), groups[Quadrant.QUICK]!!.map { it.id })
        assertEquals(listOf("medium-nodate"), groups[Quadrant.LATER]!!.map { it.id })
    }

    @Test
    fun `dragging into do first raises priority and sets today keeping the time`() {
        val change = Eisenhower.move(task("t", "LOW", at(7, 20, 15)), Quadrant.DO_FIRST, today, zone)
        assertEquals("HIGH", change.priority)
        assertEquals(at(7, 8, 15), change.dueAt)
        assertEquals(false, change.allDay)
    }

    @Test
    fun `dragging out of urgent pushes the due date a week and lowering importance drops to medium`() {
        val change = Eisenhower.move(task("t", "HIGH", at(7, 8, 23, 59), allDay = true), Quadrant.LATER, today, zone)
        assertEquals("MEDIUM", change.priority)
        assertEquals(at(7, 15, 23, 59), change.dueAt)
        assertEquals(true, change.allDay)
    }

    @Test
    fun `dragging within the same quadrant changes nothing`() {
        assertTrue(Eisenhower.move(task("t"), Quadrant.LATER, today, zone).isEmpty)
    }
}
