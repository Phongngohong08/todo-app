package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.repository.CategoryStore
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.domain.aiRecommendedIds
import com.example.todoapplication.domain.dueAtDayOffset
import com.example.todoapplication.domain.daysUntilSunday
import com.example.todoapplication.domain.isFuture
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.isUpdatedToday
import com.example.todoapplication.domain.sortLabel
import com.example.todoapplication.domain.sortTasks
import com.example.todoapplication.ui.components.AppBottomBar
import com.example.todoapplication.ui.components.EmptyState
import com.example.todoapplication.ui.components.LoadingState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.viewmodel.TaskListEvent
import com.example.todoapplication.ui.viewmodel.TaskListViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    taskListViewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory)
) {
    val context = LocalContext.current
    val userName = taskListViewModel.userName

    val state by taskListViewModel.uiState.collectAsStateWithLifecycle()
    val tasks = state.tasks
    val isLoading = state.isLoading
    val isOffline = state.isOffline
    val quickAddLoading = state.quickAddLoading

    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("DEFAULT") } // DEFAULT/DUE/PRIORITY/TITLE

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        taskListViewModel.moveTask(from.index, to.index)
    }

    val aiRecommendedIds = remember(tasks) { aiRecommendedIds(tasks) }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddText by remember { mutableStateOf("") }

    // Thanh tạo nhanh (kiểu app tham khảo)
    var showQuickCreate by remember { mutableStateOf(false) }

    // Lắng nghe sự kiện một lần từ ViewModel
    LaunchedEffect(Unit) {
        taskListViewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                is TaskListEvent.QuickAddReady -> {
                    QuickAddDraft.set(event.parsed)
                    showQuickAdd = false
                    quickAddText = ""
                    navController.navigate(Screen.TaskDetail.createRoute("new"))
                }
            }
        }
    }

    LaunchedEffect(selectedCategoryFilter, searchQuery) {
        kotlinx.coroutines.delay(300)
        taskListViewModel.loadTasks(selectedCategoryFilter, searchQuery)
    }

    // Derived stats
    val pendingCount = tasks.count { it.status != "COMPLETED" }
    val completedCount = tasks.count { it.status == "COMPLETED" }
    val overdueCount = tasks.count { it.isOverdue() }

    Scaffold(
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
        bottomBar = {
            BottomNavigationBar(navController, activeTab = 0)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingState()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Greeting hero card
                item {
                    GreetingHeroCard(
                        userName = userName,
                        pendingCount = pendingCount,
                        completedCount = completedCount,
                        overdueCount = overdueCount,
                        sortMode = sortMode,
                        onSortToggle = { sortMode = !sortMode },
                        onQuickAdd = { showQuickAdd = true },
                        onLogout = {
                            taskListViewModel.logout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.TaskList.route) { inclusive = true }
                            }
                        }
                    )
                }

                // Offline banner
                if (isOffline) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatePostponed.copy(alpha = 0.13f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📴", fontSize = 15.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Chế độ offline — hiển thị dữ liệu đã lưu trên máy",
                                    color = StatePostponed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Search bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Tìm công việc...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                // Category filter chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf("ALL") + CategoryStore.all()).forEach { filterName ->
                            StatusFilterChip(
                                text = categoryLabel(filterName),
                                selected = selectedCategoryFilter == filterName,
                                onClick = { selectedCategoryFilter = filterName }
                            )
                        }
                    }
                }

                // Thanh sắp xếp (ẩn khi đang ở chế độ kéo-thả)
                if (!sortMode && tasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.weight(1f))
                            var sortMenu by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { sortMenu = true }
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
                                    expanded = sortMenu,
                                    onDismissRequest = { sortMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    listOf("DEFAULT", "DUE", "PRIORITY", "TITLE").forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(sortLabel(opt), color = if (sortBy == opt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                            onClick = { sortBy = opt; sortMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Task list or empty state
                if (tasks.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            EmptyState(
                                emoji = "🗒️",
                                title = if (searchQuery.isNotBlank()) "Không tìm thấy công việc" else "Chưa có công việc nào",
                                subtitle = if (searchQuery.isNotBlank()) "Thử từ khóa khác nhé." else "Nhấn + để thêm, hoặc ⭐ để thêm nhanh bằng AI."
                            )
                        }
                    }
                } else if (sortMode) {
                    // Sort mode: drag & drop, no swipe-to-dismiss
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("☰  Giữ và kéo để sắp xếp thứ tự", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { sortMode = false }) { Text("Xong", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        ReorderableItem(reorderState, key = task.id) { isDragging ->
                            TaskCard(
                                task = task,
                                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                onCompleteClick = { taskListViewModel.completeTask(task) },
                                onDeleteClick = { taskToDelete = task },
                                isAiRecommended = task.id in aiRecommendedIds,
                                subtaskDone = state.subtaskProgress[task.id]?.done ?: 0,
                                subtaskTotal = state.subtaskProgress[task.id]?.total ?: 0,
                                onSetPriority = { p -> taskListViewModel.setPriority(task, p) },
                                dragHandleModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                } else {
                    // Nhóm theo: Hôm nay / Tương lai / Đã hoàn thành (giống ảnh tham khảo)
                    val pending = sortTasks(tasks.filter { it.status != "COMPLETED" }, sortBy)
                    val todayTasks = pending.filter { !it.isFuture() }
                    val futureTasks = pending.filter { it.isFuture() }
                    val completedTasks = tasks.filter { it.status == "COMPLETED" && it.isUpdatedToday() }

                    if (todayTasks.isNotEmpty()) {
                        item { SectionHeader("Hôm nay", todayTasks.size) }
                        items(todayTasks, key = { it.id }) { task ->
                            SwipeableTaskCard(
                                task = task,
                                isAiRecommended = task.id in aiRecommendedIds,
                                subtaskDone = state.subtaskProgress[task.id]?.done ?: 0,
                                subtaskTotal = state.subtaskProgress[task.id]?.total ?: 0,
                                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                onComplete = { taskListViewModel.completeTask(task) },
                                onDelete = { taskToDelete = task },
                                onSetPriority = { p -> taskListViewModel.setPriority(task, p) }
                            )
                        }
                    }
                    if (futureTasks.isNotEmpty()) {
                        item { SectionHeader("Tương lai", futureTasks.size) }
                        items(futureTasks, key = { it.id }) { task ->
                            SwipeableTaskCard(
                                task = task,
                                isAiRecommended = task.id in aiRecommendedIds,
                                subtaskDone = state.subtaskProgress[task.id]?.done ?: 0,
                                subtaskTotal = state.subtaskProgress[task.id]?.total ?: 0,
                                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                onComplete = { taskListViewModel.completeTask(task) },
                                onDelete = { taskToDelete = task },
                                onSetPriority = { p -> taskListViewModel.setPriority(task, p) }
                            )
                        }
                    }
                    if (completedTasks.isNotEmpty()) {
                        item { SectionHeader("Đã hoàn thành hôm nay", completedTasks.size) }
                        items(completedTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                onCompleteClick = { },
                                onDeleteClick = { taskToDelete = task },
                                subtaskDone = state.subtaskProgress[task.id]?.done ?: 0,
                                subtaskTotal = state.subtaskProgress[task.id]?.total ?: 0,
                                onSetPriority = { p -> taskListViewModel.setPriority(task, p) }
                            )
                        }
                    }
                }
            }
        }
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Xóa công việc?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("\"${task.title}\" sẽ bị xóa vĩnh viễn.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    taskListViewModel.deleteTask(task)
                    taskToDelete = null
                }) { Text("Xóa", color = PriorityHighColor) }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Quay lại", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // AI Quick Add bottom sheet
    if (showQuickAdd) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAdd = false },
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
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
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
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    placeholder = {
                        Text(
                            "VD: Họp với sếp thứ 6 lúc 3h chiều, khoảng 1 tiếng",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2,
                    enabled = !quickAddLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (!quickAddLoading && quickAddText.isNotBlank())
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            else Brush.horizontalGradient(listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ))
                        )
                        .clickable(enabled = !quickAddLoading && quickAddText.isNotBlank()) {
                            val nowRfc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())
                            taskListViewModel.parseQuickAdd(quickAddText, nowRfc)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (quickAddLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Phân tích bằng AI", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // Thanh tạo nhanh (gõ tiêu đề + chọn nhanh ngày/danh mục, giống app tham khảo)
    if (showQuickCreate) {
        QuickCreateSheet(
            onDismiss = { showQuickCreate = false },
            onCreate = { input ->
                taskListViewModel.createQuickTask(input)
                showQuickCreate = false
            },
            onMoreDetails = { title, category, dueDate ->
                QuickAddDraft.set(
                    com.example.todoapplication.data.model.ParsedTask(
                        title = title,
                        category = category,
                        dueDate = dueDate
                    )
                )
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
    onDismiss: () -> Unit,
    onCreate: (com.example.todoapplication.data.model.CreateTaskInput) -> Unit,
    onMoreDetails: (title: String, category: String, dueDate: String?) -> Unit,
    onAiAdd: () -> Unit,
    onTemplates: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    // Preset ngày: 0=Hôm nay,1=Ngày mai,3=3 ngày sau, -2=Cuối tuần, -1=Không
    var datePreset by remember { mutableStateOf(-1) }
    var category by remember { mutableStateOf("OTHER") }
    var priority by remember { mutableStateOf("MEDIUM") }

    fun resolveDue(): String? = when (datePreset) {
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

            // Chọn nhanh ngày
            Text("Hạn chót", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "Hôm nay", 1 to "Ngày mai", 3 to "3 ngày sau", -2 to "Cuối tuần", -1 to "Không"
                ).forEach { (value, label) ->
                    StatusFilterChip(text = label, selected = datePreset == value, onClick = { datePreset = value })
                }
            }

            // Danh mục
            Text("Danh mục", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryStore.all().forEach { c ->
                    StatusFilterChip(text = categoryLabel(c), selected = category == c, onClick = { category = c })
                }
            }

            // Ưu tiên
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
                            onCreate(
                                com.example.todoapplication.data.model.CreateTaskInput(
                                    title = title.trim(),
                                    description = "",
                                    priority = priority,
                                    dueDate = resolveDue(),
                                    category = category
                                )
                            )
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
            .background(
                Brush.linearGradient(
                    listOf(primary, primary.copy(alpha = 0.72f))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Xin chào, $userName! 👋",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp
                    )
                    Text(
                        if (pendingCount > 0) "$pendingCount việc đang chờ bạn"
                        else "Bạn đã xử lý hết việc hôm nay! 🎉",
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
                                .background(
                                    if (sortMode) Color.White else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
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
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    task: Task,
    isAiRecommended: Boolean,
    subtaskDone: Int,
    subtaskTotal: Int,
    onCardClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onSetPriority: (String) -> Unit
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onComplete()
                Toast.makeText(context, "Đã hoàn thành: ${task.title}", Toast.LENGTH_SHORT).show()
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
        TaskCard(
            task = task,
            onCardClick = onCardClick,
            onCompleteClick = onComplete,
            onDeleteClick = onDelete,
            isAiRecommended = isAiRecommended,
            subtaskDone = subtaskDone,
            subtaskTotal = subtaskTotal,
            onSetPriority = onSetPriority
        )
    }
}

@Composable
private fun HeroStatChip(value: String, label: String, valueColor: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
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
    onCompleteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isAiRecommended: Boolean = false,
    subtaskDone: Int = 0,
    subtaskTotal: Int = 0,
    onSetPriority: (String) -> Unit = {},
    dragHandleModifier: Modifier = Modifier
) {
    val isCompleted = task.status == "COMPLETED"
    val isOverdue = task.isOverdue()

    val accentColor = when (task.priority) {
        "HIGH" -> PriorityHighColor
        "MEDIUM" -> PriorityMediumColor
        else -> PriorityLowColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragHandleModifier != Modifier) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // AI recommended banner
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
                Text(
                    "AI khuyến nghị ưu tiên",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left priority accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        accentColor,
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            // Drag handle (only in sort mode)
            if (dragHandleModifier != Modifier) {
                Box(
                    modifier = dragHandleModifier
                        .width(28.dp)
                        .fillMaxHeight()
                        .padding(start = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Kéo", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            // Complete checkbox (tròn) — tick để hoàn thành nhanh
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted) StateCompleted else Color.Transparent
                        )
                        .then(
                            if (isCompleted) Modifier
                            else Modifier.border(2.dp, accentColor.copy(alpha = 0.6f), CircleShape)
                        )
                        .clickable(enabled = !isCompleted, onClick = onCompleteClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = "Hoàn thành", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp, top = 13.dp, bottom = 12.dp)
            ) {
                // Title row with overflow menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isCompleted)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Recurrence icon
                    if (task.recurrence != "NONE") {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Lặp lại",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    // Cờ ưu tiên (bấm để đổi)
                    var flagMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { flagMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Đổi ưu tiên",
                                modifier = Modifier.size(16.dp),
                                tint = accentColor
                            )
                        }
                        DropdownMenu(
                            expanded = flagMenu,
                            onDismissRequest = { flagMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf(
                                "HIGH" to PriorityHighColor,
                                "MEDIUM" to PriorityMediumColor,
                                "LOW" to PriorityLowColor
                            ).forEach { (p, color) ->
                                DropdownMenuItem(
                                    text = { Text(priorityLabel(p), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) },
                                    onClick = { flagMenu = false; onSetPriority(p) }
                                )
                            }
                        }
                    }
                    // Overflow menu
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Thêm",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chỉnh sửa", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onCardClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa", color = PriorityHighColor) },
                                leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = PriorityHighColor) },
                                onClick = { menuExpanded = false; onDeleteClick() }
                            )
                        }
                    }
                }

                // Description
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        task.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Bottom row: category + due date + subtask chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            categoryLabel(task.category),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    // Due date chip
                    task.dueDate?.let { dateStr ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOverdue) StateOverdue.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = if (isOverdue) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatUtcToLocal(dateStr),
                                    color = if (isOverdue) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    // Subtask checklist progress chip
                    if (subtaskTotal > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (subtaskDone == subtaskTotal) StateCompleted.copy(alpha = 0.13f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "☑ $subtaskDone/$subtaskTotal",
                                color = if (subtaskDone == subtaskTotal) StateCompleted else MaterialTheme.colorScheme.onSurfaceVariant,
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

// ─── Wrapper / chips ─────────────────────────────────────────────────────────

@Composable
fun BottomNavigationBar(navController: NavController, activeTab: Int) =
    AppBottomBar(navController, activeTab)

// Legacy pill composables (still used by other screens that haven't been migrated)
@Composable
fun OverduePill() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = StateOverdue.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, StateOverdue.copy(alpha = 0.4f))
    ) {
        Text(
            "QUÁ HẠN",
            color = StateOverdue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CancelledPill() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = StateCancelled.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, StateCancelled.copy(alpha = 0.4f))
    ) {
        Text(
            "ĐÃ HỦY",
            color = StateCancelled,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PriorityPill(priority: String) {
    val color = when (priority) {
        "HIGH" -> PriorityHighColor
        "MEDIUM" -> PriorityMediumColor
        else -> PriorityLowColor
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = priorityLabel(priority),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
