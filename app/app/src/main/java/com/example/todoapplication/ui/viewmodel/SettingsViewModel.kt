package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.UserPreferences
import com.example.todoapplication.data.notifications.DigestScheduler
import com.example.todoapplication.data.repository.ApiException
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.PreferencesRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.utils.WEEKDAY_SHORT
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * [TẦNG VIEWMODEL] Màn Cài đặt:
 *  - Nhịp sinh hoạt + mục tiêu ngày + ngày nghỉ (lưu server, theo tài khoản).
 *  - Thông báo: tóm tắt buổi sáng, tổng kết tuần, giờ nhắc việc "cả ngày" (lưu trên máy, áp dụng ngay).
 *  - Tài khoản: đăng xuất máy này / mọi thiết bị.
 */

data class SettingsUiState(
    val morningStart: String = "08:00",
    val eveningEnd: String = "18:00",
    val workDuration: String = "60",
    val dailyGoal: Int = LocalPrefs.DEFAULT_DAILY_GOAL,
    val daysOff: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    /** Còn thay đổi chưa gửi được lên server — hỏi lại trước khi đăng xuất (sẽ mất chúng). */
    data class ConfirmLogout(val pendingChanges: Int, val allDevices: Boolean) : SettingsEvent
    data object LoggedOut : SettingsEvent
}

class SettingsViewModel(
    private val repo: PreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val localPrefs: LocalPrefs,
    private val taskRepository: TaskRepository,
    private val sync: SyncController,
    private val authRepository: AuthRepository,
    /** Hẹn lại thông báo định kỳ sau khi đổi cài đặt (DigestScheduler cần Context — test truyền lambda rỗng). */
    private val rescheduleNotifications: () -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            dailyGoal = localPrefs.dailyGoal.value,
            daysOff = localPrefs.daysOff.value.split(",").filter { it.isNotBlank() }.toSet()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** Danh mục (mặc định + tự tạo) từ Room — thêm/xóa được cả khi offline, đồng bộ theo tài khoản. */
    val categories: StateFlow<List<String>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryRepository.DEFAULTS)

    // Cài đặt thông báo (theo thiết bị)
    val digestEnabled: StateFlow<Boolean> = localPrefs.digestEnabled
    val digestTime: StateFlow<String> = localPrefs.digestTime
    val weeklyReviewEnabled: StateFlow<Boolean> = localPrefs.weeklyReviewEnabled
    val allDayReminderTime: StateFlow<String> = localPrefs.allDayReminderTime

    fun addCategory(name: String) {
        viewModelScope.launch { categoryRepository.add(name) }
    }

    fun removeCategory(name: String) {
        viewModelScope.launch { categoryRepository.remove(name) }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repo.get().getOrNull()?.let { prefs ->
                _uiState.update {
                    it.copy(
                        morningStart = prefs.morningStartTime,
                        eveningEnd = prefs.eveningEndTime,
                        workDuration = prefs.workDurationPreference.toString(),
                        dailyGoal = prefs.dailyGoal ?: it.dailyGoal,
                        daysOff = prefs.daysOff?.split(",")?.filter { d -> d.isNotBlank() }?.toSet() ?: it.daysOff
                    )
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // Người dùng sửa tới đâu, cập nhật state tới đó (chưa lưu server). setDuration lọc chỉ giữ chữ số.
    fun setMorning(v: String) = _uiState.update { it.copy(morningStart = v) }
    fun setEvening(v: String) = _uiState.update { it.copy(eveningEnd = v) }
    fun setDuration(v: String) = _uiState.update { it.copy(workDuration = v.filter { c -> c.isDigit() }) }
    fun setDailyGoal(v: Int) = _uiState.update { it.copy(dailyGoal = v.coerceIn(1, LocalPrefs.MAX_DAILY_GOAL)) }
    fun toggleDayOff(code: String) = _uiState.update {
        it.copy(daysOff = if (code in it.daysOff) it.daysOff - code else it.daysOff + code)
    }

    // Bấm "Lưu": gửi PUT preferences lên server, rồi báo kết quả.
    fun save() {
        val s = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val daysOff = WEEKDAY_SHORT.keys.filter { it in s.daysOff }.joinToString(",")
            val result = repo.update(
                UserPreferences(
                    userId = "",     // server tự gán theo token; client để trống
                    morningStartTime = s.morningStart,
                    eveningEndTime = s.eveningEnd,
                    workDurationPreference = s.workDuration.toIntOrNull() ?: 60,
                    dailyGoal = s.dailyGoal,
                    daysOff = daysOff
                )
            )
            if (result.isFailure) {
                // Server không lưu được (offline) vẫn giữ mục tiêu trên máy để chuỗi ngày tính đúng ngay
                localPrefs.setDailyGoal(s.dailyGoal)
                localPrefs.setDaysOff(daysOff)
            }
            _uiState.update { it.copy(isSaving = false) }
            val error = result.exceptionOrNull()
            _events.emit(
                SettingsEvent.Message(
                    when {
                        error == null -> "Đã lưu thiết lập!"
                        // Server kiểm tra: giờ dạng HH:mm, giờ kết thúc sau giờ bắt đầu, thời lượng 15–480 phút
                        (error as? ApiException)?.code == 400 ->
                            "Thiết lập chưa hợp lệ: giờ dạng HH:mm, giờ kết thúc sau giờ bắt đầu, thời lượng 15–480 phút."
                        else -> "Chưa lưu được lên máy chủ (mục tiêu vẫn áp dụng trên máy này)"
                    }
                )
            )
        }
    }

    // ── Thông báo (áp dụng ngay) ──
    fun setDigestEnabled(v: Boolean) { localPrefs.setDigestEnabled(v); rescheduleNotifications() }
    fun setDigestTime(v: String) { localPrefs.setDigestTime(v); rescheduleNotifications() }
    fun setWeeklyReviewEnabled(v: Boolean) { localPrefs.setWeeklyReviewEnabled(v); rescheduleNotifications() }
    fun setAllDayReminderTime(v: String) { localPrefs.setAllDayReminderTime(v) }

    // ── Đăng xuất ──
    /**
     * Trước khi xóa dữ liệu trên máy, cố gửi nốt thay đổi chưa đồng bộ. Không gửi được (mất mạng)
     * thì hỏi lại người dùng thay vì âm thầm làm mất dữ liệu.
     * [allDevices] = false: chỉ máy này; true: thu hồi phiên trên mọi thiết bị.
     */
    fun requestLogout(allDevices: Boolean = false) {
        viewModelScope.launch {
            var pending = taskRepository.pendingSyncCount()
            if (pending > 0) {
                withTimeoutOrNull(LOGOUT_SYNC_TIMEOUT_MS) { sync.syncNow() }
                pending = taskRepository.pendingSyncCount()
            }
            if (pending > 0) _events.emit(SettingsEvent.ConfirmLogout(pending, allDevices)) else logoutNow(allDevices)
        }
    }

    fun logoutNow(allDevices: Boolean = false) {
        authRepository.logout(allDevices)
        viewModelScope.launch { _events.emit(SettingsEvent.LoggedOut) }
    }

    companion object {
        private const val LOGOUT_SYNC_TIMEOUT_MS = 8_000L

        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    ServiceLocator.preferencesRepository,
                    ServiceLocator.categoryRepository,
                    ServiceLocator.localPrefs,
                    ServiceLocator.taskRepository,
                    ServiceLocator.syncController,
                    ServiceLocator.authRepository,
                    rescheduleNotifications = { DigestScheduler.scheduleAll(ServiceLocator.context) }
                )
            }
        }
    }
}
