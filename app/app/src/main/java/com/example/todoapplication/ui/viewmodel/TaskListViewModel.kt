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

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val quickAddLoading: Boolean = false,
    val subtaskProgress: Map<String, SubtaskProgress> = emptyMap()
)

sealed interface TaskListEvent {
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
}

class TaskListViewModel(
    private val repo: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val aiRepository: AiRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    /** Tên người dùng để hiển thị lời chào — đọc một lần từ phiên đăng nhập. */
    val userName: String = sessionManager.getUserName().ifBlank { "bạn" }

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    private var lastCategory: String? = null
    private var lastQuery: String? = null

    fun loadTasks(categoryFilter: String, query: String) {
        lastCategory = if (categoryFilter == "ALL") null else categoryFilter
        lastQuery = query.trim().ifEmpty { null }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val result = repo.loadTasks(lastCategory, lastQuery)
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
            if (repo.deleteTask(task.id)) {
                _events.emit(TaskListEvent.Message("Đã xóa công việc"))
                reload()
            }
        }
    }

    /** Đổi nhanh độ ưu tiên của một task (bấm cờ trên thẻ). */
    fun setPriority(task: Task, priority: String) {
        if (task.priority == priority) return
        viewModelScope.launch {
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
        _uiState.update { state ->
            val list = state.tasks.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                list.add(toIndex, list.removeAt(fromIndex))
            }
            state.copy(tasks = list)
        }
    }

    fun parseQuickAdd(text: String, localTime: String) {
        _uiState.update { it.copy(quickAddLoading = true) }
        viewModelScope.launch {
            val result = aiRepository.parseTask(text, localTime)
            _uiState.update { it.copy(quickAddLoading = false) }
            result.fold(
                onSuccess = { _events.emit(TaskListEvent.QuickAddReady(it)) },
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
