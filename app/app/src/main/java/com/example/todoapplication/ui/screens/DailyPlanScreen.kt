package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.model.PlanSlot
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var dailyPlan by remember { mutableStateOf<DailyPlan?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    fun loadPlan() {
        isLoading = true
        coroutineScope.launch {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val localTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val response = apiService.getDailyPlan(todayStr, localTimeStr)
                dailyPlan = if (response.isSuccessful) response.body() else null
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải lịch trình: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun regeneratePlan() {
        isLoading = true
        coroutineScope.launch {
            try {
                val localTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val response = apiService.generateDailyPlan(localTimeStr)
                if (response.isSuccessful) {
                    dailyPlan = response.body()
                    Toast.makeText(context, "Đã tái tạo lịch trình!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Tái tạo thất bại", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadPlan() }

    // Today's date display
    val todayFormatted = remember {
        SimpleDateFormat("EEEE, dd MMMM", Locale("vi", "VN")).format(Date())
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, activeTab = 1) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Gradient date hero header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(primary, primaryContainer)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(
                        "Lịch trình hôm nay",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        todayFormatted.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (dailyPlan != null && dailyPlan!!.planData.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                "${dailyPlan!!.planData.size} khung giờ đã lên lịch",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (dailyPlan != null) {
                    IconButton(
                        onClick = { regeneratePlan() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Tái tạo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Content
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primary)
                    }
                }
                dailyPlan == null || dailyPlan!!.planData.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("📋", fontSize = 52.sp)
                            Text(
                                "Chưa có lịch trình hôm nay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "AI sẽ tự sắp xếp các công việc của bạn theo độ ưu tiên, hạn chót và thói quen làm việc.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.horizontalGradient(listOf(primary, MaterialTheme.colorScheme.tertiary)))
                                    .clickable { regeneratePlan() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Tạo lịch trình bằng AI",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Lịch biểu được tối ưu bởi AI ✨",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(dailyPlan!!.planData) { slot ->
                            ModernTimelineSlot(
                                slot = slot,
                                isFirst = slot == dailyPlan!!.planData.first(),
                                isLast = slot == dailyPlan!!.planData.last(),
                                onClick = {
                                    if (slot.taskId.isNotEmpty()) {
                                        navController.navigate(Screen.TaskDetail.createRoute(slot.taskId))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun slotDurationMinutes(start: String, end: String): Int? {
    fun toMinutes(t: String): Int? {
        val parts = t.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }
    val s = toMinutes(start) ?: return null
    val e = toMinutes(end) ?: return null
    val diff = e - s
    return if (diff > 0) diff else null
}

@Composable
fun ModernTimelineSlot(slot: PlanSlot, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Time column
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                slot.start,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                slot.end,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Timeline indicator
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Vertical line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .offset(y = 16.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            // Dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .offset(y = 14.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(primary, MaterialTheme.colorScheme.tertiary)
                        ),
                        CircleShape
                    )
            )
        }

        // Slot card
        val durationMins = slotDurationMinutes(slot.start, slot.end)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        slot.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (durationMins != null) {
                        Text(
                            "$durationMins phút · ${slot.start}–${slot.end}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
