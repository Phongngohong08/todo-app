package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.UserPreferences
import com.example.todoapplication.data.repository.PreferencesRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * [TẦNG VIEWMODEL] Màn Cài đặt: tải/sửa/lưu cấu hình cá nhân (giờ làm việc, thời lượng mỗi việc)
 * — dữ liệu này để backend AI dựa vào mà xếp Kế hoạch ngày. get/update qua endpoint preferences.
 */

data class SettingsUiState(
    val morningStart: String = "08:00",
    val eveningEnd: String = "18:00",
    val workDuration: String = "60",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

class SettingsViewModel(private val repo: PreferencesRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repo.get().getOrNull()?.let { prefs ->
                _uiState.update {
                    it.copy(
                        morningStart = prefs.morningStartTime,
                        eveningEnd = prefs.eveningEndTime,
                        workDuration = prefs.workDurationPreference.toString()
                    )
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // 3 hàm set: người dùng gõ tới đâu, cập nhật state tới đó (chưa lưu server). setDuration lọc chỉ giữ chữ số.
    fun setMorning(v: String) = _uiState.update { it.copy(morningStart = v) }
    fun setEvening(v: String) = _uiState.update { it.copy(eveningEnd = v) }
    fun setDuration(v: String) = _uiState.update { it.copy(workDuration = v.filter { c -> c.isDigit() }) }

    // Bấm "Lưu": đọc state hiện tại, gửi PUT preferences lên server, rồi báo kết quả.
    fun save() {
        val s = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repo.update(
                UserPreferences(
                    userId = "",     // server tự gán theo token; client để trống
                    morningStartTime = s.morningStart,
                    eveningEndTime = s.eveningEnd,
                    // toIntOrNull() ?: 60 : nếu ô đang trống/không phải số thì mặc định 60 phút.
                    workDurationPreference = s.workDuration.toIntOrNull() ?: 60
                )
            )
            _uiState.update { it.copy(isSaving = false) }
            _events.emit(if (result.isSuccess) "Cấu hình đã được lưu!" else "Lưu cấu hình thất bại")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(ServiceLocator.preferencesRepository) }
        }
    }
}
