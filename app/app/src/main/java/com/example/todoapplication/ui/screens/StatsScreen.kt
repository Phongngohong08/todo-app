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
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.viewmodel.StatsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val context = LocalContext.current
    val state by statsViewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("📊 Thống kê", "📈 Biểu đồ", "🧠 Trí nhớ AI")

    val statsSummary = state.summary
    val isLoadingStats = state.isLoadingStats
    val memories = state.memories
    val isLoadingMemories = state.isLoadingMemories
    val isTriggeringExtraction = state.isExtracting
    val weeklyData = state.weekly
    val isLoadingWeekly = state.isLoadingWeekly

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> statsViewModel.loadSummary()
            1 -> statsViewModel.loadWeekly()
            2 -> statsViewModel.loadMemories()
        }
    }
    LaunchedEffect(Unit) {
        statsViewModel.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                            // Big metric card — 2 columns: Hoàn thành / Đang chờ
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
                                            value = "${statsSummary?.pendingTasks ?: 0}",
                                            label = "Đang chờ",
                                            color = primary
                                        )
                                    }
                                }
                            }

                            // Phân bố việc đang chờ theo danh mục
                            val byCategory = statsSummary?.byCategory ?: emptyMap()
                            if (byCategory.isNotEmpty()) {
                                item {
                                    Text(
                                        "Việc đang chờ theo danh mục",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(byCategory.toList().sortedByDescending { it.second }) { (cat, count) ->
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
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 3.dp, height = 36.dp)
                                                        .background(primary, RoundedCornerShape(2.dp))
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    com.example.todoapplication.ui.utils.categoryLabel(cat),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    "$count việc",
                                                    color = primary,
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
                                                "Không còn việc nào đang chờ!",
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
                                            statsViewModel.triggerExtraction()
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
                                                onClick = { statsViewModel.deleteMemory(memory.id) },
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
