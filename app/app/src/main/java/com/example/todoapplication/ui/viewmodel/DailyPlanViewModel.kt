package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.data.repository.PlanRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.utils.nowLocalClock
import com.example.todoapplication.ui.utils.todayLocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * [TẦNG VIEWMODEL] Màn Kế hoạch ngày: xem lịch hôm nay (getDaily) hoặc nhờ AI tạo lại (generateDaily).
 * Điểm đáng chú ý: phân loại kết quả (hết lượt AI / lỗi mạng / lịch rỗng / thành công) để báo đúng.
 */

data class DailyPlanUiState(
    val plan: DailyPlan? = null,     // lịch của ngày; null = chưa có/đang tải → màn hiện gợi ý "Tạo lịch"
    val isLoading: Boolean = true
)

class DailyPlanViewModel(private val repo: PlanRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyPlanUiState())
    val uiState: StateFlow<DailyPlanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    // Xem lịch ĐÃ CÓ của hôm nay (không gọi AI). Gọi khi mở màn.
    fun loadPlan() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Ngày "2026-07-08" và giờ "14:30" theo múi giờ máy để gửi cho backend.
            val result = repo.getDaily(todayLocalDate(), nowLocalClock())
            // getOrNull(): thành công thì lấy plan, lỗi thì để null (màn tự hiện trạng thái trống).
            _uiState.update { it.copy(plan = result.getOrNull(), isLoading = false) }
        }
    }

    // Nhờ AI TẠO LẠI lịch (gọi Gemini bên backend, có thể mất 15-30s). Nút "Tạo lại".
    fun regenerate() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Gửi kèm ngày của máy: không có thì server lấy "hôm nay" theo múi giờ mặc định của nó.
            val result = repo.generateDaily(todayLocalDate(), nowLocalClock())
            _uiState.update { it.copy(plan = result.getOrNull(), isLoading = false) }
            // Phân biệt các trường hợp: hết lượt AI (429) / lỗi mạng / lịch rỗng / thành công.
            // Trước đây luôn báo "thành công" kể cả khi lịch rỗng -> người dùng tưởng app hỏng.
            val error = result.exceptionOrNull()
            val msg = when {
                error != null ->
                    error.aiFailureMessage("Tạo lịch trình thất bại. Kiểm tra kết nối rồi thử lại.")
                result.getOrNull()?.planData.isNullOrEmpty() ->
                    // Server trả lịch rỗng khi không còn việc chưa xong, hoặc khi đã hết giờ trong ngày
                    "Chưa xếp được khung giờ nào. Hãy kiểm tra còn công việc chưa hoàn thành, " +
                        "hoặc nới giờ kết thúc trong Cài đặt."
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
