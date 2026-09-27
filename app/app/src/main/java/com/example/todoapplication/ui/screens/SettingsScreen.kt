package com.example.todoapplication.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.ThemeController
import com.example.todoapplication.data.repository.ThemeMode
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.PriorityHighColor
import com.example.todoapplication.ui.utils.WEEKDAY_SHORT
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.viewmodel.SettingsEvent
import com.example.todoapplication.ui.viewmodel.SettingsViewModel

/** [TẦNG UI · MÀN HÌNH] Cài đặt — chỉnh giờ làm việc/thời lượng (cho AI) và chọn theme Sáng/Tối. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val context = LocalContext.current
    // Quan sát state cấu hình. Lưu ý: ô nhập ở màn này lấy value TỪ state ViewModel (không remember cục bộ),
    // vì giá trị cần tải từ server về và lưu lại — khác các ô nhập tạm ở LoginScreen.
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val morningStart = state.morningStart
    val eveningEnd = state.eveningEnd
    val workDuration = state.workDuration
    val isLoading = state.isLoading
    val isSaving = state.isSaving
    val categories by settingsViewModel.categories.collectAsStateWithLifecycle()

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // Mở màn: (1) tải cấu hình hiện tại từ server, (2) lắng nghe thông báo lưu thành công/thất bại.
    val digestEnabled by settingsViewModel.digestEnabled.collectAsStateWithLifecycle()
    val digestTime by settingsViewModel.digestTime.collectAsStateWithLifecycle()
    val weeklyReview by settingsViewModel.weeklyReviewEnabled.collectAsStateWithLifecycle()
    val allDayReminder by settingsViewModel.allDayReminderTime.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequest()
    var confirmLogout by remember { mutableStateOf<SettingsEvent.ConfirmLogout?>(null) }

    LaunchedEffect(Unit) { settingsViewModel.load() }
    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                is SettingsEvent.ConfirmLogout -> confirmLogout = event
                SettingsEvent.LoggedOut -> navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    confirmLogout?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmLogout = null },
            title = { Text("Đăng xuất khi chưa đồng bộ?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Còn ${pending.pendingChanges} thay đổi chưa gửi được lên máy chủ (đang offline). Đăng xuất bây giờ sẽ làm mất các thay đổi này.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = null
                    settingsViewModel.logoutNow(pending.allDevices)
                }) { Text("Vẫn đăng xuất", color = PriorityHighColor) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = null }) { Text("Ở lại", color = MaterialTheme.colorScheme.primary) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Thiết lập",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // Theme section
                    SettingsSection(title = "🌗 Giao diện") {
                        val themeOptions = listOf(
                            ThemeMode.LIGHT to "☀️ Sáng",
                            ThemeMode.DARK to "🌙 Tối",
                            ThemeMode.SYSTEM to "📱 Hệ thống"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themeOptions.forEach { (mode, label) ->
                                val selected = ThemeController.mode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { ThemeController.setMode(context, mode) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Schedule section
                    SettingsSection(title = "🕐 Nhịp sinh hoạt") {
                        Text(
                            "AI dùng các thông số này để sắp xếp lịch trình phù hợp với bạn.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TimePickerField(
                            label = "Bắt đầu buổi sáng",
                            value = morningStart,
                            onValueChange = { settingsViewModel.setMorning(it) }
                        )

                        Spacer(Modifier.height(12.dp))

                        TimePickerField(
                            label = "Kết thúc buổi tối",
                            value = eveningEnd,
                            onValueChange = { settingsViewModel.setEvening(it) }
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = workDuration,
                            onValueChange = { settingsViewModel.setDuration(it) },
                            label = { Text("Thời lượng phiên làm việc (phút)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Mục tiêu hằng ngày + ngày nghỉ (kiểu Todoist Karma)
                    SettingsSection(title = "🎯 Mục tiêu hằng ngày") {
                        Text(
                            "Chuỗi ngày 🔥 chỉ tăng khi bạn hoàn thành đủ số việc này. Ngày nghỉ không làm đứt chuỗi.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mỗi ngày hoàn thành", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            IconButton(onClick = { settingsViewModel.setDailyGoal(state.dailyGoal - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Giảm")
                            }
                            Text("${state.dailyGoal}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { settingsViewModel.setDailyGoal(state.dailyGoal + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Tăng")
                            }
                            Text("việc", color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("Ngày nghỉ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WEEKDAY_SHORT.forEach { (code, label) ->
                                val sel = code in state.daysOff
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { settingsViewModel.toggleDayOff(code) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Category management section
                    SettingsSection(title = "🏷️ Danh mục") {
                        Text(
                            "Danh mục dùng khi tạo công việc. Bạn có thể thêm danh mục riêng và xoá danh mục tự thêm.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        categories.forEach { c ->
                            val isDefault = CategoryRepository.DEFAULTS.contains(c)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(categoryLabel(c), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (isDefault) {
                                    Text("Mặc định", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                } else {
                                    IconButton(onClick = { settingsViewModel.removeCategory(c) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var newCat by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCat,
                                onValueChange = { newCat = it },
                                label = { Text("Danh mục mới") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = primary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primary)
                                    .clickable { settingsViewModel.addCategory(newCat); newCat = "" }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Thêm", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Save button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (!isSaving)
                                    Brush.horizontalGradient(listOf(primary, tertiary))
                                else Brush.horizontalGradient(listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                ))
                            )
                            .clickable(enabled = !isSaving) { settingsViewModel.save() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "Lưu thiết lập",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Thông báo (theo thiết bị, áp dụng ngay — không cần bấm Lưu)
                    SettingsSection(title = "🔔 Thông báo") {
                        SwitchRow(
                            title = "Tóm tắt buổi sáng",
                            subtitle = "Số việc đến hạn, quá hạn và trong Ngày của tôi",
                            checked = digestEnabled,
                            onChange = {
                                settingsViewModel.setDigestEnabled(it)
                                if (it) askNotificationPermission("Bật thông báo để nhận tóm tắt việc cần làm mỗi sáng.")
                            }
                        )
                        if (digestEnabled) {
                            TimePickerField(label = "Giờ gửi tóm tắt", value = digestTime, onValueChange = settingsViewModel::setDigestTime)
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = "Tổng kết tuần",
                            subtitle = "Tối Chủ nhật 19:00 — xem lại tuần và lập kế hoạch tuần tới",
                            checked = weeklyReview,
                            onChange = settingsViewModel::setWeeklyReviewEnabled
                        )
                        Spacer(Modifier.height(8.dp))
                        TimePickerField(
                            label = "Giờ nhắc cho việc \"cả ngày\"",
                            value = allDayReminder,
                            onValueChange = settingsViewModel::setAllDayReminderTime
                        )
                    }

                    // Tài khoản
                    SettingsSection(title = "👤 Tài khoản") {
                        OutlinedButton(
                            onClick = { settingsViewModel.requestLogout(allDevices = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Đăng xuất khỏi máy này") }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { settingsViewModel.requestLogout(allDevices = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Đăng xuất khỏi mọi thiết bị", color = PriorityHighColor) }
                        Text(
                            "Dùng khi mất điện thoại hoặc đăng nhập nhầm ở máy lạ.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // About / FAQ section
                    SettingsSection(title = "ℹ️ Giới thiệu") {
                        Text("TaskFlow AI", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Phiên bản 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        Text(
                            "Ứng dụng quản lý công việc tích hợp AI: thêm nhanh bằng ngôn ngữ tự nhiên, lịch trình AI hàng ngày, AI Coach và trí nhớ dài hạn.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Hỏi đáp nhanh", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "• Không nhận thông báo? Hãy cấp quyền thông báo và đặt hạn chót cho việc.\n" +
                                "• Thêm danh mục mới? Ngay tại mục Danh mục ở trên, hoặc trong màn chi tiết công việc.\n" +
                                "• AI hiểu sai khi thêm nhanh? Mô tả rõ thời gian/độ ưu tiên hơn, rồi chỉnh tay trước khi lưu.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun TimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val parts = value.split(":")
                    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    TimePickerDialog(context, { _, hour, minute ->
                        onValueChange(String.format("%02d:%02d", hour, minute))
                    }, initialHour, initialMinute, true).show()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value.ifEmpty { "Chọn giờ" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
