# Thẻ 05 — Chạy nền · Thông báo · Widget

⬅️ [Về mục lục thuyết trình](./README.md)

Đây là phần "nâng cao" hay được hỏi để kiểm tra hiểu biết về nền tảng Android (không chỉ gọi API). Chi tiết lý thuyết ở [bài 10](../10-background-va-thong-bao.md).

---

## 🔹 Nhắc việc theo hạn (WorkManager)

**Một câu:** Khi tạo/sửa/tải task, app tính "còn bao lâu tới giờ nhắc" và đặt một job WorkManager; đến giờ, job hiện thông báo — chạy cả khi app đóng hoặc sau khi reboot máy.

**Sơ đồ:**
```
Tạo/sửa task → TaskRepository.createTask/updateTask   TaskRepository.kt:50,53
   → ReminderScheduler.schedule(context, task)         ReminderScheduler.kt:21
      → WorkManager.enqueueUniqueWork("reminder_<id>", REPLACE, ...)   ReminderScheduler.kt:47
Tải danh sách → ReminderScheduler.syncAll(...)          TaskRepository.kt:30 / ReminderScheduler.kt:52
Đến giờ → ReminderWorker.doWork() → hiện thông báo      ReminderWorker.kt:23, 47
```

**Điểm nên chỉ:**
- Tính thời điểm nhắc (nhắc trước hạn `reminderOffsetMinutes` phút), tự hủy nếu đã xong/quá giờ: [ReminderScheduler.schedule:21](../../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt#L21).
- `enqueueUniqueWork(..., REPLACE)`: mỗi task đúng 1 job, sửa hạn thì ghi đè không nhân đôi — [ReminderScheduler.kt:47](../../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt#L47).

**Có thể bị hỏi:**
- *"Sao không dùng coroutine `delay`?"* → Coroutine chết khi app đóng. WorkManager lưu job xuống đĩa, OS đảm bảo chạy kể cả sau reboot.
- *"Sửa hạn task 3 lần có bị nhắc 3 lần không?"* → Không — cùng tên duy nhất `reminder_<id>` + `REPLACE` nên chỉ còn job mới nhất.
- *"Xóa/hoàn thành task thì nhắc còn không?"* → Bị hủy: `ReminderScheduler.cancel(...)` gọi trong xóa ([TaskRepository.kt:58](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L58)) và trong `schedule` khi trạng thái COMPLETED.

---

## 🔹 Nút "Hoàn thành" / "Hoãn 1 giờ" ngay trên thông báo

**Một câu:** Thông báo có 2 nút; bấm nút gửi broadcast tới một Receiver xử lý *dù app đang đóng* — hoàn thành gọi API, hoãn thì đặt lại nhắc sau 60 phút.

**Sơ đồ:**
```
ReminderWorker dựng thông báo + PendingIntent cho 2 nút   ReminderWorker.kt:68-96
Người dùng bấm nút → NotificationActionReceiver.onReceive  NotificationActionReceiver.kt:21
   ACTION_COMPLETE → api.completeTask(id) + hủy nhắc + tắt thông báo   NotificationActionReceiver.kt:28
   ACTION_SNOOZE   → ReminderScheduler.snooze(id, 60 phút)            NotificationActionReceiver.kt:42
```

**Điểm nên chỉ:**
- Receiver dùng `goAsync()` để giữ sống trong lúc gọi mạng ngắn: [NotificationActionReceiver.kt:29](../../app/app/src/main/java/com/example/todoapplication/data/notifications/NotificationActionReceiver.kt#L29).
- Khai báo Receiver trong manifest (điểm vào không-UI): [AndroidManifest.xml:33](../../app/app/src/main/AndroidManifest.xml#L33).

**Có thể bị hỏi:**
- *"App đang tắt vẫn bấm nút được?"* → Được. OS đánh thức Receiver đã khai báo trong manifest; nó lấy repository qua `ServiceLocator` để gọi API.
- *"`PendingIntent` là gì?"* → Một "hành động đóng gói sẵn" trao cho hệ thống thực thi hộ khi người dùng chạm — [ReminderWorker.kt:63](../../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L63).
- *"Android 13 cần gì để hiện thông báo?"* → Quyền `POST_NOTIFICATIONS` xin lúc chạy ([MainActivity.kt:41](../../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L41)) + Notification Channel từ Android 8 ([ReminderWorker.kt:35](../../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt#L35)).

---

## 🔹 Widget màn hình chính

**Một câu:** Một widget hiển thị danh sách việc ngay trên home screen, đọc từ cache Room và tự cập nhật mỗi khi cache đổi.

**Sơ đồ:**
```
Cache task thay đổi → TaskCacheRepository.cache(...)     TaskCacheRepository.kt:15
   → TasksWidgetProvider.refresh(context)               TaskCacheRepository.kt:21
Widget vẽ list qua TasksWidgetService (RemoteViews)      widget/TasksWidgetService.kt
```
- Provider + Service khai báo trong manifest: [AndroidManifest.xml:38](../../app/app/src/main/AndroidManifest.xml#L38), [:49](../../app/app/src/main/AndroidManifest.xml#L49).

**Có thể bị hỏi:**
- *"Widget lấy dữ liệu từ đâu?"* → Từ **cache Room** (không tự gọi mạng), nên hiển thị được cả khi app không mở. Đây là lý do cache vừa phục vụ offline vừa phục vụ widget — "một nguồn dữ liệu, nhiều nơi hiển thị".
- *"Sao widget không viết bằng Compose?"* → Widget chạy trong tiến trình launcher nên dùng khung UI cũ `RemoteViews`.

---

## 💡 Câu tổng kết nếu được hỏi "app dùng những thành phần nền tảng Android nào?"

> *"App dùng một Activity + Compose cho UI; WorkManager để nhắc việc bền bỉ; BroadcastReceiver xử lý nút trên thông báo; App Widget cho màn hình chính; Room cho lưu trữ offline; và EncryptedSharedPreferences cho token. Tất cả nối với backend Go qua Retrofit/OkHttp, với interceptor tự gắn và refresh token."*

Một câu này chạm hết các điểm nền tảng — rất "được lòng" giám khảo.

⬅️ [Về mục lục thuyết trình](./README.md)
