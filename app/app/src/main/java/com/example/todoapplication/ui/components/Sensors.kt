package com.example.todoapplication.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

/*
 * [TẦNG UI · CẢM BIẾN] Hai cử chỉ dùng gia tốc kế (SensorManager). Chỉ lắng nghe khi màn hình đang hiện
 * (DisposableEffect gỡ listener khi rời màn) để không tốn pin.
 */

/** Gia tốc tổng (đơn vị g) vượt ngưỡng này coi là một cú lắc. */
private const val SHAKE_G = 2.6f
private const val SHAKE_COOLDOWN_MS = 1_200L

/** Trục z hướng xuống đất (máy úp mặt) liên tục chừng này thì coi là "úp máy". */
private const val FACE_DOWN_Z = -8.5f
private const val FACE_DOWN_HOLD_MS = 1_500L

/** Lắc máy → [onShake] (vd Hoàn tác thao tác vừa làm). */
@Composable
fun ShakeEffect(enabled: Boolean = true, onShake: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        var last = 0L
        val listener = accelerometerListener { x, y, z ->
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            val now = System.currentTimeMillis()
            if (g > SHAKE_G && now - last > SHAKE_COOLDOWN_MS) {
                last = now
                callback()
            }
        }
        val unregister = register(context, listener)
        onDispose { unregister() }
    }
}

/** Úp máy xuống mặt bàn (màn hình hướng xuống) giữ yên ~1,5 giây → [onFaceDown] (vd bắt đầu tập trung). */
@Composable
fun FaceDownEffect(enabled: Boolean, onFaceDown: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onFaceDown)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        var since = 0L
        var fired = false
        val listener = accelerometerListener { _, _, z ->
            val now = System.currentTimeMillis()
            if (z < FACE_DOWN_Z) {
                if (since == 0L) since = now
                if (!fired && now - since >= FACE_DOWN_HOLD_MS) {
                    fired = true
                    callback()
                }
            } else {
                since = 0L
                fired = false
            }
        }
        val unregister = register(context, listener)
        onDispose { unregister() }
    }
}

private fun accelerometerListener(onValues: (Float, Float, Float) -> Unit) = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) = onValues(event.values[0], event.values[1], event.values[2])
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Đăng ký gia tốc kế; trả hàm gỡ. Máy không có cảm biến thì bỏ qua. */
private fun register(context: Context, listener: SensorEventListener): () -> Unit {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return {}
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    return { manager.unregisterListener(listener) }
}
