package com.example.todoapplication.ui.viewmodel

import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.domain.SmartFilter
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.screens.QuickCreateResult
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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
    private val zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh")

    private lateinit var taskRepository: TaskRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var sync: SyncController
    private lateinit var aiRepository: AiRepository
    private lateinit var sessionManager: SessionManager
    private val tasks = MutableStateFlow<List<Task>>(emptyList())

    private fun task(id: String, status: String = TaskStatus.TODO, dueAt: Long? = null, completedAt: Long? = null) =
        Task(id = id, title = "Task $id", status = status, dueAt = dueAt, completedAt = completedAt)

    private fun viewModel(session: SessionManager = sessionManager) = TaskListViewModel(
        taskRepository, categoryRepository, sync, aiRepository, session,
        clock = { now },
        zone = { zone },
        computeDispatcher = dispatcher
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = mock()
        categoryRepository = mock()
        sync = mock()
        aiRepository = mock()
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
        assertEquals(listOf("overdue"), state.sections.overdue.map { it.id })
        assertEquals(listOf("future"), state.sections.upcoming.map { it.id })
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

    @Test
    fun `smart filter narrows the list to overdue tasks`() = runTest(dispatcher) {
        val vm = viewModel()
        observe(vm)
        tasks.value = listOf(task("late", dueAt = now - hour), task("later", dueAt = now + 72 * hour), task("nodate"))
        vm.setSmartFilter(SmartFilter.OVERDUE)
        advanceUntilIdle()

        assertEquals(listOf("late"), vm.uiState.value.sections.overdue.map { it.id })
        assertTrue(vm.uiState.value.sections.upcoming.isEmpty())
        assertTrue(vm.uiState.value.hasFilter)
    }

    @Test
    fun `reschedule all overdue moves them to today as all day and can be undone`() = runTest(dispatcher) {
        val a = task("a", dueAt = now - 30 * hour)
        val b = task("b", dueAt = now - 2 * hour)
        val endOfToday = com.example.todoapplication.domain.allDayDueInDays(
            0, zone, java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        )
        whenever(taskRepository.reschedule(listOf("a", "b"), endOfToday, true)).thenReturn(true)
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        // Collector ở foreground: advanceUntilIdle() không chạy việc của backgroundScope khi hết việc foreground
        val collector = launch { vm.events.collect { events += it } }
        observe(vm)
        tasks.value = listOf(a, b)
        advanceUntilIdle()

        vm.rescheduleOverdue(0)
        advanceUntilIdle()

        verifyBlocking(taskRepository) { reschedule(listOf("a", "b"), endOfToday, true) }
        val event = events.single() as TaskListEvent.Rescheduled
        assertEquals(2, event.count)

        vm.undoReschedule(event.previous)
        advanceUntilIdle()
        verifyBlocking(taskRepository) { reschedule(listOf("a"), a.dueAt, false) }
        verifyBlocking(taskRepository) { reschedule(listOf("b"), b.dueAt, false) }
        collector.cancel()
    }

    @Test
    fun `quick create adds to my day and creates a new category first`() = runTest(dispatcher) {
        val draft = TaskDraft(title = "Mua quà", category = "Sinh nhật")
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        whenever(taskRepository.create(draft, emptyList(), today)).thenReturn(task("new"))
        val vm = viewModel()
        val events = mutableListOf<TaskListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.createQuickTask(QuickCreateResult(draft, addToMyDay = true, newCategory = "Sinh nhật"))
        advanceUntilIdle()

        verifyBlocking(categoryRepository) { add("Sinh nhật") }
        verifyBlocking(taskRepository) { create(eq(draft), any(), eq(today)) }
        assertEquals(TaskListEvent.Message("Đã thêm: Task new"), events.single())
    }
}
