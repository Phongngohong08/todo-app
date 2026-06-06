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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.DailyPlan
import com.example.todoapplication.data.model.PlanSlot
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SecondaryTeal
import com.example.todoapplication.ui.theme.SurfaceGlass
import com.example.todoapplication.ui.theme.BorderLight
import com.example.todoapplication.ui.theme.TextSecondary
import com.example.todoapplication.ui.navigation.Screen
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

    fun loadPlan() {
        isLoading = true
        coroutineScope.launch {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val localTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val response = apiService.getDailyPlan(todayStr, localTimeStr)
                if (response.isSuccessful) {
                    dailyPlan = response.body()
                } else {
                    dailyPlan = null
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải lịch trình: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPlan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch Trình Hôm Nay", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    if (dailyPlan != null) {
                        IconButton(onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val localTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    val response = apiService.generateDailyPlan(localTimeStr)
                                    if (response.isSuccessful) {
                                        dailyPlan = response.body()
                                        Toast.makeText(context, "Đã tái tạo lịch trình!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Tái tạo lịch trình thất bại", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate Plan", tint = PrimaryIndigo)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
            )
        },
        bottomBar = {
            BottomNavigationBar(navController, activeTab = 1)
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else if (dailyPlan == null || dailyPlan!!.planData.isEmpty()) {
            // Empy State: Button to generate plan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Bạn chưa có lịch trình hôm nay",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Hệ thống AI sẽ tự động sắp xếp các công việc của bạn dựa trên độ ưu tiên, hạn chót và thói quen làm việc.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val localTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    val response = apiService.generateDailyPlan(localTimeStr)
                                    if (response.isSuccessful) {
                                        dailyPlan = response.body()
                                    } else {
                                        Toast.makeText(context, "Không có công việc đang mở nào để lập kế hoạch", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("TẠO LỊCH TRÌNH BẰNG AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // List Timeline slots
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Lịch biểu của bạn được tối ưu hóa bằng trí tuệ nhân tạo.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(dailyPlan!!.planData) { slot ->
                    TimelineSlotRow(
                        slot = slot,
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

/** Tính khoảng thời lượng (phút) giữa hai mốc "HH:mm", trả về null nếu không hợp lệ. */
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
fun TimelineSlotRow(slot: PlanSlot, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time display
        Column(
            modifier = Modifier.width(90.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = slot.start,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = slot.end,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Vertical Line indicator
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(BorderLight)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(PrimaryIndigo, shape = CircleShape)
            )
        }

        // Schedule Slot Card
        val durationMins = slotDurationMinutes(slot.start, slot.end)
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceGlass)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = if (durationMins != null) "${slot.start} – ${slot.end} · $durationMins phút" else "${slot.start} – ${slot.end}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Mở công việc",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
