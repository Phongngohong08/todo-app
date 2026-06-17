package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.DailyPlan
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
            _events.emit(if (result.isSuccess) "Đã tái tạo lịch trình!" else "Tái tạo thất bại")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DailyPlanViewModel(ServiceLocator.planRepository) }
        }
    }
}
