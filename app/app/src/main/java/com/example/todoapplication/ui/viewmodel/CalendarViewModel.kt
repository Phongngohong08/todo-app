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

/*
 * [TẦNG VIEWMODEL] Màn Lịch: tải mọi task rồi KHAI TRIỂN các việc lặp (DAILY/WEEKLY/MONTHLY)
 * thành từng ngày cụ thể trong 12 tháng tới, gom theo ngày (tasksByDay) để vẽ lên lịch.
 */

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
            // runCatching: nếu tải lỗi thì dùng list rỗng thay vì crash (lịch chỉ để xem, không cần chặt chẽ).
            val tasks = runCatching { repo.loadTasks(null, null).tasks }.getOrDefault(emptyList())
            // map: "ngày" → danh sách task rơi vào ngày đó. Đây chính là dữ liệu để vẽ lịch.
            val map = HashMap<String, MutableList<Task>>()
            // Chiếu lịch tới 12 tháng sau để hiển thị cả các lần lặp
            val horizon = Calendar.getInstance().apply { add(Calendar.MONTH, 12) }.time

            tasks.forEach { task ->
                // Bỏ qua task không có hạn (không biết đặt vào ngày nào). return@forEach = "continue" của forEach.
                val due = task.dueDate?.let { parseIso8601(it) } ?: return@forEach
                // Nếu lặp theo tuần thì lấy tập các thứ cần rơi vào (vd {Thứ 2, Thứ 4}).
                val weekdays = if (task.recurrence == "WEEKLY") parseWeekdaySet(task.recurrenceDays) else emptySet()
                val occ = Calendar.getInstance().apply { time = due }   // "con trỏ" ngày, bắt đầu từ hạn gốc
                var guard = 0
                // Sinh dần các lần xuất hiện cho tới mốc 12 tháng. guard<800: chốt chặn tránh lặp vô hạn.
                while (occ.time.before(horizon) && guard < 800) {
                    // WEEKLY: chỉ nhận ngày đúng thứ mong muốn; các kiểu khác luôn nhận.
                    val matches = weekdays.isEmpty() || weekdays.contains(occ.get(Calendar.DAY_OF_WEEK))
                    if (matches) map.getOrPut(dayKey(occ)) { mutableListOf() }.add(task)  // gắn task vào ngày
                    // Nhảy tới lần lặp kế tiếp tùy kiểu tái diễn:
                    when (task.recurrence) {
                        "DAILY" -> occ.add(Calendar.DAY_OF_MONTH, 1)     // mỗi ngày
                        // WEEKLY có chọn thứ: đi từng ngày để kiểm tra thứ; nếu không chọn thứ nào thì +7 ngày.
                        "WEEKLY" -> if (weekdays.isNotEmpty()) occ.add(Calendar.DAY_OF_MONTH, 1) else occ.add(Calendar.DAY_OF_MONTH, 7)
                        "MONTHLY" -> occ.add(Calendar.MONTH, 1)          // mỗi tháng
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

    /** Đổi "MON,WED,FRI" thành tập các giá trị Calendar.DAY_OF_WEEK. */
    private fun parseWeekdaySet(days: String): Set<Int> {
        if (days.isBlank()) return emptySet()
        val map = mapOf(
            "SUN" to Calendar.SUNDAY, "MON" to Calendar.MONDAY, "TUE" to Calendar.TUESDAY,
            "WED" to Calendar.WEDNESDAY, "THU" to Calendar.THURSDAY, "FRI" to Calendar.FRIDAY, "SAT" to Calendar.SATURDAY
        )
        return days.split(",").mapNotNull { map[it.trim().uppercase()] }.toSet()
    }

    companion object {
        fun keyOf(year: Int, month0: Int, day: Int) = "%04d-%02d-%02d".format(year, month0 + 1, day)

        val Factory = viewModelFactory {
            initializer { CalendarViewModel(ServiceLocator.taskRepository) }
        }
    }
}
