# Công nghệ & Kỹ thuật Android sử dụng

Tài liệu liệt kê đầy đủ các công nghệ, thư viện và **kỹ thuật lập trình Android** được áp dụng trong ứng dụng Todo List tích hợp AI. Trọng tâm là kỹ thuật xây dựng phần mềm Android phía client.

---

## 1. Ngôn ngữ & Nền tảng

| Hạng mục | Giá trị |
| :--- | :--- |
| Ngôn ngữ | **Kotlin** 2.2.10 |
| Build tool | **Android Gradle Plugin (AGP)** 9.2.1 + Gradle (Kotlin DSL) |
| Quản lý dependency | **Version Catalog** (`gradle/libs.versions.toml`) |
| Annotation processor | **KSP** (Kotlin Symbol Processing) 2.2.10-2.0.2 |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 29 (Android 10) |
| JVM target | Java 11 |

---

## 2. Giao diện (UI)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Jetpack Compose** (BOM 2026.02.01) | Toàn bộ UI khai báo (declarative), không dùng XML layout |
| **Material 3** (`material3`) | Hệ thống thiết kế: `colorScheme`, `Surface`, `Scaffold`, `Card`, component… |
| **Material Icons Extended** | Bộ icon đầy đủ |
| **Compose Animation** | `animateColorAsState`, `tween` (đổi theme, đổi pha Pomodoro) |
| **Compose Canvas** | Vẽ tùy biến: vòng arc Pomodoro, biểu đồ cột năng suất tuần |
| **Compose Foundation Gestures** | `SwipeToDismissBox` (vuốt hoàn thành), kéo-thả sắp xếp |
| **Lifecycle Runtime Compose** | `collectAsStateWithLifecycle()` — thu thập StateFlow theo vòng đời |

### Kỹ thuật UI áp dụng
- **Theme động Sáng/Tối** qua `lightColorScheme()` / `darkColorScheme()` + nút chuyển (SYSTEM/LIGHT/DARK), recompose toàn app khi đổi.
- **Custom Type scale & Shapes** truyền vào `MaterialTheme`.
- **Edge-to-edge** (`enableEdgeToEdge()`) + xử lý **WindowInsets**: `statusBarsPadding()`, `navigationBarsPadding()`, `imePadding()`.
- **State hoisting** & quản lý trạng thái Compose: `remember`, `mutableStateOf`, `derivedStateOf`, `rememberCoroutineScope`, `LaunchedEffect`.
- **LazyColumn / LazyVerticalGrid** với `key` ổn định để tái sử dụng item hiệu quả.

---

## 3. Kiến trúc & Điều hướng

| Công nghệ | Vai trò |
| :--- | :--- |
| **Navigation Compose** 2.8.5 | Điều hướng giữa các màn hình, một Activity duy nhất |
| **ViewModel** (`lifecycle-viewmodel-compose`) | Tầng trình bày MVVM, giữ state qua config change |
| **Lifecycle** 2.10.0 (`lifecycle-runtime-ktx`, `lifecycle-runtime-compose`) | Thành phần nhận biết vòng đời |
| **Activity Compose** 1.13.0 | `ComponentActivity` + `setContent` |

### Kiến trúc **MVVM** (Model – View – ViewModel)

Ứng dụng tổ chức theo **MVVM phân lớp rõ ràng**:

```
View (Composable)  →  ViewModel (StateFlow<UiState>)  →  Repository  →  Data source (REST / Room)
```

- **View** (`ui/screens/`): chỉ hiển thị state và chuyển hành động người dùng tới ViewModel; **không** gọi API trực tiếp.
- **ViewModel** (`ui/viewmodel/`): 9 ViewModel (`TaskListViewModel`, `TaskDetailViewModel`, `LoginViewModel`, `RegisterViewModel`, `DailyPlanViewModel`, `AICoachViewModel`, `StatsViewModel`, `SettingsViewModel`, `PomodoroViewModel`). Mỗi VM:
  - Phơi state qua **`StateFlow<…UiState>`** (data class bất biến) — View thu bằng **`collectAsStateWithLifecycle()`**.
  - Phát **sự kiện một lần** (toast/điều hướng/rung) qua **`SharedFlow`** để tránh phát lại khi recompose.
  - Chạy nghiệp vụ trong **`viewModelScope`** → tự hủy coroutine theo vòng đời; **sống sót qua xoay màn hình** (vd timer Pomodoro chạy trong VM).
- **Repository** (`data/repository/`): `TaskRepository`, `AuthRepository`, `PreferencesRepository`, `PlanRepository`, `AiRepository`, `StatsRepository` — nguồn dữ liệu duy nhất, bọc REST + Room + side-effect (nhắc nhở, gamification). Trả `Result<T>`.
- **UiState** (`ui/state/UiState.kt`): sealed `Loading / Success / Error` mô hình hóa trạng thái.

### Dependency Injection — **ServiceLocator (manual DI)**

- `di/ServiceLocator.kt` cung cấp các phụ thuộc singleton (ApiService, AppDatabase, các Repository) dạng `by lazy`, khởi tạo một lần trong `TodoApplication.onCreate()`.
- ViewModel nhận Repository qua **`ViewModelProvider.Factory`** (`viewModelFactory { initializer { … } }`), gọi từ Compose bằng `viewModel(factory = …)`.
- > **Lưu ý:** dự định ban đầu dùng **Hilt**, nhưng Hilt Gradle plugin (tới 2.57.1) **không tương thích AGP 9** (lỗi *"Android BaseExtension not found"* — AGP 9 bỏ API `BaseExtension`). Đã chuyển sang ServiceLocator để đạt cùng mục tiêu DI mà vẫn build được.

### Kỹ thuật kiến trúc khác
- **Single-Activity Architecture**: 1 `MainActivity` + `NavHost`, mỗi màn là một `@Composable`.
- **Typed routes + arguments**: `NavType.StringType`, truyền tham số (id task) và **URL-encode** chuỗi có ký tự đặc biệt (tiêu đề task cho Pomodoro).
- **Holder singleton** cho state cấp ứng dụng: `SessionManager`, `ThemeController`, `GamificationManager`.
- **Event bus một chiều** bằng `SharedFlow` (`SessionEvents.forcedLogout`) để điều hướng buộc đăng xuất khi phiên hết hạn.

---

## 4. Mạng (Networking)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Retrofit 2** (2.9.0) | Khai báo REST API kiểu interface |
| **OkHttp** (4.12.0) | HTTP client, interceptor, authenticator |
| **Gson Converter** | (De)serialize JSON ↔ data class |
| **OkHttp Logging Interceptor** | Log request/response khi debug |

### Kỹ thuật mạng áp dụng
- **Interceptor** tự gắn `Authorization: Bearer <access_token>` vào mọi request.
- **Authenticator** tự động làm mới token khi gặp `401`: gọi `/auth/refresh` qua client "trần" (tránh đệ quy), lưu cặp token mới, phát lại request — **thread-safe** bằng `synchronized` để nhiều request 401 đồng thời chỉ refresh một lần.
- **Access + Refresh token** (JWT) lưu trong `SharedPreferences`.

---

## 5. Bất đồng bộ (Asynchronous)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Kotlin Coroutines** | Xử lý bất đồng bộ, gọi API `suspend` |
| **Flow / SharedFlow** | Luồng sự kiện (forced logout) |

### Kỹ thuật áp dụng
- `suspend fun` cho mọi lời gọi API, chạy trong `rememberCoroutineScope` / `LaunchedEffect`.
- Chuyển luồng với `Dispatchers.IO` (đọc/ghi Room) và `withContext(Dispatchers.Main)` khi cập nhật state.

---

## 6. Lưu trữ cục bộ (Local Persistence)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Room** 2.7.1 (qua KSP) | Local database (SQLite) — `@Entity`, `@Dao`, `@Database` |
| **SharedPreferences** | Lưu phiên đăng nhập, chế độ giao diện |

### Kỹ thuật áp dụng
- **Offline-first read cache**: tải task thành công → ghi đè bảng `task_cache`; mất mạng → đọc lại từ Room + hiển thị banner offline.
- **Dữ liệu thuần client** (XP, streak, huy hiệu) lưu bảng `gamification`.
- `RoomDatabase` dạng **singleton** (`@Volatile` + double-checked locking), DAO sinh mã qua **KSP**.
- > Lưu ý kỹ thuật: AGP 9 dùng "built-in Kotlin" nên cần flag `android.disallowKotlinSourceSets=false` trong `gradle.properties` để KSP của Room thêm được thư mục mã sinh.

---

## 7. Tác vụ nền (Background Processing)

| Công nghệ | Vai trò |
| :--- | :--- |
| **WorkManager** 2.10.0 | Lập lịch nhắc nhở công việc |

### Kỹ thuật áp dụng
- `OneTimeWorkRequest` đặt theo mốc `due_date` của từng task (`ExistingWorkPolicy.REPLACE` theo task id).
- **Tồn tại qua khởi động lại máy** (WorkManager tự khôi phục job).
- Hiển thị **Notification** qua `NotificationCompat` khi đến hạn — hoàn toàn local, không dùng FCM.

---

## 8. Tính năng nền tảng Android khác

| Kỹ thuật | Áp dụng |
| :--- | :--- |
| **Runtime Permissions** | Xin `POST_NOTIFICATIONS` (Android 13+) qua **Activity Result API** (`rememberLauncherForActivityResult`) |
| **Vibrator / Haptics** | Rung báo khi hết phiên Pomodoro (`VibratorManager` / `Vibrator` theo API level) |
| **System Services** | Truy cập `VIBRATOR_MANAGER_SERVICE` qua `Context.getSystemService` |
| **API-level branching** | `Build.VERSION.SDK_INT` để chọn API phù hợp (vibrate, permission) |

---

## 9. Thư viện bên thứ ba (Third-party)

| Thư viện | Mục đích |
| :--- | :--- |
| **`sh.calvin.reorderable`** 2.4.3 | Kéo-thả sắp xếp thứ tự item trong `LazyColumn` |

---

## 10. Quyền (Permissions) — `AndroidManifest.xml`

| Quyền | Mục đích |
| :--- | :--- |
| `INTERNET` | Gọi REST API |
| `POST_NOTIFICATIONS` | Hiển thị nhắc nhở (Android 13+) |
| `VIBRATE` | Rung báo hết phiên Pomodoro |

---

## 11. Backend (tóm tắt — ngoài phạm vi môn Android)

Ứng dụng giao tiếp với backend **Go (Golang)** theo Clean Architecture, dùng PostgreSQL + Qdrant (vector DB) + Google Gemini API. Chi tiết tại [`../backend/README.md`](../backend/README.md).

---

## 12. Sơ đồ phân lớp MVVM (tổng quan)

```
┌──────────────────────────────────────────────────────┐
│  VIEW — Jetpack Compose (ui/screens, components, theme)│
│   collectAsStateWithLifecycle() ← StateFlow            │
│   gọi hành động → ViewModel                             │
└───────────────────────┬────────────────────────────────┘
                        │  (viewModel(factory = …))
┌───────────────────────▼────────────────────────────────┐
│  VIEWMODEL (ui/viewmodel) — StateFlow<UiState> + SharedFlow│
│   viewModelScope · sống qua config change               │
└───────────────────────┬────────────────────────────────┘
                        │  (ServiceLocator inject Repository)
┌───────────────────────▼────────────────────────────────┐
│  REPOSITORY (data/repository) — Result<T>, single source │
│   Task · Auth · Preferences · Plan · Ai · Stats          │
└──────┬───────────────────────────────────┬──────────────┘
       │                                   │
┌──────▼─────────┐                ┌────────▼──────────────┐
│  Remote (REST) │                │  Local (Room + Prefs) │
│  Retrofit/OkHttp│                │  AppDatabase / DAO    │
└────────────────┘                └───────────────────────┘
```
