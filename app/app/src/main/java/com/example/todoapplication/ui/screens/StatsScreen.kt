package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.model.StatsSummary
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.parseIso8601
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("📊 Thống kê", "📈 Biểu đồ", "🧠 Trí nhớ AI")

    var statsSummary by remember { mutableStateOf<StatsSummary?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var isLoadingMemories by remember { mutableStateOf(false) }
    var isTriggeringExtraction by remember { mutableStateOf(false) }
    var weeklyData by remember { mutableStateOf<List<Int>>(List(7) { 0 }) }
    var isLoadingWeekly by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    fun loadStats() {
        isLoadingStats = true
        coroutineScope.launch {
            try {
                val response = apiService.getStatsSummary()
                if (response.isSuccessful) statsSummary = response.body()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải thống kê: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingStats = false
            }
        }
    }

    fun loadMemories() {
        isLoadingMemories = true
        coroutineScope.launch {
            try {
                val response = apiService.listMemories()
                if (response.isSuccessful) memories = response.body() ?: emptyList()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải trí nhớ: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingMemories = false
            }
        }
    }

    fun loadWeeklyChart() {
        isLoadingWeekly = true
        coroutineScope.launch {
            try {
                val response = apiService.listTasks(status = "COMPLETED")
                if (response.isSuccessful) {
                    val tasks: List<Task> = response.body() ?: emptyList()
                    val cal = Calendar.getInstance()
                    val now = cal.time
                    val counts = MutableList(7) { 0 }
                    // Map: Sunday=0..Saturday=6 in Calendar → Mon=0..Sun=6 display
                    tasks.forEach { task ->
                        val updated = parseIso8601(task.updatedAt) ?: return@forEach
                        cal.time = updated
                        val daysSinceNow = ((now.time - updated.time) / (1000 * 60 * 60 * 24)).toInt()
                        if (daysSinceNow in 0..6) {
                            // index 0 = 6 days ago, index 6 = today
                            val idx = 6 - daysSinceNow
                            counts[idx]++
                        }
                    }
                    weeklyData = counts
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải biểu đồ: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingWeekly = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> loadStats()
            1 -> loadWeeklyChart()
            2 -> loadMemories()
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, activeTab = 3) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with gear icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Trung tâm AI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Thiết lập",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pill tabs
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    tabTitles.forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selectedTab == index) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                title,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                1 -> {
                    if (isLoadingWeekly) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = primary)
                        }
                    } else {
                        WeeklyProductivityChart(
                            weeklyData = weeklyData,
                            primaryColor = primary,
                            tertiaryColor = tertiary
                        )
                    }
                }
                0 -> {
                    if (isLoadingStats) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = primary)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Big metric card — 3 columns
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        BigMetric(
                                            value = "${statsSummary?.completedTasks ?: 0}",
                                            label = "Hoàn thành",
                                            color = StateCompleted
                                        )
                                        VerticalDivider()
                                        BigMetric(
                                            value = "${statsSummary?.postponedTasks ?: 0}",
                                            label = "Đã hoãn",
                                            color = PriorityHighColor
                                        )
                                        VerticalDivider()
                                        BigMetric(
                                            value = "${statsSummary?.totalTimeSpentMins ?: 0}",
                                            label = "Phút làm việc",
                                            color = primary
                                        )
                                    }
                                }
                            }

                            // Postpone reasons
                            val reasons = statsSummary?.postponeReasons ?: emptyMap()
                            if (reasons.isNotEmpty()) {
                                item {
                                    Text(
                                        "Lý do trì hoãn thường gặp",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(reasons.toList().sortedByDescending { it.second }) { (reason, count) ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Colored left bar
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 3.dp, height = 36.dp)
                                                        .background(PriorityHighColor, RoundedCornerShape(2.dp))
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(reason, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PriorityHighColor.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    "$count lần",
                                                    color = PriorityHighColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🎉", fontSize = 44.sp)
                                            Text(
                                                "Chưa có lý do trì hoãn nào!",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (isLoadingMemories) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = primary)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                // Extraction trigger button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (!isTriggeringExtraction)
                                                Brush.horizontalGradient(listOf(primary, tertiary))
                                            else Brush.horizontalGradient(listOf(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            ))
                                        )
                                        .clickable(enabled = !isTriggeringExtraction) {
                                            isTriggeringExtraction = true
                                            coroutineScope.launch {
                                                try {
                                                    val response = apiService.triggerMemoryExtraction()
                                                    if (response.isSuccessful) {
                                                        val extracted = response.body()?.extracted ?: 0
                                                        val analyzed = response.body()?.analyzed ?: 0
                                                        val r2 = apiService.listMemories()
                                                        if (r2.isSuccessful) memories = r2.body() ?: emptyList()
                                                        val msg = when {
                                                            extracted > 0 -> "Đã phân tích $analyzed hoạt động và rút ra $extracted thói quen mới."
                                                            analyzed == 0 -> "Chưa có hoạt động nào trong 30 ngày để phân tích."
                                                            else -> "Đã phân tích $analyzed hoạt động nhưng chưa rút ra thói quen mới."
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Phân tích thất bại: ${response.code()}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isTriggeringExtraction = false
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isTriggeringExtraction) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Phân tích thói quen bằng AI",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    "Quan sát của AI về bạn",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (memories.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🤔", fontSize = 44.sp)
                                            Text(
                                                "AI chưa ghi nhận thói quen nào.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                            Text(
                                                "Nhấn phân tích để bắt đầu.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(memories) { memory ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        Brush.linearGradient(listOf(primary, tertiary)),
                                                        RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = memory.content,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val r = apiService.deleteMemory(memory.id)
                                                        if (r.isSuccessful) {
                                                            Toast.makeText(context, "Đã xóa trí nhớ", Toast.LENGTH_SHORT).show()
                                                            loadMemories()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
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
    }
}

@Composable
private fun WeeklyProductivityChart(
    weeklyData: List<Int>,
    primaryColor: Color,
    tertiaryColor: Color
) {
    val dayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    // Compute day labels for last 7 days
    val cal = Calendar.getInstance()
    val last7Days = (6 downTo 0).map { offset ->
        cal.time = Date(System.currentTimeMillis() - offset * 24 * 60 * 60 * 1000L)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        when (dow) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }
    }
    val maxValue = weeklyData.maxOrNull()?.coerceAtLeast(1) ?: 1
    val totalCompleted = weeklyData.sum()
    val bestDay = weeklyData.indexOfFirst { it == weeklyData.maxOrNull() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Summary chips
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = primaryColor.copy(alpha = 0.1f)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalCompleted", color = primaryColor, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Hoàn thành tuần", color = primaryColor.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
            if (bestDay >= 0 && weeklyData[bestDay] > 0) {
                Surface(shape = RoundedCornerShape(12.dp), color = tertiaryColor.copy(alpha = 0.1f)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(last7Days.getOrElse(bestDay) { "?" }, color = tertiaryColor, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("Ngày năng suất nhất", color = tertiaryColor.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }

        Text("Công việc hoàn thành 7 ngày qua", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))

        // Bar chart
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barCount = weeklyData.size
                        val barWidth = (size.width - (barCount - 1) * 12.dp.toPx()) / barCount
                        val maxH = size.height - 24.dp.toPx()

                        weeklyData.forEachIndexed { i, count ->
                            val barH = (count.toFloat() / maxValue) * maxH
                            val x = i * (barWidth + 12.dp.toPx())
                            val y = size.height - 24.dp.toPx() - barH

                            if (count == 0) {
                                drawRoundRect(
                                    color = Color.Gray.copy(alpha = 0.12f),
                                    topLeft = Offset(x, size.height - 24.dp.toPx() - 4.dp.toPx()),
                                    size = Size(barWidth, 4.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            } else {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(primaryColor, tertiaryColor),
                                        startY = y,
                                        endY = size.height - 24.dp.toPx()
                                    ),
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barH),
                                    cornerRadius = CornerRadius(8.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Day labels row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    last7Days.forEachIndexed { i, label ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            if (weeklyData.getOrElse(i) { 0 } > 0) {
                                Text(
                                    "${weeklyData[i]}",
                                    fontSize = 10.sp,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Per-day breakdown
        Text("Chi tiết từng ngày", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        last7Days.forEachIndexed { i, dayLabel ->
            val count = weeklyData.getOrElse(i) { 0 }
            val fraction = count.toFloat() / maxValue.toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Brush.horizontalGradient(listOf(primaryColor, tertiaryColor)), RoundedCornerShape(4.dp))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("$count", color = if (count > 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun BigMetric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(56.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
