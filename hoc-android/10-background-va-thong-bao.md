# Bài 10 — Chạy nền & Thông báo

Tính năng nhắc việc đòi hỏi app **làm việc khi nó không mở** — thậm chí sau khi khởi động lại máy. Đây là địa hạt của **WorkManager** (lên lịch chạy nền) + **Notifications** (hiện thông báo) + **BroadcastReceiver** (xử lý nút bấm trên thông báo). Bài này ở mức "tham khảo" — hiểu bức tranh là đủ, không cần thuộc từng dòng.

Với dev backend: **WorkManager ≈ hàng đợi job/cron có độ bền cao**, nhưng chạy trên máy người dùng và phải "thân thiện với pin".

## 1. Vì sao không dùng coroutine thường?

`viewModelScope.launch { delay(...) }` chết ngay khi ViewModel/app bị dọn. Nhắc việc cần **sống sót** qua việc app bị đóng, thậm chí máy reboot. Android cấm app chạy nền tùy tiện (để tiết kiệm pin), nên phải dùng cơ chế được OS bảo trợ: **WorkManager** — nó lưu job xuống đĩa và OS đảm bảo chạy *cuối cùng cũng chạy*, kể cả sau reboot.

## 2. Lên lịch: `ReminderScheduler`

[ReminderScheduler.kt](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt) tính "còn bao lâu tới giờ nhắc" rồi xếp một job:

```kotlin
fun schedule(context: Context, task: Task) {
    val due = task.dueDate?.let { parseIso8601(it) }
    if (due == null || task.status == "COMPLETED") { cancel(context, task.id); return }

    val fireAt = due.time - task.reminderOffsetMinutes * 60_000L   // nhắc trước hạn N phút
    val delay = fireAt - System.currentTimeMillis()
    if (delay <= 0) { cancel(context, task.id); return }           // đã qua giờ → thôi

    val work = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)             // chạy sau "delay" ms
        .setInputData(workDataOf(
            ReminderWorker.KEY_TITLE to task.title,
            ReminderWorker.KEY_TASK_ID to task.id))                // dữ liệu vào cho worker
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        uniqueName(task.id),                 // tên duy nhất theo task id
        ExistingWorkPolicy.REPLACE,          // sửa task → thay job cũ (không nhân đôi nhắc)
        work)
}
```
Điểm hay để học:
- **`enqueueUniqueWork(name, REPLACE, ...)`** — mỗi task có đúng một job "reminder_<id>". Sửa hạn/độ nhắc → **REPLACE** ghi đè, không bị nhắc trùng. Xoá/hoàn thành task → `cancel()`. Đây là cách giữ lịch nhắc **đồng bộ với dữ liệu**.
- **`setInputData(workDataOf(...))`** — truyền tham số cho worker (chỉ nhận kiểu nguyên thủy/chuỗi, như bundle key–value).
- **`syncAll(...)`** được gọi mỗi lần tải TaskList ([TaskRepository.kt:30](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L30)) để dựng lại toàn bộ lịch nhắc cho khớp dữ liệu mới nhất.
- **`snooze(...)`** — nút "Hoãn 1 giờ" chỉ là xếp lại job với delay mới.

## 3. Thực thi: `ReminderWorker`

Khi tới giờ, WorkManager gọi [ReminderWorker.doWork()](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L23):
```kotlin
class ReminderWorker(context, params) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Công việc đến hạn"
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        showNotification(applicationContext, taskId, title)
        return Result.success()      // báo OS: job xong. (Result.retry() nếu muốn thử lại)
    }
}
```
`CoroutineWorker` cho phép `doWork()` là `suspend` (bài 01) — làm việc bất đồng bộ gọn gàng. Trả `Result.success()`/`retry()`/`failure()` để báo kết quả cho hệ thống job.

## 4. Hiện thông báo — vài khái niệm Android riêng

Trong `showNotification()` có mấy thứ đặc thù đáng biết:

- **Notification Channel** ([dòng 35](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L35)): từ Android 8, mọi thông báo phải thuộc một "kênh" (nhóm để người dùng bật/tắt, chỉnh độ ưu tiên). Phải `createNotificationChannel` trước khi bắn. Lại là mẫu "phân nhánh theo API level" (`if SDK_INT >= O`).
- **Kiểm tra quyền lúc chạy** ([dòng 51](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L51)): Android 13+ nếu chưa được cấp `POST_NOTIFICATIONS` thì **lặng lẽ bỏ qua** thay vì crash (nối tiếp phần xin quyền ở bài 03).
- **`PendingIntent`** ([dòng 63](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L63)): "một hành động đóng gói sẵn, trao cho hệ thống thực hiện *sau* thay bạn". Khái niệm rất Android. Ở đây: bấm vào thông báo → mở `MainActivity`; bấm nút "Hoàn thành"/"Hoãn" → gửi broadcast tới Receiver. Bạn đưa cho OS một "phiếu" để nó bấm hộ khi người dùng chạm.
- **`NotificationCompat.Builder`** — dựng nội dung: icon, tiêu đề, text, và `addAction(...)` cho hai nút hành động.

## 5. Xử lý nút bấm: `BroadcastReceiver`

Khi người dùng bấm "Hoàn thành"/"Hoãn" **ngay trên thông báo** (app có thể đang đóng), OS đánh thức [NotificationActionReceiver](../app/app/src/main/java/com/example/todoapplication/data/notifications/NotificationActionReceiver.kt) — thành phần đã khai báo trong manifest (bài 03). Receiver chạy một đoạn ngắn: gọi API hoàn thành task, hoặc xếp lại job hoãn, rồi tắt thông báo. Đây là "điểm vào không-UI" điển hình.

## 6. Widget màn hình chính (đọc lướt)

[TasksWidgetProvider](../app/app/src/main/java/com/example/todoapplication/widget/TasksWidgetProvider.kt) + [TasksWidgetService](../app/app/src/main/java/com/example/todoapplication/widget/TasksWidgetService.kt) vẽ danh sách việc **ngay trên home screen** điện thoại. Widget dùng bộ khung UI *cũ* (RemoteViews, không phải Compose) vì chạy trong tiến trình launcher. Điểm nối đáng nhớ: mỗi khi cache task đổi, [TaskCacheRepository.cache()](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskCacheRepository.kt#L21) gọi `TasksWidgetProvider.refresh(...)` để widget cập nhật — một ví dụ nữa về "một nguồn dữ liệu, nhiều nơi hiển thị".

## 7. Bức tranh ghép lại

```
Sửa/tải task ──► ReminderScheduler.schedule()  ──► WorkManager (lưu job xuống đĩa)
                                                        │  (đến giờ, kể cả app đóng/đã reboot)
                                                        ▼
                                               ReminderWorker.doWork()
                                                        │
                                                        ▼
                                          Thông báo "Đến hạn" + nút [Hoàn thành][Hoãn]
                                                        │ người dùng bấm nút
                                                        ▼
                                          NotificationActionReceiver → gọi API / snooze
```

## 8. Tự kiểm tra
1. Vì sao nhắc việc dùng WorkManager chứ không phải `delay()` trong coroutine thường?
2. `enqueueUniqueWork(..., REPLACE)` giúp tránh lỗi gì khi người dùng sửa hạn task nhiều lần?
3. `PendingIntent` là gì và vì sao thông báo cần nó?
4. Từ Android 8 và Android 13, có hai yêu cầu bổ sung nào để hiện được thông báo?
5. Nút "Hoàn thành" trên thông báo được xử lý ở đâu khi app đang đóng?

➡️ Tiếp theo: [Bài 11 — Kiểm thử (Testing)](./11-testing.md)
