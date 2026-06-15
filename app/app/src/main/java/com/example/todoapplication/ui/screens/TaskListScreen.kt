package com.example.todoapplication.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.PostponeTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.ui.components.AppBottomBar
import com.example.todoapplication.ui.components.EmptyState
import com.example.todoapplication.ui.components.LoadingState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.statusLabel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.*

fun Task.isOverdue(): Boolean {
    if (status == "COMPLETED" || status == "CANCELLED") return false
    val dueDateStr = dueDate ?: return false
    val date = parseIso8601(dueDateStr) ?: return false
    return date.before(Date())
}

fun computeAiScore(task: Task): Double {
    var score = 0.0
    score += when (task.priority) {
        "HIGH" -> 100.0
        "MEDIUM" -> 50.0
        else -> 20.0
    }
    val now = Date()
    task.dueDate?.let { due ->
        parseIso8601(due)?.let { dueDate ->
            val hoursLeft = (dueDate.time - now.time) / 3600000.0
            score += when {
                hoursLeft < 0 -> 200.0
                hoursLeft < 24 -> 150.0
                hoursLeft < 72 -> 80.0
                hoursLeft < 168 -> 40.0
                else -> 10.0
            }
        }
    }
    if (task.status == "TODO") score += 5.0
    return score
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { NetworkClient.getApiService(context) }
    val userName = remember { sessionManager.getUserName() ?: "bạn" }

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        tasks = tasks.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    val aiRecommendedIds by remember(tasks) {
        derivedStateOf {
            tasks
                .filter { it.status != "COMPLETED" && it.status != "CANCELLED" }
                .sortedByDescending { computeAiScore(it) }
                .take(3)
                .map { it.id }
                .toSet()
        }
    }

    var showPostponeDialog by remember { mutableStateOf(false) }
    var selectedTaskForPostpone by remember { mutableStateOf<Task?>(null) }
    var postponeReason by remember { mutableStateOf("") }
    var postponeDueDate by remember { mutableStateOf("") }

    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToCancel by remember { mutableStateOf<Task?>(null) }

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAddText by remember { mutableStateOf("") }
    var quickAddLoading by remember { mutableStateOf(false) }

    val calendar = remember { Calendar.getInstance() }

    fun loadTasks() {
        isLoading = true
        coroutineScope.launch {
            try {
                val filter = if (selectedStatusFilter == "ALL") null else selectedStatusFilter
                val q = searchQuery.trim().ifEmpty { null }
                val response = apiService.listTasks(status = filter, query = q)
                if (response.isSuccessful) {
                    tasks = response.body() ?: emptyList()
                    ReminderScheduler.syncAll(context, tasks)
                } else {
                    Toast.makeText(context, "Không thể tải danh sách công việc", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedStatusFilter, searchQuery) {
        kotlinx.coroutines.delay(300)
        loadTasks()
    }

    // Derived stats
    val pendingCount = tasks.count { it.status != "COMPLETED" && it.status != "CANCELLED" }
    val completedCount = tasks.count { it.status == "COMPLETED" }
    val overdueCount = tasks.count { it.isOverdue() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.TaskDetail.createRoute("new")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
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
                            sessionManager.logout()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.TaskList.route) { inclusive = true }
                            }
                        }
                    )
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

                // Status filter chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf("ALL", "TODO", "IN_PROGRESS", "POSTPONED", "COMPLETED", "CANCELLED")
                        filters.forEach { filterName ->
                            StatusFilterChip(
                                text = statusLabel(filterName),
                                selected = selectedStatusFilter == filterName,
                                onClick = { selectedStatusFilter = filterName }
                            )
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
                                onStartClick = {
                                    coroutineScope.launch { val resp = apiService.startTask(task.id); if (resp.isSuccessful) loadTasks() }
                                },
                                onCompleteClick = {
                                    coroutineScope.launch { val resp = apiService.completeTask(task.id); if (resp.isSuccessful) loadTasks() }
                                },
                                onPostponeClick = {
                                    selectedTaskForPostpone = task; postponeDueDate = task.dueDate ?: ""; postponeReason = ""
                                    if (postponeDueDate.isNotEmpty()) { val p = parseIso8601(postponeDueDate); if (p != null) calendar.time = p } else calendar.time = Date()
                                    showPostponeDialog = true
                                },
                                onCancelClick = { taskToCancel = task },
                                onDeleteClick = { taskToDelete = task },
                                isAiRecommended = task.id in aiRecommendedIds,
                                onPomodoroClick = { navController.navigate(Screen.Pomodoro.createRoute(task.id, task.title)) },
                                dragHandleModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                } else {
                    items(tasks, key = { it.id }) { task ->
                        val isDone = task.status == "COMPLETED" || task.status == "CANCELLED"
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    coroutineScope.launch {
                                        val resp = apiService.completeTask(task.id)
                                        if (resp.isSuccessful) {
                                            Toast.makeText(context, "Đã hoàn thành: ${task.title}", Toast.LENGTH_SHORT).show()
                                            loadTasks()
                                        }
                                    }
                                }
                                false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isDone,
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
                                onCardClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) },
                                onStartClick = {
                                    coroutineScope.launch {
                                        val resp = apiService.startTask(task.id)
                                        if (resp.isSuccessful) loadTasks()
                                    }
                                },
                                onCompleteClick = {
                                    coroutineScope.launch {
                                        val resp = apiService.completeTask(task.id)
                                        if (resp.isSuccessful) loadTasks()
                                    }
                                },
                                onPostponeClick = {
                                    selectedTaskForPostpone = task
                                    postponeDueDate = task.dueDate ?: ""
                                    postponeReason = ""
                                    if (postponeDueDate.isNotEmpty()) {
                                        val parsedDate = parseIso8601(postponeDueDate)
                                        if (parsedDate != null) calendar.time = parsedDate
                                    } else {
                                        calendar.time = Date()
                                    }
                                    showPostponeDialog = true
                                },
                                onCancelClick = { taskToCancel = task },
                                onDeleteClick = { taskToDelete = task },
                                isAiRecommended = task.id in aiRecommendedIds,
                                onPomodoroClick = { navController.navigate(Screen.Pomodoro.createRoute(task.id, task.title)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Postpone Dialog
    if (showPostponeDialog && selectedTaskForPostpone != null) {
        AlertDialog(
            onDismissRequest = { showPostponeDialog = false },
            title = { Text("Trì hoãn công việc", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chọn hạn chót mới và lý do hoãn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    calendar.set(Calendar.YEAR, year)
                                    calendar.set(Calendar.MONTH, month)
                                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            calendar.set(Calendar.MINUTE, minute)
                                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                                            postponeDueDate = sdf.format(calendar.time)
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (postponeDueDate.isEmpty()) "Chọn hạn mới" else formatUtcToLocal(postponeDueDate),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedTextField(
                        value = postponeReason,
                        onValueChange = { postponeReason = it },
                        label = { Text("Lý do hoãn") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (postponeDueDate.isEmpty() || postponeReason.isEmpty()) {
                        Toast.makeText(context, "Vui lòng điền đủ hạn chót và lý do", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    coroutineScope.launch {
                        val resp = apiService.postponeTask(
                            selectedTaskForPostpone!!.id,
                            PostponeTaskInput(postponeDueDate, postponeReason)
                        )
                        if (resp.isSuccessful) {
                            Toast.makeText(context, "Đã cập nhật hoãn việc", Toast.LENGTH_SHORT).show()
                            showPostponeDialog = false
                            loadTasks()
                        } else {
                            Toast.makeText(context, "Lỗi cập nhật hoãn việc", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Xác Nhận", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostponeDialog = false }) {
                    Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    taskToCancel?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToCancel = null },
            title = { Text("Hủy công việc?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Bạn có chắc muốn hủy \"${task.title}\"?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val resp = apiService.cancelTask(task.id)
                        if (resp.isSuccessful) {
                            Toast.makeText(context, "Đã hủy công việc", Toast.LENGTH_SHORT).show()
                            loadTasks()
                        }
                    }
                    taskToCancel = null
                }) { Text("Hủy việc", color = StateCancelled) }
            },
            dismissButton = {
                TextButton(onClick = { taskToCancel = null }) {
                    Text("Quay lại", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Xóa công việc?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("\"${task.title}\" sẽ bị xóa vĩnh viễn.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val resp = apiService.deleteTask(task.id)
                        if (resp.isSuccessful) {
                            ReminderScheduler.cancel(context, task.id)
                            Toast.makeText(context, "Đã xóa công việc", Toast.LENGTH_SHORT).show()
                            loadTasks()
                        }
                    }
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
                            quickAddLoading = true
                            coroutineScope.launch {
                                try {
                                    val nowRfc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())
                                    val resp = apiService.parseTask(com.example.todoapplication.data.model.ParseTaskInput(quickAddText, nowRfc))
                                    if (resp.isSuccessful && resp.body() != null) {
                                        com.example.todoapplication.data.repository.QuickAddDraft.set(resp.body()!!)
                                        showQuickAdd = false
                                        quickAddText = ""
                                        navController.navigate(Screen.TaskDetail.createRoute("new"))
                                    } else {
                                        Toast.makeText(context, "Không phân tích được. Hãy thử mô tả rõ hơn.", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    quickAddLoading = false
                                }
                            }
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
    onStartClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onPostponeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isAiRecommended: Boolean = false,
    onPomodoroClick: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier
) {
    val isCompleted = task.status == "COMPLETED"
    val isCancelled = task.status == "CANCELLED"
    val isOverdue = task.isOverdue()

    val accentColor = when (task.priority) {
        "HIGH" -> PriorityHighColor
        "MEDIUM" -> PriorityMediumColor
        else -> PriorityLowColor
    }

    val statusDotColor = when {
        isOverdue -> StateOverdue
        task.status == "IN_PROGRESS" -> StateInProgress
        task.status == "COMPLETED" -> StateCompleted
        task.status == "POSTPONED" -> StatePostponed
        task.status == "CANCELLED" -> StateCancelled
        else -> StateTodo
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragHandleModifier != Modifier) Modifier.shadow(if (dragHandleModifier != Modifier) 8.dp else 0.dp, RoundedCornerShape(16.dp)) else Modifier)
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp, top = 13.dp, bottom = 12.dp)
            ) {
                // Title row with status dot + overflow menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusDotColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isCompleted || isCancelled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else null,
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
                            if (!isCompleted && !isCancelled) {
                                DropdownMenuItem(
                                    text = { Text("Hoãn", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = StatePostponed) },
                                    onClick = { menuExpanded = false; onPostponeClick() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hủy việc", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = StateCancelled) },
                                    onClick = { menuExpanded = false; onCancelClick() }
                                )
                            }
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
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                // Tags
                if (task.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 7.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        task.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "#$tag",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom row: date chip + action button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Due date chip
                    task.dueDate?.let { dateStr ->
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
                    if (task.estimatedDuration > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${task.estimatedDuration}p",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Pomodoro button
                    if (!isCompleted && !isCancelled) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f))
                                .clickable(onClick = onPomodoroClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍅", fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // Primary action
                    if (!isCompleted && !isCancelled) {
                        when (task.status) {
                            "IN_PROGRESS" -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StateCompleted.copy(alpha = 0.13f),
                                    modifier = Modifier.clickable(onClick = onCompleteClick)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = StateCompleted)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Hoàn thành", color = StateCompleted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StateInProgress.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable(onClick = onStartClick)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp), tint = StateInProgress)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Bắt đầu", color = StateInProgress, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
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
