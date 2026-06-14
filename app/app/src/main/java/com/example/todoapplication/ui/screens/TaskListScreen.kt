package com.example.todoapplication.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.PostponeTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.statusLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

fun Task.isOverdue(): Boolean {
    if (status == "COMPLETED" || status == "CANCELLED") return false
    val dueDateStr = dueDate ?: return false
    val date = parseIso8601(dueDateStr) ?: return false
    return date.before(Date())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { NetworkClient.getApiService(context) }

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    // State for Postpone Dialog
    var showPostponeDialog by remember { mutableStateOf(false) }
    var selectedTaskForPostpone by remember { mutableStateOf<Task?>(null) }
    var postponeReason by remember { mutableStateOf("") }
    var postponeDueDate by remember { mutableStateOf("") }

    // State for destructive confirmation dialogs
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToCancel by remember { mutableStateOf<Task?>(null) }

    // State for AI Quick Add
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
        // Debounce nhẹ để không gọi API mỗi ký tự khi gõ tìm kiếm
        kotlinx.coroutines.delay(300)
        loadTasks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Xin chào, ${sessionManager.getUserName()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hôm nay bạn có ${tasks.filter { it.status != "COMPLETED" }.size} việc cần làm",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Default.Star, contentDescription = "Thêm nhanh bằng AI", tint = SecondaryTeal)
                    }
                    IconButton(onClick = {
                        sessionManager.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.TaskList.route) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Log out", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.TaskDetail.createRoute("new")) },
                containerColor = PrimaryIndigo,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
        bottomBar = {
            BottomNavigationBar(navController, activeTab = 0)
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm công việc...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Horizontal Status Filter Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("ALL", "TODO", "IN_PROGRESS", "POSTPONED", "COMPLETED", "CANCELLED")
                filters.forEach { filterName ->
                    val isSelected = selectedStatusFilter == filterName
                    StatusFilterChip(
                        text = statusLabel(filterName),
                        selected = isSelected,
                        onClick = { selectedStatusFilter = filterName }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryIndigo)
                }
            } else if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Không tìm thấy công việc nào", color = TextSecondary, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                false // Không xóa khỏi danh sách, chỉ trượt trả về và để loadTasks() làm mới
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
                                        .background(StateCompleted.copy(alpha = 0.25f))
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
                            onCardClick = {
                                navController.navigate(Screen.TaskDetail.createRoute(task.id))
                            },
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
                                    if (parsedDate != null) {
                                        calendar.time = parsedDate
                                    }
                                } else {
                                    calendar.time = Date()
                                }
                                showPostponeDialog = true
                            },
                            onCancelClick = { taskToCancel = task },
                            onDeleteClick = { taskToDelete = task }
                        )
                        }
                    }
                }
            }
        }
    }

    // Postpone Dialog UI Setup
    if (showPostponeDialog && selectedTaskForPostpone != null) {
        AlertDialog(
            onDismissRequest = { showPostponeDialog = false },
            title = { Text("Trì hoãn công việc", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chọn hạn chót mới cho công việc và lý do hoãn.", color = TextSecondary)

                    // Due Date Picker Button
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
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (postponeDueDate.isEmpty()) "Chọn hạn mới" else formatUtcToLocal(postponeDueDate),
                            color = Color.White
                        )
                    }

                    // Reason Text Input
                    OutlinedTextField(
                        value = postponeReason,
                        onValueChange = { postponeReason = it },
                        label = { Text("Lý do hoãn") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = PrimaryIndigo,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
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
                    }
                ) {
                    Text("Xác Nhận", color = SecondaryTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostponeDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = SurfaceGlass
        )
    }

    // Cancel Confirmation Dialog
    taskToCancel?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToCancel = null },
            title = { Text("Hủy công việc?", color = Color.White) },
            text = { Text("Bạn có chắc muốn hủy \"${task.title}\"? Công việc sẽ chuyển sang trạng thái đã hủy.", color = TextSecondary) },
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
                }) {
                    Text("Hủy việc", color = StateCancelled)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToCancel = null }) {
                    Text("Quay lại", color = TextSecondary)
                }
            },
            containerColor = SurfaceGlass
        )
    }

    // Delete Confirmation Dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Xóa công việc?", color = Color.White) },
            text = { Text("Bạn có chắc muốn xóa vĩnh viễn \"${task.title}\"? Hành động này không thể hoàn tác.", color = TextSecondary) },
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
                }) {
                    Text("Xóa", color = PriorityHighColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Quay lại", color = TextSecondary)
                }
            },
            containerColor = SurfaceGlass
        )
    }

    // AI Quick Add bottom sheet
    if (showQuickAdd) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAdd = false },
            containerColor = SurfaceGlass
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Thêm nhanh bằng AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Gõ một câu mô tả, AI sẽ tự tách thành công việc.", color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    placeholder = { Text("VD: Họp với sếp thứ 6 lúc 3h chiều, khoảng 1 tiếng", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !quickAddLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Button(
                    onClick = {
                        if (quickAddText.isBlank()) return@Button
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
                    enabled = !quickAddLoading && quickAddText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (quickAddLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Phân tích", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Task Card Composable
@Composable
fun TaskCard(
    task: Task,
    onCardClick: () -> Unit,
    onStartClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onPostponeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isCompleted = task.status == "COMPLETED"
    val isCancelled = task.status == "CANCELLED"
    val isOverdue = task.isOverdue()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
        border = BorderStroke(
            1.dp,
            if (isOverdue) StateOverdue
            else when (task.status) {
                "IN_PROGRESS" -> StateInProgress
                "POSTPONED" -> StatePostponed
                "COMPLETED" -> StateCompleted
                "CANCELLED" -> StateCancelled
                else -> BorderLight
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Priority Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted || isCancelled) TextSecondary else Color.White,
                    textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.recurrence != "NONE") {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Lặp lại",
                            tint = SecondaryTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isOverdue) {
                        OverduePill()
                    } else if (isCancelled) {
                        CancelledPill()
                    }
                    PriorityPill(task.priority)
                }
            }

            // Description
            if (!task.description.isNullOrEmpty()) {
                Text(
                    text = task.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tags
            if (task.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    task.tags.forEach { tag ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = PrimaryIndigo.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "#$tag",
                                color = PrimaryIndigo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Info row: Due Date, Duration, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    task.dueDate?.let { dateStr ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = "Due", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = formatUtcToLocal(dateStr), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (task.estimatedDuration > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Duration", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${task.estimatedDuration} phút", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Action area: one primary action + overflow menu
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isCompleted && !isCancelled) {
                        // Primary action depends on status
                        when {
                            task.status == "IN_PROGRESS" -> {
                                FilledTonalButton(
                                    onClick = onCompleteClick,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = StateCompleted.copy(alpha = 0.2f),
                                        contentColor = StateCompleted
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hoàn thành", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> {
                                FilledTonalButton(
                                    onClick = onStartClick,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = StateInProgress.copy(alpha = 0.2f),
                                        contentColor = StateInProgress
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Bắt đầu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Overflow menu for the remaining actions
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Thêm", tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(SurfaceGlass)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chỉnh sửa", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary) },
                                onClick = { menuExpanded = false; onCardClick() }
                            )
                            if (!isCompleted && !isCancelled) {
                                DropdownMenuItem(
                                    text = { Text("Hoãn", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = StatePostponed) },
                                    onClick = { menuExpanded = false; onPostponeClick() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hủy việc", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = StateCancelled) },
                                    onClick = { menuExpanded = false; onCancelClick() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Xóa", color = PriorityHighColor) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PriorityHighColor) },
                                onClick = { menuExpanded = false; onDeleteClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverduePill() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = StateOverdue.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, StateOverdue.copy(alpha = 0.5f))
    ) {
        Text(
            text = "QUÁ HẠN",
            color = StateOverdue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CancelledPill() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = StateCancelled.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, StateCancelled.copy(alpha = 0.5f))
    ) {
        Text(
            text = "ĐÃ HỦY",
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

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
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

// Global Bottom Navigation Bar Composable
@Composable
fun BottomNavigationBar(navController: NavController, activeTab: Int) {
    data class NavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

    val items = listOf(
        NavItem(Screen.TaskList.route, Icons.Default.List, "Việc Cần Làm"),
        NavItem(Screen.DailyPlan.route, Icons.Default.DateRange, "Lịch Trình"),
        NavItem(Screen.AICoach.route, Icons.Default.Send, "AI Coach"),
        NavItem(Screen.Stats.route, Icons.Default.AccountBox, "Thống Kê")
    )

    NavigationBar(
        containerColor = SurfaceGlass,
        contentColor = Color.LightGray,
        tonalElevation = 8.dp
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = activeTab == index,
                onClick = {
                    // Bỏ qua nếu đang ở tab hiện tại để tránh tải lại không cần thiết
                    if (activeTab != index) {
                        navController.navigate(item.route) {
                            // Quay về start destination và lưu trạng thái các tab, tránh chồng backstack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryIndigo,
                    selectedTextColor = PrimaryIndigo,
                    indicatorColor = BackgroundObsidian
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) PrimaryIndigo else SurfaceGlass
    val contentColor = if (selected) Color.White else TextSecondary
    val borderColor = if (selected) PrimaryIndigo else BorderLight

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
