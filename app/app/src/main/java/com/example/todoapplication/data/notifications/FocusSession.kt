package com.example.todoapplication.data.notifications

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.todoapplication.R
import com.example.todoapplication.ui.navigation.AppIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Phiên tập trung kiểu Pomodoro gắn với một công việc (như TickTick / Forest): 25 phút làm → 5 phút nghỉ.
 *
 * Chỉ lưu MỐC KẾT THÚC (epoch millis) chứ không đếm ngược trong bộ nhớ: màn hình tự tính thời gian còn lại,
 * nên đóng app / xoay màn / tiến trình bị hệ thống dừng vẫn đúng. Hết giờ → [FocusWorker] báo bằng thông báo.
 */
object FocusSession {
    enum class Phase { FOCUS, BREAK }

    data class State(
        val taskId: String?,
        val title: String,
        val phase: Phase,
        val startedAt: Long,
        val endAt: Long,
        /** Số phiên tập trung đã xong liên tiếp cho việc này (hiển thị 🍅 x N). */
        val completedFocus: Int
    ) {
        fun remainingMillis(now: Long): Long = (endAt - now).coerceAtLeast(0)
        fun progress(now: Long): Float =
            if (endAt <= startedAt) 1f else ((now - startedAt).toFloat() / (endAt - startedAt)).coerceIn(0f, 1f)
    }

    const val FOCUS_MINUTES = 25
    const val BREAK_MINUTES = 5
    private const val PREFS = "focus_session"
    private const val WORK_NAME = "focus_timer"

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var loaded = false

    /** Nạp phiên đang chạy (nếu có) — gọi khi app khởi động. */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endAt = p.getLong("endAt", 0L)
        if (endAt == 0L) return
        _state.value = State(
            taskId = p.getString("taskId", null),
            title = p.getString("title", "").orEmpty(),
            phase = runCatching { Phase.valueOf(p.getString("phase", Phase.FOCUS.name)!!) }.getOrDefault(Phase.FOCUS),
            startedAt = p.getLong("startedAt", 0L),
            endAt = endAt,
            completedFocus = p.getInt("completedFocus", 0)
        )
    }

    fun start(context: Context, taskId: String?, title: String, phase: Phase = Phase.FOCUS, minutes: Int? = null) {
        val now = System.currentTimeMillis()
        val length = minutes ?: if (phase == Phase.FOCUS) FOCUS_MINUTES else BREAK_MINUTES
        val previous = _state.value
        val done = if (previous?.taskId == taskId) previous?.completedFocus ?: 0 else 0
        val next = State(taskId, title, phase, now, now + length * 60_000L, done)
        save(context, next)
        schedule(context, next)
    }

    /** Hết giờ (từ [FocusWorker]) — tăng số phiên nếu vừa xong một phiên tập trung. */
    fun onFinished(context: Context) {
        val current = _state.value ?: return
        if (current.phase == Phase.FOCUS) save(context, current.copy(completedFocus = current.completedFocus + 1))
    }

    fun stop(context: Context) {
        _state.value = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context.applicationContext).cancel(Notifications.ID_FOCUS)
    }

    private fun save(context: Context, s: State) {
        _state.value = s
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("taskId", s.taskId)
            .putString("title", s.title)
            .putString("phase", s.phase.name)
            .putLong("startedAt", s.startedAt)
            .putLong("endAt", s.endAt)
            .putInt("completedFocus", s.completedFocus)
            .apply()
    }

    private fun schedule(context: Context, s: State) {
        val work = OneTimeWorkRequestBuilder<FocusWorker>()
            .setInitialDelay((s.endAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }
}

/** Báo hết phiên tập trung / hết giờ nghỉ. */
class FocusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        FocusSession.init(ctx)
        val s = FocusSession.state.value ?: return Result.success()
        FocusSession.onFinished(ctx)

        Notifications.ensureChannels(ctx)
        if (!Notifications.canPost(ctx)) return Result.success()

        val isFocus = s.phase == FocusSession.Phase.FOCUS
        val builder = NotificationCompat.Builder(ctx, Notifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(if (isFocus) "Hết ${FocusSession.FOCUS_MINUTES} phút tập trung 🍅" else "Hết giờ nghỉ")
            .setContentText(
                if (isFocus) "\"${s.title}\" — nghỉ ${FocusSession.BREAK_MINUTES} phút rồi làm tiếp nhé"
                else "Sẵn sàng cho phiên tiếp theo: \"${s.title}\""
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(ctx, Notifications.ID_FOCUS, AppIntents.OPEN_FOCUS))

        // Xong việc luôn từ thông báo
        if (isFocus && s.taskId != null) {
            val complete = Intent(ctx, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COMPLETE
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, s.taskId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, Notifications.ID_FOCUS)
            }
            builder.addAction(
                android.R.drawable.ic_menu_save, "Việc đã xong",
                PendingIntent.getBroadcast(
                    ctx, Notifications.ID_FOCUS, complete,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        NotificationManagerCompat.from(ctx).notify(Notifications.ID_FOCUS, builder.build())
        return Result.success()
    }
}
