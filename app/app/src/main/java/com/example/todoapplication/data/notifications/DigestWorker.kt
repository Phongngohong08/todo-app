package com.example.todoapplication.data.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.todoapplication.R
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.endOfToday
import com.example.todoapplication.ui.navigation.AppIntents
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Thông báo định kỳ, đọc thẳng từ Room nên không cần mạng:
 *  - Tóm tắt buổi sáng (như Todoist "Daily summary"): "Hôm nay: 3 việc đến hạn · 1 quá hạn". Bấm → màn Hôm nay.
 *  - Tổng kết tuần (tối Chủ nhật): mời xem lại tuần và lập kế hoạch tuần tới.
 * Mỗi lần chạy tự hẹn lần kế tiếp (OneTimeWork có hẹn giờ chính xác hơn PeriodicWork với khoảng 24h).
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: KIND_DAILY
        val ctx = applicationContext
        val loggedIn = runCatching { ServiceLocator.sessionManager.isLoggedIn() }.getOrDefault(false)

        if (kind == KIND_WEEKLY) {
            if (loggedIn) showWeeklyReview(ctx)
            DigestScheduler.scheduleWeekly(ctx, fromWorker = true)
        } else {
            if (loggedIn) showDailyDigest(ctx)
            DigestScheduler.scheduleDaily(ctx, fromWorker = true)
        }
        return Result.success()
    }

    private suspend fun showDailyDigest(ctx: Context) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val dao = ServiceLocator.database.taskDao()
        val due = dao.getPendingDueBefore(endOfToday(zone))
        val overdue = due.count { it.dueAt!! < now && !(it.dueAllDay && isToday(it.dueAt, zone)) }
        val dueToday = due.size - overdue
        val myDay = dao.countMyDayPending(LocalDate.now(zone).toString())
        if (overdue == 0 && dueToday == 0 && myDay == 0) return // ngày trống → không làm phiền

        val parts = buildList {
            if (dueToday > 0) add("$dueToday việc đến hạn")
            if (overdue > 0) add("$overdue việc quá hạn")
            if (myDay > 0) add("$myDay việc trong Ngày của tôi")
        }
        notify(
            ctx, Notifications.ID_DIGEST,
            title = "Chào buổi sáng! Hôm nay của bạn",
            text = parts.joinToString(" · "),
            open = AppIntents.OPEN_TODAY
        )
    }

    private fun showWeeklyReview(ctx: Context) {
        notify(
            ctx, Notifications.ID_WEEKLY_REVIEW,
            title = "Tổng kết tuần đã sẵn sàng",
            text = "Xem tuần qua bạn làm được gì và lập kế hoạch cho tuần tới",
            open = AppIntents.OPEN_REVIEW
        )
    }

    private fun isToday(millis: Long, zone: ZoneId) =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == LocalDate.now(zone)

    private fun notify(ctx: Context, id: Int, title: String, text: String, open: String) {
        Notifications.ensureChannels(ctx)
        if (!Notifications.canPost(ctx)) return
        val notification = NotificationCompat.Builder(ctx, Notifications.CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(ctx, id, open))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, notification)
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_DAILY = "daily"
        const val KIND_WEEKLY = "weekly"
    }
}

/** Hẹn giờ cho [DigestWorker] theo cài đặt trong [com.example.todoapplication.data.repository.LocalPrefs]. */
object DigestScheduler {
    const val TAG = "digest"
    private const val DAILY = "daily_digest"
    private const val WEEKLY = "weekly_review"
    private val WEEKLY_TIME: LocalTime = LocalTime.of(19, 0)

    /** Gọi khi mở app, sau đăng nhập, và khi đổi cài đặt — REPLACE nên gọi nhiều lần vẫn chỉ có một lịch. */
    fun scheduleAll(context: Context) {
        scheduleDaily(context)
        scheduleWeekly(context)
    }

    fun scheduleDaily(context: Context, fromWorker: Boolean = false) {
        val prefs = ServiceLocator.localPrefs
        if (!prefs.digestEnabled.value) return cancel(context, DAILY)
        val time = runCatching { LocalTime.parse(prefs.digestTime.value) }.getOrDefault(LocalTime.of(7, 30))
        enqueue(context, DAILY, DigestWorker.KIND_DAILY, nextDaily(LocalDateTime.now(), time), fromWorker)
    }

    fun scheduleWeekly(context: Context, fromWorker: Boolean = false) {
        if (!ServiceLocator.localPrefs.weeklyReviewEnabled.value) return cancel(context, WEEKLY)
        enqueue(context, WEEKLY, DigestWorker.KIND_WEEKLY, nextWeekly(LocalDateTime.now()), fromWorker)
    }

    /** Lần kế tiếp của [time] hằng ngày (hôm nay nếu chưa tới giờ, không thì ngày mai). */
    fun nextDaily(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Tối Chủ nhật 19:00 kế tiếp. */
    fun nextWeekly(now: LocalDateTime): LocalDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(WEEKLY_TIME)
        return if (sunday.isAfter(now)) sunday else sunday.plusWeeks(1)
    }

    /**
     * [fromWorker]: gọi từ chính DigestWorker đang chạy → APPEND_OR_REPLACE (REPLACE sẽ hủy luôn worker hiện tại).
     */
    private fun enqueue(context: Context, name: String, kind: String, at: LocalDateTime, fromWorker: Boolean) {
        val delay = java.time.Duration.between(LocalDateTime.now(), at).toMillis().coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DigestWorker.KEY_KIND to kind))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            name, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, work
        )
    }

    private fun cancel(context: Context, name: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name)
    }
}
