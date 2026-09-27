package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GoalsAndReviewTest {

    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    /** Thứ Tư 08/07/2026. */
    private val today = LocalDate.of(2026, 7, 8)

    private fun at(d: Int, h: Int = 10) = ZonedDateTime.of(2026, 7, d, h, 0, 0, 0, vn).toInstant().toEpochMilli()
    private fun times(vararg dayCounts: Pair<Int, Int>) = dayCounts.flatMap { (d, n) -> List(n) { at(d) } }

    @Test
    fun `streak counts consecutive days meeting the goal and today does not break it yet`() {
        // Đạt 2 việc ngày 5, 6, 7; hôm nay (8) mới 1 việc
        val p = GoalCalculator.progress(times(5 to 2, 6 to 3, 7 to 2, 8 to 1), goal = 2, daysOff = emptySet(), today = today, zone = vn)
        assertEquals(3, p.currentStreak)
        assertEquals(1, p.doneToday)
        assertFalse(p.metToday)
        assertEquals(1, p.remainingToday)
    }

    @Test
    fun `meeting the goal today extends the streak`() {
        val p = GoalCalculator.progress(times(7 to 2, 8 to 2), goal = 2, daysOff = emptySet(), today = today, zone = vn)
        assertEquals(2, p.currentStreak)
        assertTrue(p.metToday)
    }

    @Test
    fun `days off do not break the streak`() {
        // 4/7 = thứ Bảy, 5/7 = Chủ nhật (nghỉ, không làm gì) — chuỗi 3 (T6) ... vẫn nối sang T2, T3
        val p = GoalCalculator.progress(
            times(3 to 1, 6 to 1, 7 to 1), goal = 1,
            daysOff = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), today = today, zone = vn
        )
        assertEquals(3, p.currentStreak)
        // Không có ngày nghỉ thì đứt ở Chủ nhật
        val strict = GoalCalculator.progress(times(3 to 1, 6 to 1, 7 to 1), goal = 1, daysOff = emptySet(), today = today, zone = vn)
        assertEquals(2, strict.currentStreak)
    }

    @Test
    fun `best streak remembers a longer run in the past`() {
        val p = GoalCalculator.progress(times(1 to 1, 2 to 1, 3 to 1, 4 to 1, 7 to 1), goal = 1, daysOff = emptySet(), today = today, zone = vn)
        assertEquals(1, p.currentStreak)
        assertEquals(4, p.bestStreak)
    }

    private fun done(id: String, completedDay: Int, category: String = "WORK", dueDay: Int? = null) = Task(
        id = id, title = id, status = TaskStatus.COMPLETED, category = category,
        completedAt = at(completedDay), dueAt = dueDay?.let { at(it, 9) }
    )

    @Test
    fun `weekly review compares with the previous week and finds the best day`() {
        val completed = listOf(
            done("a", 8), done("b", 8), done("c", 6, "PERSONAL"),
            done("late", 5, dueDay = 4),
            done("prev1", 1), done("prev2", 1) // cả hai thuộc tuần trước (1/7)
        )
        val pending = listOf(
            Task(id = "o", title = "o", dueAt = at(7), category = "PERSONAL"),
            Task(id = "p", title = "p", category = "PERSONAL"),
            Task(id = "w", title = "w", category = "WORK")
        )
        val r = WeeklyReviewCalculator.compute(completed, pending, today, now = at(8, 12), zone = vn, goal = 2)
        assertEquals(LocalDate.of(2026, 7, 2), r.from)
        assertEquals(4, r.completed)
        assertEquals(2, r.completedPreviousWeek)
        assertEquals(100, r.changePercent)
        assertEquals(today, r.bestDay)
        assertEquals(2, r.bestDayCount)
        assertEquals(1, r.completedLate)
        assertEquals(1, r.goalDaysMet)
        assertEquals(1, r.overdueNow)
        assertEquals("PERSONAL", r.mostNeglectedCategory)
        assertEquals(mapOf("WORK" to 3, "PERSONAL" to 1), r.byCategory)
        assertTrue(WeeklyReviewCalculator.coachPrompt(r) { it }.contains("hoàn thành 4 việc (+100% so với tuần trước)"))
    }
}
