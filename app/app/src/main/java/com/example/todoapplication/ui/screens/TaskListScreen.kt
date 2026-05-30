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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.PostponeTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
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

    // State for Postpone Dialog
    var showPostponeDialog by remember { mutableStateOf(false) }
    var selectedTaskForPostpone by remember { mutableStateOf<Task?>(null) }
    var postponeReason by remember { mutableStateOf("") }
    var postponeDueDate by remember { mutableStateOf("") }

    val calendar = remember { Calendar.getInstance() }

    fun loadTasks() {
        isLoading = true
        coroutineScope.launch {
            try {
                val filter = if (selectedStatusFilter == "ALL") null else selectedStatusFilter
                val response = apiService.listTasks(status = filter)
                if (response.isSuccessful) {
                    tasks = response.body() ?: emptyList()
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

    LaunchedEffect(selectedStatusFilter) {
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
                            color = Color.Gray
                        )
                    }
                },
                actions = {
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
                        text = filterName,
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
                    Text("Không tìm thấy công việc nào", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tasks) { task ->
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
                            onCancelClick = {
                                coroutineScope.launch {
                                    val resp = apiService.cancelTask(task.id)
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Đã hủy công việc", Toast.LENGTH_SHORT).show()
                                        loadTasks()
                                    }
                                }
                            },
                            onDeleteClick = {
                                coroutineScope.launch {
                                    val resp = apiService.deleteTask(task.id)
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Đã xóa công việc", Toast.LENGTH_SHORT).show()
                                        loadTasks()
                                    }
                                }
                            }
                        )
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
                    Text("Chọn hạn chót mới cho công việc và lý do hoãn.", color = Color.Gray)

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
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = PrimaryIndigo,
                            unfocusedLabelColor = Color.Gray,
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
                    Text("Hủy", color = Color.Gray)
                }
            },
            containerColor = SurfaceGlass
        )
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
                    color = if (isCompleted || isCancelled) Color.Gray else Color.White,
                    textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
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
                            Icon(Icons.Default.DateRange, contentDescription = "Due", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = formatUtcToLocal(dateStr), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    if (task.estimatedDuration > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Duration", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${task.estimatedDuration} phút", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isCompleted && !isCancelled) {
                        // Start Action
                        if (task.status == "TODO" || task.status == "POSTPONED" || isOverdue) {
                            IconButton(onClick = onStartClick, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = StateInProgress)
                            }
                        }

                        // Complete Action
                        if (task.status == "IN_PROGRESS") {
                            IconButton(onClick = onCompleteClick, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Complete", tint = StateCompleted)
                            }
                        }

                        // Postpone Action
                        IconButton(onClick = onPostponeClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Postpone", tint = StatePostponed)
                        }

                        // Cancel Action
                        IconButton(onClick = onCancelClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = StateCancelled)
                        }
                    }

                    // Delete Action
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = PriorityHighColor)
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
            text = priority,
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
    NavigationBar(
        containerColor = SurfaceGlass,
        contentColor = Color.LightGray,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { navController.navigate(Screen.TaskList.route) },
            icon = { Icon(Icons.Default.List, contentDescription = "Tasks") },
            label = { Text("Việc Cần Làm", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryIndigo,
                selectedTextColor = PrimaryIndigo,
                indicatorColor = BackgroundObsidian
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { navController.navigate(Screen.DailyPlan.route) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "Plan") },
            label = { Text("Lịch Trình", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryIndigo,
                selectedTextColor = PrimaryIndigo,
                indicatorColor = BackgroundObsidian
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { navController.navigate(Screen.AICoach.route) },
            icon = { Icon(Icons.Default.Send, contentDescription = "Coach") }, // Send icon matches a chat agent bubble
            label = { Text("AI Coach", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryIndigo,
                selectedTextColor = PrimaryIndigo,
                indicatorColor = BackgroundObsidian
            )
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = { navController.navigate(Screen.Stats.route) },
            icon = { Icon(Icons.Default.AccountBox, contentDescription = "Profile") },
            label = { Text("Thống Kê", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryIndigo,
                selectedTextColor = PrimaryIndigo,
                indicatorColor = BackgroundObsidian
            )
        )
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
    val contentColor = if (selected) Color.White else Color.Gray
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
