package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.viewmodel.CalendarViewModel
import java.util.*

/** [TẦNG UI · MÀN HÌNH] Lịch tháng — hiển thị việc theo ngày (tasksByDay từ CalendarViewModel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
) {
    // state.tasksByDay: map "ngày" → task rơi vào ngày đó (ViewModel đã khai triển việc lặp).
    val state by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary

    // State cục bộ điều khiển việc HIỂN THỊ lịch (tháng/năm đang xem, ngày đang chọn) — không đụng dữ liệu.
    // mutableIntStateOf = bản tối ưu của mutableStateOf cho kiểu Int (tránh đóng hộp).
    val today = remember { Calendar.getInstance() }
    var shownYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var shownMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) } // 0-based
    var selectedDay by remember { mutableStateOf(CalendarViewModel.keyOf(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))) }

    LaunchedEffect(Unit) { calendarViewModel.load() }   // tải & khai triển task một lần khi mở màn

    val monthNames = listOf(
        "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
        "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
    )

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, activeTab = 1) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lịch công việc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }

            // Month switcher
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    if (shownMonth == 0) { shownMonth = 11; shownYear-- } else shownMonth--
                }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Tháng trước", tint = primary) }
                Text("${monthNames[shownMonth]}, $shownYear", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = {
                    if (shownMonth == 11) { shownMonth = 0; shownYear++ } else shownMonth++
                }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Tháng sau", tint = primary) }
            }

            // Weekday labels
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { d ->
                    Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }

            // Calendar grid
            val cal = Calendar.getInstance().apply { clear(); set(shownYear, shownMonth, 1) }
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            // Monday-based offset: Calendar.MONDAY=2 → 0
            val firstDow = cal.get(Calendar.DAY_OF_WEEK)
            val offset = ((firstDow - Calendar.MONDAY) + 7) % 7

            val cells = buildList {
                repeat(offset) { add(0) }
                for (d in 1..daysInMonth) add(d)
                while (size % 7 != 0) add(0)
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (day > 0) {
                                    val key = CalendarViewModel.keyOf(shownYear, shownMonth, day)
                                    val dayTasks = state.tasksByDay[key].orEmpty()
                                    val isToday = key == CalendarViewModel.keyOf(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
                                    val isSelected = key == selectedDay
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> primary
                                                    isToday -> primary.copy(alpha = 0.12f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { selectedDay = key },
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "$day",
                                            fontSize = 14.sp,
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dayTasks.isNotEmpty()) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else priorityDotColor(dayTasks))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Tasks for selected day
            val selectedTasks = state.tasksByDay[selectedDay].orEmpty()
            Text(
                "Công việc ngày ${selectedDay.takeLast(2)}/${selectedDay.substring(5, 7)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary)
                }
            } else if (selectedTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 36.sp)
                        Text("Không có công việc nào", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedTasks, key = { it.id }) { task ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                navController.navigate(Screen.TaskDetail.createRoute(task.id))
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor(task.priority)))
                                Spacer(Modifier.width(12.dp))
                                Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun priorityColor(priority: String): Color = when (priority) {
    "HIGH" -> PriorityHighColor
    "MEDIUM" -> PriorityMediumColor
    else -> PriorityLowColor
}

private fun priorityDotColor(tasks: List<Task>): Color {
    return when {
        tasks.any { it.priority == "HIGH" } -> PriorityHighColor
        tasks.any { it.priority == "MEDIUM" } -> PriorityMediumColor
        else -> PriorityLowColor
    }
}
