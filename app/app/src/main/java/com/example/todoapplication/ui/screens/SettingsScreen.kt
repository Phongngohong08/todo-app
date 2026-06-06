package com.example.todoapplication.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
import com.example.todoapplication.data.model.UserPreferences
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.BorderLight
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SecondaryTeal
import com.example.todoapplication.ui.theme.SurfaceGlass
import com.example.todoapplication.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var morningStart by remember { mutableStateOf("08:00") }
    var eveningEnd by remember { mutableStateOf("18:00") }
    var workDuration by remember { mutableStateOf("60") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
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
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thiết lập", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
            )
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryIndigo)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text("Cài đặt cá nhân cho lập lịch AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "AI dùng các thông số này để sắp xếp lịch trình phù hợp với nhịp sinh hoạt của bạn.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                TimePickerField(
                    label = "Thời gian bắt đầu buổi sáng",
                    value = morningStart,
                    onValueChange = { morningStart = it }
                )

                TimePickerField(
                    label = "Thời gian kết thúc buổi tối",
                    value = eveningEnd,
                    onValueChange = { eveningEnd = it }
                )

                OutlinedTextField(
                    value = workDuration,
                    onValueChange = { input -> workDuration = input.filter { it.isDigit() } },
                    label = { Text("Thời lượng phiên làm việc mong muốn (phút)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = PrimaryIndigo,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val durationInt = workDuration.toIntOrNull() ?: 60
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val response = apiService.updatePreferences(
                                    UserPreferences(
                                        userId = "",
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
                                isLoading = false
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

/**
 * Nút chọn giờ dùng TimePickerDialog, hiển thị giá trị "HH:MM". Tránh việc người dùng gõ tay sai định dạng.
 */
@Composable
fun TimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Button(
            onClick = {
                val parts = value.split(":")
                val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(context, { _, hour, minute ->
                    onValueChange(String.format("%02d:%02d", hour, minute))
                }, initialHour, initialMinute, true).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
            border = BorderStroke(1.dp, BorderLight),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value.ifEmpty { "Chọn giờ" }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.DateRange, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
            }
        }
    }
}
