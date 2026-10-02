package com.example.todoapplication.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Khóa ứng dụng: khi bật trong Cài đặt, app đòi vân tay / khuôn mặt / mã PIN máy lúc mở lần đầu
 * và khi quay lại sau hơn [RELOCK_AFTER_MS] ở nền. Trạng thái chỉ nằm trong bộ nhớ — tiến trình bị
 * hệ điều hành giết thì lần mở sau khóa lại (đúng ý muốn).
 */
object AppLock {
    const val RELOCK_AFTER_MS = 30_000L
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var started = false
    private var backgroundedAt: Long? = null

    /** Activity hiện lên (ON_START). */
    fun onStart(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (!enabled) {
            _locked.value = false
        } else {
            val away = backgroundedAt?.let { now - it > RELOCK_AFTER_MS } ?: false
            if (!started || away) _locked.value = true
        }
        started = true
        backgroundedAt = null
    }

    /** Activity khuất (ON_STOP). */
    fun onStop(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    fun unlock() {
        _locked.value = false
    }

    /** Máy đã cài vân tay / khuôn mặt / mã khóa màn hình chưa (chưa có thì không bật được khóa). */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

/** Màn che toàn bộ nội dung khi app đang khóa; tự mở hộp xác thực của hệ thống. */
@Composable
fun LockScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val authenticate = remember(context) {
        {
            val activity = context as? FragmentActivity
            if (activity == null) {
                AppLock.unlock() // không thể hiện hộp xác thực → không khóa người dùng ở ngoài
            } else {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            error = null
                            AppLock.unlock()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            // Máy đã gỡ hết vân tay/mã khóa → không còn cách xác thực, mở khóa luôn
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                                errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                            ) {
                                AppLock.unlock()
                            } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
                                error = errString.toString()
                            }
                        }
                    }
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Mở khóa TaskFlow")
                        .setSubtitle("Xác thực để xem công việc của bạn")
                        .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                        .build()
                )
            }
        }
    }
    LaunchedEffect(Unit) { authenticate() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Chặn chạm xuyên xuống nội dung bên dưới
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("TaskFlow đang khóa", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { authenticate() }) { Text("Mở khóa") }
        }
    }
}
