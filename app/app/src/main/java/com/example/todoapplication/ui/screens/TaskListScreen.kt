package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.domain.SmartFilter
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.sortLabel
import com.example.todoapplication.ui.components.EmptyState
import com.example.todoapplication.ui.components.LoadingState
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.viewmodel.ALL_CATEGORIES
import com.example.todoapplication.ui.viewmodel.TaskListEvent
import com.example.todoapplication.ui.viewmodel.TaskListUiState
import com.example.todoapplication.ui.viewmodel.TaskListViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.plural

/**
 * [TẦNG UI · MÀN HÌNH] Tab "Việc làm": mọi công việc + tìm kiếm/lọc, tạo nhanh, kéo-thả, kéo-để-làm-mới.
 * Nhóm như Todoist: Quá hạn (có "Dời tất cả") · Hôm nay · Sắp tới · Chưa có hạn · Đã xong hôm nay.
 * Quy tắc Compose: màn "vẽ theo state" (uiState) và chỉ GỌI HÀM ViewModel khi người dùng thao tác.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    taskListViewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory)
) {
    val state by taskListViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Lắc máy khi đang có thanh "Hoàn tác" → hoàn tác luôn (gia tốc kế)
    com.example.todoapplication.ui.components.ShakeEffect {
        snackbarHostState.currentSnackbarData?.takeIf { it.visuals.actionLabel == str(R.string.action_undo) }?.performAction()
    }
    val scope = rememberCoroutineScope()
    val askNotificationPermission = rememberNotificationPermissionRequest()

    // Ô tìm kiếm giữ text cục bộ: TextField cần cập nhật đồng bộ từng phím gõ, không thể đợi state
    // vòng qua ViewModel (sẽ giật con trỏ). ViewModel chỉ nhận giá trị để lọc (có debounce).
    var searchText by rememberSaveable { mutableStateOf(state.query) }
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var showAiAdd by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf("") }
    var showQuickCreate by remember { mutableStateOf(false) }
    // Màn rộng: bấm thẻ → mở ở khung bên phải thay vì sang màn khác
    val twoPane = com.example.todoapplication.ui.components.isTwoPane()
    var paneTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val openTask: (String) -> Unit = { id ->
        if (twoPane) paneTaskId = id else navController.navigate(Screen.TaskDetail.createRoute(id))
    }
    // "Chụp ảnh tạo việc": quét giấy / bảng trắng → nhận chữ (ML Kit) → chọn dòng → tạo việc
    val startScan = com.example.todoapplication.ui.components.rememberScanToTasks { lines -> taskListViewModel.createFromLines(lines) }
    // Việc đang chọn ngày hoãn (vuốt trái); showBulkPostpone = dời các việc đang chọn
    var postponeTarget by remember { mutableStateOf<Task?>(null) }
    var showBulkPostpone by remember { mutableStateOf(false) }
    val selectedIds by taskListViewModel.selectedIds.collectAsStateWithLifecycle()
    val savedFilters by taskListViewModel.savedFilters.collectAsStateWithLifecycle()
    var showSaveFilter by remember { mutableStateOf(false) }
    var deletingFilter by remember { mutableStateOf<String?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    BackHandler(enabled = selectionMode) { taskListViewModel.clearSelection() }

    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        taskListViewModel.onDragMove(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(Unit) {
        taskListViewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
                is TaskListEvent.Completed -> scope.launch {
                    val text = if (event.spawnedNext) str(R.string.tasks_completed_spawned) else str(R.string.today_completed, event.title)
                    val result = snackbarHostState.showSnackbar(text, actionLabel = str(R.string.action_undo), duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.reopenTask(event.taskId)
                }
                is TaskListEvent.Deleted -> scope.launch {
                    val result = snackbarHostState.showSnackbar(str(R.string.tasks_moved_to_trash, event.title), actionLabel = str(R.string.action_undo), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.undoDelete(event.taskId)
                }
                is TaskListEvent.Rescheduled -> scope.launch {
                    val result = snackbarHostState.showSnackbar(plural(R.plurals.tasks_rescheduled, event.count), actionLabel = str(R.string.action_undo), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.undoReschedule(event.previous)
                }
                is TaskListEvent.BulkCompleted -> scope.launch {
                    val result = snackbarHostState.showSnackbar(plural(R.plurals.tasks_bulk_completed, event.ids.size), actionLabel = str(R.string.action_undo), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.undoBulkComplete(event.ids)
                }
                is TaskListEvent.BulkDeleted -> scope.launch {
                    val result = snackbarHostState.showSnackbar(plural(R.plurals.tasks_bulk_trashed, event.ids.size), actionLabel = str(R.string.action_undo), duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.undoBulkDelete(event.ids)
                }
                is TaskListEvent.Duplicated -> scope.launch {
                    val result = snackbarHostState.showSnackbar(str(R.string.today_duplicated, event.title), actionLabel = str(R.string.action_open), duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) navController.navigate(Screen.TaskDetail.createRoute(event.taskId))
                }
                is TaskListEvent.QuickAddReady -> {
                    QuickAddDraft.set(event.parsed)
                    showAiAdd = false
                    aiText = ""
                    navController.navigate(Screen.TaskDetail.createRoute("new"))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Thanh chọn nhiều ghim ở trên cùng (không cuộn mất khi chọn việc ở cuối danh sách)
            if (selectionMode) {
                Box(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    SelectionBar(
                        count = selectedIds.size,
                        onClose = taskListViewModel::clearSelection,
                        onSelectAll = taskListViewModel::selectAllVisible,
                        onComplete = taskListViewModel::completeSelected,
                        onMyDay = taskListViewModel::addSelectedToMyDay,
                        onPostpone = { showBulkPostpone = true },
                        onPriority = taskListViewModel::setSelectedPriority,
                        onDelete = taskListViewModel::deleteSelected
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = { showQuickCreate = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tasks_add))
                }
            }
        },
        bottomBar = { BottomNavigationBar(navController, activeTab = 1) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
            return@Scaffold
        }

        // Tablet / máy gập mở: danh sách | chi tiết cạnh nhau (list-detail)
        Row(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = taskListViewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero", contentType = "hero") {
                    ListHeroCard(
                        userName = taskListViewModel.userName,
                        pendingCount = state.pendingCount,
                        completedCount = state.completedCount,
                        overdueCount = state.overdueCount,
                        sortMode = sortMode,
                        onSortToggle = {
                            sortMode = !sortMode
                            if (!sortMode) taskListViewModel.endSortMode()
                        },
                        onAiAdd = { showAiAdd = true },
                        onMatrix = { navController.navigate(Screen.Matrix.route) },
                        onLists = { navController.navigate(Screen.Lists.route) }
                    )
                }

                item(key = "sync-banner", contentType = "banner") {
                    SyncBanner(state.isOnline, state.pendingSyncCount, Modifier.animateItem())
                }

                item(key = "search", contentType = "search") {
                    SearchField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            taskListViewModel.setQuery(it)
                        }
                    )
                }

                item(key = "filters", contentType = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Bộ lọc đã lưu (giữ để xóa) + nút lưu bộ lọc hiện tại
                        if (savedFilters.isNotEmpty() || state.hasFilter) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                savedFilters.forEach { f ->
                                    SavedFilterChip(
                                        name = f.name,
                                        onClick = { searchText = taskListViewModel.applySavedFilter(f) },
                                        onLongClick = { deletingFilter = f.name }
                                    )
                                }
                                if (state.hasFilter) {
                                    AssistChip(
                                        onClick = { showSaveFilter = true },
                                        label = { Text(stringResource(R.string.filter_save_chip)) }
                                    )
                                    AssistChip(
                                        onClick = { taskListViewModel.clearFilters(); searchText = "" },
                                        label = { Text(stringResource(R.string.filter_clear_chip)) }
                                    )
                                }
                            }
                        }
                        // Bộ lọc thông minh
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SmartFilter.entries.forEach { f ->
                                StatusFilterChip(
                                    text = f.label,
                                    selected = state.smartFilter == f,
                                    onClick = { taskListViewModel.setSmartFilter(f) }
                                )
                            }
                        }
                        // Danh mục
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf(ALL_CATEGORIES) + state.categories).forEach { filterName ->
                                StatusFilterChip(
                                    text = if (filterName == ALL_CATEGORIES) stringResource(R.string.filter_all_lists) else categoryLabel(filterName),
                                    selected = state.selectedCategory == filterName,
                                    onClick = { taskListViewModel.setCategory(filterName) }
                                )
                            }
                        }
                        // Nhãn
                        if (state.availableTags.isNotEmpty() || state.selectedTag != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusFilterChip(text = stringResource(R.string.filter_all_tags), selected = state.selectedTag == null, onClick = { taskListViewModel.setTag(null) })
                                (listOfNotNull(state.selectedTag) + state.availableTags).distinct().forEach { t ->
                                    StatusFilterChip(text = "@$t", selected = state.selectedTag == t, onClick = {
                                        taskListViewModel.setTag(if (state.selectedTag == t) null else t)
                                    })
                                }
                            }
                        }
                    }
                }

                val hasTasks = !state.sections.isEmpty
                if (!sortMode && hasTasks) {
                    item(key = "sort", contentType = "sort") {
                        SortMenu(sortBy = state.sortBy, onSelect = taskListViewModel::setSortBy)
                    }
                }

                when {
                    !hasTasks -> item(key = "empty", contentType = "empty") {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            EmptyState(
                                emoji = "🗒️",
                                title = stringResource(if (state.hasFilter) R.string.tasks_empty_filtered else R.string.tasks_empty),
                                subtitle = stringResource(if (state.hasFilter) R.string.tasks_empty_filtered_sub else R.string.tasks_empty_sub)
                            )
                        }
                    }

                    sortMode -> {
                        item(key = "sort-hint", contentType = "hint") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.tasks_sort_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { sortMode = false; taskListViewModel.endSortMode() }) {
                                    Text(stringResource(R.string.action_done), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        items(state.manualOrder, key = { it.id }, contentType = { "task" }) { task ->
                            ReorderableItem(reorderState, key = task.id) { isDragging ->
                                val elevation = if (isDragging) 10.dp else 0.dp
                                TaskCard(
                                    task = task,
                                    modifier = Modifier.shadow(elevation, RoundedCornerShape(16.dp)),
                                    onCardClick = { openTask(task.id) },
                                    onToggleComplete = { taskListViewModel.completeTask(task) },
                                    onDeleteClick = { taskListViewModel.deleteTask(task) },
                                    recommendation = state.recommendations[task.id],
                                    onSetPriority = { p -> taskListViewModel.setPriority(task, p) },
                                    isInMyDay = task.myDay == state.today,
                                    dragHandleModifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            taskListViewModel.onDragStart(task.id)
                                        },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            taskListViewModel.onDragEnd()
                                        }
                                    )
                                )
                            }
                        }
                    }

                    else -> {
                        val onPostpone: (Task) -> Unit = { postponeTarget = it }
                        taskSection(
                            str(R.string.filter_overdue), state.sections.overdue, state, selectedIds, navController, openTask, taskListViewModel, onPostpone,
                            titleColor = StateOverdue,
                            action = if (selectionMode) null else ({ RescheduleMenu(onPick = taskListViewModel::rescheduleOverdue) })
                        )
                        taskSection(str(R.string.day_today), state.sections.today, state, selectedIds, navController, openTask, taskListViewModel, onPostpone)
                        taskSection(str(R.string.tasks_upcoming), state.sections.upcoming, state, selectedIds, navController, openTask, taskListViewModel, onPostpone)
                        taskSection(str(R.string.filter_no_date), state.sections.noDate, state, selectedIds, navController, openTask, taskListViewModel, onPostpone)
                        taskSection(str(R.string.tasks_done_today), state.sections.completedToday, state, selectedIds, navController, openTask, taskListViewModel, onPostpone, swipeable = false)
                    }
                }
            }
        }
        if (twoPane) {
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1.25f).fillMaxHeight()) {
                val paneId = paneTaskId
                if (paneId == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.tasks_pick_one), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else key(paneId) {
                    TaskDetailScreen(
                        navController = navController,
                        taskId = paneId,
                        taskDetailViewModel = rememberPaneDetailViewModel(paneId),
                        onClose = { paneTaskId = null },
                        onOpenTask = { paneTaskId = it }
                    )
                }
            }
        }
        }
    }

    if (showAiAdd) {
        AiQuickAddSheet(
            text = aiText,
            onTextChange = { aiText = it },
            isLoading = state.quickAddLoading,
            onDismiss = { showAiAdd = false },
            onSubmit = {
                val nowRfc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
                taskListViewModel.parseQuickAdd(aiText, nowRfc)
            }
        )
    }

    if (showSaveFilter) {
        TextPromptDialog(stringResource(R.string.filter_save), stringResource(R.string.filter_save_hint), "", onDismiss = { showSaveFilter = false }) {
            taskListViewModel.saveCurrentFilter(it)
            showSaveFilter = false
        }
    }
    deletingFilter?.let { name ->
        AlertDialog(
            onDismissRequest = { deletingFilter = null },
            title = { Text(stringResource(R.string.filter_delete_title, name)) },
            confirmButton = {
                TextButton(onClick = { taskListViewModel.deleteSavedFilter(name); deletingFilter = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingFilter = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    postponeTarget?.let { task ->
        PostponeSheet(
            taskTitle = task.title,
            onDismiss = { postponeTarget = null },
            onPick = { date ->
                taskListViewModel.postpone(task, date)
                postponeTarget = null
            }
        )
    }

    if (showBulkPostpone) {
        PostponeSheet(
            taskTitle = pluralStringResource(R.plurals.tasks_selected, selectedIds.size, selectedIds.size),
            onDismiss = { showBulkPostpone = false },
            onPick = { date ->
                taskListViewModel.postponeSelected(date)
                showBulkPostpone = false
            }
        )
    }

    if (showQuickCreate) {
        QuickCreateSheet(
            categories = state.categories,
            onDismiss = { showQuickCreate = false },
            onCreate = { result ->
                taskListViewModel.createQuickTask(result)
                if (result.draft.dueAt != null) askNotificationPermission(str(R.string.notif_rationale_due))
                showQuickCreate = false
            },
            onMoreDetails = { result ->
                QuickAddDraft.set(QuickAddDraft.Prefill(result.draft, result.addToMyDay))
                showQuickCreate = false
                navController.navigate(Screen.TaskDetail.createRoute("new"))
            },
            onAiAdd = { text ->
                showQuickCreate = false
                aiText = text
                showAiAdd = true
            },
            onTemplates = {
                showQuickCreate = false
                navController.navigate(Screen.Templates.route)
            },
            onScan = { showQuickCreate = false; startScan() }
        )
    }
}

/** Một nhóm task có tiêu đề. animateItem(): thẻ trượt mượt sang nhóm khác khi hoàn thành/mở lại. */
private fun LazyListScope.taskSection(
    title: String,
    tasks: List<Task>,
    state: TaskListUiState,
    selectedIds: Set<String>,
    navController: NavController,
    openTask: (String) -> Unit,
    viewModel: TaskListViewModel,
    onPostpone: (Task) -> Unit,
    swipeable: Boolean = true,
    titleColor: Color? = null,
    action: (@Composable () -> Unit)? = null
) {
    if (tasks.isEmpty()) return
    item(key = "header-$title", contentType = "header") {
        SectionHeader(
            title, tasks.size, Modifier.animateItem(),
            color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            action = action
        )
    }
    items(tasks, key = { it.id }, contentType = { "task" }) { task ->
        val selectionMode = selectedIds.isNotEmpty()
        val onToggle = { if (task.isCompleted) viewModel.reopenTask(task.id) else viewModel.completeTask(task) }
        val card = @Composable {
            TaskCard(
                task = task,
                onCardClick = {
                    if (selectionMode) viewModel.toggleSelection(task.id)
                    else openTask(task.id)
                },
                onToggleComplete = if (selectionMode) ({ viewModel.toggleSelection(task.id) }) else onToggle,
                selectionMode = selectionMode,
                selected = task.id in selectedIds,
                onLongClick = { viewModel.toggleSelection(task.id) },
                onDuplicate = { viewModel.duplicate(task) },
                onPostpone = if (swipeable && !task.isCompleted) ({ onPostpone(task) }) else null,
                onDeleteClick = { viewModel.deleteTask(task) },
                recommendation = state.recommendations[task.id],
                onSetPriority = { p -> viewModel.setPriority(task, p) },
                isInMyDay = task.myDay == state.today,
                onToggleMyDay = { viewModel.toggleMyDay(task) },
                onFocus = { navController.navigate(Screen.Focus.createRoute(task.id)) },
                today = LocalDate.parse(state.today.ifEmpty { LocalDate.now().toString() })
            )
        }
        Box(Modifier.animateItem()) {
            if (swipeable) {
                SwipeToCompleteBox(
                    onComplete = { viewModel.completeTask(task) },
                    onPostpone = { onPostpone(task) },
                    enabled = !selectionMode
                ) { card() }
            } else card()
        }
    }
}

/** "Dời tất cả" việc quá hạn sang hôm nay / ngày mai. */
@Composable
private fun RescheduleMenu(onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.tasks_reschedule_all), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_to_today)) }, onClick = { expanded = false; onPick(0) })
            DropdownMenuItem(text = { Text(stringResource(R.string.tasks_to_tomorrow)) }, onClick = { expanded = false; onPick(1) })
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.tasks_search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun SortMenu(sortBy: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        var expanded by remember { mutableStateOf(false) }
        Box {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.tasks_sort_by, sortLabel(sortBy)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                listOf("DEFAULT", "DUE", "PRIORITY", "TITLE").forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                sortLabel(option),
                                color = if (sortBy == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListHeroCard(
    userName: String,
    pendingCount: Int,
    completedCount: Int,
    overdueCount: Int,
    sortMode: Boolean,
    onSortToggle: () -> Unit,
    onAiAdd: () -> Unit,
    onMatrix: () -> Unit,
    onLists: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.72f))))
            .padding(20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasks_hero_title, userName), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        if (pendingCount > 0) pluralStringResource(R.plurals.tasks_pending, pendingCount, pendingCount) else stringResource(R.string.tasks_none_pending),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(onClick = onLists, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.lists_title), modifier = Modifier.size(20.dp), tint = Color.White)
                }
                IconButton(onClick = onMatrix, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.tasks_matrix), modifier = Modifier.size(20.dp), tint = Color.White)
                }
                IconButton(onClick = onAiAdd, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.tasks_ai_add), modifier = Modifier.size(20.dp), tint = Color.White)
                }
                IconButton(onClick = onSortToggle, modifier = Modifier.size(38.dp)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(if (sortMode) Color.White else Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.tasks_manual_sort),
                            modifier = Modifier.size(14.dp),
                            tint = if (sortMode) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStatChip(value = completedCount.toString(), label = stringResource(R.string.stats_completed), valueColor = StateCompleted)
                HeroStatChip(value = pendingCount.toString(), label = stringResource(R.string.stats_pending), valueColor = Color.White)
                if (overdueCount > 0) {
                    HeroStatChip(value = overdueCount.toString(), label = stringResource(R.string.filter_overdue), valueColor = StateOverdue)
                }
            }
        }
    }
}

@Composable
fun HeroStatChip(value: String, label: String, valueColor: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.18f)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        }
    }
}

/** Chip bộ lọc đã lưu: bấm để áp dụng, nhấn giữ để xóa. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SavedFilterChip(name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Text(
            "⭐ $name",
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
