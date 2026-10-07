package com.example.todoapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.todoapplication.data.notifications.FocusSession
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.theme.StateCompleted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/**
 * [TẦNG UI · MÀN HÌNH] Hẹn giờ tập trung (Pomodoro 25'/5') cho một việc — như TickTick Focus / Forest.
 * Thời gian còn lại tính từ mốc kết thúc đã lưu → đóng app vẫn đúng; hết giờ có thông báo (FocusWorker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(navController: NavController, taskId: String?, autoStart: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by FocusSession.state.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequest()
    val task by remember(taskId) {
        taskId?.let { ServiceLocator.taskRepository.observeTask(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    // Đang chạy phiên cho việc khác → hiện phiên đó (một lúc chỉ tập trung một việc)
    val active = session
    val title = active?.title ?: task?.title ?: stringResource(R.string.focus_default_title)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.endAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    fun start(phase: FocusSession.Phase) {
        FocusSession.start(context, active?.taskId ?: taskId, title, phase)
        askNotificationPermission(str(R.string.focus_notif_rationale))
    }

    // Úp máy xuống bàn (gia tốc kế) khi chưa có phiên / vừa hết phiên → bắt đầu tập trung, có rung xác nhận
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val idle = active == null || active.remainingMillis(now) == 0L
    com.example.todoapplication.ui.components.FaceDownEffect(enabled = idle) {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        start(FocusSession.Phase.FOCUS)
    }

    // Mở từ ô Quick Settings "Tập trung": bắt đầu ngay nếu chưa có phiên nào
    LaunchedEffect(autoStart) {
        if (autoStart && FocusSession.state.value == null) start(FocusSession.Phase.FOCUS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)

            val remaining = active?.remainingMillis(now) ?: FocusSession.FOCUS_MINUTES * 60_000L
            val progress = active?.progress(now) ?: 0f
            val isBreak = active?.phase == FocusSession.Phase.BREAK
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = if (isBreak) StateCompleted else MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val secs = remaining / 1000
                    Text("%02d:%02d".format(secs / 60, secs % 60), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            active == null -> stringResource(R.string.focus_ready)
                            remaining == 0L -> stringResource(R.string.focus_time_up)
                            isBreak -> stringResource(R.string.focus_on_break)
                            else -> stringResource(R.string.focus_focusing)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if ((active?.completedFocus ?: 0) > 0) {
                Text("🍅".repeat(active!!.completedFocus.coerceAtMost(8)) + "  " + pluralStringResource(R.plurals.focus_sessions_done, active.completedFocus, active.completedFocus), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                active == null -> Button(onClick = { start(FocusSession.Phase.FOCUS) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_start_minutes, FocusSession.FOCUS_MINUTES))
                }
                remaining == 0L -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isBreak) OutlinedButton(onClick = { start(FocusSession.Phase.BREAK) }) { Text(stringResource(R.string.focus_break_minutes, FocusSession.BREAK_MINUTES)) }
                    Button(onClick = { start(FocusSession.Phase.FOCUS) }) { Text(stringResource(R.string.focus_next_session)) }
                }
                else -> OutlinedButton(onClick = { FocusSession.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_stop))
                }
            }

            val doneTaskId = active?.taskId ?: taskId
            if (doneTaskId != null && task?.isCompleted != true) {
                TextButton(onClick = {
                    scope.launch {
                        ServiceLocator.taskRepository.setCompleted(doneTaskId, true)
                        FocusSession.stop(context)
                        navController.popBackStack()
                    }
                }) {
                    Text(stringResource(R.string.focus_task_done), color = StateCompleted, fontWeight = FontWeight.SemiBold)
                }
            }

            if (active == null || remaining == 0L) {
                Text(stringResource(R.string.focus_flip_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
            }

            Text(
                stringResource(R.string.focus_tip),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
