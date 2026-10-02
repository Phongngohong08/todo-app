package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.Eisenhower
import com.example.todoapplication.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MatrixUiState(
    val isLoading: Boolean = true,
    val quadrants: Map<Eisenhower.Quadrant, List<Task>> = Eisenhower.Quadrant.entries.associateWith { emptyList() }
)

sealed interface MatrixEvent {
    /** Đã chuyển ô — Hoàn tác trả lại ưu tiên + hạn cũ. */
    data class Moved(val task: Task, val target: Eisenhower.Quadrant) : MatrixEvent
}

/**
 * [TẦNG VIEWMODEL] Ma trận Eisenhower: chia việc đang chờ thành 4 ô Quan trọng × Gấp.
 * Kéo việc sang ô khác → đổi ưu tiên và/hoặc hạn theo [Eisenhower.move].
 */
class MatrixViewModel(
    private val repo: TaskRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    val uiState: StateFlow<MatrixUiState> = repo.observeTasks(null, null)
        .map { tasks -> MatrixUiState(isLoading = false, quadrants = Eisenhower.group(tasks, today(), zone())) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatrixUiState())

    private val _events = MutableSharedFlow<MatrixEvent>()
    val events: SharedFlow<MatrixEvent> = _events.asSharedFlow()

    fun move(taskId: String, target: Eisenhower.Quadrant) {
        val task = uiState.value.quadrants.values.flatten().firstOrNull { it.id == taskId } ?: return
        val change = Eisenhower.move(task, target, today(), zone())
        if (change.isEmpty) return
        viewModelScope.launch {
            change.priority?.let { repo.setPriority(task.id, it) }
            if (change.dueAt != null) repo.reschedule(listOf(task.id), change.dueAt, change.allDay ?: false)
            _events.emit(MatrixEvent.Moved(task, target))
        }
    }

    fun undo(previous: Task) {
        viewModelScope.launch {
            repo.setPriority(previous.id, previous.priority)
            repo.reschedule(listOf(previous.id), previous.dueAt, previous.dueAllDay)
        }
    }

    fun complete(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, completed = true) }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    companion object {
        val Factory = viewModelFactory {
            initializer { MatrixViewModel(ServiceLocator.taskRepository) }
        }
    }
}
