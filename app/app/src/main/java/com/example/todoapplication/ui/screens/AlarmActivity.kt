package com.example.todoapplication.ui.screens

import android.content.Context
import com.example.todoapplication.i18n.AppLanguage
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoapplication.data.notifications.AlarmScheduler
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R

/**
 * Màn báo thức toàn màn hình (hiện cả trên màn hình khóa, tự bật màn hình) cho việc ưu tiên cao đến hạn.
 * Chuông do thông báo phát (lặp tới khi tắt); mọi nút ở đây đều tắt thông báo đó.
 */
class AlarmActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val app = applicationContext

        fun dismiss() {
            AlarmScheduler.dismiss(app, taskId)
            finish()
        }

        setContent {
            TodoApplicationTheme {
                BackHandler { dismiss() }
                AlarmContent(
                    title = title,
                    onComplete = {
                        scope.launch { ServiceLocator.taskRepository.setCompleted(taskId, completed = true) }
                        dismiss()
                    },
                    onSnooze = {
                        AlarmScheduler.schedule(app, taskId, title, System.currentTimeMillis() + AlarmScheduler.SNOOZE_MINUTES * 60_000L)
                        dismiss()
                    },
                    onDismiss = { dismiss() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TITLE = "title"
        /** Ghi database phải xong dù Activity đã đóng. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, taskId: String, title: String): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
            }
    }
}

@Composable
private fun AlarmContent(title: String, onComplete: () -> Unit, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(primary, MaterialTheme.colorScheme.tertiary)))
            .systemBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.alarm_heading), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = primary)
            ) { Text(stringResource(R.string.alarm_complete), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text(stringResource(R.string.alarm_snooze, AlarmScheduler.SNOOZE_MINUTES), fontSize = 15.sp) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alarm_dismiss), color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}
