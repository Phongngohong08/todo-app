package com.example.todoapplication.data.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.todoapplication.R
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.todoapplication.i18n.str

/**
 * [TẦNG DATA · SERVICE] Foreground Service của phiên tập trung (Pomodoro):
 *  - Thông báo CỐ ĐỊNH đếm ngược (Chronometer đếm lùi tới endAt) hiện cả trên màn khóa, có nút Dừng.
 *  - Bật "Không làm phiền" trong lúc tập trung (nếu người dùng cho phép + bật trong Thiết lập), tắt lại khi xong.
 *  - Tự dừng khi hết giờ — FocusWorker (WorkManager) vẫn là nơi báo "hết giờ" và ghi lịch sử, nên app bị hệ thống
 *    dừng service thì phiên vẫn kết thúc đúng.
 *
 * Kiểu FGS "specialUse" (Android 14+): hẹn giờ tập trung do người dùng chủ động bắt đầu, không thuộc loại có sẵn.
 */
class FocusService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FocusSession.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        FocusSession.init(this)
        show(FocusSession.state.value)
        return START_NOT_STICKY
    }

    private var watchJob: Job? = null

    /** Hiện thông báo cho phiên [state] và theo dõi tới khi hết giờ / phiên bị dừng / được thay bằng phiên khác. */
    private fun show(state: FocusSession.State?) {
        if (state == null || state.endAt <= System.currentTimeMillis()) {
            stopSelf()
            return
        }
        ServiceCompat.startForeground(
            this, Notifications.ID_FOCUS_ONGOING, buildNotification(state),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (state.phase == FocusSession.Phase.FOCUS) FocusDnd.enable(this) else FocusDnd.restore(this)

        watchJob?.cancel()
        watchJob = scope.launch {
            val timer = launch {
                delay((state.endAt - System.currentTimeMillis()).coerceAtLeast(0))
                stopSelf()
            }
            val next = FocusSession.state.first { it == null || it.startedAt != state.startedAt }
            timer.cancel()
            show(next) // null → dừng; phiên mới (vd chuyển sang nghỉ) → cập nhật thông báo
        }
    }

    override fun onDestroy() {
        scope.cancel()
        FocusDnd.restore(this)
        super.onDestroy()
    }

    private fun buildNotification(s: FocusSession.State) = NotificationCompat.Builder(this, Notifications.CHANNEL_FOCUS_ONGOING)
        .setSmallIcon(R.drawable.ic_stat_task)
        .setContentTitle(str(if (s.phase == FocusSession.Phase.FOCUS) R.string.focus_notif_focusing else R.string.focus_notif_break))
        .setContentText(s.title.ifBlank { str(R.string.focus_default_title) })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // Đếm ngược do hệ thống vẽ — không cần cập nhật thông báo mỗi giây
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(s.endAt)
        .setShowWhen(true)
        .setContentIntent(Notifications.openApp(this, Notifications.ID_FOCUS_ONGOING, AppIntents.OPEN_FOCUS))
        .addAction(
            0, str(R.string.focus_notif_stop),
            PendingIntent.getService(
                this, 0, Intent(this, FocusService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val TAG = "FocusService"
        const val ACTION_STOP = "com.example.todoapplication.action.FOCUS_STOP"

        /** Bật service cho phiên đang chạy. Nếu hệ thống không cho bắt đầu FGS từ nền thì vẫn còn FocusWorker. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException (Android 12+, khi app không ở tiền cảnh)
                Log.w(TAG, "Cannot start focus service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
        }
    }
}

/**
 * Bật / trả lại chế độ "Không làm phiền" của hệ thống trong phiên tập trung.
 * Cần quyền truy cập chính sách thông báo (người dùng cấp trong Cài đặt) — không có quyền thì bỏ qua.
 * Chỉ trả lại chế độ cũ nếu CHÍNH app đã bật (người dùng tự bật DND trước đó thì giữ nguyên).
 */
object FocusDnd {
    private const val PREFS = "focus_dnd"
    private const val KEY_PREVIOUS = "previous_filter"

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun enable(context: Context) {
        if (!ServiceLocator.localPrefs.focusDnd.value || !hasAccess(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val current = nm.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL) return // người dùng đã tự bật một chế độ
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_PREVIOUS, current).apply()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getInt(KEY_PREVIOUS, -1)
        if (previous == -1 || !hasAccess(context)) return
        context.getSystemService(NotificationManager::class.java).setInterruptionFilter(previous)
        prefs.edit().remove(KEY_PREVIOUS).apply()
    }
}
