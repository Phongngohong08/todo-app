package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.model.PlanSlot
import com.example.todoapplication.data.notifications.FocusSession
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.domain.GoalProgress
import com.example.todoapplication.domain.PlanLogic
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.navigation.QuickCreateRequest
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.weekdayShort
import com.example.todoapplication.ui.viewmodel.TodayEvent
import com.example.todoapplication.ui.viewmodel.TodayUiState
import com.example.todoapplication.ui.viewmodel.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * [TẦNG UI · MÀN HÌNH] Tab "Hôm nay" — nơi bắt đầu mỗi ngày:
 * mục tiêu + chuỗi ngày → "Ngày của tôi" → gợi ý thêm việc → lịch trình AI (tích xong, đổi giờ, bắt đầu tập trung).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    navController: NavController,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus by FocusSession.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val askNotificationPermission = rememberNotificationPermissionRequest()

    var quickCreateText by remember { mutableStateOf<String?>(null) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    // Yêu cầu mở thanh tạo nhanh từ ngoài app (shortcut / chia sẻ / widget)
    val requested by QuickCreateRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        requested?.let {
            quickCreateText = it
            QuickCreateRequest.pending.value = null
        }
    }

    LaunchedEffect(Unit) { viewModel.loadPlan() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is TodayEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
                is TodayEvent.Completed -> scope.launch {
                    val r = snackbar.showSnackbar("Đã hoàn thành: ${e.title}", actionLabel = "Hoàn tác", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.reopen(e.taskId)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { quickCreateText = "" },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, contentDescription = "Thêm việc cho hôm nay") }
        },
        bottomBar = { BottomNavigationBar(navController, activeTab = 0) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = padding.calculateBottomPadding() + 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                TodayHeader(
                    state = state,
                    onCoach = { navController.navigate(Screen.AICoach.createRoute()) }
                )
            }

            focus?.let { f ->
                item(key = "focus") {
                    FocusMiniBar(f) { navController.navigate(Screen.Focus.createRoute(f.taskId)) }
                }
            }

            item(key = "sync") { SyncBanner(state.isOnline, state.pendingSyncCount) }

            // ── Ngày của tôi ──
            item(key = "myday-header") {
                SectionHeader("☀ Ngày của tôi", state.myDay.size - state.myDayDone)
            }
            if (state.myDay.isEmpty()) {
                item(key = "myday-empty") {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Chọn vài việc bạn cam kết làm hôm nay — từ gợi ý bên dưới, hoặc nhấn + để thêm. " +
                                "Danh sách tự làm mới mỗi sáng.",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(state.myDay, key = { "my-" + it.id }) { task ->
                Box(Modifier.animateItem()) {
                    val card = @Composable {
                        TaskCard(
                            task = task,
                            onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                            onToggleComplete = { viewModel.toggleComplete(task) },
                            onDeleteClick = { viewModel.removeFromMyDay(task) },
                            isInMyDay = true,
                            onToggleMyDay = { viewModel.removeFromMyDay(task) },
                            onFocus = { navController.navigate(Screen.Focus.createRoute(task.id)) },
                            today = state.today
                        )
                    }
                    if (task.isCompleted) card() else SwipeToCompleteBox(onComplete = { viewModel.toggleComplete(task) }) { card() }
                }
            }

            // ── Gợi ý ──
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggest-header") {
                    SectionHeader("Gợi ý cho hôm nay", state.suggestions.size, action = {
                        TextButton(onClick = { viewModel.addToMyDay(state.suggestions.map { it.task.id }) }) {
                            Text("Thêm tất cả", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    })
                }
                items(state.suggestions, key = { "sg-" + it.task.id }) { s ->
                    SuggestionRow(
                        title = s.task.title,
                        reason = s.reason,
                        accent = priorityColor(s.task.priority),
                        onAdd = { viewModel.addToMyDay(listOf(s.task.id)) },
                        onOpen = { navController.navigate(Screen.TaskDetail.createRoute(s.task.id)) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // ── Lịch trình AI ──
            item(key = "plan-header") {
                PlanHeader(state, onRegenerate = viewModel::regeneratePlan)
            }
            planContent(state, navController, viewModel, onEditTime = { editingSlot = it })
        }
    }

    editingSlot?.let { index ->
        val slot = state.plan.plan?.planData?.getOrNull(index)
        if (slot == null) {
            editingSlot = null
        } else {
            SlotTimeDialog(
                slot = slot,
                onDismiss = { editingSlot = null },
                onConfirm = { time -> viewModel.moveSlot(index, time); editingSlot = null },
                onRemove = { viewModel.removeSlot(index); editingSlot = null }
            )
        }
    }

    quickCreateText?.let { initial ->
        QuickCreateSheet(
            categories = state.categories,
            initialText = initial,
            defaultAddToMyDay = true,
            onDismiss = { quickCreateText = null },
            onCreate = { result ->
                viewModel.createQuickTask(result)
                if (result.draft.dueAt != null) askNotificationPermission("Bật thông báo để được nhắc khi việc đến hạn.")
                quickCreateText = null
            },
            onMoreDetails = { result ->
                QuickAddDraft.set(QuickAddDraft.Prefill(result.draft, result.addToMyDay))
                quickCreateText = null
                navController.navigate(Screen.TaskDetail.createRoute("new"))
            },
            onAiAdd = {
                // Phân tích AI nằm ở tab Việc làm (cần mạng); ở đây mở form chi tiết với câu đã gõ
                QuickAddDraft.set(QuickAddDraft.Prefill(com.example.todoapplication.domain.model.TaskDraft(title = it), true))
                quickCreateText = null
                navController.navigate(Screen.TaskDetail.createRoute("new"))
            },
            onTemplates = {
                quickCreateText = null
                navController.navigate(Screen.Templates.route)
            }
        )
    }
}

// ─── Header: ngày + mục tiêu + chuỗi ─────────────────────────────────────────

@Composable
private fun TodayHeader(state: TodayUiState, onCoach: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(primary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f))))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${weekdayShort(state.today)}, ${state.today.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                    Text(greeting(state.userName), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                }
                IconButton(onClick = onCoach, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SmartToy, contentDescription = "AI Coach", tint = Color.White)
                }
            }
            state.goal?.let { goal ->
                Spacer(Modifier.height(14.dp))
                GoalRow(goal)
            }
        }
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val part = when (h) {
        in 5..10 -> "Chào buổi sáng"
        in 11..13 -> "Chào buổi trưa"
        in 14..17 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }
    return "$part, $name!"
}

@Composable
private fun GoalRow(goal: GoalProgress) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
            CircularProgressIndicator(
                progress = { (goal.doneToday.toFloat() / goal.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeWidth = 5.dp
            )
            Text("${goal.doneToday}/${goal.goal}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                when {
                    goal.metToday -> "Đạt mục tiêu hôm nay! 🎉"
                    goal.isDayOffToday -> "Hôm nay là ngày nghỉ — làm được gì cũng là thêm"
                    else -> "Còn ${goal.remainingToday} việc để đạt mục tiêu"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                if (goal.currentStreak > 0) "🔥 Chuỗi ${goal.currentStreak} ngày · kỷ lục ${goal.bestStreak}"
                else "Đạt mục tiêu hôm nay để bắt đầu chuỗi 🔥",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FocusMiniBar(state: FocusSession.State, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.endAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = state.remainingMillis(now) / 1000
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.phase == FocusSession.Phase.FOCUS) "🍅" else "☕", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.phase == FocusSession.Phase.FOCUS) "Đang tập trung" else "Đang nghỉ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(state.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                if (remaining > 0) "%02d:%02d".format(remaining / 60, remaining % 60) else "Hết giờ",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SuggestionRow(title: String, reason: String, accent: Color, onAdd: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.WbSunny, contentDescription = "Thêm vào Ngày của tôi", tint = PriorityMediumColor)
            }
        }
    }
}

// ─── Lịch trình AI ───────────────────────────────────────────────────────────

@Composable
private fun PlanHeader(state: TodayUiState, onRegenerate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("🗓 Lịch trình AI", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (state.plan.isLoading || state.plan.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRegenerate, enabled = state.isOnline) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.plan.plan?.planData.isNullOrEmpty()) "Xếp lịch" else "Xếp lại", fontSize = 13.sp)
                }
            }
        }
        if (state.planIsStale && !state.plan.isLoading) {
            Surface(shape = RoundedCornerShape(12.dp), color = StatePostponed.copy(alpha = 0.13f), modifier = Modifier.fillMaxWidth().clickable(onClick = onRegenerate)) {
                Text(
                    "Danh sách việc đã thay đổi so với lịch — nhấn để xếp lại",
                    color = StatePostponed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        state.plan.error?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.planContent(
    state: TodayUiState,
    navController: NavController,
    viewModel: TodayViewModel,
    onEditTime: (Int) -> Unit
) {
    val slots = state.plan.plan?.planData.orEmpty()
    if (slots.isEmpty()) {
        if (!state.plan.isLoading && state.plan.error == null) {
            item(key = "plan-empty") {
                Text(
                    "Chưa có lịch. Chọn việc cho Ngày của tôi rồi nhấn \"Xếp lịch\" — AI sẽ xếp khung giờ theo thời lượng ước tính và thói quen của bạn.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    itemsIndexed(slots, key = { i, s -> "slot-$i-${s.taskId}" }) { index, slot ->
        val task = state.tasksById[slot.taskId]
        PlanSlotRow(
            slot = slot,
            isLast = index == slots.lastIndex,
            isDone = task?.isCompleted == true,
            exists = task != null,
            onToggle = { task?.let(viewModel::toggleComplete) },
            onOpen = { if (task != null) navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
            onEditTime = { onEditTime(index) },
            onFocus = { navController.navigate(Screen.Focus.createRoute(slot.taskId.takeIf { task != null })) }
        )
    }
}

@Composable
private fun PlanSlotRow(
    slot: PlanSlot,
    isLast: Boolean,
    isDone: Boolean,
    exists: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEditTime: () -> Unit,
    onFocus: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Giờ — bấm để đổi
        Column(
            Modifier.width(52.dp).clickable(onClick = onEditTime),
            horizontalAlignment = Alignment.End
        ) {
            Text(slot.start, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 14.dp))
            Text(slot.end, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) {
                Box(Modifier.width(2.dp).fillMaxHeight().offset(y = 16.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Box(
                Modifier
                    .size(14.dp)
                    .offset(y = 14.dp)
                    .background(if (isDone) Brush.linearGradient(listOf(StateCompleted, StateCompleted)) else Brush.linearGradient(listOf(primary, MaterialTheme.colorScheme.tertiary)), CircleShape)
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f).padding(bottom = 8.dp).clickable(onClick = onOpen)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (exists) {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isDone) StateCompleted else Color.Transparent)
                            .then(if (isDone) Modifier else Modifier.border(2.dp, primary.copy(alpha = 0.6f), CircleShape))
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) Icon(Icons.Default.Check, contentDescription = "Mở lại", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        slot.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${PlanLogic.minutes(slot)} phút", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isDone) {
                    IconButton(onClick = onFocus) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Bắt đầu tập trung", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

/** Đổi giờ bắt đầu một khung (giữ thời lượng) hoặc bỏ khung khỏi lịch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotTimeDialog(slot: PlanSlot, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit, onRemove: () -> Unit) {
    val start = runCatching { LocalTime.parse(slot.start.padStart(5, '0')) }.getOrDefault(LocalTime.of(9, 0))
    val pickerState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi giờ: ${slot.title}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = pickerState)
                Text(
                    "Các khung phía sau bị trùng sẽ được dời xuống.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("Lưu") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Bỏ khỏi lịch", color = PriorityHighColor) }
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        }
    )
}
