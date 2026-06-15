package com.example.todoapplication.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.ui.theme.*
import kotlinx.coroutines.delay

enum class PomodoroPhase { WORK, SHORT_BREAK, LONG_BREAK }

private val PHASE_DURATIONS = mapOf(
    PomodoroPhase.WORK to 25 * 60,
    PomodoroPhase.SHORT_BREAK to 5 * 60,
    PomodoroPhase.LONG_BREAK to 15 * 60
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    navController: NavController,
    taskId: String,
    taskTitle: String
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    var phase by remember { mutableStateOf(PomodoroPhase.WORK) }
    var sessionCount by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(PHASE_DURATIONS[PomodoroPhase.WORK]!!) }
    var isRunning by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }

    val totalSeconds = PHASE_DURATIONS[phase]!!
    val progress = secondsLeft.toFloat() / totalSeconds.toFloat()

    val phaseColor by animateColorAsState(
        targetValue = when (phase) {
            PomodoroPhase.WORK -> primary
            PomodoroPhase.SHORT_BREAK -> StateCompleted
            PomodoroPhase.LONG_BREAK -> tertiary
        },
        animationSpec = tween(600),
        label = "phaseColor"
    )

    fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 300, 100, 300), -1))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 300, 100, 300, 100, 300), -1)
        }
    }

    fun advancePhase() {
        vibrate()
        when (phase) {
            PomodoroPhase.WORK -> {
                val newCount = sessionCount + 1
                sessionCount = newCount
                phase = if (newCount % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> {
                phase = PomodoroPhase.WORK
            }
        }
        secondsLeft = PHASE_DURATIONS[phase]!!
        isRunning = false
        isCompleted = true
    }

    LaunchedEffect(isRunning, phase) {
        isCompleted = false
        while (isRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        if (isRunning && secondsLeft == 0) {
            advancePhase()
        }
    }

    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pomodoro Timer", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(taskTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Phase pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = phaseColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = when (phase) {
                        PomodoroPhase.WORK -> "🍅  Tập trung làm việc"
                        PomodoroPhase.SHORT_BREAK -> "☕  Nghỉ ngắn"
                        PomodoroPhase.LONG_BREAK -> "🌴  Nghỉ dài"
                    },
                    color = phaseColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Circular timer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(260.dp)
            ) {
                val arcStrokeWidth = 16.dp
                val trackColor = phaseColor.copy(alpha = 0.12f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = arcStrokeWidth.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    val topLeft = Offset(inset, inset)

                    // Track arc
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(phaseColor.copy(alpha = 0.7f), phaseColor),
                            center = Offset(size.width / 2f, size.height / 2f)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 2.sp
                    )
                    if (isCompleted) {
                        Text("✓ Xong!", color = phaseColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            when (phase) {
                                PomodoroPhase.WORK -> "còn lại"
                                else -> "nghỉ ngơi"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Session dots
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Phiên:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index < sessionCount % 4 || (sessionCount > 0 && sessionCount % 4 == 0)) 14.dp else 10.dp)
                            .background(
                                if (index < sessionCount % 4 || (sessionCount > 0 && sessionCount % 4 == 0 && index == 3))
                                    phaseColor
                                else phaseColor.copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }
                if (sessionCount > 0) {
                    Text("×${sessionCount}", color = phaseColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(40.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            isRunning = false
                            secondsLeft = PHASE_DURATIONS[phase]!!
                            isCompleted = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }

                // Play/Pause button (big)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(phaseColor, phaseColor.copy(alpha = 0.75f))))
                        .clickable { isRunning = !isRunning },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Tạm dừng" else "Bắt đầu",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip to next phase
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { advancePhase() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏭", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Stats card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PomodoroStat(value = "$sessionCount", label = "Phiên hoàn thành", color = phaseColor)
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    PomodoroStat(value = "${sessionCount * 25}", label = "Phút tập trung", color = primary)
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    PomodoroStat(value = "${sessionCount / 4}", label = "Chu kỳ hoàn thành", color = tertiary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tip text
            Text(
                text = when (phase) {
                    PomodoroPhase.WORK -> "💡 Đặt điện thoại xuống và tập trung trong 25 phút"
                    PomodoroPhase.SHORT_BREAK -> "🚶 Đứng dậy đi lại một chút!"
                    PomodoroPhase.LONG_BREAK -> "🧘 Nghỉ ngơi sâu — cơ thể bạn xứng đáng được thư giãn"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun PomodoroStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
