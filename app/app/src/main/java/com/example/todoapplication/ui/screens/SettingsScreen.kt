package com.example.todoapplication.ui.screens

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.example.todoapplication.data.notifications.AlarmScheduler
import com.example.todoapplication.data.notifications.FocusDnd
import com.example.todoapplication.data.notifications.QuickAddNotifier
import com.example.todoapplication.ui.tiles.FocusTileService
import com.example.todoapplication.ui.tiles.QuickAddTileService
import com.example.todoapplication.R
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.ThemeController
import com.example.todoapplication.data.repository.ThemeMode
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.PriorityHighColor
import com.example.todoapplication.ui.utils.weekdayShortLabels
import com.example.todoapplication.ui.utils.formatDuration
import com.example.todoapplication.i18n.AppLanguage
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.viewmodel.SettingsEvent
import com.example.todoapplication.ui.viewmodel.SettingsViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.i18n.str

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
    val nagMinutes by settingsViewModel.nagMinutes.collectAsStateWithLifecycle()
    val nagHighOnly by settingsViewModel.nagHighOnly.collectAsStateWithLifecycle()
    val quietEnabled by settingsViewModel.quietEnabled.collectAsStateWithLifecycle()
    val quietStart by settingsViewModel.quietStart.collectAsStateWithLifecycle()
    val quietEnd by settingsViewModel.quietEnd.collectAsStateWithLifecycle()
    val alarmHighPriority by settingsViewModel.alarmHighPriority.collectAsStateWithLifecycle()
    val appLockEnabled by settingsViewModel.appLockEnabled.collectAsStateWithLifecycle()
    val dailyCapacity by settingsViewModel.dailyCapacityMinutes.collectAsStateWithLifecycle()
    val focusDnd by settingsViewModel.focusDnd.collectAsStateWithLifecycle()
    val quickAddNotif by settingsViewModel.quickAddNotification.collectAsStateWithLifecycle()
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
            title = { Text(stringResource(R.string.logout_unsynced_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    pluralStringResource(R.plurals.logout_unsynced_text, pending.pendingChanges, pending.pendingChanges),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = null
                    settingsViewModel.logoutNow(pending.allDevices)
                }) { Text(stringResource(R.string.logout_anyway), color = PriorityHighColor) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = null }) { Text(stringResource(R.string.logout_stay), color = MaterialTheme.colorScheme.primary) }
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
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.settings_title),
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
                    SettingsSection(title = stringResource(R.string.settings_appearance)) {
                        val themeOptions = listOf(
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system)
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

                    // Ngôn ngữ của riêng app (per-app language): Android 13+ do hệ thống lưu, máy cũ hơn app tự lưu
                    SettingsSection(title = stringResource(R.string.settings_language)) {
                        val currentLanguage = remember { AppLanguage.current(context) }
                        val activity = remember(context) {
                            generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                                .filterIsInstance<android.app.Activity>().firstOrNull()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf(AppLanguage.SYSTEM to stringResource(R.string.language_system)) + AppLanguage.SUPPORTED).forEach { (tag, label) ->
                                StatusFilterChip(text = label, selected = currentLanguage == tag, onClick = {
                                    if (tag != currentLanguage) activity?.let { AppLanguage.set(it, tag) }
                                })
                            }
                        }
                        Text(
                            stringResource(R.string.language_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    // Schedule section
                    SettingsSection(title = stringResource(R.string.settings_rhythm)) {
                        Text(
                            stringResource(R.string.settings_rhythm_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TimePickerField(
                            label = stringResource(R.string.settings_morning_start),
                            value = morningStart,
                            onValueChange = { settingsViewModel.setMorning(it) }
                        )

                        Spacer(Modifier.height(12.dp))

                        TimePickerField(
                            label = stringResource(R.string.settings_evening_end),
                            value = eveningEnd,
                            onValueChange = { settingsViewModel.setEvening(it) }
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = workDuration,
                            onValueChange = { settingsViewModel.setDuration(it) },
                            label = { Text(stringResource(R.string.settings_work_duration)) },
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
                    SettingsSection(title = stringResource(R.string.settings_daily_goal)) {
                        Text(
                            stringResource(R.string.settings_daily_goal_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_complete_each_day), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            IconButton(onClick = { settingsViewModel.setDailyGoal(state.dailyGoal - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease))
                            }
                            Text("${state.dailyGoal}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { settingsViewModel.setDailyGoal(state.dailyGoal + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
                            }
                            Text(stringResource(R.string.unit_tasks), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(R.string.settings_days_off), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            weekdayShortLabels().forEach { (code, label) ->
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
                    SettingsSection(title = stringResource(R.string.settings_lists)) {
                        Text(
                            stringResource(R.string.settings_lists_hint),
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
                                    Text(stringResource(R.string.settings_default_list), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                } else {
                                    IconButton(onClick = { settingsViewModel.removeCategory(c) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
                                label = { Text(stringResource(R.string.list_new)) },
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
                                Text(stringResource(R.string.action_add), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                                stringResource(R.string.settings_save),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Thông báo (theo thiết bị, áp dụng ngay — không cần bấm Lưu)
                    SettingsSection(title = stringResource(R.string.settings_notifications)) {
                        SwitchRow(
                            title = stringResource(R.string.settings_morning_digest),
                            subtitle = stringResource(R.string.settings_morning_digest_sub),
                            checked = digestEnabled,
                            onChange = {
                                settingsViewModel.setDigestEnabled(it)
                                if (it) askNotificationPermission(str(R.string.settings_digest_rationale))
                            }
                        )
                        if (digestEnabled) {
                            TimePickerField(label = stringResource(R.string.settings_digest_time), value = digestTime, onValueChange = settingsViewModel::setDigestTime)
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = stringResource(R.string.review_title),
                            subtitle = stringResource(R.string.settings_weekly_review_sub),
                            checked = weeklyReview,
                            onChange = settingsViewModel::setWeeklyReviewEnabled
                        )
                        Spacer(Modifier.height(8.dp))
                        TimePickerField(
                            label = stringResource(R.string.settings_all_day_time),
                            value = allDayReminder,
                            onValueChange = settingsViewModel::setAllDayReminderTime
                        )
                    }

                    // Tiện ích hệ thống Android (theo thiết bị)
                    SettingsSection(title = stringResource(R.string.settings_system)) {
                        SwitchRow(
                            title = stringResource(R.string.settings_focus_dnd),
                            subtitle = stringResource(R.string.settings_focus_dnd_sub),
                            checked = focusDnd,
                            onChange = { enable ->
                                settingsViewModel.setFocusDnd(enable)
                                if (enable && !FocusDnd.hasAccess(context)) {
                                    Toast.makeText(context, str(R.string.settings_dnd_grant), Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            title = stringResource(R.string.settings_quick_add_notif),
                            subtitle = stringResource(R.string.settings_quick_add_notif_sub),
                            checked = quickAddNotif,
                            onChange = { enable ->
                                settingsViewModel.setQuickAddNotification(enable)
                                if (enable) askNotificationPermission(str(R.string.settings_quick_add_rationale))
                                QuickAddNotifier.refresh(context)
                            }
                        )
                        if (Build.VERSION.SDK_INT >= 33) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.settings_qs_tiles), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.settings_qs_tiles_sub),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                OutlinedButton(onClick = { requestAddTile(context, FocusTileService::class.java, str(R.string.tile_focus), R.drawable.ic_shortcut_focus) }) {
                                    Text("+ " + stringResource(R.string.tile_focus))
                                }
                                OutlinedButton(onClick = { requestAddTile(context, QuickAddTileService::class.java, str(R.string.tile_quick_add), R.drawable.ic_shortcut_add) }) {
                                    Text("+ " + stringResource(R.string.tile_quick_add))
                                }
                            }
                        }
                    }

                    // Kế hoạch tuần (theo thiết bị)
                    SettingsSection(title = stringResource(R.string.settings_planning)) {
                        Text(stringResource(R.string.settings_capacity), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_capacity_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            IconButton(onClick = { settingsViewModel.setDailyCapacityMinutes(dailyCapacity - 30) }) {
                                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease))
                            }
                            Text(
                                formatDuration(dailyCapacity),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            IconButton(onClick = { settingsViewModel.setDailyCapacityMinutes(dailyCapacity + 30) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
                            }
                        }
                    }

                    // Nhắc hẹn nâng cao (theo thiết bị)
                    SettingsSection(title = stringResource(R.string.settings_advanced_reminders)) {
                        Text(stringResource(R.string.settings_nag), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_nag_hint, LocalPrefs.MAX_NAGS),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf(0 to stringResource(R.string.settings_off)) + listOf(5, 10, 15, 30).map { it to formatDuration(it) }).forEach { (m, label) ->
                                StatusFilterChip(text = label, selected = nagMinutes == m, onClick = { settingsViewModel.setNagMinutes(m) })
                            }
                        }
                        if (nagMinutes > 0) {
                            SwitchRow(
                                title = stringResource(R.string.settings_nag_high_only),
                                subtitle = stringResource(R.string.settings_nag_high_only_sub),
                                checked = nagHighOnly,
                                onChange = settingsViewModel::setNagHighOnly
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(
                            title = stringResource(R.string.settings_alarm_high),
                            subtitle = stringResource(R.string.settings_alarm_high_sub),
                            checked = alarmHighPriority,
                            onChange = {
                                settingsViewModel.setAlarmHighPriority(it)
                                if (it) askNotificationPermission(str(R.string.settings_alarm_rationale))
                            }
                        )
                        if (alarmHighPriority) AlarmPermissionHints()
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(
                            title = stringResource(R.string.settings_quiet_hours),
                            subtitle = stringResource(R.string.settings_quiet_hours_sub),
                            checked = quietEnabled,
                            onChange = settingsViewModel::setQuietEnabled
                        )
                        if (quietEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { TimePickerField(stringResource(R.string.settings_from), quietStart, settingsViewModel::setQuietStart) }
                                Box(Modifier.weight(1f)) { TimePickerField(stringResource(R.string.settings_to), quietEnd, settingsViewModel::setQuietEnd) }
                            }
                        }
                    }

                    // Bảo mật (theo thiết bị)
                    SettingsSection(title = stringResource(R.string.settings_security)) {
                        SwitchRow(
                            title = stringResource(R.string.settings_app_lock),
                            subtitle = stringResource(R.string.settings_app_lock_sub, AppLock.RELOCK_AFTER_MS / 1000),
                            checked = appLockEnabled,
                            onChange = { enable ->
                                if (enable && !AppLock.isAvailable(context)) {
                                    Toast.makeText(context, str(R.string.settings_no_lock_screen), Toast.LENGTH_LONG).show()
                                } else {
                                    settingsViewModel.setAppLockEnabled(enable)
                                }
                            }
                        )
                    }

                    // Sao lưu / xuất / khôi phục (Storage Access Framework)
                    SettingsSection(title = stringResource(R.string.settings_data)) { BackupSection() }

                    // Tài khoản
                    SettingsSection(title = stringResource(R.string.settings_account)) {
                        OutlinedButton(
                            onClick = { settingsViewModel.requestLogout(allDevices = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_logout)) }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { settingsViewModel.requestLogout(allDevices = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.settings_logout_all), color = PriorityHighColor) }
                        Text(
                            stringResource(R.string.settings_logout_all_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // About / FAQ section
                    SettingsSection(title = stringResource(R.string.settings_about)) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.settings_version, com.example.todoapplication.BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        Text(
                            stringResource(R.string.settings_about_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.settings_faq), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            stringResource(R.string.settings_faq_text),
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

/**
 * Báo thức cần 2 quyền đặc biệt mà người dùng có thể đã tắt: hẹn giờ chính xác (Android 12+) và
 * hiện toàn màn hình (Android 14+). Thiếu quyền nào thì hiện nút mở đúng trang cài đặt hệ thống.
 * Kiểm tra lại mỗi khi quay về app (ON_RESUME) vì người dùng bật quyền ở màn khác.
 */
@Composable
private fun AlarmPermissionHints() {
    val context = LocalContext.current
    var exactOk by remember { mutableStateOf(true) }
    var fullScreenOk by remember { mutableStateOf(true) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exactOk = AlarmScheduler.canScheduleExact(context)
        fullScreenOk = Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val packageUri = Uri.parse("package:${context.packageName}")
    if (!exactOk && Build.VERSION.SDK_INT >= 31) {
        PermissionHint(stringResource(R.string.settings_exact_alarm_missing)) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
        }
    }
    if (!fullScreenOk && Build.VERSION.SDK_INT >= 34) {
        PermissionHint(stringResource(R.string.settings_full_screen_missing)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))
        }
    }
}

@Composable
private fun PermissionHint(text: String, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 11.sp, color = PriorityHighColor, modifier = Modifier.weight(1f))
        TextButton(onClick = onGrant) { Text(stringResource(R.string.settings_grant)) }
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
                        onValueChange(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute))
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
                    value.ifEmpty { stringResource(R.string.detail_pick_time) },
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

/**
 * Android 13+: hỏi hệ thống thêm ô vào bảng cài đặt nhanh ngay trong app (StatusBarManager.requestAddTileService),
 * thay vì bắt người dùng tự tìm trong chế độ sửa ô.
 */
@androidx.annotation.RequiresApi(33)
private fun requestAddTile(context: android.content.Context, service: Class<*>, label: String, icon: Int) {
    val manager = context.getSystemService(android.app.StatusBarManager::class.java) ?: return
    manager.requestAddTileService(
        android.content.ComponentName(context, service),
        label,
        android.graphics.drawable.Icon.createWithResource(context, icon),
        context.mainExecutor
    ) { result ->
        val msg = when (result) {
            android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> str(R.string.settings_tile_added, label)
            android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> str(R.string.settings_tile_exists, label)
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
