package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.ApiException
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/*
 * [TẦNG VIEWMODEL] Nghiệp vụ cho hai màn Đăng nhập / Đăng ký.
 * Kiểm tra đầu vào → gọi AuthRepository → phát AuthEvent (thành công/lỗi) để màn hiện Toast + điều hướng.
 */

/** Sự kiện một lần cho màn xác thực (toast + điều hướng). */
sealed interface AuthEvent {
    data class Success(val message: String) : AuthEvent
    data class Error(val message: String) : AuthEvent
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    // State: cờ đang tải (để nút hiện spinner + khóa bấm). _private ghi được, public chỉ đọc.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Event: sự kiện dùng-một-lần (Toast + điều hướng). Dùng SharedFlow, KHÔNG để trong state
    // vì nếu để state thì xoay màn sẽ phát lại Toast/điều hướng lần nữa.
    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun login(email: String, password: String) {
        // 1. Kiểm tra đầu vào ngay tại client (chưa gọi mạng nếu bỏ trống).
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(AuthEvent.Error(str(R.string.auth_fill_all))) }
            return
        }
        _isLoading.value = true                              // 2. bật spinner
        viewModelScope.launch {                              // 3. coroutine: gọi mạng không chặn UI
            val result = authRepository.login(email.trim(), password)   // repo lo gọi API + lưu token
            _isLoading.value = false                         // 4. tắt spinner (dù thành công hay lỗi)
            // 5. fold: tách 2 nhánh của Result — thành công có data (it), thất bại có exception.
            result.fold(
                onSuccess = { _events.emit(AuthEvent.Success(str(R.string.auth_welcome_back, it.user.name))) },
                onFailure = { error ->
                    val msg = when ((error as? ApiException)?.code) {
                        401, 400 -> str(R.string.auth_wrong_credentials)
                        429 -> str(R.string.auth_login_rate_limited)
                        null -> str(R.string.error_cannot_reach_server)
                        else -> str(R.string.auth_login_failed)
                    }
                    _events.emit(AuthEvent.Error(msg))
                }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(ServiceLocator.authRepository) }
        }
    }
}

// RegisterViewModel: cấu trúc y hệt LoginViewModel ở trên, chỉ khác gọi register() và thông báo.
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(AuthEvent.Error(str(R.string.auth_fill_all))) }
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.register(name.trim(), email.trim(), password)
            _isLoading.value = false
            result.fold(
                onSuccess = { _events.emit(AuthEvent.Success(str(R.string.auth_register_success))) },
                onFailure = { error ->
                    // Backend: 409 = email đã dùng, 400 = dữ liệu không hợp lệ, 429 = đăng ký quá nhiều lần
                    val msg = when ((error as? ApiException)?.code) {
                        409 -> str(R.string.auth_email_taken)
                        400 -> str(R.string.auth_invalid_input)
                        429 -> str(R.string.auth_register_rate_limited)
                        else -> str(R.string.auth_register_failed)
                    }
                    _events.emit(AuthEvent.Error(msg))
                }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(ServiceLocator.authRepository) }
        }
    }
}
