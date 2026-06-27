package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.utils.parseIso8601
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class CalendarUiState(
    val isLoading: Boolean = true,
    /** Map "yyyy-MM-dd" (giờ địa phương) → các task đến hạn ngày đó. */
    val tasksByDay: Map<String, List<Task>> = emptyMap()
)

class CalendarViewModel(private val repo: TaskRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val tasks = runCatching { repo.loadTasks(null, null).tasks }.getOrDefault(emptyList())
            val map = HashMap<String, MutableList<Task>>()
            // Chiếu lịch tới 12 tháng sau để hiển thị cả các lần lặp
            val horizon = Calendar.getInstance().apply { add(Calendar.MONTH, 12) }.time

            tasks.forEach { task ->
                val due = task.dueDate?.let { parseIso8601(it) } ?: return@forEach
                val occ = Calendar.getInstance().apply { time = due }
                var guard = 0
                while (occ.time.before(horizon) && guard < 800) {
                    map.getOrPut(dayKey(occ)) { mutableListOf() }.add(task)
                    when (task.recurrence) {
                        "DAILY" -> occ.add(Calendar.DAY_OF_MONTH, 1)
                        "WEEKLY" -> occ.add(Calendar.DAY_OF_MONTH, 7)
                        "MONTHLY" -> occ.add(Calendar.MONTH, 1)
                        else -> break // không lặp: chỉ thêm đúng ngày hạn chót
                    }
                    guard++
                }
            }
            _uiState.update { it.copy(isLoading = false, tasksByDay = map) }
        }
    }

    private fun dayKey(cal: Calendar): String = "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )

    companion object {
        fun keyOf(year: Int, month0: Int, day: Int) = "%04d-%02d-%02d".format(year, month0 + 1, day)

        val Factory = viewModelFactory {
            initializer { CalendarViewModel(ServiceLocator.taskRepository) }
        }
    }
}
