package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.model.PlanSlot
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.PlanRepository
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.StatsRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.GoalCalculator
import com.example.todoapplication.domain.GoalProgress
import com.example.todoapplication.domain.MyDaySuggestion
import com.example.todoapplication.domain.PlanLogic
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.myDaySuggestions
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.ui.screens.QuickCreateResult
import com.example.todoapplication.ui.utils.nowLocalClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Lịch trình AI của hôm nay (chỉ có khi online). */
data class PlanState(
    val plan: DailyPlan? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Chưa tải được (offline/lỗi) — hiện thông báo thay vì "chưa có lịch". */
    val error: String? = null
)

data class TodayUiState(
    val today: LocalDate = LocalDate.now(),
    val userName: String = "",
    /** Việc trong "Ngày của tôi": chưa xong trước, đã xong sau. */
    val myDay: List<Task> = emptyList(),
    val suggestions: List<MyDaySuggestion> = emptyList(),
    val goal: GoalProgress? = null,
    val plan: PlanState = PlanState(),
    /** Lịch AI không còn khớp với danh sách việc (có việc mới / việc đã xóa) → gợi ý xếp lại. */
    val planIsStale: Boolean = false,
    /** Để khung giờ biết việc tương ứng đã xong chưa. */
    val tasksById: Map<String, Task> = emptyMap(),
    val categories: List<String> = CategoryRepository.DEFAULTS,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0
) {
    val myDayDone: Int get() = myDay.count { it.isCompleted }
}

sealed interface TodayEvent {
    data class Message(val text: String) : TodayEvent
    data class Completed(val taskId: String, val title: String) : TodayEvent
}

/**
 * [TẦNG VIEWMODEL] Tab "Hôm nay" — trả lời câu hỏi "hôm nay làm gì?":
 *  - "Ngày của tôi" (Microsoft To Do): danh sách người dùng tự chọn mỗi ngày, tự làm mới lúc nửa đêm.
 *  - Gợi ý: việc quá hạn / đến hạn / hôm qua chưa xong để thêm nhanh.
 *  - Mục tiêu ngày + chuỗi ngày (Todoist Karma).
 *  - Lịch trình AI cho CÁC VIỆC ĐÃ CHỌN, tích xong ngay trên lịch, đổi giờ tay, báo khi lịch đã cũ.
 */
class TodayViewModel(
    private val repo: TaskRepository,
    private val planRepository: PlanRepository,
    private val categoryRepository: CategoryRepository,
    statsRepository: StatsRepository,
    private val sync: SyncController,
    localPrefs: LocalPrefs,
    sessionManager: SessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val userName = sessionManager.getUserName().ifBlank { "bạn" }
    private val planState = MutableStateFlow(PlanState())

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    private data class TaskPart(
        val today: LocalDate,
        val myDay: List<Task>,
        val suggestions: List<MyDaySuggestion>,
        val byId: Map<String, Task>,
        val pendingScope: Set<String>
    )

    private val taskPart = repo.observeTasks(null, null).map { tasks ->
        val now = clock()
        val day = today()
        val key = day.toString()
        val mine = tasks.filter { it.myDay == key }
        val pendingMine = mine.filter { !it.isCompleted }
        TaskPart(
            today = day,
            myDay = pendingMine + mine.filter { it.isCompleted },
            suggestions = myDaySuggestions(tasks, day, now, zone()),
            byId = tasks.associateBy { it.id },
            // Phạm vi xếp lịch giống server: việc của "Ngày của tôi" nếu đã chọn, không thì mọi việc đang chờ
            pendingScope = (pendingMine.ifEmpty { tasks.filter { !it.isCompleted } }).mapTo(HashSet()) { it.id }
        )
    }.flowOn(computeDispatcher)

    private val goal = combine(
        statsRepository.observeCompletedTimes(clock() - 400L * 24 * 3600 * 1000),
        localPrefs.dailyGoal,
        localPrefs.daysOff
    ) { times, goal, daysOff ->
        GoalCalculator.progress(times, goal, RecurrenceRules.parseWeekdays(daysOff), today(), zone())
    }.flowOn(computeDispatcher)

    private val syncInfo = combine(sync.isOnline, repo.observePendingSyncCount()) { online, pending -> online to pending }

    val uiState: StateFlow<TodayUiState> = combine(
        taskPart, goal, planState, categoryRepository.observeAll(), syncInfo
    ) { tasks, goal, plan, categories, (online, pending) ->
        val planIds = plan.plan?.planData.orEmpty().mapTo(HashSet()) { it.taskId }
        TodayUiState(
            today = tasks.today,
            userName = userName,
            myDay = tasks.myDay,
            suggestions = tasks.suggestions,
            goal = goal,
            plan = plan,
            planIsStale = plan.plan != null && planIds.isNotEmpty() &&
                PlanLogic.isStale(planIds, tasks.pendingScope, tasks.byId.keys),
            tasksById = tasks.byId,
            categories = categories,
            isOnline = online,
            pendingSyncCount = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(userName = userName))

    private val _events = MutableSharedFlow<TodayEvent>()
    val events: SharedFlow<TodayEvent> = _events.asSharedFlow()

    // ── "Ngày của tôi" ──

    fun addToMyDay(ids: List<String>) {
        viewModelScope.launch { repo.setMyDay(ids, today().toString()) }
    }

    fun removeFromMyDay(task: Task) {
        viewModelScope.launch { repo.setMyDay(listOf(task.id), null) }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            if (task.isCompleted) {
                repo.setCompleted(task.id, false)
            } else if (repo.setCompleted(task.id, true)) {
                _events.emit(TodayEvent.Completed(task.id, task.title))
            }
        }
    }

    fun reopen(taskId: String) {
        viewModelScope.launch { repo.setCompleted(taskId, false) }
    }

    fun createQuickTask(result: QuickCreateResult) {
        viewModelScope.launch {
            result.newCategory?.let { categoryRepository.add(it) }
            val created = repo.create(result.draft, myDay = if (result.addToMyDay) today().toString() else null)
            _events.emit(TodayEvent.Message("Đã thêm: ${created.title}"))
        }
    }

    // ── Lịch trình AI ──

    /** Xem lịch đã có (server tự tạo nếu chưa có). Offline → báo, không xóa lịch đang hiện. */
    fun loadPlan() {
        if (planState.value.isLoading) return
        planState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = planRepository.getDaily(today().toString(), nowLocalClock())
            planState.update { current ->
                result.fold(
                    onSuccess = { PlanState(plan = it) },
                    onFailure = { current.copy(isLoading = false, error = it.aiFailureMessage("Không tải được lịch trình (cần mạng).")) }
                )
            }
        }
    }

    /** Nhờ AI xếp lại (dùng "Ngày của tôi" nếu đã chọn). */
    fun regeneratePlan() {
        if (planState.value.isLoading) return
        planState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Gửi ngay các thay đổi chưa đồng bộ (vd vừa thêm vào Ngày của tôi) để server xếp đúng danh sách
            runCatching { sync.syncNow() }
            val result = planRepository.generateDaily(today().toString(), nowLocalClock())
            planState.update { current ->
                result.fold(
                    onSuccess = { PlanState(plan = it) },
                    onFailure = { current.copy(isLoading = false) }
                )
            }
            val msg = when {
                result.isFailure -> result.exceptionOrNull()!!.aiFailureMessage("Tạo lịch trình thất bại. Kiểm tra kết nối rồi thử lại.")
                result.getOrNull()?.planData.isNullOrEmpty() ->
                    "Chưa xếp được khung giờ nào — hãy thêm việc vào Ngày của tôi, hoặc nới giờ kết thúc trong Cài đặt."
                else -> "Đã xếp lịch cho hôm nay"
            }
            _events.emit(TodayEvent.Message(msg))
        }
    }

    /** Đổi giờ bắt đầu một khung (các khung sau bị chồng sẽ được dồn xuống). */
    fun moveSlot(index: Int, newStart: LocalTime) {
        val slots = planState.value.plan?.planData ?: return
        val updated = PlanLogic.moveSlot(slots, index, newStart)
        if (updated == null) {
            viewModelScope.launch { _events.emit(TodayEvent.Message("Không đủ chỗ trong ngày cho giờ đó")) }
            return
        }
        savePlan(updated)
    }

    fun removeSlot(index: Int) {
        val slots = planState.value.plan?.planData ?: return
        savePlan(slots.filterIndexed { i, _ -> i != index })
    }

    private fun savePlan(slots: List<PlanSlot>) {
        val previous = planState.value.plan ?: return
        // Hiện ngay (lạc quan), lưu lên server sau; lỗi thì trả lại bản cũ
        planState.update { it.copy(plan = previous.copy(planData = slots), isSaving = true) }
        viewModelScope.launch {
            val result = planRepository.saveEdited(today().toString(), slots)
            planState.update {
                result.fold(
                    onSuccess = { saved -> it.copy(plan = saved, isSaving = false) },
                    onFailure = { _ -> it.copy(plan = previous, isSaving = false) }
                )
            }
            if (result.isFailure) _events.emit(TodayEvent.Message("Không lưu được lịch trình (cần mạng)"))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TodayViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.planRepository,
                    ServiceLocator.categoryRepository,
                    ServiceLocator.statsRepository,
                    ServiceLocator.syncController,
                    ServiceLocator.localPrefs,
                    ServiceLocator.sessionManager
                )
            }
        }
    }
}
