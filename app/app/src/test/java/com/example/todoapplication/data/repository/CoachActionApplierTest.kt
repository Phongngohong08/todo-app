package com.example.todoapplication.data.repository

import com.example.todoapplication.data.model.CoachAction
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.time.ZoneId
import java.time.ZonedDateTime

class CoachActionApplierTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val now = ZonedDateTime.of(2026, 7, 8, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val repo: TaskRepository = mock()
    private val applier = CoachActionApplier(repo, clock = { now }, zone = { zone })

    @Test
    fun `reschedule all day uses 23-59 local time`() = runTest {
        whenever(repo.reschedule(any(), anyOrNull(), any())).thenReturn(true)
        val msg = applier.apply(
            CoachAction(CoachAction.RESCHEDULE, "Dời", taskIds = listOf("a", "b"), dueDate = "2026-07-13T09:00:00+07:00", allDay = true)
        )
        val expected = ZonedDateTime.of(2026, 7, 13, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        verifyBlocking(repo) { reschedule(listOf("a", "b"), expected, true) }
        assertEquals("Đã dời 2 việc", msg)
    }

    @Test
    fun `add to my day uses today's local date`() = runTest {
        whenever(repo.setMyDay(any(), anyOrNull())).thenReturn(true)
        applier.apply(CoachAction(CoachAction.ADD_TO_MY_DAY, "Thêm", taskIds = listOf("a")))
        verifyBlocking(repo) { setMyDay(listOf("a"), "2026-07-08") }
    }

    @Test
    fun `create task with a timed due date`() = runTest {
        whenever(repo.create(any(), any(), anyOrNull())).thenReturn(Task(id = "n", title = "Đi bộ 15 phút"))
        val msg = applier.apply(
            CoachAction(CoachAction.CREATE_TASK, "Tạo", title = "Đi bộ 15 phút", dueDate = "2026-07-08T18:00:00+07:00", priority = "LOW")
        )
        val due = ZonedDateTime.of(2026, 7, 8, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        verifyBlocking(repo) { create(TaskDraft(title = "Đi bộ 15 phút", priority = "LOW", dueAt = due, dueAllDay = false), emptyList(), null) }
        assertEquals("Đã tạo: Đi bộ 15 phút", msg)
    }

    @Test
    fun `invalid actions are ignored`() = runTest {
        assertNull(applier.apply(CoachAction(CoachAction.RESCHEDULE, "Dời", taskIds = listOf("a"), dueDate = "không phải ngày")))
        assertNull(applier.apply(CoachAction(CoachAction.SET_PRIORITY, "Ưu tiên", taskIds = listOf("a"), priority = "URGENT")))
        assertNull(applier.apply(CoachAction("DELETE_ALL", "Xóa hết", taskIds = listOf("a"))))
        verifyBlocking(repo, never()) { reschedule(any(), anyOrNull(), any()) }
    }
}
