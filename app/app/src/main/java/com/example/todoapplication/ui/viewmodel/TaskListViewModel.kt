package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.model.PostponeTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.repository.Badge
import com.example.todoapplication.data.repository.SubtaskProgress
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
    data class BadgeUnlocked(val badge: Badge) : TaskListEvent
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
}

class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    private var lastStatus: String? = null
    private var lastQuery: String? = null

    fun loadTasks(statusFilter: String, query: String) {
        lastStatus = if (statusFilter == "ALL") null else statusFilter
        lastQuery = query.trim().ifEmpty { null }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val result = repo.loadTasks(lastStatus, lastQuery)
                val progress = ServiceLocator.subtaskRepository.progressByTask()
                _uiState.update { it.copy(tasks = result.tasks, isLoading = false, isOffline = result.isOffline, subtaskProgress = progress) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(TaskListEvent.Message("Lỗi kết nối: ${e.message}"))
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            runCatching { repo.loadTasks(lastStatus, lastQuery) }.getOrNull()?.let { result ->
                val progress = ServiceLocator.subtaskRepository.progressByTask()
                _uiState.update { it.copy(tasks = result.tasks, isOffline = result.isOffline, subtaskProgress = progress) }
            }
        }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            val newBadges = repo.completeTask(task)
            newBadges.firstOrNull()?.let { _events.emit(TaskListEvent.BadgeUnlocked(it)) }
            reload()
        }
    }

    fun startTask(task: Task) {
        viewModelScope.launch { if (repo.startTask(task.id)) reload() }
    }

    fun cancelTask(task: Task) {
        viewModelScope.launch {
            if (repo.cancelTask(task.id)) {
                _events.emit(TaskListEvent.Message("Đã hủy công việc"))
                reload()
            }
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

    fun postponeTask(id: String, input: PostponeTaskInput) {
        viewModelScope.launch {
            if (repo.postponeTask(id, input)) {
                _events.emit(TaskListEvent.Message("Đã cập nhật hoãn việc"))
                reload()
            } else {
                _events.emit(TaskListEvent.Message("Lỗi cập nhật hoãn việc"))
            }
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
            val result = ServiceLocator.aiRepository.parseTask(text, localTime)
            _uiState.update { it.copy(quickAddLoading = false) }
            result.fold(
                onSuccess = { _events.emit(TaskListEvent.QuickAddReady(it)) },
                onFailure = { _events.emit(TaskListEvent.Message("Không phân tích được. Hãy thử mô tả rõ hơn.")) }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TaskListViewModel(ServiceLocator.taskRepository) }
        }
    }
}
