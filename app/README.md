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
| Local DB (offline + gamification) | Room 2.7.1 (qua KSP) |
| Lưu phiên | SharedPreferences |

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
    local/                       # Room database (local DB, version 2)
      AppDatabase.kt             # RoomDatabase singleton (3 bảng)
      TaskCacheEntity.kt         # Bản sao offline của task
      GamificationEntity.kt      # 1 dòng: XP, streak, huy hiệu
      SubtaskEntity.kt           # Bước con (checklist) theo taskId
      Daos.kt                    # TaskCacheDao + GamificationDao + SubtaskDao
    repository/                  # Tầng Repository (MVVM) — nguồn dữ liệu duy nhất
      TaskRepository.kt          # CRUD task + cache offline + gamification
      Repositories.kt            # Auth/Preferences/Plan/Ai/Stats Repository
      SessionManager.kt          # Lưu/đọc access & refresh token, thông tin user
      SessionEvents.kt           # SharedFlow phát sự kiện buộc đăng xuất
      QuickAddDraft.kt           # Holder tạm cho kết quả AI Quick Add
      ThemeController.kt         # Singleton quản lý chế độ sáng/tối/hệ thống
      TaskCacheRepository.kt     # Đọc/ghi cache task offline (map Task ↔ entity)
      GamificationManager.kt     # Logic XP / streak / level / huy hiệu (state Compose)
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
    navigation/Screen.kt         # Định nghĩa route (kể cả Pomodoro)
    state/UiState.kt             # sealed Loading / Success / Error
    viewmodel/                   # 10 ViewModel (StateFlow<UiState> + SharedFlow sự kiện)
    components/
      AppBottomBar.kt            # Floating pill navigation bar tái dùng
      CommonComponents.kt        # EmptyState, LoadingState
      Pills.kt                   # OverduePill, PriorityPill, StatePill
    screens/
      LoginScreen.kt             # Đăng nhập
      RegisterScreen.kt          # Đăng ký
      TaskListScreen.kt          # Danh sách task (chính)
      TaskDetailScreen.kt        # Thêm/sửa task
      DailyPlanScreen.kt         # Lịch trình AI
      AICoachScreen.kt           # Chat AI Coach
      StatsScreen.kt             # Thống kê + Biểu đồ + Trí nhớ AI
      SettingsScreen.kt          # Cài đặt cá nhân
      PomodoroScreen.kt          # Pomodoro Timer
      AchievementsScreen.kt      # Thành tích: cấp độ, streak, huy hiệu
      CalendarScreen.kt          # Lịch tháng xem việc theo ngày
    theme/
      Color.kt                   # Bảng màu pastel Light + Dark + AppAccent
      Theme.kt                   # LightColorScheme / DarkColorScheme
      Type.kt                    # Type scale
      Shapes.kt                  # Material3 Shapes
    utils/
      Labels.kt                  # Việt hóa enum (priority, status)
      DateTimeUtils.kt           # Parse ISO8601, format UTC→local
```

---

## 📱 Các màn hình

| Màn hình | Chức năng |
| :--- | :--- |
| `LoginScreen` / `RegisterScreen` | Đăng nhập / đăng ký — gradient hero + white sheet từ dưới |
| `TaskListScreen` | Danh sách task: tìm kiếm, lọc trạng thái, vuốt-hoàn-thành, AI badge ưu tiên, kéo-thả sắp xếp, Pomodoro, AI Quick Add |
| `TaskDetailScreen` | Thêm/sửa task: ưu tiên, hạn chót, thời lượng, khung giờ, nhãn, lặp lại — chia 4 section card |
| `DailyPlanScreen` | Lịch trình do AI tạo, timeline với dot gradient |
| `AICoachScreen` | Chat với AI Coach — bubble hiện đại, gradient send button |
| `StatsScreen` | 3 tab: **Thống kê** (big numbers) · **Biểu đồ** (bar chart 7 ngày) · **Trí nhớ AI** |
| `SettingsScreen` | Chọn giao diện Sáng/Tối/Hệ thống; cấu hình giờ giấc cho lập lịch AI |
| `PomodoroScreen` | Pomodoro Timer: arc tiến độ Canvas, 3 pha làm việc/nghỉ, đếm phiên, vibration |
| `AchievementsScreen` | Cấp độ + thanh XP, chuỗi ngày (streak), lưới huy hiệu thành tích |
| `CalendarScreen` | Lịch tháng: xem việc theo ngày (chấm màu priority), chọn ngày để lọc |

---

## 🆕 Tính năng Android nâng cao

### ☑️ Subtask / Checklist
Mỗi task có các bước con lưu cục bộ trong **Room** (bảng `subtask`, quan hệ 1-nhiều theo `taskId`). Trong `TaskDetailScreen` có section checklist: thêm/tích/xóa bước + thanh % hoàn thành. Thẻ task ngoài danh sách hiển thị chip tiến độ `☑ 2/5`.

### 🔔 Thông báo có nút hành động (Notification Actions)
Thông báo nhắc việc có 2 nút **"Hoàn thành"** và **"Hoãn 1 giờ"** bấm trực tiếp:
- `NotificationCompat.Action` + `PendingIntent` broadcast → `NotificationActionReceiver` (dùng `goAsync()` để gọi mạng).
- "Hoàn thành" gọi API complete + tắt thông báo; "Hoãn 1 giờ" đặt lại reminder sau 60 phút (`ReminderScheduler.snooze`).

### 📅 Lịch tháng (Calendar)
`CalendarScreen` vẽ lưới lịch tháng tự xây (offset thứ 2 đầu tuần), chấm màu theo priority trên ngày có việc, đổi tháng, chọn ngày để xem danh sách việc đến hạn. `CalendarViewModel` nhóm task theo ngày địa phương.

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
- **ViewModel** (`ui/viewmodel/`, 9 cái): phơi `StateFlow<…UiState>` (View thu bằng `collectAsStateWithLifecycle()`), phát sự kiện một lần qua `SharedFlow` (toast/điều hướng/rung), chạy nghiệp vụ trong `viewModelScope` → sống sót qua xoay màn hình (vd timer Pomodoro).
- **Repository** (`data/repository/`): `TaskRepository`, `AuthRepository`, `PreferencesRepository`, `PlanRepository`, `AiRepository`, `StatsRepository` — nguồn dữ liệu duy nhất, bọc REST + Room + side-effect, trả `Result<T>`.
- **UiState** (`ui/state/UiState.kt`): sealed `Loading / Success / Error`.
- **DI**: `di/ServiceLocator.kt` (manual DI, lazy singleton) cấp Repository cho ViewModel qua `ViewModelProvider.Factory`.

> ⚠️ Định hướng ban đầu dùng **Hilt** nhưng Hilt Gradle plugin (≤ 2.57.1) **không tương thích AGP 9** (lỗi *"Android BaseExtension not found"*). Đã chuyển sang **ServiceLocator** để đạt cùng mục tiêu DI mà vẫn build được. Chi tiết công nghệ: [CONG-NGHE-SU-DUNG.md](CONG-NGHE-SU-DUNG.md).

---

## ✨ Tính năng nổi bật

### 🍅 Pomodoro Timer
Mở từ nút 🍅 trên mỗi task card trong `TaskListScreen`. Màn hình `PomodoroScreen` hiển thị:
- **Vòng arc Canvas** thể hiện tiến độ theo màu phase
- **3 pha tự động**: Làm việc 25p → Nghỉ ngắn 5p → Nghỉ dài 15p (sau 4 phiên)
- Nút Play / Pause / Reset / Bỏ qua pha
- Vibration khi hết phiên, bộ đếm phiên và phút tập trung lũy kế

### 🤖 AI Priority Badge
`TaskListScreen` tự tính điểm ưu tiên cho từng task dựa trên:
- Mức độ ưu tiên (HIGH/MEDIUM/LOW)
- Độ gần với deadline (quá hạn → hôm nay → 3 ngày → 7 ngày)
- Trạng thái (TODO ưu tiên hơn IN_PROGRESS)

**Top 3 task điểm cao nhất** hiển thị banner "🤖 AI khuyến nghị ưu tiên" màu primary — hoàn toàn client-side, không cần API mới.

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

### 🔥 Gamification — Streak, XP, Huy hiệu
[`GamificationManager`](app/src/main/java/com/example/todoapplication/data/repository/GamificationManager.kt) lưu trạng thái thuần client trong Room (bảng `gamification`, 1 dòng):
- **XP**: mỗi việc hoàn thành +10, cộng thêm theo độ ưu tiên (HIGH +15, MEDIUM +5)
- **Cấp độ**: tính từ tổng XP, mỗi cấp cần thêm 50 XP so với cấp trước
- **Streak**: chuỗi ngày liên tiếp có hoàn thành việc (so sánh `lastCompletionDate` với hôm qua/hôm nay)
- **7 huy hiệu**: Khởi đầu 🌱, Chăm chỉ ⭐, Bậc thầy 🏆, Bền bỉ 🔥, Không thể cản ⚡, Lên đỉnh 👑, Kho báu 💎
- Hoàn thành việc → cộng thưởng + **Toast chúc mừng** khi mở khóa huy hiệu mới
- Hiển thị: card streak trên `TaskListScreen` + màn `AchievementsScreen` đầy đủ (ring cấp độ, thanh XP, lưới huy hiệu)

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

Hoàn toàn **local**, không dùng FCM. [`ReminderScheduler`](app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt) dùng WorkManager đặt `OneTimeWorkRequest` đến thời điểm `due_date` (REPLACE theo task id). Lập lịch khi lưu task và khi tải danh sách; huỷ khi xoá. WorkManager tự khôi phục job sau khởi động lại máy.

---

## ✨ AI Quick Add

Bottom sheet trên `TaskListScreen`: nhập câu tự nhiên → gọi `POST /ai/parse-task` → lưu kết quả vào [`QuickAddDraft`](app/src/main/java/com/example/todoapplication/data/repository/QuickAddDraft.kt) → mở `TaskDetailScreen` điền sẵn để người dùng xác nhận trước khi lưu.

---

## ⚙️ Cấu hình

Địa chỉ backend đặt trong [`NetworkClient.kt`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt):

```kotlin
private const val BASE_URL = "https://todo.phongngohong.online/api/v1/"
```

Khi chạy backend ở local, đổi sang địa chỉ máy chủ. Lưu ý nếu dùng HTTP cleartext: `usesCleartextTraffic="true"` đã bật trong `AndroidManifest.xml`; với emulator, host máy là `http://10.0.2.2:8080/api/v1/`.

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
| `VIBRATE` | Rung khi hết phiên Pomodoro |
