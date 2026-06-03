package com.example.todoapplication

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todoapplication.ui.utils.VibratorHelper


class AlarmActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vibratorHelper=  VibratorHelper(this)

        // Cấu hình để hiển thị trên màn hình khóa và bật màn hình
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
       // playAlarmSound()
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Báo thức!"
        vibratorHelper.startAlarmVibration()
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = message, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        vibratorHelper.stopVibration()
                        onStopAlarmClick()
                    }) {
                        Text("Đóng")
                    }
                }
            }
        }
    }

    private fun playAlarmSound() {
        try {
            // Sử dụng R.raw.tên_file_bài_hát của bạn
            mediaPlayer = MediaPlayer.create(this, R.raw.ido).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Chọn MUSIC cho bài hát chuẩn âm thanh hơn
                        .build()
                )
                isLooping = true // Phát hết bài tự động lặp lại
                start() // Đối với MediaPlayer.create thì không cần prepare(), gọi start() luôn
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Hàm xử lý khi người dùng bấm nút "TẮT BÁO THỨC" trên màn hình
    fun onStopAlarmClick() {
        stopAlarmSound()
        finish() // Đóng màn hình báo thức lại
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cực kỳ quan trọng: Phải tắt nhạc khi Activity bị hủy để tránh rò rỉ bộ nhớ
        stopAlarmSound()
    }
}