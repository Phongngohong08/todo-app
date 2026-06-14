# Ứng dụng Android — Todo List hỗ trợ bởi AI

Ứng dụng Android (Jetpack Compose) cho hệ thống Quản lý Công việc tích hợp AI. Giao tiếp với [backend Go](../backend/README.md) qua REST API, cung cấp quản lý task, lập lịch AI, AI Coach, nhắc nhở và tạo task bằng ngôn ngữ tự nhiên.

---

## 🧰 Công nghệ sử dụng

| Thành phần | Thư viện |
| :--- | :--- |
| Giao diện | Jetpack Compose + Material 3 |
| Điều hướng | Navigation Compose |
| Mạng | Retrofit 2 + OkHttp (Gson) |
| Bất đồng bộ | Kotlin Coroutines |
| Nhắc nhở nền | WorkManager |
| Lưu phiên | SharedPreferences |

- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`
- Package: `com.example.todoapplication`
- Dependency khai báo qua version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

---

## 🏗️ Cấu trúc thư mục

```
app/src/main/java/com/example/todoapplication/
  MainActivity.kt            # Host NavHost; xin quyền thông báo; lắng nghe forced-logout
  data/
    api/
      ApiService.kt          # Khai báo các endpoint Retrofit
      NetworkClient.kt       # OkHttp + interceptor token + Authenticator tự refresh
    model/Models.kt          # Các data class request/response
    repository/
      SessionManager.kt      # Lưu/đọc access & refresh token, thông tin user
      SessionEvents.kt       # SharedFlow phát sự kiện buộc đăng xuất
      QuickAddDraft.kt       # Holder tạm cho kết quả AI Quick Add
    notifications/
      ReminderScheduler.kt   # Lập/huỷ lịch nhắc nhở bằng WorkManager
      ReminderWorker.kt      # Hiển thị notification khi đến hạn
  ui/
    navigation/Screen.kt     # Định nghĩa route
    screens/                 # Các màn hình Compose
    theme/                   # Màu, typography, theme tối
    utils/                   # Labels (Việt hóa enum), DateTimeUtils
```

---

## 📱 Các màn hình

| Màn hình | Chức năng |
| :--- | :--- |
| `LoginScreen` / `RegisterScreen` | Đăng nhập / đăng ký |
| `TaskListScreen` | Danh sách task: tìm kiếm, lọc trạng thái, vuốt-hoàn-thành, menu thao tác, AI Quick Add |
| `TaskDetailScreen` | Thêm/sửa task: ưu tiên, hạn chót, thời lượng, khung giờ, **nhãn (tags)**, **lặp lại** |
| `DailyPlanScreen` | Lịch trình do AI tạo theo timeline |
| `AICoachScreen` | Chat với AI Coach (giữ lịch sử, gợi ý câu hỏi) |
| `StatsScreen` | Thống kê + Trí nhớ AI (2 tab) |
| `SettingsScreen` | Cấu hình giờ giấc cá nhân cho lập lịch AI |

---

## 🔐 Xử lý token (Access + Refresh)

- Khi đăng nhập, lưu cả `token` (access) và `refresh_token` vào [`SessionManager`](app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt).
- [`NetworkClient`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt) gắn `Authorization: Bearer <access>` vào mọi request, và cài **OkHttp `Authenticator`**: gặp `401` → tự gọi `/auth/refresh` (qua client phụ không gắn authenticator để tránh đệ quy) → lưu cặp token mới → phát lại request.
- Refresh thất bại → xóa phiên và bắn `SessionEvents.forcedLogout`; `MainActivity` lắng nghe và **tự điều hướng về Login, xóa backstack**.

## 🔔 Nhắc nhở (Reminders)

- Hoàn toàn **local**, không dùng FCM. [`ReminderScheduler`](app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt) dùng WorkManager đặt `OneTimeWorkRequest` đến thời điểm `due_date` (REPLACE theo task id).
- Lập lịch khi lưu task và khi tải danh sách (đồng bộ); huỷ khi xoá. WorkManager tự khôi phục job sau khi khởi động lại máy.
- Cần quyền `POST_NOTIFICATIONS` (Android 13+) — `MainActivity` xin lúc khởi động.

## ✨ AI Quick Add

- Bottom sheet trên `TaskListScreen`: nhập câu tự nhiên → gọi `POST /ai/parse-task` → lưu kết quả vào [`QuickAddDraft`](app/src/main/java/com/example/todoapplication/data/repository/QuickAddDraft.kt) → mở `TaskDetailScreen` điền sẵn để người dùng xác nhận trước khi lưu.

---

## ⚙️ Cấu hình

Địa chỉ backend đặt trong [`NetworkClient.kt`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt):

```kotlin
private const val BASE_URL = "https://todo.phongngohong.online/api/v1/"
```

Khi chạy backend ở local, đổi sang địa chỉ máy chủ của bạn. Lưu ý nếu dùng HTTP cleartext: `usesCleartextTraffic="true"` đã bật trong `AndroidManifest.xml`; với emulator, host máy là `http://10.0.2.2:8080/api/v1/`.

---

## 🚀 Build & chạy

**Bằng Android Studio**: mở thư mục `app/`, đồng bộ Gradle, chọn thiết bị/emulator (khuyến nghị **API 33+** để test quyền thông báo) rồi Run.

**Bằng dòng lệnh** (cần JDK 17+; có thể dùng JBR đi kèm Android Studio/IntelliJ):
```bash
# Biên dịch Kotlin (kiểm tra nhanh)
./gradlew compileDebugKotlin

# Đóng gói APK debug
./gradlew assembleDebug   # → app/build/outputs/apk/debug/

# Cài lên thiết bị đang kết nối
./gradlew installDebug
```

---

## 🔒 Quyền (Permissions)

| Quyền | Mục đích |
| :--- | :--- |
| `INTERNET` | Gọi REST API |
| `POST_NOTIFICATIONS` | Hiển thị nhắc nhở công việc (Android 13+) |
