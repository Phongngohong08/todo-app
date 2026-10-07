package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.FocusHistoryRepository
import com.example.todoapplication.data.repository.Attachment
import com.example.todoapplication.data.repository.SkipResult
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.model.FocusRecord
import com.example.todoapplication.domain.model.ListInfo
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.plural

/** Sự kiện một lần của màn chi tiết: đã tải xong task (kèm dữ liệu) / đã lưu / có lỗi. */
sealed interface TaskDetailEvent {
    data class Loaded(val task: Task) : TaskDetailEvent   // mang theo task để màn đổ vào các ô nhập
    data object Saved : TaskDetailEvent                    // lưu xong → màn popBackStack về danh sách
    data class Error(val message: String) : TaskDetailEvent
    data class Info(val message: String) : TaskDetailEvent
    data object Deleted : TaskDetailEvent
    /** AI đã chia nhỏ — màn cho xem trước, người dùng chọn bước nào thêm. */
    data class BreakdownReady(val steps: List<String>, val estimatedMinutes: Int) : TaskDetailEvent
    data class EstimateReady(val minutes: Int) : TaskDetailEvent
    data class Duplicated(val taskId: String) : TaskDetailEvent
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
    private val categoryRepository: CategoryRepository,
    private val aiRepository: AiRepository,
    private val focusRepository: FocusHistoryRepository
) : ViewModel() {

    val taskId: String = savedStateHandle.get<String>("taskId") ?: NEW_TASK
    val isNew: Boolean = taskId == NEW_TASK

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<TaskDetailEvent>()
    val events: SharedFlow<TaskDetailEvent> = _events.asSharedFlow()

    val categories: StateFlow<List<String>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryRepository.DEFAULTS)

    /** Danh sách kèm các mục — để chọn mục (cột Kanban) theo danh sách đang chọn. */
    val lists: StateFlow<List<ListInfo>> = categoryRepository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nhãn đang dùng (nhiều việc dùng nhất trước) — gợi ý khi gắn nhãn. */
    val allTags: StateFlow<List<String>> = repo.observeTasks(null, null)
        .map { tasks -> tasks.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Nhắc theo địa điểm (chỉ trên máy) ──
    private val placeRepo = ServiceLocator.placeRepository
    val places: StateFlow<List<com.example.todoapplication.data.local.PlaceEntity>> = placeRepo.observePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val taskLocation: StateFlow<com.example.todoapplication.data.local.TaskLocationEntity?> =
        (if (isNew) flowOf(null) else placeRepo.observeForTask(taskId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLocation(placeId: String, trigger: String) {
        if (isNew) return
        viewModelScope.launch { placeRepo.setTaskLocation(taskId, placeId, trigger) }
    }

    fun clearLocation() {
        viewModelScope.launch { placeRepo.clearTaskLocation(taskId) }
    }

    // ── Đính kèm (chỉ trên máy) ──
    private val attachmentRepo = ServiceLocator.attachmentRepository
    val attachments: StateFlow<List<Attachment>> =
        (if (isNew) flowOf(emptyList()) else attachmentRepo.observe(taskId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAttachments(uris: List<android.net.Uri>) {
        if (isNew) return
        viewModelScope.launch {
            val added = uris.count { attachmentRepo.add(taskId, it) != null }
            if (added < uris.size) _events.emit(TaskDetailEvent.Error(plural(R.plurals.attach_copy_failed, uris.size - added)))
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch { attachmentRepo.delete(attachment.id) }
    }

    /** Các phiên tập trung đã làm cho việc này. */
    val focusHistory: StateFlow<List<FocusRecord>> =
        (if (isNew) flowOf(emptyList()) else focusRepository.observeForTask(taskId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            else _events.emit(TaskDetailEvent.Error(str(R.string.detail_task_gone)))
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

    /** Đặt / bỏ hạn riêng của bước con ("yyyy-MM-dd" hoặc null). */
    fun setSubtaskDue(item: Subtask, date: String?) {
        if (isNew) {
            draftSubtasks.update { list -> list.map { if (it.id == item.id) it.copy(dueDate = date) else it } }
        } else {
            viewModelScope.launch { repo.setSubtaskDue(taskId, item.id, date) }
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

    /** Lưu nội dung form. [inMyDay]: có nằm trong "Ngày của tôi" hôm nay không. */
    fun save(draft: TaskDraft, inMyDay: Boolean) {
        if (_isBusy.value) return // chặn bấm Lưu hai lần
        _isBusy.value = true
        viewModelScope.launch {
            val today = todayKey()
            val ok = if (isNew) {
                repo.create(draft, draftSubtasks.value, myDay = if (inMyDay) today else null)
                true
            } else {
                val current = repo.getTask(taskId)
                repo.update(taskId, draft).also { updated ->
                    // Chỉ đổi khi người dùng thật sự bật/tắt — không xóa "Ngày của tôi" của một ngày khác
                    if (updated && current != null && (current.myDay == today) != inMyDay) {
                        repo.setMyDay(listOf(taskId), if (inMyDay) today else null)
                    }
                }
            }
            _isBusy.value = false
            _events.emit(
                if (ok) TaskDetailEvent.Saved
                else TaskDetailEvent.Error(str(R.string.detail_save_gone))
            )
        }
    }

    /** "Bỏ qua lần này" của việc lặp: nhảy sang lần kế tiếp, không tính là hoàn thành. */
    fun skipOccurrence() {
        if (isNew) return
        viewModelScope.launch {
            val msg = when (repo.skipOccurrence(taskId)) {
                SkipResult.SKIPPED -> {
                    repo.getTask(taskId)?.let { _events.emit(TaskDetailEvent.Loaded(it)) } // cập nhật hạn trên form
                    str(R.string.detail_skipped)
                }
                SkipResult.SERIES_ENDED -> str(R.string.detail_series_ended)
                SkipResult.NOT_RECURRING -> str(R.string.detail_skip_needs_recurring)
                SkipResult.NOT_FOUND -> str(R.string.detail_task_gone_short)
            }
            _events.emit(TaskDetailEvent.Info(msg))
        }
    }

    // ── Trợ lý AI (cần mạng) ──

    /** Đang chờ AI: null = rảnh. */
    private val _aiWorking = MutableStateFlow<AiJob?>(null)
    val aiWorking: StateFlow<AiJob?> = _aiWorking.asStateFlow()

    enum class AiJob { BREAKDOWN, ESTIMATE }

    /** "Nhờ AI chia nhỏ": dùng tiêu đề/mô tả ĐANG gõ trên form (chưa cần lưu). */
    fun requestBreakdown(title: String, description: String) {
        runAi(AiJob.BREAKDOWN, title) {
            aiRepository.breakdown(title.trim(), description.trim()).fold(
                onSuccess = { r ->
                    val existing = subtasks.value.map { it.title.lowercase() }.toSet()
                    val steps = r.subtasks.orEmpty().filter { it.isNotBlank() && it.lowercase() !in existing }
                    if (steps.isEmpty()) TaskDetailEvent.Info(str(R.string.detail_ai_no_steps))
                    else TaskDetailEvent.BreakdownReady(steps, r.estimatedMinutes)
                },
                onFailure = { TaskDetailEvent.Error(it.aiFailureMessage(str(R.string.detail_ai_breakdown_failed))) }
            )
        }
    }

    fun requestEstimate(title: String, description: String) {
        runAi(AiJob.ESTIMATE, title) {
            aiRepository.estimate(title.trim(), description.trim()).fold(
                onSuccess = { minutes ->
                    if (minutes > 0) TaskDetailEvent.EstimateReady(minutes)
                    else TaskDetailEvent.Info(str(R.string.detail_ai_no_estimate))
                },
                onFailure = { TaskDetailEvent.Error(it.aiFailureMessage(str(R.string.detail_ai_estimate_failed))) }
            )
        }
    }

    private fun runAi(job: AiJob, title: String, block: suspend () -> TaskDetailEvent) {
        if (_aiWorking.value != null) return
        if (title.isBlank()) {
            viewModelScope.launch { _events.emit(TaskDetailEvent.Info(str(R.string.detail_ai_needs_title))) }
            return
        }
        _aiWorking.value = job
        viewModelScope.launch {
            val event = block()
            _aiWorking.value = null
            _events.emit(event)
        }
    }

    /** Thêm các bước AI gợi ý mà người dùng đã chọn. */
    fun addSubtasks(titles: List<String>) {
        val clean = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        if (isNew) {
            draftSubtasks.update { list ->
                list + clean.mapIndexed { i, t -> Subtask(UUID.randomUUID().toString(), t, isDone = false, position = list.size + i) }
            }
        } else {
            viewModelScope.launch { repo.addSubtasks(taskId, clean) }
        }
    }

    /** Nhân bản (bản đã lưu) rồi mở bản sao. */
    fun duplicate() {
        if (isNew) return
        viewModelScope.launch {
            repo.duplicate(taskId)?.let { _events.emit(TaskDetailEvent.Duplicated(it.id)) }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            if (repo.delete(taskId)) _events.emit(TaskDetailEvent.Deleted)
        }
    }

    fun todayKey(): String = LocalDate.now().toString()

    companion object {
        const val NEW_TASK = "new"

        val Factory = viewModelFactory {
            initializer {
                TaskDetailViewModel(
                    createSavedStateHandle(),
                    ServiceLocator.taskRepository,
                    ServiceLocator.categoryRepository,
                    ServiceLocator.aiRepository,
                    ServiceLocator.focusHistoryRepository
                )
            }
        }
    }
}
