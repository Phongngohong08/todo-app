package com.example.todoapplication.ui.viewmodel

import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.data.sync.SyncResult
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Test ViewModel với repository giả lập (Mockito). ViewModel nhận mọi phụ thuộc qua constructor
 * (kể cả đồng hồ và dispatcher tính toán) nên chạy tất định trên dispatcher của test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_780_000_000_000L // mốc cố định
    private val hour = 3_600_000L

    private lateinit var taskRepository: TaskRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var sync: SyncController
    private lateinit var aiRepository: AiRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager
    private val tasks = MutableStateFlow<List<Task>>(emptyList())

    private fun task(id: String, status: String = TaskStatus.TODO, dueAt: Long? = null, completedAt: Long? = null) =
        Task(id = id, title = "Task $id", status = status, dueAt = dueAt, completedAt = completedAt)

    private fun viewModel(session: SessionManager = sessionManager) = TaskListViewModel(
        taskRepository, categoryRepository, sync, aiRepository, authRepository, session,
        clock = { now },
        computeDispatcher = dispatcher
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = mock()
        categoryRepository = mock()
        sync = mock()
        aiRepository = mock()
        authRepository = mock()
        sessionManager = mock()
        whenever(sessionManager.getUserName()).thenReturn("Phong")
        whenever(taskRepository.observeTasks(anyOrNull(), anyOrNull())).thenReturn(tasks)
        whenever(taskRepository.observePendingSyncCount()).thenReturn(flowOf(0))
        whenever(categoryRepository.observeAll()).thenReturn(flowOf(CategoryRepository.DEFAULTS))
        whenever(sync.isOnline).thenReturn(flowOf(true))
        whenever(sync.hasSyncedOnce).thenReturn(flowOf(true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** uiState dùng WhileSubscribed → phải có người lắng nghe thì mới tính. */
    private fun TestScope.observe(vm: TaskListViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `userName falls back to default when session has no name`() {
        val emptySession: SessionManager = mock()
        whenever(emptySession.getUserName()).thenReturn("")
        assertEquals("bạn", viewModel(emptySession).userName)
    }

    @Test
    fun `tasks from Room are grouped into sections with counts`() = runTest(dispatcher) {
        val vm = viewModel()
        observe(vm)
        tasks.value = listOf(
            task("overdue", dueAt = now - 2 * hour),
            task("future", dueAt = now + 72 * hour),
            task("done", status = TaskStatus.COMPLETED, completedAt = now)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("overdue"), state.sections.today.map { it.id })
        assertEquals(listOf("future"), state.sections.future.map { it.id })
        assertEquals(listOf("done"), state.sections.completedToday.map { it.id })
        assertEquals(2, state.pendingCount)
        assertEquals(1, state.overdueCount)
    }

    @Test
    fun `search query is debounced before hitting the repository`() = runTest(dispatcher) {
        val vm = viewModel()
        observe(vm)
        vm.setQuery("h")
        vm.setQuery("họp")
        advanceUntilIdle()

        verify(taskRepository).observeTasks(null, "họp")
        verify(taskRepository, never()).observeTasks(null, "h")
    }

    @Test
    fun `completing a task writes to Room and offers undo`() = runTest(dispatcher) {
        whenever(taskRepository.setCompleted("1", true)).thenReturn(true)
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.completeTask(task("1"))
        advanceUntilIdle()

        verifyBlocking(taskRepository) { setCompleted("1", true) }
        assertTrue(events.single() is TaskListEvent.Completed)

        vm.reopenTask("1")
        advanceUntilIdle()
        verifyBlocking(taskRepository) { setCompleted("1", false) }
    }

    @Test
    fun `deleting a task can be undone`() = runTest(dispatcher) {
        whenever(taskRepository.delete("1")).thenReturn(true)
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.deleteTask(task("1"))
        advanceUntilIdle()
        assertEquals(TaskListEvent.Deleted("1", "Task 1"), events.single())

        vm.undoDelete("1")
        advanceUntilIdle()
        verifyBlocking(taskRepository) { restore("1") }
    }

    @Test
    fun `logout goes straight through when nothing is waiting to sync`() = runTest(dispatcher) {
        whenever(taskRepository.pendingSyncCount()).thenReturn(0)
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.requestLogout()
        advanceUntilIdle()

        verify(authRepository).logout()
        assertEquals(TaskListEvent.LoggedOut, events.single())
    }

    @Test
    fun `logout asks for confirmation when unsynced changes cannot be pushed`() = runTest(dispatcher) {
        whenever(taskRepository.pendingSyncCount()).thenReturn(3)
        whenever(sync.syncNow()).thenReturn(SyncResult.NetworkError)
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.requestLogout()
        advanceUntilIdle()

        verifyBlocking(sync) { syncNow() }
        verify(authRepository, never()).logout()
        assertEquals(TaskListEvent.ConfirmLogout(3), events.single())
    }

    @Test
    fun `drag and drop persists only when the finger is lifted`() = runTest(dispatcher) {
        val vm = viewModel()
        observe(vm)
        tasks.value = listOf(task("a"), task("b"), task("c"))
        advanceUntilIdle()

        vm.onDragStart("c")
        vm.onDragMove("c", "a")
        advanceUntilIdle()
        assertEquals(listOf("c", "a", "b"), vm.uiState.value.manualOrder.map { it.id })
        verifyBlocking(taskRepository, never()) { move(anyOrNull(), anyOrNull()) }

        vm.onDragEnd()
        advanceUntilIdle()
        verifyBlocking(taskRepository) { move(listOf("c", "a", "b"), "c") }
    }
}
