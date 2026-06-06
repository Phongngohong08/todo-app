package com.example.todoapplication.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SecondaryTeal
import com.example.todoapplication.ui.theme.SurfaceGlass
import com.example.todoapplication.ui.theme.BorderLight
import com.example.todoapplication.ui.theme.TextSecondary
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
import com.example.todoapplication.ui.utils.priorityLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(navController: NavController, taskId: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    val isNewTask = taskId == "new"

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var duration by remember { mutableStateOf("30") }
    var dueDate by remember { mutableStateOf("") }
    var preferredTimeStart by remember { mutableStateOf("") }
    var preferredTimeEnd by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }

    // Fetch task if editing
    LaunchedEffect(taskId) {
        if (!isNewTask) {
            isLoading = true
            try {
                val response = apiService.getTask(taskId)
                if (response.isSuccessful && response.body() != null) {
                    val task = response.body()!!
                    title = task.title
                    description = task.description ?: ""
                    priority = task.priority
                    duration = task.estimatedDuration.toString()
                    dueDate = task.dueDate ?: ""
                    preferredTimeStart = task.preferredTimeStart ?: ""
                    preferredTimeEnd = task.preferredTimeEnd ?: ""
                    if (dueDate.isNotEmpty()) {
                        val parsedDate = parseIso8601(dueDate)
                        if (parsedDate != null) {
                            calendar.time = parsedDate
                        }
                    }
                } else {
                    Toast.makeText(context, "Không thể tải chi tiết công việc", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewTask) "Thêm Công Việc" else "Chỉnh Sửa Công Việc", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
            )
        },
        bottomBar = {
            Surface(color = BackgroundObsidian) {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Tiêu đề không được để trống", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val estimatedMinutes = duration.toIntOrNull() ?: 30
                        val dateString = if (dueDate.isEmpty()) null else dueDate
                        val timeStart = if (preferredTimeStart.isEmpty()) null else preferredTimeStart
                        val timeEnd = if (preferredTimeEnd.isEmpty()) null else preferredTimeEnd

                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val success = if (isNewTask) {
                                    val response = apiService.createTask(
                                        CreateTaskInput(title, description, priority, dateString, estimatedMinutes, timeStart, timeEnd)
                                    )
                                    response.isSuccessful
                                } else {
                                    val response = apiService.updateTask(
                                        taskId,
                                        UpdateTaskInput(title, description, priority, dateString, estimatedMinutes, timeStart, timeEnd)
                                    )
                                    response.isSuccessful
                                }

                                if (success) {
                                    Toast.makeText(context, "Đã lưu công việc thành công!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(context, "Không thể lưu công việc", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("LƯU CÔNG VIỆC", fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề công việc") },
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

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả chi tiết") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Priority Selection
                Text("Độ ưu tiên", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val priorities = listOf("LOW", "MEDIUM", "HIGH")
                    priorities.forEach { p ->
                        val isSelected = priority == p
                        Button(
                            onClick = { priority = p },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) PrimaryIndigo else SurfaceGlass
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryIndigo else BorderLight),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(priorityLabel(p), color = if (isSelected) Color.White else TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Estimated Duration Input + quick presets
                Text("Thời gian ước tính (phút)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60, 90).forEach { preset ->
                        val isSelected = duration == preset.toString()
                        FilterChip(
                            selected = isSelected,
                            onClick = { duration = preset.toString() },
                            label = { Text("$preset", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo,
                                selectedLabelColor = Color.White,
                                labelColor = TextSecondary,
                                containerColor = SurfaceGlass
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = BorderLight,
                                selectedBorderColor = PrimaryIndigo
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { input -> duration = input.filter { it.isDigit() } },
                    label = { Text("Tùy chỉnh (phút)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                // Due Date Picker Button
                Text("Hạn chót công việc", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                        dueDate = sdf.format(calendar.time)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (dueDate.isEmpty()) "Chọn hạn chót" else formatUtcToLocal(dueDate),
                        color = Color.White
                    )
                }

                // Preferred Working Hours
                Text("Khung giờ ưu tiên làm việc (Không bắt buộc)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start hour picker
                    Button(
                        onClick = {
                            val initialHour = if (preferredTimeStart.isNotEmpty()) preferredTimeStart.split(":")[0].toIntOrNull() ?: 8 else 8
                            val initialMinute = if (preferredTimeStart.isNotEmpty()) preferredTimeStart.split(":")[1].toIntOrNull() ?: 0 else 0
                            TimePickerDialog(context, { _, hour, minute ->
                                preferredTimeStart = String.format("%02d:%02d", hour, minute)
                            }, initialHour, initialMinute, true).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (preferredTimeStart.isEmpty()) "Bắt đầu" else "Từ: $preferredTimeStart",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // End hour picker
                    Button(
                        onClick = {
                            val initialHour = if (preferredTimeEnd.isNotEmpty()) preferredTimeEnd.split(":")[0].toIntOrNull() ?: 18 else 18
                            val initialMinute = if (preferredTimeEnd.isNotEmpty()) preferredTimeEnd.split(":")[1].toIntOrNull() ?: 0 else 0
                            TimePickerDialog(context, { _, hour, minute ->
                                preferredTimeEnd = String.format("%02d:%02d", hour, minute)
                            }, initialHour, initialMinute, true).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                        border = BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (preferredTimeEnd.isEmpty()) "Kết thúc" else "Đến: $preferredTimeEnd",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // Clear button
                    if (preferredTimeStart.isNotEmpty() || preferredTimeEnd.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                preferredTimeStart = ""
                                preferredTimeEnd = ""
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa khung giờ", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
