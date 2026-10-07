package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.calendar.DeviceCalendar
import com.example.todoapplication.data.calendar.DeviceEvent
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.recurrenceSpec
import com.example.todoapplication.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Một mục trên lịch: task thật, hoặc lần lặp DỰ KIẾN (chưa tồn tại — sẽ được tạo khi hoàn thành lần trước). */
data class CalendarEntry(val task: Task, val isProjected: Boolean)

data class CalendarUiState(
    val isLoading: Boolean = true,
    /** Map "yyyy-MM-dd" (giờ địa phương) → các mục rơi vào ngày đó. */
    val entriesByDay: Map<String, List<CalendarEntry>> = emptyMap()
)

sealed interface CalendarEvent {
    /** Đã kéo việc sang ngày khác — Hoàn tác trả lại hạn cũ. */
    data class Moved(val previous: Task, val date: LocalDate) : CalendarEvent
}

/**
 * [TẦNG VIEWMODEL] Màn Lịch: quan sát task có hạn chót trong Room rồi gom theo ngày.
 * Việc lặp chưa hoàn thành được "chiếu" thêm các lần kế tiếp trong 12 tháng tới bằng CHÍNH quy tắc mà
 * repository/server dùng để tạo lần lặp thật (RecurrenceRules) — lịch dự kiến và thực tế không lệch nhau.
 */
class CalendarViewModel(
    private val repo: TaskRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    localPrefs: LocalPrefs? = null
) : ViewModel() {

    val uiState: StateFlow<CalendarUiState> = repo.observeScheduled()
        .map { tasks -> CalendarUiState(isLoading = false, entriesByDay = buildEntries(tasks)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    // ── Lịch của máy (CalendarContract) ──
    private val prefs = localPrefs
    val showDeviceCalendar: StateFlow<Boolean> = prefs?.showDeviceCalendar ?: MutableStateFlow(false)

    private val _deviceEvents = MutableStateFlow<Map<String, List<DeviceEvent>>>(emptyMap())
    /** "yyyy-MM-dd" → sự kiện của lịch máy rơi vào ngày đó. */
    val deviceEvents: StateFlow<Map<String, List<DeviceEvent>>> = _deviceEvents

    fun setShowDeviceCalendar(enabled: Boolean, context: android.content.Context) {
        prefs?.setShowDeviceCalendar(enabled)
        reloadDeviceEvents(context)
    }

    /** Đọc lại sự kiện (gọi khi bật, khi vừa cấp quyền, khi quay lại màn hình). Khoảng: 3 tháng trước → 6 tháng tới. */
    fun reloadDeviceEvents(context: android.content.Context) {
        if (!showDeviceCalendar.value || !DeviceCalendar.hasPermission(context)) {
            _deviceEvents.value = emptyMap()
            return
        }
        val app = context.applicationContext
        viewModelScope.launch {
            val z = zone()
            val today = LocalDate.now(z)
            val from = today.minusMonths(3).atStartOfDay(z).toInstant().toEpochMilli()
            val to = today.plusMonths(6).atStartOfDay(z).toInstant().toEpochMilli()
            _deviceEvents.value = DeviceCalendar.byDay(DeviceCalendar.events(app, from, to), z)
        }
    }

    /** Số phút làm việc mỗi ngày (để tô đỏ ngày quá tải trong lịch tuần). */
    val dailyCapacityMinutes: StateFlow<Int> = localPrefs?.dailyCapacityMinutes ?: MutableStateFlow(LocalPrefs.DEFAULT_DAILY_CAPACITY)

    private val _events = MutableSharedFlow<CalendarEvent>()
    val events: SharedFlow<CalendarEvent> = _events.asSharedFlow()

    /** Kéo-thả trên lịch tuần: chuyển việc sang [date], giữ giờ hẹn (việc cả ngày vẫn là cả ngày). */
    fun moveTask(taskId: String, date: LocalDate) {
        val task = uiState.value.entriesByDay.values.asSequence().flatten()
            .firstOrNull { it.task.id == taskId && !it.isProjected }?.task ?: return
        if (task.isCompleted) return
        val currentDay = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDate() }
        if (currentDay == date) return
        viewModelScope.launch {
            if (repo.moveToDate(listOf(taskId), date)) _events.emit(CalendarEvent.Moved(task, date))
        }
    }

    fun undoMove(previous: Task) {
        viewModelScope.launch { repo.reschedule(listOf(previous.id), previous.dueAt, previous.dueAllDay) }
    }

    fun complete(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, completed = !task.isCompleted) }
    }

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
        // Trong ngày: việc có giờ theo giờ, việc cả ngày xếp cuối
        map.values.forEach { list -> list.sortWith(compareBy({ it.task.dueAllDay }, { it.task.dueAt })) }
        return map
    }

    companion object {
        fun keyOf(year: Int, month0: Int, day: Int) = "%04d-%02d-%02d".format(year, month0 + 1, day)

        private fun dayKey(millis: Long, zone: ZoneId): String =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

        val Factory = viewModelFactory {
            initializer { CalendarViewModel(ServiceLocator.taskRepository, localPrefs = ServiceLocator.localPrefs) }
        }
    }
}
