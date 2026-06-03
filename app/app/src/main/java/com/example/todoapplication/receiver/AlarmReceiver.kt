package com.example.todoapplication.receiver

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.todoapplication.AlarmActivity
import com.example.todoapplication.R
import com.example.todoapplication.data.model.AlarmItem
import com.example.todoapplication.manager.AlarmScheduler
import com.example.todoapplication.ui.utils.VibratorHelper
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {

    @SuppressLint("Wakelock")
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "Báo thức!"
        val id = intent.getIntExtra("EXTRA_ID",0)

        Log.d("AlarmReceiver", "Đã nhận báo thức: $message $id")

        val alarmItem = AlarmItem(
            id = id,
            message = message,
            time = LocalDateTime.now().plusDays(1),
        )
        val vibratorHelper= VibratorHelper(context)

        val scheduler= AlarmScheduler(context)
       // scheduler.schedule(alarmItem)
        // 1. WakeLock: Đảm bảo CPU không ngủ trong khi xử lý
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "NewLiveKit:AlarmWakeLock"
        )
        wakeLock.acquire(10 * 1000L)

        // 2. Kích hoạt rung ngay lập tức
        try {
            vibratorHelper.startAlarmVibration()
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Lỗi khi rung: ${e.message}")
        }

        // 3. Chuẩn bị Intent mở AlarmActivity (nên dùng AlarmActivity để xử lý giao diện báo thức)
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_MESSAGE", message)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Tạo Notification Channel (BẮT BUỘC TRÊN ANDROID 8+)
        val channelId = "alarm_urgent_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Báo thức khẩn cấp",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh dành cho báo thức"
                setBypassDnd(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 5. Build Notification
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BÁO THỨC ĐANG REO")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(true)

        notificationManager.notify(1001, notificationBuilder.build())

        // 6. Cố gắng mở Activity trực tiếp (dành cho Android cũ hoặc khi có quyền Overlay)
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Không thể mở Activity trực tiếp, phụ thuộc vào FullScreenIntent: ${e.message}")
        }

        if (wakeLock.isHeld) wakeLock.release()
    }
}