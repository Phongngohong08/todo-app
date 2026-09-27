package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskListLogicTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = ZonedDateTime.of(2026, 7, 8, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun task(
        id: String = "1",
        status: String = TaskStatus.TODO,
        priority: String = "MEDIUM",
        dueAt: Long? = null,
        title: String = "Task",
        completedAt: Long? = null
    ) = Task(id = id, title = title, priority = priority, dueAt = dueAt, status = status, completedAt = completedAt)

    @Test
    fun `isOverdue is false for completed task even with past due date`() {
        assertFalse(task(status = TaskStatus.COMPLETED, dueAt = now - 2 * hour).isOverdue(now))
    }

    @Test
    fun `isOverdue is true when due date is in the past and not completed`() {
        assertTrue(task(dueAt = now - 2 * hour).isOverdue(now))
    }

    @Test
    fun `isOverdue is false when there is no due date`() {
        assertFalse(task(dueAt = null).isOverdue(now))
    }

    @Test
    fun `groupTasks separates overdue, today, upcoming, no date and completed today`() {
        val tasks = listOf(
            task(id = "overdue", dueAt = now - 30 * hour),
            task(id = "tonight", dueAt = now + 10 * hour),       // 20h hôm nay
            task(id = "tomorrow", dueAt = now + 20 * hour),      // 6h sáng mai
            task(id = "no-date"),
            task(id = "done-today", status = TaskStatus.COMPLETED, completedAt = now - hour),
            task(id = "done-yesterday", status = TaskStatus.COMPLETED, completedAt = now - 24 * hour)
        )
        val sections = groupTasks(tasks, "DEFAULT", now, zone)
        assertEquals(listOf("overdue"), sections.overdue.map { it.id })
        assertEquals(listOf("tonight"), sections.today.map { it.id })
        assertEquals(listOf("tomorrow"), sections.upcoming.map { it.id })
        assertEquals(listOf("no-date"), sections.noDate.map { it.id })
        assertEquals(listOf("done-today"), sections.completedToday.map { it.id })
    }

    @Test
    fun `all day task due today is not overdue in the afternoon`() {
        // Tạo lúc 14h, chọn "Hôm nay" → trước đây hạn 9h sáng nên quá hạn ngay lập tức
        val afternoon = ZonedDateTime.of(2026, 7, 8, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val todayAllDay = task(id = "a", dueAt = allDayDueInDays(0, zone, java.time.LocalDate.of(2026, 7, 8)))
            .copy(dueAllDay = true)
        assertFalse(todayAllDay.isOverdue(afternoon))
        assertEquals(listOf("a"), groupTasks(listOf(todayAllDay), "DEFAULT", afternoon, zone).today.map { it.id })
        // Sang ngày hôm sau mới quá hạn
        assertTrue(todayAllDay.isOverdue(afternoon + 24 * hour))
    }

    @Test
    fun `recommendation reason explains why`() {
        assertEquals("Quá hạn 2 ngày · Ưu tiên cao", recommendationReason(task(priority = "HIGH", dueAt = now - 48 * hour), now, zone))
        assertEquals("Hạn trong 5 giờ", recommendationReason(task(priority = "LOW", dueAt = now + 5 * hour), now, zone))
        assertEquals("Hạn ngày mai", recommendationReason(task(priority = "LOW", dueAt = now + 20 * hour), now, zone))
        assertEquals("Ưu tiên cao", recommendationReason(task(priority = "HIGH"), now, zone))
    }

    @Test
    fun `my day suggestions put overdue first and skip tasks already chosen`() {
        val today = java.time.LocalDate.of(2026, 7, 8)
        val tasks = listOf(
            task(id = "later", dueAt = now + 72 * hour),
            task(id = "high-nodate", priority = "HIGH"),
            task(id = "yesterday").copy(myDay = "2026-07-07"),
            task(id = "due-today", dueAt = now + 3 * hour),
            task(id = "overdue", dueAt = now - 5 * hour),
            task(id = "chosen", dueAt = now - 5 * hour).copy(myDay = "2026-07-08"),
            task(id = "done", status = TaskStatus.COMPLETED, dueAt = now - 5 * hour)
        )
        val ids = myDaySuggestions(tasks, today, now, zone).map { it.task.id }
        assertEquals(listOf("overdue", "due-today", "yesterday", "high-nodate"), ids)
    }

    @Test
    fun `smart filters`() {
        assertTrue(SmartFilter.OVERDUE.matches(task(dueAt = now - hour), now))
        assertFalse(SmartFilter.OVERDUE.matches(task(dueAt = now + hour), now))
        assertTrue(SmartFilter.NO_DATE.matches(task(), now))
        assertTrue(SmartFilter.HIGH_PRIORITY.matches(task(priority = "HIGH"), now))
    }

    @Test
    fun `computeAiScore ranks overdue high priority above future low priority`() {
        val urgent = task(priority = "HIGH", dueAt = now - hour)
        val relaxed = task(priority = "LOW", dueAt = now + 30 * 24 * hour)
        assertTrue(computeAiScore(urgent, now) > computeAiScore(relaxed, now))
    }

    @Test
    fun `aiRecommendedIds recommends about one third and excludes completed`() {
        val tasks = (1..6).map { task(id = "$it", priority = if (it <= 2) "HIGH" else "LOW") } +
            task(id = "done", status = TaskStatus.COMPLETED, priority = "HIGH")
        val ids = aiRecommendedIds(tasks, now)
        assertEquals(setOf("1", "2"), ids)
    }

    @Test
    fun `aiRecommendedIds caps at three even with many tasks`() {
        val tasks = (1..30).map { task(id = "$it") }
        assertEquals(3, aiRecommendedIds(tasks, now).size)
    }

    @Test
    fun `aiRecommendedIds returns empty when fewer than three pending tasks`() {
        assertTrue(aiRecommendedIds(listOf(task(id = "1"), task(id = "2")), now).isEmpty())
    }

    @Test
    fun `sortTasks by DUE puts tasks without a due date last`() {
        val sorted = sortTasks(listOf(task(id = "none"), task(id = "late", dueAt = now + 5 * hour), task(id = "soon", dueAt = now + hour)), "DUE")
        assertEquals(listOf("soon", "late", "none"), sorted.map { it.id })
    }

    @Test
    fun `sortTasks by PRIORITY orders HIGH before MEDIUM before LOW`() {
        val sorted = sortTasks(listOf(task(id = "l", priority = "LOW"), task(id = "h", priority = "HIGH"), task(id = "m")), "PRIORITY")
        assertEquals(listOf("h", "m", "l"), sorted.map { it.id })
    }

    @Test
    fun `sortTasks with DEFAULT keeps the manual order from the database`() {
        val tasks = listOf(task(id = "b"), task(id = "a"), task(id = "c"))
        assertEquals(tasks, sortTasks(tasks, "DEFAULT"))
    }

    @Test
    fun `dueAtDayOffset is local time on the target day`() {
        val today = java.time.LocalDate.of(2026, 7, 8)
        val expected = ZonedDateTime.of(2026, 7, 9, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, dueAtDayOffset(1, zone = zone, today = today))
    }

    @Test
    fun `daysUntilSunday counts to the coming Sunday`() {
        assertEquals(4, daysUntilSunday(java.time.LocalDate.of(2026, 7, 8))) // thứ Tư → Chủ nhật 12/7
        assertEquals(0, daysUntilSunday(java.time.LocalDate.of(2026, 7, 12)))
    }
}
