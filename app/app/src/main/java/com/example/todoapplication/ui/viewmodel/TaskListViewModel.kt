package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.data.sync.SyncResult
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.TaskSections
import com.example.todoapplication.domain.aiRecommendedIds
import com.example.todoapplication.domain.groupTasks
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
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
import kotlinx.coroutines.withTimeoutOrNull

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
    val aiRecommendedIds: Set<String> = emptySet(),
    val categories: List<String> = CategoryRepository.DEFAULTS,
    val selectedCategory: String = ALL_CATEGORIES,
    val query: String = "",
    val sortBy: String = "DEFAULT",
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val isRefreshing: Boolean = false,
    val quickAddLoading: Boolean = false
) {
    val hasFilter: Boolean get() = query.isNotBlank() || selectedCategory != ALL_CATEGORIES
}

/** Sự kiện DÙNG-MỘT-LẦN (Snackbar / điều hướng) — khác state, không lặp lại khi màn vẽ lại. */
sealed interface TaskListEvent {
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
    /** Đã hoàn thành — màn hiện Snackbar kèm nút Hoàn tác. */
    data class Completed(val taskId: String, val title: String, val spawnedNext: Boolean) : TaskListEvent
    data class Deleted(val taskId: String, val title: String) : TaskListEvent
    /** Còn thay đổi chưa gửi được lên server — hỏi lại trước khi đăng xuất (sẽ mất chúng). */
    data class ConfirmLogout(val pendingChanges: Int) : TaskListEvent
    data object LoggedOut : TaskListEvent
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
    private val authRepository: AuthRepository,
    sessionManager: SessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
    // Luồng tính nhóm/sắp xếp — test truyền dispatcher của test để chạy tất định
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    /** Tên người dùng để hiển thị lời chào — đọc một lần từ phiên đăng nhập. */
    val userName: String = sessionManager.getUserName().ifBlank { "bạn" }

    private val category = MutableStateFlow(ALL_CATEGORIES)
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
        val aiIds: Set<String>
    )

    private data class SyncInfo(val isOnline: Boolean, val pendingCount: Int, val hasSyncedOnce: Boolean)

    // Gõ tìm kiếm: chờ người dùng ngừng gõ 250ms mới truy vấn. Đổi danh mục: truy vấn ngay.
    // flatMapLatest hủy truy vấn cũ khi bộ lọc đổi → không bao giờ hiện kết quả của bộ lọc trước.
    private val tasks: Flow<List<Task>> =
        combine(category, query.debounce(250).distinctUntilChanged()) { c, q -> c to q }
            .flatMapLatest { (c, q) -> repo.observeTasks(c.takeUnless { it == ALL_CATEGORIES }, q) }

    // Nhóm/sắp xếp/gợi ý tính trên Dispatchers.Default — không chiếm luồng UI, cuộn mượt với danh sách dài.
    private val listState: Flow<ListState> = combine(tasks, sortBy, dragOrder) { list, sort, order ->
        val now = clock()
        val pending = list.filter { !it.isCompleted }
        ListState(
            tasks = list,
            sections = groupTasks(list, sort, now),
            manualOrder = applyDragOrder(pending, order),
            pending = pending.size,
            completed = list.size - pending.size,
            overdue = pending.count { it.isOverdue(now) },
            aiIds = aiRecommendedIds(list, now)
        )
    }.flowOn(computeDispatcher)

    private val syncInfo: Flow<SyncInfo> =
        combine(sync.isOnline, repo.observePendingSyncCount(), sync.hasSyncedOnce, ::SyncInfo)

    private val filters: Flow<Triple<String, String, String>> =
        combine(category, query, sortBy) { c, q, s -> Triple(c, q, s) }

    val uiState: StateFlow<TaskListUiState> = combine(
        listState, categoryRepository.observeAll(), syncInfo, transient, filters
    ) { list, categories, syncInfo, transient, (c, q, s) ->
        // Lần đầu đăng nhập: Room còn trống trong lúc tải từ server → hiện "đang tải" thay vì "chưa có việc"
        val waitingFirstSync = list.tasks.isEmpty() && !syncInfo.hasSyncedOnce && syncInfo.isOnline && c == ALL_CATEGORIES && q.isBlank()
        TaskListUiState(
            isLoading = waitingFirstSync,
            sections = list.sections,
            manualOrder = list.manualOrder,
            pendingCount = list.pending,
            completedCount = list.completed,
            overdueCount = list.overdue,
            aiRecommendedIds = list.aiIds,
            categories = categories,
            selectedCategory = c,
            query = q,
            sortBy = s,
            isOnline = syncInfo.isOnline,
            pendingSyncCount = syncInfo.pendingCount,
            isRefreshing = transient.isRefreshing,
            quickAddLoading = transient.quickAddLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    // ── Bộ lọc ──
    fun setCategory(value: String) { category.value = value }
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

    /** Tạo nhanh một công việc từ thanh nhập (không qua màn chi tiết). */
    fun createQuickTask(draft: TaskDraft) {
        viewModelScope.launch {
            val created = repo.create(draft)
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
    // Nhờ AI (backend) tách câu tự nhiên thành task, rồi phát QuickAddReady để màn mở
    // màn chi tiết điền sẵn (chưa lưu — người dùng xác nhận mới tạo).
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

    // ── Đăng xuất ──
    /**
     * Trước khi xóa dữ liệu trên máy, cố gửi nốt thay đổi chưa đồng bộ. Không gửi được (mất mạng)
     * thì hỏi lại người dùng thay vì âm thầm làm mất dữ liệu.
     */
    fun requestLogout() {
        viewModelScope.launch {
            var pending = repo.pendingSyncCount()
            if (pending > 0) {
                transient.update { it.copy(isRefreshing = true) }
                withTimeoutOrNull(LOGOUT_SYNC_TIMEOUT_MS) { sync.syncNow() }
                transient.update { it.copy(isRefreshing = false) }
                pending = repo.pendingSyncCount()
            }
            if (pending > 0) emit(TaskListEvent.ConfirmLogout(pending)) else logoutNow()
        }
    }

    fun logoutNow() {
        authRepository.logout()
        viewModelScope.launch { emit(TaskListEvent.LoggedOut) }
    }

    private suspend fun emit(event: TaskListEvent) = _events.emit(event)

    private fun applyDragOrder(pending: List<Task>, order: List<String>?): List<Task> {
        if (order == null) return pending
        val byId = pending.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        return ordered + pending.filter { it.id !in order.toSet() } // việc mới xuất hiện trong lúc sắp xếp
    }

    companion object {
        private const val LOGOUT_SYNC_TIMEOUT_MS = 8_000L

        val Factory = viewModelFactory {
            initializer {
                TaskListViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.categoryRepository,
                    ServiceLocator.syncController,
                    ServiceLocator.aiRepository,
                    ServiceLocator.authRepository,
                    ServiceLocator.sessionManager
                )
            }
        }
    }
}
