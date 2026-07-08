package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.model.StatsSummary
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.data.repository.StatsRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.utils.parseIso8601
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

/*
 * [TẦNG VIEWMODEL] Màn Thống kê: tải số liệu tổng hợp + việc đã hoàn thành, rồi TỰ GOM ở client
 * thành biểu đồ tuần, bản đồ nhiệt cả năm và "ngày hoàn hảo". Cũng quản lý phần Trí nhớ AI.
 */

data class StatsUiState(
    val summary: StatsSummary? = null,
    val isLoadingStats: Boolean = false,
    val weekly: List<Int> = List(7) { 0 },
    val isLoadingWeekly: Boolean = false,
    val memories: List<MemoryItem> = emptyList(),
    val isLoadingMemories: Boolean = false,
    val isExtracting: Boolean = false,
    /** Số việc hoàn thành theo từng ngày trong năm ("yyyy-MM-dd" -> count) cho bản đồ nhiệt. */
    val yearly: Map<String, Int> = emptyMap(),
    /** Số ngày hoàn hảo = số ngày có hoàn thành ít nhất 1 việc trong năm. */
    val perfectDays: Int = 0
)

class StatsViewModel(
    private val repo: StatsRepository,
    private val aiRepository: AiRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun loadSummary() {
        _uiState.update { it.copy(isLoadingStats = true) }
        viewModelScope.launch {
            val result = repo.summary()
            _uiState.update { it.copy(summary = result.getOrNull(), isLoadingStats = false) }
        }
        loadYearly()
    }

    /** Tải việc đã hoàn thành rồi gom theo từng ngày trong năm hiện tại (cho bản đồ nhiệt + ngày hoàn hảo). */
    private fun loadYearly() {
        viewModelScope.launch {
            val result = repo.completedTasks()
            val map = HashMap<String, Int>()
            val year = Calendar.getInstance().get(Calendar.YEAR)
            result.getOrNull()?.forEach { task ->
                val d = parseIso8601(task.updatedAt) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = d }
                if (cal.get(Calendar.YEAR) == year) {
                    val key = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    map[key] = (map[key] ?: 0) + 1
                }
            }
            _uiState.update { it.copy(yearly = map, perfectDays = map.size) }
        }
    }

    fun loadWeekly() {
        _uiState.update { it.copy(isLoadingWeekly = true) }
        viewModelScope.launch {
            val result = repo.completedTasks()
            val counts = MutableList(7) { 0 }
            val now = Date()
            result.getOrNull()?.forEach { task ->
                val updated = parseIso8601(task.updatedAt) ?: return@forEach
                val daysSinceNow = ((now.time - updated.time) / (1000 * 60 * 60 * 24)).toInt()
                if (daysSinceNow in 0..6) counts[6 - daysSinceNow]++
            }
            _uiState.update { it.copy(weekly = counts, isLoadingWeekly = false) }
        }
    }

    fun loadMemories() {
        _uiState.update { it.copy(isLoadingMemories = true) }
        viewModelScope.launch {
            val result = aiRepository.listMemories()
            _uiState.update { it.copy(memories = result.getOrDefault(emptyList()), isLoadingMemories = false) }
        }
    }

    fun triggerExtraction() {
        _uiState.update { it.copy(isExtracting = true) }
        viewModelScope.launch {
            val result = aiRepository.triggerExtraction()
            result.fold(
                onSuccess = { res ->
                    val memResult = aiRepository.listMemories()
                    _uiState.update { it.copy(memories = memResult.getOrDefault(emptyList()), isExtracting = false) }
                    val msg = when {
                        res.extracted > 0 -> "Đã phân tích ${res.analyzed} hoạt động và rút ra ${res.extracted} thói quen mới."
                        res.analyzed == 0 -> "Chưa có hoạt động nào trong 30 ngày để phân tích."
                        else -> "Đã phân tích ${res.analyzed} hoạt động nhưng chưa rút ra thói quen mới."
                    }
                    _events.emit(msg)
                },
                onFailure = {
                    _uiState.update { it.copy(isExtracting = false) }
                    _events.emit("Phân tích thất bại")
                }
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            if (aiRepository.deleteMemory(id)) {
                _events.emit("Đã xóa trí nhớ")
                loadMemories()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StatsViewModel(ServiceLocator.statsRepository, ServiceLocator.aiRepository) }
        }
    }
}
