package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.local.SubtaskEntity
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.repository.SubtaskRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * [TẦNG VIEWMODEL] Nghiệp vụ màn chi tiết công việc: tải task để sửa, tạo mới hoặc cập nhật,
 * và quản lý các "bước con" (subtask) lưu cục bộ trong Room. Phát TaskDetailEvent để màn phản ứng.
 */

sealed interface TaskDetailEvent {
    data class Loaded(val task: Task) : TaskDetailEvent
    data object Saved : TaskDetailEvent
    data class Error(val message: String) : TaskDetailEvent
}

class TaskDetailViewModel(
    private val repo: TaskRepository,
    private val subtaskRepo: SubtaskRepository
) : ViewModel() {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<TaskDetailEvent>()
    val events: SharedFlow<TaskDetailEvent> = _events.asSharedFlow()

    private val _subtasks = MutableStateFlow<List<SubtaskEntity>>(emptyList())
    val subtasks: StateFlow<List<SubtaskEntity>> = _subtasks.asStateFlow()

    fun loadSubtasks(taskId: String) {
        viewModelScope.launch { _subtasks.value = subtaskRepo.getForTask(taskId) }
    }

    fun addSubtask(taskId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            subtaskRepo.add(taskId, title, _subtasks.value.size)
            _subtasks.value = subtaskRepo.getForTask(taskId)
        }
    }

    fun toggleSubtask(item: SubtaskEntity) {
        viewModelScope.launch {
            subtaskRepo.toggle(item)
            _subtasks.value = subtaskRepo.getForTask(item.taskId)
        }
    }

    fun deleteSubtask(item: SubtaskEntity) {
        viewModelScope.launch {
            subtaskRepo.delete(item)
            _subtasks.value = subtaskRepo.getForTask(item.taskId)
        }
    }

    fun loadTask(id: String) {
        _isBusy.value = true
        viewModelScope.launch {
            val task = repo.getTask(id)
            _isBusy.value = false
            if (task != null) _events.emit(TaskDetailEvent.Loaded(task))
            else _events.emit(TaskDetailEvent.Error("Không thể tải chi tiết công việc"))
        }
    }

    fun create(input: CreateTaskInput) = save { repo.createTask(input) }

    fun update(id: String, input: UpdateTaskInput) = save { repo.updateTask(id, input) }

    private fun save(block: suspend () -> Result<Task>) {
        _isBusy.value = true
        viewModelScope.launch {
            val result = block()
            _isBusy.value = false
            result.fold(
                onSuccess = { _events.emit(TaskDetailEvent.Saved) },
                onFailure = { _events.emit(TaskDetailEvent.Error("Không thể lưu công việc")) }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TaskDetailViewModel(ServiceLocator.taskRepository, ServiceLocator.subtaskRepository) }
        }
    }
}
