package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.domain.daysUntilSunday
import com.example.todoapplication.domain.dueAtDayOffset
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.domain.sortLabel
import com.example.todoapplication.ui.components.AppBottomBar
import com.example.todoapplication.ui.components.EmptyState
import com.example.todoapplication.ui.components.LoadingState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDateTime
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.toIsoString
import com.example.todoapplication.ui.viewmodel.ALL_CATEGORIES
import com.example.todoapplication.ui.viewmodel.TaskListEvent
import com.example.todoapplication.ui.viewmodel.TaskListUiState
import com.example.todoapplication.ui.viewmodel.TaskListViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.*

/**
 * [TẦNG UI · MÀN HÌNH] Màn chính: danh sách công việc + tìm kiếm/lọc, tạo nhanh, kéo-thả, kéo-để-làm-mới.
 * Quy tắc Compose: màn "vẽ theo state" (uiState) và chỉ GỌI HÀM ViewModel khi người dùng thao tác
 * ("state xuống, event lên"). Nhóm/sắp xếp đã được ViewModel tính sẵn trên luồng nền.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    // Lấy ViewModel (sống-dai qua các lần vẽ lại); Factory nạp sẵn repository từ ServiceLocator.
    taskListViewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory)
) {
    val state by taskListViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ô tìm kiếm giữ text cục bộ: TextField cần cập nhật đồng bộ từng phím gõ, không thể đợi state
    // vòng qua ViewModel (sẽ giật con trỏ). ViewModel chỉ nhận giá trị để lọc (có debounce).
    var searchText by rememberSaveable { mutableStateOf(state.query) }
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddText by remember { mutableStateOf("") }
    var showQuickCreate by remember { mutableStateOf(false) }
    var confirmLogoutCount by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        taskListViewModel.onDragMove(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    // Lắng nghe sự kiện một lần từ ViewModel
    LaunchedEffect(Unit) {
        taskListViewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
                is TaskListEvent.Completed -> scope.launch {
                    val text = if (event.spawnedNext) "Đã hoàn thành · đã tạo lần lặp kế tiếp" else "Đã hoàn thành: ${event.title}"
                    val result = snackbarHostState.showSnackbar(text, actionLabel = "Hoàn tác", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.reopenTask(event.taskId)
                }
                is TaskListEvent.Deleted -> scope.launch {
                    val result = snackbarHostState.showSnackbar("Đã xóa: ${event.title}", actionLabel = "Hoàn tác", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) taskListViewModel.undoDelete(event.taskId)
                }
                is TaskListEvent.QuickAddReady -> {
                    QuickAddDraft.set(event.parsed)
                    showQuickAdd = false
                    quickAddText = ""
                    navController.navigate(Screen.TaskDetail.createRoute("new"))
                }
                is TaskListEvent.ConfirmLogout -> confirmLogoutCount = event.pendingChanges
                TaskListEvent.LoggedOut -> navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.TaskList.route) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm công việc")
            }
        },
        bottomBar = { BottomNavigationBar(navController, activeTab = 0) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
            return@Scaffold
        }

        // Kéo xuống để đồng bộ ngay với server
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = taskListViewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())
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
                    GreetingHeroCard(
                        userName = taskListViewModel.userName,
                        pendingCount = state.pendingCount,
                        completedCount = state.completedCount,
                        overdueCount = state.overdueCount,
                        sortMode = sortMode,
                        onSortToggle = {
                            sortMode = !sortMode
                            if (!sortMode) taskListViewModel.endSortMode()
                        },
                        onQuickAdd = { showQuickAdd = true },
                        onLogout = taskListViewModel::requestLogout
                    )
                }

                syncBanner(state)

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
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf(ALL_CATEGORIES) + state.categories).forEach { filterName ->
                            StatusFilterChip(
                                text = categoryLabel(filterName),
                                selected = state.selectedCategory == filterName,
                                onClick = { taskListViewModel.setCategory(filterName) }
                            )
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
                                title = if (state.hasFilter) "Không tìm thấy công việc" else "Chưa có công việc nào",
                                subtitle = if (state.hasFilter) "Thử từ khóa hoặc danh mục khác nhé." else "Nhấn + để thêm, hoặc ⭐ để thêm nhanh bằng AI."
                            )
                        }
                    }

                    sortMode -> {
                        item(key = "sort-hint", contentType = "hint") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("☰  Giữ và kéo để sắp xếp thứ tự", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { sortMode = false; taskListViewModel.endSortMode() }) {
                                    Text("Xong", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        items(state.manualOrder, key = { it.id }, contentType = { "task" }) { task ->
                            ReorderableItem(reorderState, key = task.id) { isDragging ->
                                val elevation = if (isDragging) 10.dp else 0.dp
                                TaskCard(
                                    task = task,
                                    modifier = Modifier.shadow(elevation, RoundedCornerShape(16.dp)),
                                    onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                    onToggleComplete = { taskListViewModel.completeTask(task) },
                                    onDeleteClick = { taskListViewModel.deleteTask(task) },
                                    isAiRecommended = task.id in state.aiRecommendedIds,
                                    onSetPriority = { p -> taskListViewModel.setPriority(task, p) },
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
                        taskSection("Hôm nay", state.sections.today, state, navController, taskListViewModel, swipeable = true)
                        taskSection("Tương lai", state.sections.future, state, navController, taskListViewModel, swipeable = true)
                        taskSection("Đã hoàn thành hôm nay", state.sections.completedToday, state, navController, taskListViewModel, swipeable = false)
                    }
                }
            }
        }
    }

    confirmLogoutCount?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmLogoutCount = null },
            title = { Text("Đăng xuất khi chưa đồng bộ?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Còn $pending thay đổi chưa gửi được lên máy chủ (đang offline). Đăng xuất bây giờ sẽ làm mất các thay đổi này.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogoutCount = null
                    taskListViewModel.logoutNow()
                }) { Text("Vẫn đăng xuất", color = PriorityHighColor) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutCount = null }) {
                    Text("Ở lại", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showQuickAdd) {
        AiQuickAddSheet(
            text = quickAddText,
            onTextChange = { quickAddText = it },
            isLoading = state.quickAddLoading,
            onDismiss = { showQuickAdd = false },
            onSubmit = {
                val nowRfc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
                taskListViewModel.parseQuickAdd(quickAddText, nowRfc)
            }
        )
    }

    // Thanh tạo nhanh (gõ tiêu đề + chọn nhanh ngày/danh mục, giống app tham khảo)
    if (showQuickCreate) {
        QuickCreateSheet(
            categories = state.categories,
            onDismiss = { showQuickCreate = false },
            onCreate = { draft ->
                taskListViewModel.createQuickTask(draft)
                showQuickCreate = false
            },
            onMoreDetails = { title, category, dueAt ->
                QuickAddDraft.set(ParsedTask(title = title, category = category, dueDate = dueAt?.let(::toIsoString)))
                showQuickCreate = false
                navController.navigate(Screen.TaskDetail.createRoute("new"))
            },
            onAiAdd = {
                showQuickCreate = false
                showQuickAdd = true
            },
            onTemplates = {
                showQuickCreate = false
                navController.navigate(Screen.Templates.route)
            }
        )
    }
}

/** Một nhóm task có tiêu đề. animateItem(): thẻ trượt mượt sang nhóm khác khi hoàn thành/mở lại. */
private fun LazyListScope.taskSection(
    title: String,
    tasks: List<Task>,
    state: TaskListUiState,
    navController: NavController,
    viewModel: TaskListViewModel,
    swipeable: Boolean
) {
    if (tasks.isEmpty()) return
    item(key = "header-$title", contentType = "header") {
        SectionHeader(title, tasks.size, Modifier.animateItem())
    }
    items(tasks, key = { it.id }, contentType = { "task" }) { task ->
        val onToggle = { if (task.isCompleted) viewModel.reopenTask(task.id) else viewModel.completeTask(task) }
        val card = @Composable {
            TaskCard(
                task = task,
                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                onToggleComplete = onToggle,
                onDeleteClick = { viewModel.deleteTask(task) },
                isAiRecommended = task.id in state.aiRecommendedIds,
                onSetPriority = { p -> viewModel.setPriority(task, p) }
            )
        }
        Box(Modifier.animateItem()) {
            if (swipeable) SwipeToCompleteBox(onComplete = { viewModel.completeTask(task) }) { card() } else card()
        }
    }
}

/** Banner trạng thái đồng bộ: offline / còn thay đổi chưa gửi. */
private fun LazyListScope.syncBanner(state: TaskListUiState) {
    val (icon, text) = when {
        !state.isOnline && state.pendingSyncCount > 0 ->
            Icons.Outlined.CloudOff to "Đang offline — ${state.pendingSyncCount} thay đổi đã lưu trên máy, sẽ tự đồng bộ khi có mạng"
        !state.isOnline -> Icons.Outlined.CloudOff to "Đang offline — bạn vẫn thêm/sửa công việc bình thường"
        state.pendingSyncCount > 0 -> Icons.Outlined.CloudUpload to "Đang đồng bộ ${state.pendingSyncCount} thay đổi…"
        else -> return
    }
    item(key = "sync-banner", contentType = "banner") {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StatePostponed.copy(alpha = 0.13f),
            modifier = Modifier.fillMaxWidth().animateItem()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = StatePostponed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = StatePostponed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Tìm công việc...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Text("Sắp xếp: ${sortLabel(sortBy)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiQuickAddSheet(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Thêm nhanh bằng AI", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("AI sẽ tự phân tích và điền form cho bạn", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("VD: Họp với sếp thứ 6 lúc 3h chiều, khoảng 1 tiếng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            val enabled = !isLoading && text.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                        else Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .clickable(enabled = enabled, onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Phân tích bằng AI", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

// Mẫu nhiệm vụ gợi ý (emoji, tiêu đề, danh mục) — giống "Mẫu nhiệm vụ" trong app tham khảo.
private val QUICK_TEMPLATES = listOf(
    Triple("💧", "Uống nước", "PERSONAL"),
    Triple("🦷", "Đánh răng", "PERSONAL"),
    Triple("😴", "Đi ngủ sớm", "PERSONAL"),
    Triple("🌅", "Dậy sớm", "PERSONAL"),
    Triple("💊", "Uống thuốc", "PERSONAL"),
    Triple("🍎", "Ăn trái cây", "PERSONAL"),
    Triple("📚", "Học tập", "WORK"),
    Triple("🏃", "Tập thể dục", "PERSONAL"),
    Triple("📧", "Gửi email", "WORK"),
    Triple("🛒", "Đi mua sắm", "OTHER")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCreateSheet(
    categories: List<String>,
    onDismiss: () -> Unit,
    onCreate: (TaskDraft) -> Unit,
    onMoreDetails: (title: String, category: String, dueAt: Long?) -> Unit,
    onAiAdd: () -> Unit,
    onTemplates: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    // Preset ngày: 0=Hôm nay,1=Ngày mai,3=3 ngày sau, -2=Cuối tuần, -1=Không
    var datePreset by remember { mutableIntStateOf(-1) }
    var category by remember { mutableStateOf("OTHER") }
    var priority by remember { mutableStateOf("MEDIUM") }

    fun resolveDue(): Long? = when (datePreset) {
        0 -> dueAtDayOffset(0)
        1 -> dueAtDayOffset(1)
        3 -> dueAtDayOffset(3)
        -2 -> dueAtDayOffset(daysUntilSunday())
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Bạn cần làm gì?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Gợi ý mẫu nhiệm vụ (bấm để điền nhanh)
            if (title.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_TEMPLATES.forEach { (emoji, tmplTitle, tmplCat) ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { title = tmplTitle; category = tmplCat }
                        ) {
                            Text(
                                "$emoji $tmplTitle",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Text("Hạn chót", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Hôm nay", 1 to "Ngày mai", 3 to "3 ngày sau", -2 to "Cuối tuần", -1 to "Không").forEach { (value, label) ->
                    StatusFilterChip(text = label, selected = datePreset == value, onClick = { datePreset = value })
                }
            }

            Text("Danh mục", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { c ->
                    StatusFilterChip(text = categoryLabel(c), selected = category == c, onClick = { category = c })
                }
            }

            Text("Ưu tiên", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                    StatusFilterChip(text = priorityLabel(p), selected = priority == p, onClick = { priority = p })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTemplates, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Mẫu", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                TextButton(onClick = { onMoreDetails(title, category, resolveDue()) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Chi tiết", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAiAdd, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text("AI", color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreate(TaskDraft(title = title.trim(), priority = priority, dueAt = resolveDue(), category = category))
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (title.isNotBlank()) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Thêm")
                }
            }
        }
    }
}

// ─── Greeting hero card ──────────────────────────────────────────────────────

@Composable
private fun GreetingHeroCard(
    userName: String,
    pendingCount: Int,
    completedCount: Int,
    overdueCount: Int,
    sortMode: Boolean,
    onSortToggle: () -> Unit,
    onQuickAdd: () -> Unit,
    onLogout: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Xin chào, $userName! 👋", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text(
                        if (pendingCount > 0) "$pendingCount việc đang chờ bạn" else "Bạn đã xử lý hết việc hôm nay! 🎉",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row {
                    IconButton(onClick = onQuickAdd, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Star, contentDescription = "AI Quick Add", modifier = Modifier.size(20.dp), tint = Color.White)
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
                                contentDescription = "Sắp xếp",
                                modifier = Modifier.size(14.dp),
                                tint = if (sortMode) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", modifier = Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.75f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStatChip(value = completedCount.toString(), label = "Hoàn thành", valueColor = StateCompleted)
                HeroStatChip(value = pendingCount.toString(), label = "Đang chờ", valueColor = Color.White)
                if (overdueCount > 0) {
                    HeroStatChip(value = overdueCount.toString(), label = "Quá hạn", valueColor = StateOverdue)
                }
            }
        }
    }
}

// ─── Section header + swipeable row ──────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$title ($count)",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

/** Vuốt sang phải để hoàn thành (thẻ bật lại chỗ cũ; ViewModel chuyển nó sang nhóm "Đã hoàn thành"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToCompleteBox(onComplete: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onComplete()
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StateCompleted.copy(alpha = 0.2f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = StateCompleted)
                    Spacer(Modifier.width(8.dp))
                    Text("Hoàn thành", color = StateCompleted, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun HeroStatChip(value: String, label: String, valueColor: Color) {
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

// ─── Task Card ───────────────────────────────────────────────────────────────

@Composable
fun TaskCard(
    task: Task,
    onCardClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAiRecommended: Boolean = false,
    onSetPriority: (String) -> Unit = {},
    dragHandleModifier: Modifier? = null
) {
    val isCompleted = task.isCompleted
    val isOverdue = task.isOverdue()

    val accentColor = when (task.priority) {
        "HIGH" -> PriorityHighColor
        "MEDIUM" -> PriorityMediumColor
        else -> PriorityLowColor
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (isAiRecommended) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🤖", fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text("AI khuyến nghị ưu tiên", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left priority accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            // Drag handle (only in sort mode)
            if (dragHandleModifier != null) {
                Box(
                    modifier = dragHandleModifier.width(28.dp).fillMaxHeight().padding(start = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Kéo", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            // Ô tích tròn: tích để hoàn thành, tích lại để mở lại
            Box(modifier = Modifier.padding(start = 8.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) StateCompleted else Color.Transparent)
                        .then(if (isCompleted) Modifier else Modifier.border(2.dp, accentColor.copy(alpha = 0.6f), CircleShape))
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = "Mở lại", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp, top = 13.dp, bottom = 12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.hasPendingSync) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = "Chưa đồng bộ",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    if (task.isRecurring) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Refresh, contentDescription = "Lặp lại", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    PriorityFlagMenu(accentColor = accentColor, onSetPriority = onSetPriority)
                    OverflowMenu(onEdit = onCardClick, onDelete = onDeleteClick)
                }

                if (task.description.isNotEmpty()) {
                    Text(
                        task.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            categoryLabel(task.category),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    task.dueAt?.let { due ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOverdue) StateOverdue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = if (isOverdue) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatDateTime(due),
                                    color = if (isOverdue) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (task.subtaskTotal > 0) {
                        val allDone = task.subtaskDone == task.subtaskTotal
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (allDone) StateCompleted.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "☑ ${task.subtaskDone}/${task.subtaskTotal}",
                                color = if (allDone) StateCompleted else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityFlagMenu(accentColor: Color, onSetPriority: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Flag, contentDescription = "Đổi ưu tiên", modifier = Modifier.size(16.dp), tint = accentColor)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            listOf("HIGH" to PriorityHighColor, "MEDIUM" to PriorityMediumColor, "LOW" to PriorityLowColor).forEach { (p, color) ->
                DropdownMenuItem(
                    text = { Text(priorityLabel(p), color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) },
                    onClick = { expanded = false; onSetPriority(p) }
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Thêm", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("Chỉnh sửa", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { expanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Xóa", color = PriorityHighColor) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PriorityHighColor) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

// ─── Wrapper / chips ─────────────────────────────────────────────────────────

@Composable
fun BottomNavigationBar(navController: NavController, activeTab: Int) =
    AppBottomBar(navController, activeTab)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
