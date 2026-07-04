package com.example.todoapplication.ui.viewmodel

import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.SubtaskRepository
import com.example.todoapplication.data.repository.TaskListResult
import com.example.todoapplication.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Test ViewModel với repository giả lập (Mockito) — trước đây không thể test vì
 * ViewModel gọi thẳng ServiceLocator (singleton static) thay vì nhận qua constructor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var taskRepository: TaskRepository
    private lateinit var subtaskRepository: SubtaskRepository
    private lateinit var aiRepository: AiRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: TaskListViewModel

    private fun task(id: String = "1", status: String = "TODO") = Task(
        id = id,
        userId = "u1",
        title = "Task $id",
        description = null,
        priority = "MEDIUM",
        dueDate = null,
        status = status,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = mock()
        subtaskRepository = mock()
        aiRepository = mock()
        sessionManager = mock()
        whenever(sessionManager.getUserName()).thenReturn("Phong")
        viewModel = TaskListViewModel(taskRepository, subtaskRepository, aiRepository, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `userName falls back to default when session has no name`() {
        val emptySession: SessionManager = mock()
        whenever(emptySession.getUserName()).thenReturn("")
        val vm = TaskListViewModel(taskRepository, subtaskRepository, aiRepository, emptySession)
        assertEquals("bạn", vm.userName)
    }

    @Test
    fun `loadTasks populates state on success`() = runTest(dispatcher) {
        val tasks = listOf(task("1"), task("2", status = "COMPLETED"))
        whenever(taskRepository.loadTasks(null, null)).thenReturn(TaskListResult(tasks, isOffline = false))
        whenever(subtaskRepository.progressByTask()).thenReturn(emptyMap())

        viewModel.loadTasks("ALL", "")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.tasks.size)
        assertFalse(state.isLoading)
        assertFalse(state.isOffline)
    }

    @Test
    fun `loadTasks emits message event and stops loading on failure`() = runTest(dispatcher) {
        whenever(taskRepository.loadTasks(any(), any())).thenThrow(RuntimeException("network down"))

        val events = mutableListOf<TaskListEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.loadTasks("ALL", "")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(events.any { it is TaskListEvent.Message })
        job.cancel()
    }

    @Test
    fun `completeTask calls repository then reloads with last filters`() = runTest(dispatcher) {
        val t = task("1")
        whenever(taskRepository.loadTasks(null, null)).thenReturn(TaskListResult(emptyList(), isOffline = false))
        whenever(subtaskRepository.progressByTask()).thenReturn(emptyMap())

        viewModel.completeTask(t)
        dispatcher.scheduler.advanceUntilIdle()

        verifyBlocking(taskRepository) { completeTask(t) }
        verifyBlocking(taskRepository) { loadTasks(null, null) }
    }

    @Test
    fun `deleteTask emits message only when repository confirms deletion`() = runTest(dispatcher) {
        val t = task("1")
        whenever(taskRepository.deleteTask(t.id)).thenReturn(false)

        val events = mutableListOf<TaskListEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.deleteTask(t)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `logout delegates to sessionManager`() {
        viewModel.logout()
        verify(sessionManager).logout()
    }
}
