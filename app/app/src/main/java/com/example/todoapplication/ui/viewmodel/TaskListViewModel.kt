package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.data.sync.SyncResult
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.SmartFilter
import com.example.todoapplication.domain.TaskSections
import com.example.todoapplication.domain.allDayDueInDays
import com.example.todoapplication.domain.groupTasks
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.recommendations
import com.example.todoapplication.ui.screens.QuickCreateResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

const val ALL_CATEGORIES = "ALL"

/** Toàn bộ những gì màn danh sách cần để tự vẽ, gom vào MỘT object ("single source of truth"). */
data class TaskListUiState(
    val isLoading: Boolean = true,
    val sections: TaskSections = TaskSections(),
    /** Việc chưa xong theo thứ tự kéo-thả (chế độ sắp xếp). */
    val manualOrder: List<Task> = emptyList(),
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val overdueCount: Int = 0,
    /** id → lý do "Nên làm trước". */
    val recommendations: Map<String, String> = emptyMap(),
    val categories: List<String> = CategoryRepository.DEFAULTS,
    val selectedCategory: String = ALL_CATEGORIES,
    val smartFilter: SmartFilter = SmartFilter.NONE,
    val query: String = "",
    val sortBy: String = "DEFAULT",
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val isRefreshing: Boolean = false,
    val quickAddLoading: Boolean = false,
    /** "yyyy-MM-dd" hôm nay — để biết thẻ nào đang trong "Ngày của tôi". */
    val today: String = ""
) {
    val hasFilter: Boolean
        get() = query.isNotBlank() || selectedCategory != ALL_CATEGORIES || smartFilter != SmartFilter.NONE
}

/** Sự kiện DÙNG-MỘT-LẦN (Snackbar / điều hướng) — khác state, không lặp lại khi màn vẽ lại. */
sealed interface TaskListEvent {
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
    /** Đã hoàn thành — màn hiện Snackbar kèm nút Hoàn tác. */
    data class Completed(val taskId: String, val title: String, val spawnedNext: Boolean) : TaskListEvent
    data class Deleted(val taskId: String, val title: String) : TaskListEvent
    /** Đã dời hạn hàng loạt — Hoàn tác trả lại hạn cũ của từng việc. */
    data class Rescheduled(val count: Int, val previous: List<Task>) : TaskListEvent
}

/**
 * [TẦNG VIEWMODEL] "Bộ não" của màn danh sách công việc.
 *
 * Luồng dữ liệu một chiều (UDF): Room → Flow → combine → uiState → Compose.
 * Người dùng thao tác → gọi hàm ở đây → ghi Room → Room tự phát dữ liệu mới → màn tự vẽ lại.
 * ViewModel KHÔNG tự sửa danh sách trong state sau khi ghi: dữ liệu luôn đi theo một đường duy nhất.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val repo: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val sync: SyncController,
    private val aiRepository: AiRepository,
    sessionManager: SessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Luồng tính nhóm/sắp xếp — test truyền dispatcher của test để chạy tất định
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    /** Tên người dùng để hiển thị lời chào — đọc một lần từ phiên đăng nhập. */
    val userName: String = sessionManager.getUserName().ifBlank { "bạn" }

    private val category = MutableStateFlow(ALL_CATEGORIES)
    private val smartFilter = MutableStateFlow(SmartFilter.NONE)
    private val query = MutableStateFlow("")
    private val sortBy = MutableStateFlow("DEFAULT")
    /** Thứ tự tạm trong lúc kéo-thả (id), null khi không ở chế độ sắp xếp. */
    private val dragOrder = MutableStateFlow<List<String>?>(null)
    private var draggedId: String? = null
    private val transient = MutableStateFlow(Transient())

    private data class Transient(val isRefreshing: Boolean = false, val quickAddLoading: Boolean = false)

    private data class ListState(
        val tasks: List<Task>,
        val sections: TaskSections,
        val manualOrder: List<Task>,
        val pending: Int,
        val completed: Int,
        val overdue: Int,
        val recommendations: Map<String, String>,
        val today: String
    )

    private data class SyncInfo(val isOnline: Boolean, val pendingCount: Int, val hasSyncedOnce: Boolean)

    private data class Filters(val category: String, val smart: SmartFilter, val query: String, val sortBy: String)

    // Gõ tìm kiếm: chờ người dùng ngừng gõ 250ms mới truy vấn. Đổi danh mục: truy vấn ngay.
    // flatMapLatest hủy truy vấn cũ khi bộ lọc đổi → không bao giờ hiện kết quả của bộ lọc trước.
    private val tasks: Flow<List<Task>> =
        combine(category, query.debounce(250).distinctUntilChanged()) { c, q -> c to q }
            .flatMapLatest { (c, q) -> repo.observeTasks(c.takeUnless { it == ALL_CATEGORIES }, q) }

    // Nhóm/sắp xếp/gợi ý tính trên Dispatchers.Default — không chiếm luồng UI, cuộn mượt với danh sách dài.
    private val listState: Flow<ListState> = combine(tasks, sortBy, dragOrder, smartFilter) { all, sort, order, smart ->
        val now = clock()
        val z = zone()
        val list = if (smart == SmartFilter.NONE) all else all.filter { !it.isCompleted && smart.matches(it, now) }
        val pending = list.filter { !it.isCompleted }
        ListState(
            tasks = list,
            sections = groupTasks(list, sort, now, z),
            manualOrder = applyDragOrder(pending, order),
            pending = pending.size,
            completed = list.size - pending.size,
            overdue = pending.count { it.isOverdue(now) },
            recommendations = recommendations(list, now, z),
            today = Instant.ofEpochMilli(now).atZone(z).toLocalDate().toString()
        )
    }.flowOn(computeDispatcher)

    private val syncInfo: Flow<SyncInfo> =
        combine(sync.isOnline, repo.observePendingSyncCount(), sync.hasSyncedOnce, ::SyncInfo)

    private val filters: Flow<Filters> =
        combine(category, smartFilter, query, sortBy) { c, f, q, s -> Filters(c, f, q, s) }

    val uiState: StateFlow<TaskListUiState> = combine(
        listState, categoryRepository.observeAll(), syncInfo, transient, filters
    ) { list, categories, syncInfo, transient, f ->
        // Lần đầu đăng nhập: Room còn trống trong lúc tải từ server → hiện "đang tải" thay vì "chưa có việc"
        val waitingFirstSync = list.tasks.isEmpty() && !syncInfo.hasSyncedOnce && syncInfo.isOnline &&
            f.category == ALL_CATEGORIES && f.query.isBlank() && f.smart == SmartFilter.NONE
        TaskListUiState(
            isLoading = waitingFirstSync,
            sections = list.sections,
            manualOrder = list.manualOrder,
            pendingCount = list.pending,
            completedCount = list.completed,
            overdueCount = list.overdue,
            recommendations = list.recommendations,
            categories = categories,
            selectedCategory = f.category,
            smartFilter = f.smart,
            query = f.query,
            sortBy = f.sortBy,
            isOnline = syncInfo.isOnline,
            pendingSyncCount = syncInfo.pendingCount,
            isRefreshing = transient.isRefreshing,
            quickAddLoading = transient.quickAddLoading,
            today = list.today
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    // ── Bộ lọc ──
    fun setCategory(value: String) { category.value = value }
    fun setSmartFilter(value: SmartFilter) { smartFilter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setSortBy(value: String) { sortBy.value = value }

    /** Kéo-để-làm-mới: đồng bộ ngay với server và báo kết quả. */
    fun refresh() {
        if (transient.value.isRefreshing) return
        transient.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = sync.syncNow()
            transient.update { it.copy(isRefreshing = false) }
            when (result) {
                SyncResult.Success, SyncResult.NotLoggedIn, SyncResult.AuthError -> Unit
                SyncResult.NetworkError -> emit(TaskListEvent.Message("Không có mạng — thay đổi sẽ tự đồng bộ khi có mạng"))
                is SyncResult.ServerError -> emit(TaskListEvent.Message("Máy chủ đang bận, sẽ thử đồng bộ lại sau"))
            }
        }
    }

    // ── Thao tác trên task (ghi Room → UI tự cập nhật) ──
    fun completeTask(task: Task) {
        viewModelScope.launch {
            if (repo.setCompleted(task.id, completed = true)) {
                emit(TaskListEvent.Completed(task.id, task.title, spawnedNext = task.isRecurring && task.dueAt != null))
            }
        }
    }

    /** Mở lại việc đã xong (bấm lại ô tích, hoặc Hoàn tác trên Snackbar). */
    fun reopenTask(taskId: String) {
        viewModelScope.launch { repo.setCompleted(taskId, completed = false) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            if (repo.delete(task.id)) emit(TaskListEvent.Deleted(task.id, task.title))
        }
    }

    fun undoDelete(taskId: String) {
        viewModelScope.launch { repo.restore(taskId) }
    }

    /** Đổi nhanh độ ưu tiên của một task (bấm cờ trên thẻ). */
    fun setPriority(task: Task, priority: String) {
        viewModelScope.launch { repo.setPriority(task.id, priority) }
    }

    /** Đưa vào / bỏ khỏi "Ngày của tôi" (hôm nay). */
    fun toggleMyDay(task: Task) {
        val today = uiState.value.today.ifEmpty { todayKey() }
        viewModelScope.launch {
            val adding = task.myDay != today
            repo.setMyDay(listOf(task.id), if (adding) today else null)
            emit(TaskListEvent.Message(if (adding) "Đã thêm vào Ngày của tôi ☀" else "Đã bỏ khỏi Ngày của tôi"))
        }
    }

    /**
     * "Dời tất cả" ở nhóm Quá hạn (như Todoist "Reschedule"): chuyển mọi việc quá hạn sang hôm nay/ngày mai
     * dạng "cả ngày". Hoàn tác trả lại hạn cũ.
     */
    fun rescheduleOverdue(daysFromToday: Int) {
        val overdue = uiState.value.sections.overdue
        if (overdue.isEmpty()) return
        viewModelScope.launch {
            val z = zone()
            val due = allDayDueInDays(daysFromToday, z, Instant.ofEpochMilli(clock()).atZone(z).toLocalDate())
            if (repo.reschedule(overdue.map { it.id }, due, allDay = true)) {
                emit(TaskListEvent.Rescheduled(overdue.size, overdue))
            }
        }
    }

    /** Hoàn tác "Dời tất cả": trả từng việc về hạn cũ. */
    fun undoReschedule(previous: List<Task>) {
        viewModelScope.launch {
            previous.groupBy { it.dueAt to it.dueAllDay }.forEach { (key, tasks) ->
                repo.reschedule(tasks.map { it.id }, key.first, key.second)
            }
        }
    }

    /** Tạo nhanh từ thanh nhập (không qua màn chi tiết). Danh mục mới gõ sau "#" được tạo luôn. */
    fun createQuickTask(result: QuickCreateResult) {
        viewModelScope.launch {
            result.newCategory?.let { categoryRepository.add(it) }
            val created = repo.create(result.draft, myDay = if (result.addToMyDay) todayKey() else null)
            emit(TaskListEvent.Message("Đã thêm: ${created.title}"))
        }
    }

    // ── Kéo-thả ──
    fun onDragStart(taskId: String) {
        draggedId = taskId
        if (dragOrder.value == null) dragOrder.value = uiState.value.manualOrder.map { it.id }
    }

    /** Gọi liên tục khi kéo qua các thẻ — chỉ đổi thứ tự tạm trong bộ nhớ (60fps, chưa ghi database). */
    fun onDragMove(fromKey: Any, toKey: Any) {
        dragOrder.update { current ->
            val order = (current ?: uiState.value.manualOrder.map { it.id }).toMutableList()
            val from = order.indexOf(fromKey)
            val to = order.indexOf(toKey)
            if (from < 0 || to < 0) return@update current
            order.add(to, order.removeAt(from))
            order
        }
    }

    /** Thả tay: lưu vị trí mới của task được kéo (chỉ một dòng thay đổi — xem SortOrder). */
    fun onDragEnd() {
        val moved = draggedId ?: return
        val order = dragOrder.value ?: return
        draggedId = null
        viewModelScope.launch { repo.move(order, moved) }
    }

    /** Thoát chế độ sắp xếp → bỏ thứ tự tạm, hiển thị theo database. */
    fun endSortMode() {
        dragOrder.value = null
    }

    // ── AI Quick Add ──
    // Câu phức tạp mà bộ phân tích trên máy chưa hiểu → nhờ AI (backend) tách, rồi phát QuickAddReady để màn
    // mở màn chi tiết điền sẵn (chưa lưu — người dùng xác nhận mới tạo).
    fun parseQuickAdd(text: String, localTime: String) {
        transient.update { it.copy(quickAddLoading = true) }
        viewModelScope.launch {
            val result = aiRepository.parseTask(text, localTime)
            transient.update { it.copy(quickAddLoading = false) }
            result.fold(
                onSuccess = { emit(TaskListEvent.QuickAddReady(it)) },
                onFailure = {
                    emit(TaskListEvent.Message(it.aiFailureMessage("Không phân tích được. Hãy thử mô tả rõ hơn.")))
                }
            )
        }
    }

    private fun todayKey(): String = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toString()

    private suspend fun emit(event: TaskListEvent) = _events.emit(event)

    private fun applyDragOrder(pending: List<Task>, order: List<String>?): List<Task> {
        if (order == null) return pending
        val byId = pending.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        return ordered + pending.filter { it.id !in order.toSet() } // việc mới xuất hiện trong lúc sắp xếp
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TaskListViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.categoryRepository,
                    ServiceLocator.syncController,
                    ServiceLocator.aiRepository,
                    ServiceLocator.sessionManager
                )
            }
        }
    }
}
