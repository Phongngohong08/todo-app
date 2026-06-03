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
import com.example.todoapplication.data.model.UserPreferences
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SecondaryTeal
import com.example.todoapplication.ui.theme.SurfaceGlass
import com.example.todoapplication.ui.theme.BorderLight
import com.example.todoapplication.ui.theme.PriorityHighColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Thống kê", "Trí nhớ AI", "Thiết lập")

    // Tab 1: Stats states
    var statsSummary by remember { mutableStateOf<StatsSummary?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }

    // Tab 2: Memory states
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var isLoadingMemories by remember { mutableStateOf(false) }
    var isTriggeringExtraction by remember { mutableStateOf(false) }

    // Tab 3: Preference states
    var morningStart by remember { mutableStateOf("08:00") }
    var eveningEnd by remember { mutableStateOf("18:00") }
    var workDuration by remember { mutableStateOf("60") }
    var isLoadingPrefs by remember { mutableStateOf(false) }

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

    fun loadPreferences() {
        isLoadingPrefs = true
        coroutineScope.launch {
            try {
                val response = apiService.getPreferences()
                if (response.isSuccessful && response.body() != null) {
                    val prefs = response.body()!!
                    morningStart = prefs.morningStartTime
                    eveningEnd = prefs.eveningEndTime
                    workDuration = prefs.workDurationPreference.toString()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải cấu hình: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingPrefs = false
            }
        }
    }

    // Load data based on active tab
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> loadStats()
            1 -> loadMemories()
            2 -> loadPreferences()
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(BackgroundObsidian)) {
                TopAppBar(
                    title = { Text("Trung tâm AI & Thống kê", color = Color.White, fontWeight = FontWeight.Bold) },
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
                            unselectedContentColor = Color.Gray
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
                                            Text("Hoàn thành", color = Color.Gray, fontSize = 12.sp)
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
                                            Text("Hoãn công việc", color = Color.Gray, fontSize = 12.sp)
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
                                        Text("Thời gian thực hiện tích lũy", color = Color.Gray, fontSize = 12.sp)
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
                                                    val responseList = apiService.listMemories()
                                                    if (responseList.isSuccessful) {
                                                        memories = responseList.body() ?: emptyList()
                                                        if (memories.isEmpty()) {
                                                            Toast.makeText(context, "Phân tích xong. Cần thêm hoạt động công việc hoặc trò chuyện với AI Coach để đúc rút thói quen.", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "Đã phân tích và cập nhật thói quen thành công!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Tải trí nhớ thất bại", Toast.LENGTH_SHORT).show()
                                                    }
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
                                        Text("AI chưa ghi nhận thói quen nào của bạn.", color = Color.Gray)
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

                2 -> { // Preferences tab
                    if (isLoadingPrefs) {
                        CircularProgressIndicator(color = PrimaryIndigo, modifier = Modifier.align(Alignment.Center))
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("Cài đặt cá nhân cho lập lịch AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            // Morning Start Time
                            OutlinedTextField(
                                value = morningStart,
                                onValueChange = { morningStart = it },
                                label = { Text("Thời gian bắt đầu buổi sáng (HH:MM)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryIndigo,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = PrimaryIndigo,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )

                            // Evening End Time
                            OutlinedTextField(
                                value = eveningEnd,
                                onValueChange = { eveningEnd = it },
                                label = { Text("Thời gian kết thúc buổi tối (HH:MM)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryIndigo,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = PrimaryIndigo,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )

                            // Work Duration Block
                            OutlinedTextField(
                                value = workDuration,
                                onValueChange = { workDuration = it },
                                label = { Text("Thời lượng phiên làm việc mong muốn (phút)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryIndigo,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = PrimaryIndigo,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Save Preferences Button
                            Button(
                                onClick = {
                                    val durationInt = workDuration.toIntOrNull() ?: 60
                                    isLoadingPrefs = true
                                    coroutineScope.launch {
                                        try {
                                            val response = apiService.updatePreferences(
                                                UserPreferences(
                                                    userId = "", // Handled on backend side via JWT claims
                                                    morningStartTime = morningStart,
                                                    eveningEndTime = eveningEnd,
                                                    workDurationPreference = durationInt
                                                )
                                            )
                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Cấu hình lập lịch đã được lưu!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Lưu cấu hình thất bại", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isLoadingPrefs = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("LƯU CẤU HÌNH THIẾT LẬP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
