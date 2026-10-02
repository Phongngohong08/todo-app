package com.example.todoapplication.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.model.Task
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Lập lịch nhắc việc theo hạn chót.
 *
 * Hai đường:
 *  - Thông báo thường: WorkManager (tự khôi phục sau khi khởi động lại máy).
 *  - Báo thức toàn màn hình cho việc ưu tiên cao (nếu bật trong Thiết lập): AlarmManager — đúng giờ hơn
 *    WorkManager khi máy đang ngủ (xem [AlarmScheduler]).
 *
 * Mỗi task có các job tách biệt, mỗi job một tên riêng để không đè lên nhau:
 *  - "reminder_<id>": nhắc theo hạn — [schedule] đặt lại mỗi khi task thay đổi (xem TaskEffects).
 *  - "snooze_<id>":   lượt "Hoãn" người dùng chọn — [schedule] KHÔNG đụng tới, nếu không thì lần đồng bộ kế
 *    tiếp (mốc nhắc gốc đã qua) sẽ hủy luôn lượt hoãn.
 *  - "nag_<id>":      nhắc dai — lặp lại tới khi xong (tối đa LocalPrefs.MAX_NAGS lần).
 *  - "quiet_<id>":    nhắc rơi vào giờ yên tĩnh, dời tới lúc hết giờ yên tĩnh.
 * Job nhắc dai / giờ yên tĩnh được hẹn từ CHÍNH worker đang chạy → dùng APPEND_OR_REPLACE (REPLACE sẽ hủy
 * luôn worker hiện tại).
 */
object ReminderScheduler {

    /** Tag chung cho mọi job nhắc việc — để hủy hết một lần khi đăng xuất. */
    const val TAG = "task_reminder"

    private fun uniqueName(taskId: String) = "reminder_$taskId"
    private fun snoozeName(taskId: String) = "snooze_$taskId"
    private fun nagName(taskId: String) = "nag_$taskId"
    private fun quietName(taskId: String) = "quiet_$taskId"

    /** Đặt (hoặc cập nhật) nhắc nhở cho task. Tự huỷ nếu không có hạn, đã xong/huỷ, hoặc đã quá hạn. */
    fun schedule(context: Context, task: Task) {
        val due = task.dueAt
        if (due == null || task.isCompleted) {
            // Hết lý do để nhắc (kể cả lượt đang hoãn, nhắc dai, báo thức)
            cancel(context, task.id)
            return
        }

        // Nhắc trước hạn theo reminderOffsetMinutes (0 = đúng giờ)
        val prefs = ServiceLocator.localPrefs
        val fireAt = reminderTime(task, due, prefs.allDayReminderTime.value) - task.reminderOffsetMinutes * 60_000L
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) {
            // Đã quá thời điểm nhắc -> không nhắc lại theo hạn, nhưng GIỮ lượt hoãn / nhắc dai nếu có
            workManager(context).cancelUniqueWork(uniqueName(task.id))
            AlarmScheduler.cancel(context, task.id)
            return
        }

        if (prefs.alarmHighPriority.value && task.priority == "HIGH") {
            AlarmScheduler.schedule(context, task.id, task.title, fireAt)
            workManager(context).cancelUniqueWork(uniqueName(task.id))
            return
        }
        AlarmScheduler.cancel(context, task.id)

        enqueue(context, uniqueName(task.id), task.id, task.title, delay, attempt = 0, ExistingWorkPolicy.REPLACE)
    }

    /** Đặt lại nhắc việc cho mọi việc còn chờ (sau khởi động lại máy, hoặc khi đổi cài đặt báo thức). */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        ServiceLocator.database.taskDao().getPendingWithDue().forEach { schedule(app, it.toDomain()) }
    }

    /**
     * Mốc nhắc gốc (chưa trừ "nhắc trước"): việc có giờ → đúng hạn; việc "cả ngày" (hạn 23:59) → giờ nhắc
     * mặc định [allDayTime] ("HH:mm") của chính ngày đó, vì nhắc lúc 23:59 thì đã muộn.
     */
    fun reminderTime(task: Task, due: Long, allDayTime: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!task.dueAllDay) return due
        val time = runCatching { LocalTime.parse(allDayTime) }.getOrDefault(LocalTime.of(8, 0))
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** Hoãn nhắc nhở: nhắc lại sau [delayMinutes] phút (dùng cho nút "Hoãn 1 giờ"). */
    fun snooze(context: Context, taskId: String, title: String, delayMinutes: Long) =
        snoozeUntil(context, taskId, title, System.currentTimeMillis() + delayMinutes * 60_000L)

    /** Hoãn tới thời điểm cụ thể (vd "Tối nay 20:00", "Sáng mai 08:00"). Hoãn thì dừng nhắc dai đang chạy. */
    fun snoozeUntil(context: Context, taskId: String, title: String, atMillis: Long) {
        workManager(context).cancelUniqueWork(nagName(taskId))
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        enqueue(context, snoozeName(taskId), taskId, title, delay, attempt = 0, ExistingWorkPolicy.REPLACE)
    }

    /** Nhắc dai: lần thứ [attempt] sau [minutes] phút. Gọi từ ReminderWorker đang chạy. */
    fun scheduleNag(context: Context, taskId: String, title: String, minutes: Int, attempt: Int) {
        enqueue(
            context, nagName(taskId), taskId, title, minutes * 60_000L, attempt,
            ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }

    /** Nhắc rơi vào giờ yên tĩnh → nhắc lại lúc [atMillis] (hết giờ yên tĩnh). Gọi từ ReminderWorker đang chạy. */
    fun deferForQuietHours(context: Context, taskId: String, title: String, atMillis: Long, attempt: Int) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        enqueue(context, quietName(taskId), taskId, title, delay, attempt, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Hủy mọi nhắc nhở của task (theo hạn, hoãn, nhắc dai, giờ yên tĩnh, báo thức) — khi xóa/hoàn thành. */
    fun cancel(context: Context, taskId: String) {
        val wm = workManager(context)
        wm.cancelUniqueWork(uniqueName(taskId))
        wm.cancelUniqueWork(snoozeName(taskId))
        wm.cancelUniqueWork(nagName(taskId))
        wm.cancelUniqueWork(quietName(taskId))
        AlarmScheduler.cancel(context, taskId)
    }

    private fun enqueue(
        context: Context,
        name: String,
        taskId: String,
        title: String,
        delayMillis: Long,
        attempt: Int,
        policy: ExistingWorkPolicy
    ) {
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TITLE to title,
                    ReminderWorker.KEY_TASK_ID to taskId,
                    ReminderWorker.KEY_ATTEMPT to attempt
                )
            )
            .addTag(TAG)
            .build()
        workManager(context).enqueueUniqueWork(name, policy, work)
    }

    private fun workManager(context: Context) = WorkManager.getInstance(context.applicationContext)
}
