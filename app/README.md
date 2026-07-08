# Ứng dụng Android — Todo List hỗ trợ bởi AI

Ứng dụng Android (Jetpack Compose) cho hệ thống Quản lý Công việc tích hợp AI. Giao tiếp với [backend Go](../backend/README.md) qua REST API, cung cấp quản lý task, lập lịch AI, AI Coach, nhắc nhở, tạo task bằng ngôn ngữ tự nhiên và nhiều tính năng nâng cao.

---

## 🧰 Công nghệ sử dụng

| Thành phần | Thư viện / Phiên bản |
| :--- | :--- |
| Giao diện | Jetpack Compose + Material 3 |
| Điều hướng | Navigation Compose |
| Mạng | Retrofit 2 + OkHttp (Gson) |
| Bất đồng bộ | Kotlin Coroutines |
| Nhắc nhở nền | WorkManager |
| Sắp xếp kéo-thả | `sh.calvin.reorderable:reorderable:2.4.3` |
| Local DB (offline cache + subtask) | Room 2.7.1 (qua KSP) |
| Lưu phiên / danh mục tùy chỉnh | SharedPreferences |

- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`
- Package: `com.example.todoapplication`
- Dependency khai báo qua version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

---

## 🏗️ Cấu trúc thư mục

```
app/src/main/java/com/example/todoapplication/
  MainActivity.kt                # Host NavHost; xin quyền thông báo; lắng nghe forced-logout
  data/
    api/
      ApiService.kt              # Khai báo các endpoint Retrofit
      NetworkClient.kt           # OkHttp + interceptor token + Authenticator tự refresh
    model/Models.kt              # Các data class request/response
    local/                       # Room database (local DB, version 4)
      AppDatabase.kt             # RoomDatabase singleton (2 bảng)
      TaskCacheEntity.kt         # Bản sao offline của task
      SubtaskEntity.kt           # Bước con (checklist) theo taskId
      Daos.kt                    # TaskCacheDao + SubtaskDao
    repository/                  # Tầng Repository (MVVM) — nguồn dữ liệu duy nhất
      TaskRepository.kt          # CRUD task + hoàn thành + cache offline
      Repositories.kt            # Auth/Preferences/Plan/Ai/Stats Repository
      SessionManager.kt          # Lưu/đọc access & refresh token, thông tin user
      SessionEvents.kt           # SharedFlow phát sự kiện buộc đăng xuất
      QuickAddDraft.kt           # Holder tạm cho kết quả AI Quick Add / tạo nhanh
      ThemeController.kt         # Singleton quản lý chế độ sáng/tối/hệ thống
      CategoryStore.kt           # Danh mục mặc định + danh mục tùy chỉnh (SharedPreferences)
      TaskCacheRepository.kt     # Đọc/ghi cache task offline (map Task ↔ entity)
    notifications/
      ReminderScheduler.kt       # Lập/huỷ/hoãn lịch nhắc nhở bằng WorkManager
      ReminderWorker.kt          # Notification + nút Hoàn thành/Hoãn
      NotificationActionReceiver.kt # Xử lý nút hành động trên thông báo
  widget/
    TasksWidgetProvider.kt       # AppWidgetProvider cho widget màn hình chính
    TasksWidgetService.kt        # RemoteViewsService/Factory đọc Room cache
  di/
    ServiceLocator.kt            # Manual DI: cung cấp ApiService/DB/Repository (lazy singleton)
  ui/
    navigation/Screen.kt         # Định nghĩa route
    state/UiState.kt             # sealed Loading / Success / Error
    viewmodel/                   # 8 ViewModel (StateFlow<UiState> + SharedFlow sự kiện)
    components/
      AppBottomBar.kt            # Floating pill navigation bar tái dùng
      CommonComponents.kt        # EmptyState, LoadingState
      Pills.kt                   # OverduePill, PriorityPill, StatePill
    screens/
      LoginScreen.kt             # Đăng nhập
      RegisterScreen.kt          # Đăng ký
      TaskListScreen.kt          # Danh sách task (chính) — nhóm Hôm nay/Tương lai/Đã hoàn thành, thanh tạo nhanh, cờ ưu tiên, sắp xếp
      TaskDetailScreen.kt        # Thêm/sửa task (danh mục, lặp theo thứ, lời nhắc, subtask)
      DailyPlanScreen.kt         # Lịch trình AI
      AICoachScreen.kt           # Chat AI Coach
      StatsScreen.kt             # Thống kê + Biểu đồ + Trí nhớ AI
      SettingsScreen.kt          # Cài đặt cá nhân
      CalendarScreen.kt          # Lịch tháng xem việc theo ngày (chiếu cả lần lặp)
      TemplatesScreen.kt         # Thư viện Mẫu nhiệm vụ theo nhóm
    theme/
      Color.kt                   # Bảng màu pastel Light + Dark + AppAccent
      Theme.kt                   # LightColorScheme / DarkColorScheme
      Type.kt                    # Type scale
      Shapes.kt                  # Material3 Shapes
    utils/
      Labels.kt                  # Việt hóa enum (priority, status, category)
      DateTimeUtils.kt           # Parse ISO8601, format UTC→local
```

---

## 📱 Các màn hình

| Màn hình | Chức năng |
| :--- | :--- |
| `LoginScreen` / `RegisterScreen` | Đăng nhập / đăng ký — gradient hero + white sheet từ dưới |
| `TaskListScreen` | Danh sách task: tìm kiếm, **lọc theo danh mục**, nhóm **Hôm nay/Tương lai/Đã hoàn thành**, vuốt/tick-hoàn-thành, **cờ ưu tiên** (bấm đổi), **sắp xếp**, AI badge ưu tiên, kéo-thả sắp xếp, **thanh tạo nhanh** + AI Quick Add |
| `TaskDetailScreen` | Thêm/sửa task: ưu tiên, hạn chót (có preset), **danh mục (chọn/thêm mới)**, lặp lại (**chọn thứ khi Hàng tuần**), **lời nhắc** (trước hạn), subtask — chia section card |
| `TemplatesScreen` | Thư viện **Mẫu nhiệm vụ** theo nhóm (Sức khỏe/Cuộc sống/Công việc/Học tập); bấm mẫu → mở form điền sẵn |
| `DailyPlanScreen` | Lịch trình do AI tạo, timeline với dot gradient |
| `AICoachScreen` | Chat với AI Coach — bubble hiện đại, gradient send button |
| `StatsScreen` | 3 tab: **Thống kê** (hồ sơ: tên + Hoàn thành/Đang chờ/Ngày hoàn hảo + **bản đồ nhiệt năm** + phân bố danh mục) · **Biểu đồ** (bar chart 7 ngày) · **Trí nhớ AI** |
| `SettingsScreen` | Giao diện Sáng/Tối/Hệ thống; giờ giấc cho lập lịch AI; **quản lý danh mục** (thêm/xoá); **Giới thiệu & Hỏi đáp** |
| `CalendarScreen` | Lịch tháng: xem việc theo ngày (chấm màu priority), **chiếu các lần lặp ra tương lai**, chọn ngày để lọc |

---

## 🆕 Tính năng Android nâng cao

### ☑️ Subtask / Checklist
Mỗi task có các bước con lưu cục bộ trong **Room** (bảng `subtask`, quan hệ 1-nhiều theo `taskId`). Trong `TaskDetailScreen` có section checklist: thêm/tích/xóa bước + thanh % hoàn thành. Thẻ task ngoài danh sách hiển thị chip tiến độ `☑ 2/5`.

### 🔔 Thông báo có nút hành động (Notification Actions)
Thông báo nhắc việc có 2 nút **"Hoàn thành"** và **"Hoãn 1 giờ"** bấm trực tiếp:
- `NotificationCompat.Action` + `PendingIntent` broadcast → `NotificationActionReceiver` (dùng `goAsync()` để gọi mạng).
- "Hoàn thành" gọi API complete + tắt thông báo; "Hoãn 1 giờ" đặt lại reminder sau 60 phút (`ReminderScheduler.snooze`).

### 📅 Lịch tháng (Calendar)
`CalendarScreen` vẽ lưới lịch tháng tự xây (offset thứ 2 đầu tuần), chấm màu theo priority trên ngày có việc, đổi tháng, chọn ngày để xem danh sách việc đến hạn. `CalendarViewModel` nhóm task theo ngày địa phương và **chiếu các lần lặp** (`DAILY`/`WEEKLY`/`MONTHLY`) ra tới 12 tháng sau — nhờ vậy ngày lặp lại trong tương lai cũng hiển thị chấm.

### 🏷️ Danh mục tùy chỉnh (Custom Category)
Mỗi task thuộc **một danh mục**. Ngoài 3 danh mục mặc định (Cá nhân / Công việc / Khác), người dùng **tự thêm danh mục mới** ngay trong `TaskDetailScreen`. Danh mục tùy chỉnh lưu cục bộ qua [`CategoryStore`](app/src/main/java/com/example/todoapplication/data/repository/CategoryStore.kt) (SharedPreferences, state Compose) và xuất hiện đồng bộ ở chip chọn, bộ lọc danh sách và thanh tạo nhanh.

### ⚡ Thanh tạo nhanh (Quick Create)
Nút **+** mở bottom sheet tạo nhanh: gõ tiêu đề (hoặc chọn **mẫu gợi ý**), chọn preset hạn chót (Hôm nay/Ngày mai/3 ngày sau/Cuối tuần/Không), danh mục, ưu tiên → tạo ngay; hoặc **Chi tiết** để mở form đầy đủ, **Mẫu** để mở thư viện mẫu, **AI** để phân tích câu tự nhiên.

### 📋 Thư viện Mẫu nhiệm vụ (Templates)
[`TemplatesScreen`](app/src/main/java/com/example/todoapplication/ui/screens/TemplatesScreen.kt) liệt kê các việc làm sẵn theo nhóm (Sức khỏe / Cuộc sống / Công việc / Học tập). Bấm một mẫu → set [`QuickAddDraft`](app/src/main/java/com/example/todoapplication/data/repository/QuickAddDraft.kt) (tên + danh mục) rồi mở `TaskDetailScreen` để người dùng chỉnh giờ/lặp và lưu.

### 🚩 Cờ ưu tiên & Sắp xếp
- Mỗi thẻ task có **cờ màu theo ưu tiên**; bấm → menu chọn nhanh Cao/Trung bình/Thấp (gọi `PUT /tasks/{id}` cập nhật).
- Nút **Sắp xếp** đổi thứ tự hiển thị trong các nhóm: Mặc định / Hạn chót / Ưu tiên / Tên (A-Z) — sắp xếp client-side.

### 🔔 Lời nhắc trước hạn & Lặp theo thứ
- Màn chi tiết có mục **Lời nhắc**: Đúng giờ / Trước 5–10–30 phút / 1 giờ → lưu `reminder_offset_minutes`; `ReminderScheduler` đặt notification tại `due_date − offset`.
- Khi lặp **Hàng tuần**: chọn các **thứ** lặp lại (T2…CN) → lưu `recurrence_days` (vd `"MON,WED,FRI"`). Lịch tháng chiếu chấm đúng các thứ này; backend sinh occurrence kế tiếp nhảy đúng thứ.

### 🧩 Widget màn hình chính
**Collection widget** (RemoteViews) hiển thị việc cần làm ngay ngoài home screen:
- `TasksWidgetProvider : AppWidgetProvider` + `TasksWidgetService : RemoteViewsService` (Factory đọc **Room cache** → ListView).
- Bấm widget mở app; tự cập nhật qua `notifyAppWidgetViewDataChanged` mỗi khi app cache lại task.
- Hoạt động cả khi **offline** (đọc từ Room).

---

## 🏛️ Kiến trúc MVVM

Ứng dụng theo **MVVM** với luồng dữ liệu một chiều:

```
View (Composable) → ViewModel (StateFlow<UiState>) → Repository → REST / Room
```

- **View** chỉ render state + chuyển hành động cho ViewModel; không gọi API trực tiếp.
- **ViewModel** (`ui/viewmodel/`, 8 cái): phơi `StateFlow<…UiState>` (View thu bằng `collectAsStateWithLifecycle()`), phát sự kiện một lần qua `SharedFlow` (toast/điều hướng), chạy nghiệp vụ trong `viewModelScope` → sống sót qua xoay màn hình.
- **Repository** (`data/repository/`): `TaskRepository`, `AuthRepository`, `PreferencesRepository`, `PlanRepository`, `AiRepository`, `StatsRepository` — nguồn dữ liệu duy nhất, bọc REST + Room + side-effect (nhắc nhở), trả `Result<T>`.
- **UiState** (`ui/state/UiState.kt`): sealed `Loading / Success / Error`.
- **DI**: `di/ServiceLocator.kt` (manual DI, lazy singleton) cấp Repository cho ViewModel qua `ViewModelProvider.Factory`.

> ⚠️ Định hướng ban đầu dùng **Hilt** nhưng Hilt Gradle plugin (≤ 2.57.1) **không tương thích AGP 9** (lỗi *"Android BaseExtension not found"*). Đã chuyển sang **ServiceLocator** để đạt cùng mục tiêu DI mà vẫn build được. Chi tiết công nghệ: [CONG-NGHE-SU-DUNG.md](CONG-NGHE-SU-DUNG.md).

---

## ✨ Tính năng nổi bật

### 🤖 AI Priority Badge
`TaskListScreen` tự tính điểm ưu tiên cho từng task dựa trên:
- Mức độ ưu tiên (HIGH/MEDIUM/LOW)
- Độ gần với deadline (quá hạn → hôm nay → 3 ngày → 7 ngày)
- Trạng thái (việc chưa xong)

**Khoảng 1/3 số việc đang chờ (tối đa 3, và chỉ khi có từ 3 việc trở lên)** — những task điểm cao nhất — hiển thị banner "🤖 AI khuyến nghị ưu tiên" màu primary. Hoàn toàn client-side, không cần API mới. Giới hạn 1/3 để badge còn ý nghĩa "nổi bật" thay vì dính lên mọi việc khi danh sách ngắn.

### ☰ Kéo-thả sắp xếp task (Drag & Drop)
Nhấn icon ☰ trên greeting hero card để vào **sort mode**:
- SwipeToDismissBox bị tắt, mỗi card hiện drag handle
- Giữ và kéo để đổi thứ tự — dùng thư viện `sh.calvin.reorderable`
- Nhấn "Xong" để thoát, quay về chế độ vuốt bình thường

### 📈 Biểu đồ năng suất tuần
Tab "📈 Biểu đồ" trong `StatsScreen`:
- **Bar chart Canvas** hiển thị task hoàn thành 7 ngày qua với gradient bar primary→tertiary
- Chip "Tổng tuần" + "Ngày năng suất nhất"
- Progress bar chi tiết từng ngày
- Dữ liệu tính từ `GET /tasks?status=COMPLETED`, nhóm theo ngày trong tuần

### 📴 Offline-first với Room
Cache đọc offline qua local DB (Room) — [`TaskCacheRepository`](app/src/main/java/com/example/todoapplication/data/repository/TaskCacheRepository.kt):
- Mỗi lần tải task từ API **thành công** (xem toàn bộ, không lọc) → ghi đè cache xuống bảng `task_cache`
- Khi **mất mạng** (API ném exception) → tự đọc lại từ Room, hiển thị danh sách + **banner "📴 Chế độ offline"**
- `AppDatabase` là RoomDatabase singleton, dùng KSP sinh mã DAO

> ⚙️ AGP 9 dùng "built-in Kotlin" nên cần flag `android.disallowKotlinSourceSets=false` trong `gradle.properties` để KSP (Room) thêm được source set sinh mã.

### 🎨 Design System hiện đại
- **Bảng màu pastel** Light + Dark — cả 2 mode qua Material3 `colorScheme`
- **Nút chuyển theme** trong Cài đặt: Sáng / Tối / Theo hệ thống
- **Floating pill bottom nav** với active indicator
- **Gradient heroes** trên TaskList, DailyPlan, Login, Register
- **Section card grouping** trên TaskDetail, Settings
- Không còn hardcode màu — mọi màu qua `MaterialTheme.colorScheme.*`

---

## 🔐 Xử lý token (Access + Refresh)

- Khi đăng nhập, lưu cả `token` (access) và `refresh_token` vào [`SessionManager`](app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt).
- [`NetworkClient`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt) gắn `Authorization: Bearer <access>` vào mọi request, và cài **OkHttp `Authenticator`**: gặp `401` → tự gọi `/auth/refresh` (qua client phụ không gắn authenticator để tránh đệ quy) → lưu cặp token mới → phát lại request.
- Refresh thất bại → xóa phiên và bắn `SessionEvents.forcedLogout`; `MainActivity` lắng nghe và **tự điều hướng về Login, xóa backstack**.

---

## 🔔 Nhắc nhở (Reminders)

Hoàn toàn **local**, không dùng FCM. [`ReminderScheduler`](app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt) dùng WorkManager đặt `OneTimeWorkRequest` đến thời điểm **`due_date − reminder_offset_minutes`** (REPLACE theo task id). Lập lịch khi lưu task và khi tải danh sách; huỷ khi xoá. WorkManager tự khôi phục job sau khởi động lại máy.

---

## ✨ AI Quick Add

Bottom sheet trên `TaskListScreen`: nhập câu tự nhiên → gọi `POST /ai/parse-task` → lưu kết quả vào [`QuickAddDraft`](app/src/main/java/com/example/todoapplication/data/repository/QuickAddDraft.kt) → mở `TaskDetailScreen` điền sẵn để người dùng xác nhận trước khi lưu.

---

## ⚙️ Cấu hình

Địa chỉ backend đặt trong [`NetworkClient.kt`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt):

```kotlin
private const val BASE_URL = "http://10.0.2.2:8080/api/v1/"
```

Chọn địa chỉ theo nơi chạy app (xem chú thích trong `NetworkClient.kt`):
- **Máy ảo Android Studio (AVD)**: `http://10.0.2.2:8080/api/v1/` (`10.0.2.2` = máy tính host) — mặc định.
- **Genymotion**: `http://10.0.3.2:8080/api/v1/`.
- **Điện thoại thật (cùng Wi-Fi)**: `http://<IP_LAN_máy_tính>:8080/api/v1/`.
- **KHÔNG** dùng `localhost` vì trên thiết bị Android nó trỏ về chính thiết bị, không phải PC.

HTTP cleartext đã được bật sẵn (`usesCleartextTraffic="true"` trong `AndroidManifest.xml`). Timeout đọc/ghi của OkHttp đặt **60s** để chịu được các tác vụ AI (Daily Plan ~15-20s).

---

## 🚀 Build & chạy

**Bằng Android Studio**: mở thư mục `app/`, đồng bộ Gradle, chọn thiết bị/emulator (khuyến nghị **API 33+** để test quyền thông báo) rồi Run.

**Bằng dòng lệnh** (cần JAVA_HOME trỏ đến JDK 17+):
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
| `VIBRATE` | Rung khi có thông báo nhắc việc |
