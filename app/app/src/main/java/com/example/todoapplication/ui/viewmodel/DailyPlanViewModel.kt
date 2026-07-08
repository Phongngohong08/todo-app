package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.repository.ApiException
import com.example.todoapplication.data.repository.PlanRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/*
 * [TẦNG VIEWMODEL] Màn Kế hoạch ngày: xem lịch hôm nay (getDaily) hoặc nhờ AI tạo lại (generateDaily).
 * Điểm đáng chú ý: phân loại kết quả (hết lượt AI / lỗi mạng / lịch rỗng / thành công) để báo đúng.
 */

data class DailyPlanUiState(
    val plan: DailyPlan? = null,
    val isLoading: Boolean = true
)

class DailyPlanViewModel(private val repo: PlanRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyPlanUiState())
    val uiState: StateFlow<DailyPlanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun loadPlan() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val localTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val result = repo.getDaily(today, localTime)
            _uiState.update { it.copy(plan = result.getOrNull(), isLoading = false) }
        }
    }

    fun regenerate() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val localTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val result = repo.generateDaily(localTime)
            _uiState.update { it.copy(plan = result.getOrNull(), isLoading = false) }
            // Phân biệt các trường hợp: hết lượt AI (429) / lỗi mạng / lịch rỗng (hết khung giờ) / thành công.
            // Trước đây luôn báo "thành công" kể cả khi lịch rỗng -> người dùng tưởng app hỏng.
            val error = result.exceptionOrNull()
            val msg = when {
                error is ApiException && error.isRateLimited ->
                    "AI đang quá tải hoặc đã hết lượt hôm nay. Hãy thử lại sau ít phút."
                result.isFailure ->
                    "Tạo lịch trình thất bại. Kiểm tra kết nối rồi thử lại."
                result.getOrNull()?.planData.isNullOrEmpty() ->
                    "AI chưa xếp được khung giờ nào — có thể đã quá giờ làm việc trong ngày. " +
                        "Hãy nới giờ kết thúc trong Cài đặt hoặc thử lại vào ban ngày."
                else ->
                    "Đã tạo lịch trình!"
            }
            _events.emit(msg)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DailyPlanViewModel(ServiceLocator.planRepository) }
        }
    }
}
