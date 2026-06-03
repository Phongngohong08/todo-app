package com.example.todoapplication.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.todoapplication.receiver.AlarmReceiver
import com.example.todoapplication.data.model.AlarmItem
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(
    private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(item: AlarmItem) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_MESSAGE", item.message)
            putExtra("EXTRA_ID", item.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id ?: -1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()

        // 1. Làm tròn giây và nano trước khi so sánh, tránh bị lệch vài giây so với hiện tại
        val baseTime = (item.time ?: now).withSecond(0).withNano(0)

        // 2. Nếu thời gian đặt nhỏ hơn hoặc bằng hiện tại, tự động cộng thêm 1 ngày
        val time = if (baseTime.isBefore(now) || baseTime.isEqual(now)) {
            baseTime.plusDays(1)
        } else {
            baseTime
        }

        // 3. Đổi sang mili-giây chuẩn xác theo múi giờ máy
        val triggerAt = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        Log.d("DUCLUONG", "Đặt báo thức thành công vào lúc: " + AlarmItem.Companion.getFormattedTime(time) + " | Timestamp: $triggerAt")

        // 4. Tiến hành đặt báo thức vào AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt, // 🛠️ SỬA TẠI ĐÂY: Thay số 0 thành triggerAt
                    pendingIntent
                )
            } else {
                // Fallback nếu không có quyền đặt lịch chính xác
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt, // 🛠️ Đảm bảo ở đây cũng dùng triggerAt
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt, // 🛠️ Đảm bảo ở đây cũng dùng triggerAt
                pendingIntent
            )
        }
    }

    fun cancel(item: AlarmItem) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                item.id ?: -1,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}