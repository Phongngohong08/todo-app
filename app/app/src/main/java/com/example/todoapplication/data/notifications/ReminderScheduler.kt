package com.example.todoapplication.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.model.Task
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Lập lịch local notification cho task theo hạn chót bằng WorkManager.
 * WorkManager tự khôi phục job sau khi khởi động lại máy, dùng REPLACE theo task id để đồng bộ khi sửa/xoá.
 *
 * Mỗi task có tối đa hai job tách biệt:
 * - "reminder_<id>": nhắc theo hạn chót — [schedule] đặt lại mỗi khi task thay đổi (xem TaskEffects).
 * - "snooze_<id>": nhắc lại do người dùng bấm "Hoãn" — [schedule] KHÔNG đụng tới, nếu không thì lần tải
 *   danh sách kế tiếp (thời điểm nhắc gốc đã qua) sẽ hủy luôn lượt hoãn.
 */
object ReminderScheduler {

    /** Tag chung cho mọi job nhắc việc — để hủy hết một lần khi đăng xuất. */
    const val TAG = "task_reminder"

    private fun uniqueName(taskId: String) = "reminder_$taskId"
    private fun snoozeName(taskId: String) = "snooze_$taskId"

    /** Đặt (hoặc cập nhật) nhắc nhở cho task. Tự huỷ nếu không có hạn, đã xong/huỷ, hoặc đã quá hạn. */
    fun schedule(context: Context, task: Task) {
        val due = task.dueAt
        if (due == null || task.isCompleted) {
            // Hết lý do để nhắc (kể cả lượt đang hoãn)
            cancel(context, task.id)
            return
        }

        // Nhắc trước hạn theo reminderOffsetMinutes (0 = đúng giờ)
        val fireAt = reminderTime(task, due, ServiceLocator.localPrefs.allDayReminderTime.value) -
            task.reminderOffsetMinutes * 60_000L
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) {
            // Đã quá thời điểm nhắc -> không nhắc lại theo hạn, nhưng GIỮ lượt hoãn nếu có
            workManager(context).cancelUniqueWork(uniqueName(task.id))
            return
        }

        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TITLE to task.title,
                    ReminderWorker.KEY_TASK_ID to task.id
                )
            )
            .addTag(TAG)
            .build()

        workManager(context)
            .enqueueUniqueWork(uniqueName(task.id), ExistingWorkPolicy.REPLACE, work)
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

    /** Hoãn tới thời điểm cụ thể (vd "Tối nay 20:00", "Sáng mai 08:00"). */
    fun snoozeUntil(context: Context, taskId: String, title: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TITLE to title,
                    ReminderWorker.KEY_TASK_ID to taskId
                )
            )
            .addTag(TAG)
            .build()
        workManager(context)
            .enqueueUniqueWork(snoozeName(taskId), ExistingWorkPolicy.REPLACE, work)
    }

    /** Hủy mọi nhắc nhở của task (cả nhắc theo hạn lẫn lượt hoãn) — khi xóa/hoàn thành. */
    fun cancel(context: Context, taskId: String) {
        val wm = workManager(context)
        wm.cancelUniqueWork(uniqueName(taskId))
        wm.cancelUniqueWork(snoozeName(taskId))
    }

    private fun workManager(context: Context) = WorkManager.getInstance(context.applicationContext)
}
