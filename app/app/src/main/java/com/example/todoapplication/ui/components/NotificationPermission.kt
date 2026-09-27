package com.example.todoapplication.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.todoapplication.data.notifications.Notifications
import com.example.todoapplication.di.ServiceLocator

/**
 * Xin quyền thông báo (Android 13+) ĐÚNG LÚC — khi người dùng vừa đặt hạn/nhắc việc hoặc bật thông báo buổi
 * sáng — kèm một câu giải thích, thay vì hỏi ngay lúc mở app khi họ chưa hiểu app làm gì (dễ bị từ chối).
 * Chỉ hỏi một lần; đã từ chối thì không làm phiền nữa (vẫn bật lại được trong cài đặt hệ thống).
 *
 * Trả về hàm `ask(lýDo)` để gọi ở nơi cần.
 */
@Composable
fun rememberNotificationPermissionRequest(): (reason: String) -> Unit {
    val context = LocalContext.current
    var rationale by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    rationale?.let { reason ->
        AlertDialog(
            onDismissRequest = { rationale = null },
            title = { Text("Bật thông báo?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = null
                    ServiceLocator.localPrefs.notificationPermissionAsked = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Bật", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    rationale = null
                    ServiceLocator.localPrefs.notificationPermissionAsked = true
                }) { Text("Để sau", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    return remember(context) {
        { reason ->
            val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)
            if (needs && !ServiceLocator.localPrefs.notificationPermissionAsked) rationale = reason
        }
    }
}
