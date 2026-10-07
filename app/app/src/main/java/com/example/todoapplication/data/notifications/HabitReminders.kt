package com.example.todoapplication.data.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.todoapplication.R
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.ui.navigation.AppIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import com.example.todoapplication.i18n.str

/**
 * Nhắc thói quen: mỗi thói quen có giờ nhắc → một job "habit_<id>" chạy vào giờ đó mỗi ngày. Worker chỉ hiện
 * thông báo nếu HÔM NAY còn cần làm (theo lịch, chưa đánh dấu, thói quen N lần/tuần chưa đủ), rồi tự hẹn ngày mai.
 */
object HabitReminderScheduler {
    const val TAG = "habit_reminder"
    private fun name(habitId: String) = "habit_$habitId"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Hẹn lại nhắc của mọi thói quen (sau khi thêm/sửa/xóa, đồng bộ về, mở app). */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val habits = ServiceLocator.database.habitDao().getActive().map { it.toDomain() }
            val wm = WorkManager.getInstance(app)
            wm.cancelAllWorkByTag(TAG)
            habits.filter { it.reminderTime.isNotBlank() }.forEach { schedule(app, it, fromWorker = false) }
        }
    }

    /** [fromWorker]: gọi từ chính worker đang chạy → APPEND_OR_REPLACE (REPLACE sẽ hủy luôn worker hiện tại). */
    fun schedule(context: Context, habit: Habit, fromWorker: Boolean) {
        val time = runCatching { LocalTime.parse(habit.reminderTime) }.getOrNull() ?: return
        val at = DigestScheduler.nextDaily(LocalDateTime.now().plusSeconds(if (fromWorker) 60 else 0), time)
        val delay = java.time.Duration.between(LocalDateTime.now(), at).toMillis().coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(HabitReminderWorker.KEY_HABIT_ID to habit.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            name(habit.id),
            if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            work
        )
    }

    fun notificationId(habitId: String): Int = habitId.hashCode() xor 0x4AB1_0000
}

class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getString(KEY_HABIT_ID) ?: return Result.success()
        val ctx = applicationContext
        if (!runCatching { ServiceLocator.sessionManager.isLoggedIn() }.getOrDefault(false)) return Result.success()

        val dao = ServiceLocator.database.habitDao()
        val habit = dao.getById(habitId)?.takeIf { !it.isDeleted && !it.archived }?.toDomain()
            ?: return Result.success() // đã xóa / lưu trữ → dừng chuỗi nhắc
        if (habit.reminderTime.isBlank()) return Result.success()

        val today = LocalDate.now()
        val weekStart = HabitLogic.weekStart(today)
        val done = buildSet {
            for (i in 0L..6L) {
                val d = weekStart.plusDays(i).toString()
                if (dao.isDone(habitId, d) > 0) add(d)
            }
        }
        if (HabitLogic.isDueToday(habit, done, today)) show(ctx, habit)
        HabitReminderScheduler.schedule(ctx, habit, fromWorker = true)
        return Result.success()
    }

    private fun show(ctx: Context, habit: Habit) {
        Notifications.ensureChannels(ctx)
        if (!Notifications.canPost(ctx)) return
        val id = HabitReminderScheduler.notificationId(habit.id)
        val doneIntent = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, HabitActionReceiver::class.java)
                .setAction(HabitActionReceiver.ACTION_DONE)
                .putExtra(KEY_HABIT_ID, habit.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle("${habit.emoji.ifBlank { "🌱" }} ${habit.name}")
            .setContentText(str(R.string.habit_reminder_text))
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(ctx, id, AppIntents.OPEN_HABITS))
            .addAction(0, str(R.string.habit_done), doneIntent)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, notification)
    }

    companion object {
        const val KEY_HABIT_ID = "habit_id"
    }
}

/** Nút "Đã làm" trên thông báo nhắc thói quen: đánh dấu hôm nay (ghi Room, đồng bộ sau). */
class HabitActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val habitId = intent.getStringExtra(HabitReminderWorker.KEY_HABIT_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now().toString()
                val dao = ServiceLocator.database.habitDao()
                // Chỉ đánh dấu khi chưa làm (bấm hai lần không bỏ đánh dấu)
                if (dao.isDone(habitId, today) == 0) ServiceLocator.habitRepository.toggle(habitId, today)
            } finally {
                NotificationManagerCompat.from(app).cancel(HabitReminderScheduler.notificationId(habitId))
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.example.todoapplication.action.HABIT_DONE"
    }
}
