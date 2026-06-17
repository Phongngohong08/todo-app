package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PomodoroPhase { WORK, SHORT_BREAK, LONG_BREAK }

val PHASE_DURATIONS = mapOf(
    PomodoroPhase.WORK to 25 * 60,
    PomodoroPhase.SHORT_BREAK to 5 * 60,
    PomodoroPhase.LONG_BREAK to 15 * 60
)

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    val secondsLeft: Int = PHASE_DURATIONS[PomodoroPhase.WORK]!!,
    val isRunning: Boolean = false,
    val sessionCount: Int = 0,
    val isCompleted: Boolean = false
)

/**
 * Timer Pomodoro đặt trong ViewModel: vòng đếm sống trong viewModelScope nên **tồn tại qua
 * xoay màn hình / đổi cấu hình** (config change) — một minh họa MVVM điển hình.
 */
class PomodoroViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()

    /** Phát tín hiệu rung cho UI (side-effect cần Context, để tầng UI xử lý). */
    private val _vibrate = MutableSharedFlow<Unit>()
    val vibrate: SharedFlow<Unit> = _vibrate.asSharedFlow()

    private var tickJob: Job? = null

    fun toggleRunning() {
        if (_uiState.value.isRunning) pause() else start()
    }

    private fun start() {
        _uiState.update { it.copy(isRunning = true, isCompleted = false) }
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (_uiState.value.isRunning && _uiState.value.secondsLeft > 0) {
                delay(1000L)
                _uiState.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            if (_uiState.value.isRunning && _uiState.value.secondsLeft == 0) advancePhase()
        }
    }

    private fun pause() {
        tickJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun reset() {
        tickJob?.cancel()
        _uiState.update {
            it.copy(isRunning = false, isCompleted = false, secondsLeft = PHASE_DURATIONS[it.phase]!!)
        }
    }

    fun skip() = advancePhase()

    private fun advancePhase() {
        tickJob?.cancel()
        viewModelScope.launch { _vibrate.emit(Unit) }
        _uiState.update { s ->
            val (nextPhase, nextCount) = when (s.phase) {
                PomodoroPhase.WORK -> {
                    val c = s.sessionCount + 1
                    (if (c % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK) to c
                }
                else -> PomodoroPhase.WORK to s.sessionCount
            }
            s.copy(
                phase = nextPhase,
                sessionCount = nextCount,
                secondsLeft = PHASE_DURATIONS[nextPhase]!!,
                isRunning = false,
                isCompleted = true
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PomodoroViewModel() }
        }
    }
}
