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
import com.example.todoapplication.domain.BoardLogic
import com.example.todoapplication.domain.model.ListInfo
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

// ─── Tổng quan các danh sách ─────────────────────────────────────────────────

data class ListOverview(val info: ListInfo, val pending: Int, val overdue: Int)

data class ListsUiState(val isLoading: Boolean = true, val lists: List<ListOverview> = emptyList())

/** [TẦNG VIEWMODEL] Màn "Danh sách": mọi danh sách kèm số việc đang chờ, tạo / sửa / xóa danh sách. */
class ListsViewModel(
    private val categories: CategoryRepository,
    private val tasks: TaskRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val uiState: StateFlow<ListsUiState> = combine(categories.observeLists(), tasks.observeTasks(null, null)) { lists, all ->
        val now = clock()
        val pending = all.filter { !it.isCompleted }.groupBy { it.category }
        ListsUiState(
            isLoading = false,
            lists = lists.map { info ->
                val items = pending[info.name].orEmpty()
                ListOverview(info, items.size, items.count { (it.dueAt ?: Long.MAX_VALUE) < now })
            }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    fun create(name: String, color: String, emoji: String) {
        viewModelScope.launch {
            val added = categories.add(name)
            if (added.isNotEmpty()) categories.saveListInfo(categories.getList(added).copy(color = color, emoji = emoji))
        }
    }

    fun saveAppearance(info: ListInfo) {
        viewModelScope.launch { categories.saveListInfo(info) }
    }

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Lưu danh sách đang sửa: đổi tên (nếu khác, các việc chuyển theo) rồi lưu màu / emoji / mục dưới tên mới. */
    fun saveList(original: ListInfo, edited: ListInfo) {
        viewModelScope.launch {
            var name = original.name
            val wanted = edited.name.trim()
            if (wanted != original.name) {
                if (!categories.isNameAvailable(wanted, except = original.name)) {
                    _messages.emit(str(R.string.list_name_taken, wanted))
                    return@launch
                }
                tasks.renameCategory(original.name, wanted)
                name = categories.rename(original.name, wanted).ifEmpty { original.name }
            }
            categories.saveListInfo(edited.copy(name = name, sections = original.sections))
        }
    }

    fun delete(name: String) {
        viewModelScope.launch { categories.remove(name) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ListsViewModel(ServiceLocator.categoryRepository, ServiceLocator.taskRepository) }
        }
    }
}

// ─── Một danh sách: Danh sách / Kanban theo mục ──────────────────────────────

data class BoardColumn(
    /** "" = cột "Chưa phân mục". */
    val section: String,
    val tasks: List<Task>
)

data class ListBoardUiState(
    val isLoading: Boolean = true,
    val info: ListInfo = ListInfo(""),
    val columns: List<BoardColumn> = emptyList(),
    val completedCount: Int = 0
)

sealed interface ListBoardEvent {
    data class Moved(val task: Task, val section: String) : ListBoardEvent
    data class Message(val text: String) : ListBoardEvent
}

/** [TẦNG VIEWMODEL] Một danh sách: các cột theo mục, kéo việc giữa các cột, thêm nhanh việc vào cột, quản lý mục. */
class ListBoardViewModel(
    savedStateHandle: SavedStateHandle,
    private val categories: CategoryRepository,
    private val tasks: TaskRepository
) : ViewModel() {

    val listName: String = savedStateHandle.get<String>("name").orEmpty()

    val uiState: StateFlow<ListBoardUiState> =
        combine(categories.observeLists(), tasks.observeTasks(listName, null)) { lists, items ->
            val info = lists.firstOrNull { it.name == listName } ?: ListInfo(listName)
            ListBoardUiState(
                isLoading = false,
                info = info,
                columns = BoardLogic.columns(info.sections, items.filter { !it.isCompleted })
                    .map { (section, list) -> BoardColumn(section, list) },
                completedCount = items.count { it.isCompleted }
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListBoardUiState())

    private val _events = MutableSharedFlow<ListBoardEvent>()
    val events: SharedFlow<ListBoardEvent> = _events.asSharedFlow()

    /** Kéo thả sang cột khác. */
    fun moveTo(taskId: String, section: String) {
        val task = uiState.value.columns.flatMap { it.tasks }.firstOrNull { it.id == taskId } ?: return
        if (task.section == section) return
        viewModelScope.launch {
            if (tasks.setSection(listOf(taskId), section)) _events.emit(ListBoardEvent.Moved(task, section))
        }
    }

    fun undoMove(task: Task) {
        viewModelScope.launch { tasks.setSection(listOf(task.id), task.section) }
    }

    /** Thêm nhanh việc vào cột [section]. */
    fun addTask(title: String, section: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { tasks.create(TaskDraft(title = clean, category = listName, section = section)) }
    }

    fun complete(task: Task) {
        viewModelScope.launch { tasks.setCompleted(task.id, completed = true) }
    }

    // ── Quản lý mục ──

    fun addSection(name: String) {
        val clean = name.trim()
        val info = uiState.value.info
        if (clean.isEmpty() || info.sections.any { it.equals(clean, ignoreCase = true) }) return
        if (info.sections.size >= CategoryRepository.MAX_SECTIONS) {
            viewModelScope.launch { _events.emit(ListBoardEvent.Message(str(R.string.list_max_sections, CategoryRepository.MAX_SECTIONS))) }
            return
        }
        viewModelScope.launch { categories.saveListInfo(info.copy(sections = info.sections + clean)) }
    }

    fun renameSection(from: String, to: String) {
        val clean = to.trim()
        val info = uiState.value.info
        if (clean.isEmpty() || clean == from || info.sections.any { it.equals(clean, ignoreCase = true) && it != from }) return
        viewModelScope.launch {
            categories.saveListInfo(info.copy(sections = info.sections.map { if (it == from) clean else it }))
            tasks.renameSection(listName, from, clean)
        }
    }

    /** Xóa mục: việc trong mục chuyển về "Chưa phân mục" (không xóa việc). */
    fun deleteSection(name: String) {
        val info = uiState.value.info
        viewModelScope.launch {
            categories.saveListInfo(info.copy(sections = info.sections - name))
            tasks.renameSection(listName, name, "")
        }
    }

    /** Đổi vị trí mục sang trái (-1) / phải (+1). */
    fun shiftSection(name: String, delta: Int) {
        val info = uiState.value.info
        val list = info.sections.toMutableList()
        val from = list.indexOf(name)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { categories.saveListInfo(info.copy(sections = list)) }
    }

    fun saveAppearance(color: String, emoji: String) {
        viewModelScope.launch { categories.saveListInfo(uiState.value.info.copy(color = color, emoji = emoji)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ListBoardViewModel(createSavedStateHandle(), ServiceLocator.categoryRepository, ServiceLocator.taskRepository)
            }
        }
    }
}
