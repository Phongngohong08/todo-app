package com.example.todoapplication.domain

import com.example.todoapplication.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TaskListLogicTest {

    private fun isoAt(offsetHours: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(System.currentTimeMillis() + offsetHours * 3_600_000L))
    }

    private fun task(
        id: String = "1",
        status: String = "TODO",
        priority: String = "MEDIUM",
        dueDate: String? = null,
        title: String = "Task",
        updatedAt: String = isoAt(0)
    ) = Task(
        id = id,
        userId = "u1",
        title = title,
        description = null,
        priority = priority,
        dueDate = dueDate,
        status = status,
        updatedAt = updatedAt,
        createdAt = updatedAt
    )

    @Test
    fun `isOverdue is false for completed task even with past due date`() {
        val t = task(status = "COMPLETED", dueDate = isoAt(-5))
        assertFalse(t.isOverdue())
    }

    @Test
    fun `isOverdue is true when due date is in the past and not completed`() {
        val t = task(status = "TODO", dueDate = isoAt(-5))
        assertTrue(t.isOverdue())
    }

    @Test
    fun `isOverdue is false when there is no due date`() {
        val t = task(dueDate = null)
        assertFalse(t.isOverdue())
    }

    @Test
    fun `computeAiScore ranks overdue high priority above future low priority`() {
        val overdueHigh = task(priority = "HIGH", dueDate = isoAt(-1))
        val futureLow = task(priority = "LOW", dueDate = isoAt(24 * 30))
        assertTrue(computeAiScore(overdueHigh) > computeAiScore(futureLow))
    }

    @Test
    fun `aiRecommendedIds recommends about one third and excludes completed`() {
        // 9 việc đang chờ -> 9/3 = 3 khuyến nghị; task đã hoàn thành không tính.
        val pending = (1..9).map { task(id = "p$it", priority = "HIGH", dueDate = isoAt(-it)) }
        val completed = task(id = "completed", status = "COMPLETED", priority = "HIGH", dueDate = isoAt(-100))
        val ids = aiRecommendedIds(pending + completed)
        assertEquals(3, ids.size)
        assertFalse(ids.contains("completed"))
    }

    @Test
    fun `aiRecommendedIds caps at three even with many tasks`() {
        val pending = (1..30).map { task(id = "p$it", priority = "HIGH", dueDate = isoAt(-it)) }
        assertEquals(3, aiRecommendedIds(pending).size)
    }

    @Test
    fun `aiRecommendedIds highlights only one of three tasks`() {
        val tasks = listOf(
            task(id = "a", priority = "HIGH", dueDate = isoAt(-1)),
            task(id = "b", priority = "MEDIUM", dueDate = isoAt(10)),
            task(id = "c", priority = "LOW", dueDate = isoAt(100))
        )
        val ids = aiRecommendedIds(tasks)
        assertEquals(1, ids.size)
        assertEquals(setOf("a"), ids) // việc quá hạn, ưu tiên cao nhất
    }

    @Test
    fun `aiRecommendedIds returns empty when fewer than three pending tasks`() {
        val tasks = listOf(
            task(id = "a", priority = "HIGH", dueDate = isoAt(-1)),
            task(id = "b", priority = "HIGH", dueDate = isoAt(-2))
        )
        assertTrue(aiRecommendedIds(tasks).isEmpty())
    }

    @Test
    fun `sortTasks by DUE puts tasks without a due date last`() {
        val withDue = task(id = "with", dueDate = isoAt(1))
        val withoutDue = task(id = "without", dueDate = null)
        val sorted = sortTasks(listOf(withoutDue, withDue), "DUE")
        assertEquals("with", sorted.first().id)
    }

    @Test
    fun `sortTasks by PRIORITY orders HIGH before MEDIUM before LOW`() {
        val low = task(id = "low", priority = "LOW")
        val high = task(id = "high", priority = "HIGH")
        val medium = task(id = "medium", priority = "MEDIUM")
        val sorted = sortTasks(listOf(low, medium, high), "PRIORITY")
        assertEquals(listOf("high", "medium", "low"), sorted.map { it.id })
    }

    @Test
    fun `sortTasks with DEFAULT keeps original order`() {
        val a = task(id = "a")
        val b = task(id = "b")
        val sorted = sortTasks(listOf(b, a), "DEFAULT")
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }
}
