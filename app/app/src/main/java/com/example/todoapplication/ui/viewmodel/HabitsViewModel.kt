package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.HabitRepository
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.HabitSummary
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitDraft
import com.example.todoapplication.domain.summarize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HabitsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val active: List<HabitSummary> = emptyList(),
    val archived: List<HabitSummary> = emptyList()
) {
    val todayDue: Int get() = active.count { it.dueToday || it.doneToday }
    val todayDone: Int get() = active.count { it.doneToday }
}

sealed interface HabitsEvent {
    data class Deleted(val habit: Habit) : HabitsEvent
    data class Message(val text: String) : HabitsEvent
}

/** [TẦNG VIEWMODEL] Màn Thói quen: danh sách + chuỗi + tiến độ tuần, đánh dấu theo ngày, thêm/sửa/xóa. */
class HabitsViewModel(
    private val repo: HabitRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    val uiState: StateFlow<HabitsUiState> = combine(
        repo.observeHabits(),
        repo.observeCheckins(today().minusDays(HISTORY_DAYS).toString())
    ) { habits, checkins ->
        val day = today()
        val z = zone()
        val summaries = habits.map { h ->
            val created = Instant.ofEpochMilli(h.createdAt).atZone(z).toLocalDate()
            HabitLogic.summarize(h, checkins[h.id].orEmpty(), day, created)
        }
        HabitsUiState(
            isLoading = false,
            today = day,
            active = summaries.filter { !it.habit.archived },
            archived = summaries.filter { it.habit.archived }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    private val _events = MutableSharedFlow<HabitsEvent>()
    val events: SharedFlow<HabitsEvent> = _events.asSharedFlow()

    fun toggle(habitId: String, date: LocalDate) {
        if (date.isAfter(today())) return
        viewModelScope.launch { repo.toggle(habitId, date.toString()) }
    }

    fun save(id: String?, draft: HabitDraft) {
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            if (id == null) repo.create(draft) else repo.update(id, draft)
        }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { if (repo.delete(habit.id)) _events.emit(HabitsEvent.Deleted(habit)) }
    }

    fun restore(habitId: String) {
        viewModelScope.launch { repo.restore(habitId) }
    }

    companion object {
        /** Đủ cho chuỗi dài và tỉ lệ 30 ngày. */
        const val HISTORY_DAYS = 400L

        val Factory = viewModelFactory {
            initializer { HabitsViewModel(ServiceLocator.habitRepository) }
        }
    }
}
