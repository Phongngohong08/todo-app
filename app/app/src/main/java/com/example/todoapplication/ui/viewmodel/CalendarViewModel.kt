package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.recurrenceSpec
import com.example.todoapplication.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Một mục trên lịch: task thật, hoặc lần lặp DỰ KIẾN (chưa tồn tại — sẽ được tạo khi hoàn thành lần trước). */
data class CalendarEntry(val task: Task, val isProjected: Boolean)

data class CalendarUiState(
    val isLoading: Boolean = true,
    /** Map "yyyy-MM-dd" (giờ địa phương) → các mục rơi vào ngày đó. */
    val entriesByDay: Map<String, List<CalendarEntry>> = emptyMap()
)

/**
 * [TẦNG VIEWMODEL] Màn Lịch: quan sát task có hạn chót trong Room rồi gom theo ngày.
 * Việc lặp chưa hoàn thành được "chiếu" thêm các lần kế tiếp trong 12 tháng tới bằng CHÍNH quy tắc mà
 * repository/server dùng để tạo lần lặp thật (RecurrenceRules) — lịch dự kiến và thực tế không lệch nhau.
 */
class CalendarViewModel(
    repo: TaskRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    val uiState: StateFlow<CalendarUiState> = repo.observeScheduled()
        .map { tasks -> CalendarUiState(isLoading = false, entriesByDay = buildEntries(tasks)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun buildEntries(tasks: List<Task>): Map<String, List<CalendarEntry>> {
        val zone = zone()
        val horizon = LocalDate.now(zone).plusMonths(12).atStartOfDay(zone).toInstant().toEpochMilli()
        val map = HashMap<String, MutableList<CalendarEntry>>()
        fun add(millis: Long, entry: CalendarEntry) {
            map.getOrPut(dayKey(millis, zone)) { mutableListOf() }.add(entry)
        }

        tasks.forEach { task ->
            val due = task.dueAt ?: return@forEach
            add(due, CalendarEntry(task, isProjected = false))
            // Việc đã xong thì lần kế tiếp đã là một task thật — không chiếu thêm
            if (!task.isCompleted) {
                RecurrenceRules.projectedOccurrences(due, task.recurrenceSpec(), horizon, zone)
                    .forEach { add(it, CalendarEntry(task, isProjected = true)) }
            }
        }
        return map
    }

    companion object {
        fun keyOf(year: Int, month0: Int, day: Int) = "%04d-%02d-%02d".format(year, month0 + 1, day)

        private fun dayKey(millis: Long, zone: ZoneId): String =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

        val Factory = viewModelFactory {
            initializer { CalendarViewModel(ServiceLocator.taskRepository) }
        }
    }
}
