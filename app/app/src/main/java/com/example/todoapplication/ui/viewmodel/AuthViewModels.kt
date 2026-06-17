package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sự kiện một lần cho màn xác thực (toast + điều hướng). */
sealed interface AuthEvent {
    data class Success(val message: String) : AuthEvent
    data class Error(val message: String) : AuthEvent
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(AuthEvent.Error("Vui lòng điền đầy đủ thông tin")) }
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.login(email.trim(), password)
            _isLoading.value = false
            result.fold(
                onSuccess = { _events.emit(AuthEvent.Success("Chào mừng quay trở lại, ${it.user.name}!")) },
                onFailure = { _events.emit(AuthEvent.Error("Tài khoản hoặc mật khẩu không chính xác")) }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(ServiceLocator.authRepository) }
        }
    }
}

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(AuthEvent.Error("Vui lòng điền đầy đủ thông tin")) }
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.register(name.trim(), email.trim(), password)
            _isLoading.value = false
            result.fold(
                onSuccess = { _events.emit(AuthEvent.Success("Đăng ký thành công! Hãy đăng nhập.")) },
                onFailure = { _events.emit(AuthEvent.Error("Đăng ký thất bại. Email có thể đã tồn tại.")) }
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(ServiceLocator.authRepository) }
        }
    }
}
