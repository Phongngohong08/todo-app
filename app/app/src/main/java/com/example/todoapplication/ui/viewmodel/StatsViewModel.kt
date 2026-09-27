package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.StatsRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.GoalCalculator
import com.example.todoapplication.domain.GoalProgress
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.StatsCalculator
import com.example.todoapplication.domain.model.StatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class StatsUiState(
    val summary: StatsSummary? = null,
    val isLoadingStats: Boolean = true,
    /** 7 phần tử, index 6 = hôm nay. */
    val weekly: List<Int> = List(7) { 0 },
    val isLoadingWeekly: Boolean = true,
    /** Số việc hoàn thành theo từng ngày trong năm ("yyyy-MM-dd" -> count) cho bản đồ nhiệt. */
    val yearly: Map<String, Int> = emptyMap(),
    /** Số ngày trong năm ĐẠT MỤC TIÊU ngày (trước đây: chỉ cần xong 1 việc — quá dễ). */
    val perfectDays: Int = 0,
    /** Mục tiêu hôm nay + chuỗi ngày. */
    val goal: GoalProgress? = null,
    val memories: List<MemoryItem> = emptyList(),
    val isLoadingMemories: Boolean = false,
    val isExtracting: Boolean = false
)

/**
 * [TẦNG VIEWMODEL] Màn Thống kê. Số liệu công việc tính TRỰC TIẾP từ Room (Flow):
 * hoàn thành một việc ở màn khác → biểu đồ ở đây tự cập nhật, xem được cả khi offline.
 * Phần Trí nhớ AI vẫn cần mạng (dữ liệu nằm ở server).
 */
class StatsViewModel(
    repo: StatsRepository,
    private val aiRepository: AiRepository,
    localPrefs: LocalPrefs,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    private data class ChartData(val weekly: List<Int>, val yearly: Map<String, Int>, val goal: GoalProgress)

    private val today = LocalDate.now(zone())
    private val since = minOf(
        StatsCalculator.startOfYear(today.year, zone()),
        // đủ xa để tính chuỗi ngày cả khi đang đầu năm
        today.minusDays(400).atStartOfDay(zone()).toInstant().toEpochMilli()
    )

    // Gom thời điểm hoàn thành thành biểu đồ tuần + bản đồ nhiệt năm, trên luồng nền
    private val charts = combine(repo.observeCompletedTimes(since), localPrefs.dailyGoal, localPrefs.daysOff) { times, goal, daysOff ->
        ChartData(
            weekly = StatsCalculator.weekly(times, today, zone()),
            yearly = StatsCalculator.yearly(times, today.year, zone()),
            goal = GoalCalculator.progress(times, goal, RecurrenceRules.parseWeekdays(daysOff), today, zone())
        )
    }.flowOn(Dispatchers.Default)

    private val memoryState = MutableStateFlow(MemoryState())

    private data class MemoryState(
        val memories: List<MemoryItem> = emptyList(),
        val isLoading: Boolean = false,
        val isExtracting: Boolean = false
    )

    val uiState: StateFlow<StatsUiState> = combine(repo.observeSummary(), charts, memoryState) { summary, charts, memory ->
        StatsUiState(
            summary = summary,
            isLoadingStats = false,
            weekly = charts.weekly,
            isLoadingWeekly = false,
            yearly = charts.yearly,
            perfectDays = charts.yearly.values.count { it >= charts.goal.goal },
            goal = charts.goal,
            memories = memory.memories,
            isLoadingMemories = memory.isLoading,
            isExtracting = memory.isExtracting
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun loadMemories() {
        memoryState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = aiRepository.listMemories()
            memoryState.update { it.copy(memories = result.getOrDefault(emptyList()), isLoading = false) }
        }
    }

    // Bấm "Phân tích": nhờ backend chạy vòng trích xuất trí nhớ (đọc log 30 ngày → AI → lưu Qdrant),
    // rồi tải lại danh sách trí nhớ và báo kết quả tùy số lượng phân tích/rút ra.
    fun triggerExtraction() {
        memoryState.update { it.copy(isExtracting = true) }
        viewModelScope.launch {
            val result = aiRepository.triggerExtraction()
            result.fold(
                onSuccess = { res ->
                    val memories = aiRepository.listMemories().getOrDefault(emptyList())
                    memoryState.update { it.copy(memories = memories, isExtracting = false) }
                    _events.emit(
                        when {
                            res.extracted > 0 -> "Đã phân tích ${res.analyzed} hoạt động và rút ra ${res.extracted} thói quen mới."
                            res.analyzed == 0 -> "Chưa có hoạt động nào trong 30 ngày để phân tích."
                            else -> "Đã phân tích ${res.analyzed} hoạt động nhưng chưa rút ra thói quen mới."
                        }
                    )
                },
                onFailure = { error ->
                    memoryState.update { it.copy(isExtracting = false) }
                    _events.emit(error.aiFailureMessage("Phân tích thất bại"))
                }
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            if (aiRepository.deleteMemory(id)) {
                memoryState.update { state -> state.copy(memories = state.memories.filter { it.id != id }) }
                _events.emit("Đã xóa trí nhớ")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel(ServiceLocator.statsRepository, ServiceLocator.aiRepository, ServiceLocator.localPrefs) }
        }
    }
}
