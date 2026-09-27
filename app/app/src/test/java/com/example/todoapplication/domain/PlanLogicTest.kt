package com.example.todoapplication.domain

import com.example.todoapplication.data.model.PlanSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PlanLogicTest {
    private fun slot(start: String, end: String, id: String) = PlanSlot(start, end, id, id)
    private val plan = listOf(
        slot("09:00", "10:00", "a"),
        slot("10:00", "10:30", "b"),
        slot("11:00", "12:00", "c")
    )

    @Test
    fun `moving a slot keeps its length and pushes overlapping slots later`() {
        // Dời "a" (60') sang 10:00 → a 10:00–11:00, b bị dồn 11:00–11:30, c bị dồn 11:30–12:30
        val moved = PlanLogic.moveSlot(plan, 0, LocalTime.of(10, 0))!!
        assertEquals(listOf("a", "b", "c"), moved.map { it.taskId })
        assertEquals(listOf("10:00-11:00", "11:00-11:30", "11:30-12:30"), moved.map { "${it.start}-${it.end}" })
    }

    @Test
    fun `moving earlier reorders without touching later slots`() {
        val moved = PlanLogic.moveSlot(plan, 2, LocalTime.of(7, 0))!!
        assertEquals(listOf("c", "a", "b"), moved.map { it.taskId })
        assertEquals("07:00", moved[0].start)
        assertEquals("09:00", moved[1].start)
    }

    @Test
    fun `moving past the end of the day is rejected`() {
        assertNull(PlanLogic.moveSlot(plan, 2, LocalTime.of(23, 30)))
    }

    @Test
    fun `stale when a new task needs planning or a planned task was deleted`() {
        val ids = setOf("a", "b")
        assertFalse(PlanLogic.isStale(ids, pendingInScope = setOf("a"), existingIds = setOf("a", "b")))
        assertTrue(PlanLogic.isStale(ids, pendingInScope = setOf("a", "new"), existingIds = setOf("a", "b", "new")))
        assertTrue(PlanLogic.isStale(ids, pendingInScope = setOf("a"), existingIds = setOf("a")))
    }
}
