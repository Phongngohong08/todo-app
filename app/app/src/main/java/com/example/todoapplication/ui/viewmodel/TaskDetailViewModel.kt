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

// Sự kiện một lần của màn chi tiết: đã tải xong task (kèm dữ liệu) / đã lưu / có lỗi.
sealed interface TaskDetailEvent {
    data class Loaded(val task: Task) : TaskDetailEvent   // mang theo task để màn đổ vào các ô nhập
    data object Saved : TaskDetailEvent                    // lưu xong → màn thường popBackStack về danh sách
    data class Error(val message: String) : TaskDetailEvent
}

class TaskDetailViewModel(
    private val repo: TaskRepository,          // task lưu trên SERVER (qua API)
    private val subtaskRepo: SubtaskRepository // bước con lưu CỤC BỘ (Room) — hai nguồn dữ liệu khác nhau
) : ViewModel() {
    // isBusy: đang tải/đang lưu → màn khóa nút, hiện spinner.
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<TaskDetailEvent>()
    val events: SharedFlow<TaskDetailEvent> = _events.asSharedFlow()

    // Danh sách bước con là STATE (bền, hiển thị liên tục) nên dùng StateFlow, khác events ở trên.
    private val _subtasks = MutableStateFlow<List<SubtaskEntity>>(emptyList())
    val subtasks: StateFlow<List<SubtaskEntity>> = _subtasks.asStateFlow()

    // Nạp bước con của task từ Room. Gọi khi mở màn chi tiết.
    fun loadSubtasks(taskId: String) {
        viewModelScope.launch { _subtasks.value = subtaskRepo.getForTask(taskId) }
    }

    // Thêm 1 bước con rồi ĐỌC LẠI từ Room để state khớp DB (mẫu "ghi xong tải lại" lặp ở cả toggle/delete).
    fun addSubtask(taskId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            subtaskRepo.add(taskId, title, _subtasks.value.size)   // position = số bước hiện có (thêm vào cuối)
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

    // Tải 1 task để SỬA (chế độ edit). Xong thì phát Loaded(task) để màn điền vào form.
    fun loadTask(id: String) {
        _isBusy.value = true
        viewModelScope.launch {
            val task = repo.getTask(id)
            _isBusy.value = false
            if (task != null) _events.emit(TaskDetailEvent.Loaded(task))
            else _events.emit(TaskDetailEvent.Error("Không thể tải chi tiết công việc"))
        }
    }

    // create/update dùng chung logic lưu bên dưới, chỉ khác lời gọi repo → gom vào save{} cho gọn (DRY).
    fun create(input: CreateTaskInput) = save { repo.createTask(input) }

    fun update(id: String, input: UpdateTaskInput) = save { repo.updateTask(id, input) }

    // Nhận một "khối lệnh lưu" (lambda suspend) rồi chạy chung: bật busy → gọi → phát Saved/Error.
    // Nhờ vậy không phải viết lặp phần bận/kết quả ở cả create lẫn update.
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
