package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.model.Subtask
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Sự kiện một lần của màn chi tiết: đã tải xong task (kèm dữ liệu) / đã lưu / có lỗi. */
sealed interface TaskDetailEvent {
    data class Loaded(val task: Task) : TaskDetailEvent   // mang theo task để màn đổ vào các ô nhập
    data object Saved : TaskDetailEvent                    // lưu xong → màn popBackStack về danh sách
    data class Error(val message: String) : TaskDetailEvent
}

/**
 * [TẦNG VIEWMODEL] Màn chi tiết công việc: tạo mới hoặc sửa, kèm checklist các bước con.
 *
 * taskId lấy từ [SavedStateHandle] — Navigation Compose tự đặt tham số route vào đó, và SavedStateHandle
 * còn sống sót qua cả việc hệ điều hành giết tiến trình khi app ở nền.
 * Mọi thao tác ghi đi vào Room nên tức thì và dùng được khi offline.
 */
class TaskDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: TaskRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val taskId: String = savedStateHandle.get<String>("taskId") ?: NEW_TASK
    val isNew: Boolean = taskId == NEW_TASK

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<TaskDetailEvent>()
    val events: SharedFlow<TaskDetailEvent> = _events.asSharedFlow()

    val categories: StateFlow<List<String>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryRepository.DEFAULTS)

    // Task mới chưa có trong database → giữ checklist trong bộ nhớ, lưu cùng lúc với task.
    // Task đã có → đọc thẳng từ Room (Flow), mỗi thao tác ghi ngay và tự hiện lên.
    private val draftSubtasks = MutableStateFlow<List<Subtask>>(emptyList())
    private val subtaskSource: Flow<List<Subtask>> = if (isNew) draftSubtasks else repo.observeSubtasks(taskId)
    val subtasks: StateFlow<List<Subtask>> =
        subtaskSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tải task để điền form (chỉ chế độ sửa). */
    fun loadTask() {
        if (isNew) return
        viewModelScope.launch {
            val task = repo.getTask(taskId)
            if (task != null) _events.emit(TaskDetailEvent.Loaded(task))
            else _events.emit(TaskDetailEvent.Error("Công việc không còn tồn tại (có thể đã bị xóa trên thiết bị khác)"))
        }
    }

    fun addSubtask(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        if (isNew) {
            draftSubtasks.update { it + Subtask(UUID.randomUUID().toString(), clean, isDone = false, position = it.size) }
        } else {
            viewModelScope.launch { repo.addSubtask(taskId, clean) }
        }
    }

    fun toggleSubtask(item: Subtask) {
        if (isNew) {
            draftSubtasks.update { list -> list.map { if (it.id == item.id) it.copy(isDone = !it.isDone) else it } }
        } else {
            viewModelScope.launch { repo.toggleSubtask(taskId, item.id) }
        }
    }

    fun deleteSubtask(item: Subtask) {
        if (isNew) {
            draftSubtasks.update { list -> list.filter { it.id != item.id } }
        } else {
            viewModelScope.launch { repo.deleteSubtask(taskId, item.id) }
        }
    }

    /** Thêm danh mục mới; trả tên đã chuẩn hoá qua [onAdded] để form chọn luôn danh mục đó. */
    fun addCategory(name: String, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val added = categoryRepository.add(name)
            if (added.isNotEmpty()) onAdded(added)
        }
    }

    fun save(draft: TaskDraft) {
        if (_isBusy.value) return // chặn bấm Lưu hai lần
        _isBusy.value = true
        viewModelScope.launch {
            val ok = if (isNew) {
                repo.create(draft, draftSubtasks.value)
                true
            } else {
                repo.update(taskId, draft)
            }
            _isBusy.value = false
            _events.emit(
                if (ok) TaskDetailEvent.Saved
                else TaskDetailEvent.Error("Không thể lưu: công việc đã bị xóa trên thiết bị khác")
            )
        }
    }

    companion object {
        const val NEW_TASK = "new"

        val Factory = viewModelFactory {
            initializer {
                TaskDetailViewModel(
                    createSavedStateHandle(),
                    ServiceLocator.taskRepository,
                    ServiceLocator.categoryRepository
                )
            }
        }
    }
}
