package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.WeeklyReview
import com.example.todoapplication.domain.WeeklyReviewCalculator
import com.example.todoapplication.domain.model.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * [TẦNG VIEWMODEL] Các màn "nhìn lại": Tổng kết tuần, Lịch sử hoàn thành, Thùng rác.
 * Tất cả đọc từ Room nên xem được cả khi offline.
 */

/** Tổng kết 7 ngày gần nhất so với 7 ngày trước (Weekly Review của GTD). */
class WeeklyReviewViewModel(
    repo: TaskRepository,
    prefs: LocalPrefs,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {
    private fun today() = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    val review: StateFlow<WeeklyReview?> = combine(
        repo.observeCompletedSince(today().minusDays(13).atStartOfDay(zone()).toInstant().toEpochMilli()),
        repo.observeTasks(null, null).map { list -> list.filter { !it.isCompleted } },
        prefs.dailyGoal
    ) { completed, pending, goal ->
        WeeklyReviewCalculator.compute(completed, pending, today(), clock(), zone(), goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { WeeklyReviewViewModel(ServiceLocator.taskRepository, ServiceLocator.localPrefs) }
        }
    }
}

/** Việc đã hoàn thành, gom theo ngày hoàn thành (mới nhất trước). */
class HistoryViewModel(
    private val repo: TaskRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    val days: StateFlow<List<Pair<LocalDate, List<Task>>>?> = repo.observeCompletedHistory()
        .map { tasks ->
            tasks.groupBy { Instant.ofEpochMilli(it.completedAt ?: 0).atZone(zone()).toLocalDate() }
                .toList()
                .sortedByDescending { it.first }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reopen(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HistoryViewModel(ServiceLocator.taskRepository) }
        }
    }
}

/** Thùng rác 30 ngày: khôi phục hoặc xóa hẳn. */
class TrashViewModel(
    private val repo: TaskRepository,
    private val sync: SyncController
) : ViewModel() {

    val items: StateFlow<List<Task>?> = repo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun restore(task: Task) {
        viewModelScope.launch {
            if (repo.restore(task.id)) _messages.emit("Đã khôi phục: ${task.title}")
        }
    }

    fun deleteForever(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // Việc xóa chưa gửi lên server thì phải gửi trước — không thì lần tải toàn bộ sau sẽ làm nó sống lại
            if (repo.pendingSyncCount() > 0) runCatching { sync.syncNow() }
            val left = repo.purgeFromTrash(ids)
            _messages.emit(
                if (left == 0) "Đã xóa vĩnh viễn"
                else "$left mục sẽ được xóa hẳn sau khi đồng bộ (đang offline)"
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TrashViewModel(ServiceLocator.taskRepository, ServiceLocator.syncController) }
        }
    }
}
