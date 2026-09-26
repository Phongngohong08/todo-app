package com.example.todoapplication.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortOrderTest {

    @Test
    fun `value between two neighbours`() {
        assertEquals(1.5, SortOrder.between(1.0, 2.0)!!, 0.0)
    }

    @Test
    fun `dropping at the top or bottom of the list`() {
        assertEquals(0.0, SortOrder.between(null, 1.0)!!, 0.0)
        assertEquals(3.0, SortOrder.between(2.0, null)!!, 0.0)
        assertEquals(0.0, SortOrder.between(null, null)!!, 0.0)
    }

    @Test
    fun `new tasks go above older ones`() {
        assertTrue(SortOrder.forNewTask(2_000) < SortOrder.forNewTask(1_000))
    }

    @Test
    fun `returns null when precision is exhausted so the caller rebalances`() {
        val a = 1.0
        val b = Math.nextUp(a) // hai số Double liền kề, không còn số nào ở giữa
        assertNull(SortOrder.between(a, b))
    }

    @Test
    fun `many moves between the same neighbours stay strictly ordered`() {
        var low = 0.0
        val high = 1.0
        repeat(40) {
            val mid = SortOrder.between(low, high)!!
            assertTrue(mid > low && mid < high)
            low = mid
        }
    }
}
