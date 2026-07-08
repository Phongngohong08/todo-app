package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.SubtaskProgress
import com.example.todoapplication.data.repository.SubtaskRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Toàn bộ những gì màn danh sách cần để tự vẽ, gom vào MỘT object ("single source of truth").
data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,          // true khi đang đọc từ cache (mất mạng) → UI hiện banner
    val quickAddLoading: Boolean = false,
    val subtaskProgress: Map<String, SubtaskProgress> = emptyMap()
)

// Sự kiện DÙNG-MỘT-LẦN (Toast / điều hướng) — khác state, không lặp lại khi màn vẽ lại.
sealed interface TaskListEvent {
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
}

/**
 * [TẦNG VIEWMODEL] "Bộ não" của màn danh sách công việc.
 * Giữ state (uiState) + nghiệp vụ; nhận repository qua constructor (dễ test).
 * Quy tắc: UI đọc `uiState`, gọi các hàm public bên dưới; ViewModel là nơi DUY NHẤT đổi state.
 */
class TaskListViewModel(
    private val repo: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val aiRepository: AiRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    // Mẫu chuẩn: _uiState (private, ghi được) + uiState (public, chỉ đọc) → UI không tự sửa được state.
    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    /** Tên người dùng để hiển thị lời chào — đọc một lần từ phiên đăng nhập. */
    val userName: String = sessionManager.getUserName().ifBlank { "bạn" }

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    private var lastCategory: String? = null
    private var lastQuery: String? = null

    // Tải danh sách theo bộ lọc/từ khóa. Bật loading NGAY, rồi tải nền, rồi đổ kết quả vào state.
    fun loadTasks(categoryFilter: String, query: String) {
        lastCategory = if (categoryFilter == "ALL") null else categoryFilter
        lastQuery = query.trim().ifEmpty { null }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {                        // coroutine: chạy nền, không chặn UI
            try {
                val result = repo.loadTasks(lastCategory, lastQuery)   // suspend: chờ mạng/cache
                val progress = subtaskRepository.progressByTask()
                _uiState.update { it.copy(tasks = result.tasks, isLoading = false, isOffline = result.isOffline, subtaskProgress = progress) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(TaskListEvent.Message("Lỗi kết nối: ${e.message}"))
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            runCatching { repo.loadTasks(lastCategory, lastQuery) }.getOrNull()?.let { result ->
                val progress = subtaskRepository.progressByTask()
                _uiState.update { it.copy(tasks = result.tasks, isOffline = result.isOffline, subtaskProgress = progress) }
            }
        }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            repo.completeTask(task)
            reload()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            // Chỉ báo "Đã xóa" và tải lại KHI server xác nhận xóa thành công (repo trả true).
            if (repo.deleteTask(task.id)) {
                _events.emit(TaskListEvent.Message("Đã xóa công việc"))
                reload()
            }
        }
    }

    /** Đổi nhanh độ ưu tiên của một task (bấm cờ trên thẻ). */
    fun setPriority(task: Task, priority: String) {
        if (task.priority == priority) return   // đã đúng mức đó rồi thì khỏi gọi mạng
        viewModelScope.launch {
            // API sửa task cần đủ các trường → sao chép mọi trường của task cũ, chỉ thay priority.
            val input = UpdateTaskInput(
                title = task.title,
                description = task.description ?: "",
                priority = priority,
                dueDate = task.dueDate,
                category = task.category,
                recurrence = task.recurrence,
                recurrenceDays = task.recurrenceDays,
                reminderOffsetMinutes = task.reminderOffsetMinutes
            )
            if (repo.updateTask(task.id, input).isSuccess) reload()
        }
    }

    /** Tạo nhanh một công việc từ thanh nhập (không qua màn chi tiết). */
    fun createQuickTask(input: CreateTaskInput) {
        viewModelScope.launch {
            repo.createTask(input).fold(
                onSuccess = {
                    _events.emit(TaskListEvent.Message("Đã thêm: ${it.title}"))
                    reload()
                },
                onFailure = { _events.emit(TaskListEvent.Message("Không thể thêm công việc")) }
            )
        }
    }

    /** Đổi thứ tự cục bộ (kéo-thả). */
    fun moveTask(fromIndex: Int, toIndex: Int) {
        // CHỈ đổi thứ tự trong state (không gọi server). Bỏ phần tử ở fromIndex rồi chèn vào toIndex.
        _uiState.update { state ->
            val list = state.tasks.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                list.add(toIndex, list.removeAt(fromIndex))
            }
            state.copy(tasks = list)
        }
    }

    // Quick Add: nhờ AI (backend) tách câu tự nhiên thành task, rồi phát QuickAddReady để màn mở
    // màn chi tiết điền sẵn (chưa lưu — người dùng xác nhận mới tạo).
    fun parseQuickAdd(text: String, localTime: String) {
        _uiState.update { it.copy(quickAddLoading = true) }
        viewModelScope.launch {
            val result = aiRepository.parseTask(text, localTime)
            _uiState.update { it.copy(quickAddLoading = false) }
            result.fold(
                onSuccess = { _events.emit(TaskListEvent.QuickAddReady(it)) },   // "it" = ParsedTask AI trả về
                onFailure = { _events.emit(TaskListEvent.Message("Không phân tích được. Hãy thử mô tả rõ hơn.")) }
            )
        }
    }

    /** Đăng xuất khỏi phiên hiện tại. */
    fun logout() = sessionManager.logout()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TaskListViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.subtaskRepository,
                    ServiceLocator.aiRepository,
                    ServiceLocator.sessionManager
                )
            }
        }
    }
}
