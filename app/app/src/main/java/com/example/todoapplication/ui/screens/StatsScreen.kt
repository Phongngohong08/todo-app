package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.MemoryItem
import com.example.todoapplication.data.model.StatsSummary
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SecondaryTeal
import com.example.todoapplication.ui.theme.SurfaceGlass
import com.example.todoapplication.ui.theme.BorderLight
import com.example.todoapplication.ui.theme.TextSecondary
import com.example.todoapplication.ui.theme.PriorityHighColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Thống kê", "Trí nhớ AI")

    // Tab 1: Stats states
    var statsSummary by remember { mutableStateOf<StatsSummary?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }

    // Tab 2: Memory states
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var isLoadingMemories by remember { mutableStateOf(false) }
    var isTriggeringExtraction by remember { mutableStateOf(false) }

    fun loadStats() {
        isLoadingStats = true
        coroutineScope.launch {
            try {
                val response = apiService.getStatsSummary()
                if (response.isSuccessful) {
                    statsSummary = response.body()
                }
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
                if (response.isSuccessful) {
                    memories = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải trí nhớ: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingMemories = false
            }
        }
    }

    // Load data based on active tab
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> loadStats()
            1 -> loadMemories()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(BackgroundObsidian)) {
                TopAppBar(
                    title = { Text("Trung tâm AI & Thống kê", color = Color.White, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Thiết lập", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
                )

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = BackgroundObsidian,
                    contentColor = PrimaryIndigo
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            selectedContentColor = PrimaryIndigo,
                            unselectedContentColor = TextSecondary
                        )
                    }
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(navController, activeTab = 3)
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> { // Stats tab
                    if (isLoadingStats) {
                        CircularProgressIndicator(color = PrimaryIndigo, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text("Tổng quan hoạt động", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Completed card
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceGlass)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Hoàn thành", color = TextSecondary, fontSize = 12.sp)
                                            Text(
                                                "${statsSummary?.completedTasks ?: 0}",
                                                color = SecondaryTeal,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    // Postponed card
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceGlass)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Hoãn công việc", color = TextSecondary, fontSize = 12.sp)
                                            Text(
                                                "${statsSummary?.postponedTasks ?: 0}",
                                                color = PriorityHighColor,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceGlass)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Thời gian thực hiện tích lũy", color = TextSecondary, fontSize = 12.sp)
                                        Text(
                                            "${statsSummary?.totalTimeSpentMins ?: 0} phút",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Reasons for postpone list
                            val reasons = statsSummary?.postponeReasons ?: emptyMap()
                            if (reasons.isNotEmpty()) {
                                item {
                                    Text("Lý do trì hoãn nhiều nhất", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                                }

                                items(reasons.toList()) { (reason, count) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                                        border = BorderStroke(1.dp, BorderLight)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(reason, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text("$count lần", color = PriorityHighColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> { // AI Memories tab
                    if (isLoadingMemories) {
                        CircularProgressIndicator(color = PrimaryIndigo, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                // Manual extraction trigger
                                Button(
                                    onClick = {
                                        isTriggeringExtraction = true
                                        coroutineScope.launch {
                                            try {
                                                val response = apiService.triggerMemoryExtraction()
                                                if (response.isSuccessful) {
                                                    val extracted = response.body()?.extracted ?: 0
                                                    val analyzed = response.body()?.analyzed ?: 0
                                                    val responseList = apiService.listMemories()
                                                    if (responseList.isSuccessful) {
                                                        memories = responseList.body() ?: emptyList()
                                                    }
                                                    val msg = when {
                                                        extracted > 0 -> "Đã phân tích $analyzed hoạt động và rút ra $extracted thói quen mới."
                                                        analyzed == 0 -> "Chưa có hoạt động hay trò chuyện nào trong 30 ngày để phân tích. Hãy tạo/hoàn thành/hoãn vài công việc hoặc nhắn với AI Coach rồi thử lại."
                                                        else -> "Đã phân tích $analyzed hoạt động nhưng chưa rút ra thói quen mới (hoặc đã trùng với trí nhớ cũ)."
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isTriggeringExtraction
                                ) {
                                    if (isTriggeringExtraction) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Phân tích thói quen bằng AI", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    "Quan sát của AI về bạn (Long-term memory)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 8.dp)
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
                                        Text("AI chưa ghi nhận thói quen nào của bạn.", color = TextSecondary)
                                    }
                                }
                            } else {
                                items(memories) { memory ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceGlass)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = "Insight",
                                                tint = PrimaryIndigo,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = memory.content,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val response = apiService.deleteMemory(memory.id)
                                                        if (response.isSuccessful) {
                                                            Toast.makeText(context, "Đã xóa trí nhớ", Toast.LENGTH_SHORT).show()
                                                            loadMemories()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa",
                                                    tint = PriorityHighColor
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
