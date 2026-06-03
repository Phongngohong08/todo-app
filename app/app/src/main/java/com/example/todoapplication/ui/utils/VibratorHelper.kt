package com.example.todoapplication.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager


class VibratorHelper constructor(
     private val context: Context
) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Rung một lần trong khoảng thời gian xác định
     */
    fun vibrateOneShot(duration: Long = 500L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    /**
     * Rung theo nhịp (pattern) liên tục (thường dùng cho báo thức)
     */
    fun startAlarmVibration() {
        val pattern = longArrayOf(0, 500, 1000) // Nghỉ 0ms, Rung 500ms, Nghỉ 1000ms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 nghĩa là lặp lại từ đầu
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    /**
     * Dừng rung
     */
    fun stopVibration() {
        vibrator.cancel()
    }
}