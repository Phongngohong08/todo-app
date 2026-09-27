# Công nghệ & Kỹ thuật Android sử dụng

Tài liệu liệt kê đầy đủ các công nghệ, thư viện và **kỹ thuật lập trình Android** được áp dụng trong ứng dụng Todo List tích hợp AI. Trọng tâm là kỹ thuật xây dựng phần mềm Android phía client, đặc biệt là kiến trúc **offline-first** (Room là nguồn dữ liệu duy nhất + đồng bộ nền bằng WorkManager).

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
| **Material 3** (`material3`) | `colorScheme`, `Scaffold`, `Card`, `SnackbarHost`, `PullToRefreshBox`, `ModalBottomSheet`, `SwipeToDismissBox`… |
| **Material Icons Extended** | Bộ icon đầy đủ (vd `CloudOff`, `CloudUpload` cho trạng thái đồng bộ) |
| **Compose Animation** | `Modifier.animateItem()` (thẻ trượt mượt giữa các nhóm), `animateColorAsState` |
| **Compose Canvas** | Vẽ tùy biến: biểu đồ cột năng suất tuần, bản đồ nhiệt năm |
| **Haptic feedback** | `LocalHapticFeedback` — rung nhẹ khi kéo-thả, khi vuốt hoàn thành |
| **SplashScreen API** (`core-splashscreen` 1.0.1) | Màn khởi động theo thương hiệu, icon AnimatedVectorDrawable (Android 12+), hiệu ứng thoát tùy biến |
| **Adaptive icon** (vector) | Lớp nền + lớp trước + lớp **monochrome** (themed icon Android 13+); không cần ảnh bitmap theo mật độ màn hình vì minSdk 29 |
| **Lifecycle Runtime Compose** | `collectAsStateWithLifecycle()`, `LifecycleEventEffect(ON_START)` |

### Kỹ thuật UI áp dụng
- **Theme động Sáng/Tối** qua `lightColorScheme()` / `darkColorScheme()` + nút chuyển (SYSTEM/LIGHT/DARK).
- **Edge-to-edge** (`enableEdgeToEdge()`) + xử lý **WindowInsets**.
- **State hoisting**: màn hình chỉ giữ state UI thuần (menu mở/đóng, chế độ sắp xếp) bằng `remember`/`rememberSaveable`; dữ liệu nghiệp vụ đến từ ViewModel.
- **TextField không vòng qua ViewModel**: ô tìm kiếm giữ text cục bộ (cập nhật đồng bộ từng phím), ViewModel chỉ nhận giá trị để lọc — tránh giật con trỏ.
- **Undo pattern**: xóa/hoàn thành thực hiện ngay, Snackbar có nút **Hoàn tác** (thay cho hộp thoại xác nhận).
- **LazyColumn tối ưu**: `key` ổn định + `contentType` cho từng loại item; dữ liệu đã được nhóm/sắp xếp sẵn trong ViewModel nên khối composable không tính toán lại khi recompose.

---

## 3. Kiến trúc & Điều hướng

| Công nghệ | Vai trò |
| :--- | :--- |
| **Navigation Compose** 2.8.5 | Điều hướng giữa các màn hình, một Activity duy nhất |
| **ViewModel** (`lifecycle-viewmodel-compose`) | Tầng trình bày MVVM, giữ state qua config change |
| **SavedStateHandle** | ViewModel đọc tham số route (`taskId`) — sống sót cả khi hệ điều hành giết tiến trình |
| **Lifecycle** 2.10.0 | Thành phần nhận biết vòng đời |

### Kiến trúc **MVVM + Offline-first**

```
View (Composable) ──hành động──► ViewModel ──ghi──► Repository ──► Room (đánh dấu dirty)
       ▲                              │                               │
       └──────── StateFlow ◄── combine/stateIn ◄──── Flow ◄───────────┘
                                                                      │ WorkManager
                                                      SyncEngine ◄────┘ ⇄ Server (REST)
```

- **View** (`ui/screens/`): chỉ render state và gửi hành động; **không** gọi API.
- **ViewModel** (`ui/viewmodel/`, 9 cái): `StateFlow<…UiState>` được **dẫn xuất** từ các Flow của Room bằng `combine` + `stateIn(WhileSubscribed(5000))`; sự kiện một lần (Snackbar, điều hướng) qua `SharedFlow`. Việc nặng (nhóm/sắp xếp/gợi ý) chạy bằng `flowOn(Dispatchers.Default)`; dispatcher được truyền qua constructor để test tất định.
- **Domain** (`domain/`): logic thuần Kotlin — `TaskListLogic`, `RecurrenceRules`, `SortOrder`, `StatsCalculator`. Nhận "bây giờ" và múi giờ làm tham số → test lặp lại được.
- **Repository** (`data/repository/`): đọc = Flow từ Room; ghi = transaction Room + side effect (`TaskEffects`: nhắc việc, widget, xin đồng bộ). Repository công việc **không gọi API**.
- **Sync** (`data/sync/`): `SyncEngine` đẩy/kéo thay đổi; `SyncController` là mặt tiền duy nhất cho ViewModel (trạng thái mạng, đang đồng bộ, "đồng bộ ngay").
- **Tách 3 dạng dữ liệu**: `TaskDto` (JSON) ↔ `TaskEntity` (Room) ↔ `Task` (domain), chuyển đổi trong `Mappers.kt`.

### Dependency Injection — **ServiceLocator (manual DI)**

- `di/ServiceLocator.kt` cung cấp singleton `by lazy` (ApiService, AppDatabase, SyncEngine, các Repository), khởi tạo trong `TodoApplication.onCreate()`.
- ViewModel nhận phụ thuộc qua **`viewModelFactory { initializer { … } }`** (kể cả `createSavedStateHandle()`), gọi bằng `viewModel(factory = …)`.
- > **Lưu ý:** dự định ban đầu dùng **Hilt**, nhưng Hilt Gradle plugin (tới 2.57.1) **không tương thích AGP 9** (lỗi *"Android BaseExtension not found"*). Đã chuyển sang ServiceLocator để đạt cùng mục tiêu DI mà vẫn build được.

### Kỹ thuật kiến trúc khác
- **Single-Activity Architecture**: 1 `MainActivity` + `NavHost`.
- **Luồng dữ liệu một chiều (UDF)**: sau khi ghi, ViewModel không tự sửa state — Room phát dữ liệu mới và mọi màn hình (kể cả widget) tự cập nhật.
- **Event bus một chiều** bằng `SharedFlow` (`SessionEvents.forcedLogout`) để buộc đăng xuất khi phiên hết hạn.

---

## 4. Mạng (Networking)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Retrofit 2** (2.9.0) | Khai báo REST API kiểu interface |
| **OkHttp** (4.12.0) | HTTP client, interceptor, authenticator |
| **Gson Converter** | (De)serialize JSON ↔ data class |
| **OkHttp Logging Interceptor** | Log request/response (chỉ bản debug, che header `Authorization`) |

### Kỹ thuật mạng áp dụng
- **Interceptor** tự gắn `Authorization: Bearer <access_token>`.
- **Authenticator** tự làm mới token khi gặp `401` — thread-safe bằng `synchronized` để nhiều request 401 đồng thời chỉ refresh một lần.
- **Access + Refresh token** lưu bằng **EncryptedSharedPreferences** (khóa trong Android Keystore). Đăng xuất gọi `POST /auth/logout` để server thu hồi refresh token.
- **Network Security Config**: bản release chỉ cho HTTPS; bản debug cho phép HTTP tới địa chỉ local (source set `src/debug`).
- **Request idempotent** cho đồng bộ: `PUT /tasks/{id}` (tạo nếu chưa có), `DELETE` lặp lại vẫn thành công → gửi lại sau khi mất mạng luôn an toàn.

---

## 5. Bất đồng bộ (Asynchronous)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Kotlin Coroutines** | `suspend fun`, `viewModelScope`, `goAsync()` + coroutine trong BroadcastReceiver |
| **Flow / StateFlow / SharedFlow** | Dữ liệu phản ứng từ Room tới UI; sự kiện một lần |
| **Mutex** | Chỉ một lần đồng bộ chạy tại một thời điểm |

### Toán tử Flow áp dụng
- `combine` — ghép dữ liệu Room + bộ lọc + trạng thái mạng + trạng thái đồng bộ thành một `UiState`.
- `debounce(250)` + `distinctUntilChanged` — chỉ truy vấn khi người dùng ngừng gõ tìm kiếm.
- `flatMapLatest` — đổi bộ lọc thì hủy truy vấn cũ, không bao giờ hiện kết quả của bộ lọc trước.
- `flowOn(Dispatchers.Default)` — tính toán nặng ngoài luồng UI.
- `stateIn(WhileSubscribed(5000))` — ngừng lắng nghe database khi màn hình không hiển thị (giữ 5 giây để qua được xoay màn hình).
- `callbackFlow` + `awaitClose` — bọc `ConnectivityManager.NetworkCallback` thành `Flow<Boolean>`.

---

## 6. Lưu trữ cục bộ (Room — nguồn dữ liệu duy nhất)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Room** 2.7.1 (qua KSP) | SQLite: `@Entity`, `@Dao`, `@Database`, `@Upsert`, `@Transaction`, `@Embedded`, `ForeignKey`, `Index` |
| **room-testing** | `MigrationTestHelper` kiểm tra migration trên thiết bị |
| **EncryptedSharedPreferences** | Phiên đăng nhập (mã hóa) |
| **SharedPreferences** | Chế độ giao diện (tùy chọn riêng của máy) |

### Kỹ thuật áp dụng
- **5 bảng** (version 5): `tasks` (kèm cờ đồng bộ `isDirty`, `isDeleted`, `localVersion`), `subtasks` (khóa ngoại `ON DELETE CASCADE`), `categories`, `chat_messages`, `sync_state`.
- **Flow từ DAO**: Room tự phát lại khi bảng liên quan đổi → danh sách, lịch, thống kê, widget luôn khớp.
- **Truy vấn tổng hợp trong SQL**: tiến độ checklist bằng subquery (`TaskWithProgress` + `@Embedded`), thống kê `SUM(CASE…)` / `GROUP BY`.
- **`@Upsert` thay vì `REPLACE`**: `REPLACE` = DELETE + INSERT sẽ kích hoạt CASCADE xóa sạch bước con.
- **Transaction** (`withTransaction`) cho mọi thao tác ghi nhiều bảng (vd hoàn thành việc lặp = cập nhật task + tạo lần kế tiếp + chép checklist).
- **Migration có kiểm thử**: `MIGRATION_4_5` chuyển bảng cache cũ sang mô hình mới *mà không mất dữ liệu* — đổi chuỗi thời gian ISO sang epoch millis bằng `strftime` của SQLite, tạo lại bảng để thêm khóa ngoại. Schema mỗi version export ra `app/schemas/` và được `MigrationTest` so khớp.
- **Thời gian lưu dạng `Long`** (epoch millis): so sánh/sắp xếp nhanh, không parse chuỗi khi vẽ.
- `RoomDatabase` **singleton** (`@Volatile` + double-checked locking).
- > Lưu ý kỹ thuật: AGP 9 dùng "built-in Kotlin" nên cần flag `android.disallowKotlinSourceSets=false` trong `gradle.properties` để KSP của Room thêm được thư mục mã sinh.

---

## 7. Tác vụ nền (WorkManager)

| Công nghệ | Vai trò |
| :--- | :--- |
| **WorkManager** 2.10.0 | Đồng bộ dữ liệu + nhắc nhở công việc |

### Đồng bộ (`SyncWorker`, `SyncScheduler`)
- **Unique work + `ExistingWorkPolicy.REPLACE` + trễ 1 giây** = *debounce*: nhiều thao tác liên tiếp chỉ tạo một lần đồng bộ.
- **Constraints `NetworkType.CONNECTED`**: mất mạng thì WorkManager tự chờ, có mạng là chạy — kể cả khi app đã đóng.
- **Backoff EXPONENTIAL** khi lỗi mạng/máy chủ (`Result.retry()`), dừng hẳn khi lỗi xác thực (`Result.failure()`).
- **PeriodicWorkRequest 30 phút** (`ExistingPeriodicWorkPolicy.KEEP`) để kéo thay đổi từ thiết bị khác.
- `getWorkInfosByTagFlow` → Flow "đang đồng bộ" cho UI.

### Nhắc nhở (`ReminderScheduler`, `ReminderWorker`)
- `OneTimeWorkRequest` đặt tại `due − reminder_offset`, unique theo task id; lượt **"Hoãn 1 giờ"** là job riêng nên không bị hủy khi đặt lại nhắc việc.
- **Tồn tại qua khởi động lại máy**; hiển thị `NotificationCompat` — hoàn toàn local, không dùng FCM.

---

## 8. Tính năng nền tảng Android khác

| Kỹ thuật | Áp dụng |
| :--- | :--- |
| **Runtime Permissions** | Xin `POST_NOTIFICATIONS` (Android 13+) qua **Activity Result API** |
| **ConnectivityManager** | `registerDefaultNetworkCallback` + `NET_CAPABILITY_VALIDATED` — biết khi nào thật sự có Internet |
| **Application class** | Khởi tạo DI, bật đồng bộ nền, đồng bộ lại khi có mạng (scope sống cùng tiến trình) |
| **Lifecycle-aware sync** | `LifecycleEventEffect(ON_START)` — đồng bộ mỗi khi app trở lại màn hình |
| **BroadcastReceiver** | `NotificationActionReceiver` xử lý nút Hoàn thành/Hoãn (`goAsync()` + ghi Room) |
| **Notification Actions** | `NotificationCompat.Action` + `PendingIntent.getBroadcast` |
| **App Widget (Collection)** | `AppWidgetProvider` + `RemoteViewsService`/`RemoteViewsFactory` đọc Room |
| **Vibrator / Haptics** | Rung báo nhắc việc; haptic feedback khi kéo-thả |

---

## 9. Thư viện bên thứ ba (Third-party)

| Thư viện | Mục đích |
| :--- | :--- |
| **`sh.calvin.reorderable`** 2.4.3 | Kéo-thả sắp xếp item trong `LazyColumn` (`draggableHandle` + `onDragStopped` để lưu khi thả tay) |

---

## 10. Quyền (Permissions) — `AndroidManifest.xml`

| Quyền | Mục đích |
| :--- | :--- |
| `INTERNET` | Gọi REST API |
| `ACCESS_NETWORK_STATE` | Theo dõi trạng thái mạng (banner offline, đồng bộ lại khi có mạng) |
| `POST_NOTIFICATIONS` | Hiển thị nhắc nhở (Android 13+) |
| `VIBRATE` | Rung báo khi có thông báo nhắc việc |

---

## 11. Kiểm thử (Testing)

| Loại | Công cụ | Nội dung |
| :--- | :--- | :--- |
| **Unit test (JVM)** | JUnit 4 | Domain: nhóm/sắp xếp, quy tắc lặp, id tất định (cùng vector với backend Go), fractional indexing, thống kê theo ngày lịch |
| **ViewModel test** | Mockito-Kotlin + kotlinx-coroutines-test (`StandardTestDispatcher`, `advanceUntilIdle`, `backgroundScope`) | Luồng state từ Room, debounce tìm kiếm, Hoàn tác, kéo-thả, đăng xuất khi còn thay đổi chưa đồng bộ |
| **Instrumented test** | AndroidJUnit4 + Room in-memory + server giả (`FakeServer : ApiService`) | Giao thức đồng bộ: tạo offline, mất mạng giữa chừng, thay đổi từ máy khác, hoàn tác xóa, việc lặp offline, sửa khi đang gửi |
| **Migration test** | `MigrationTestHelper` | Dựng DB v4 từ schema JSON, chạy migration, so schema với v5, kiểm tra dữ liệu |

Chạy: `./gradlew testDebugUnitTest` và `./gradlew connectedDebugAndroidTest` (cần emulator).

---

## 12. Backend (tóm tắt — ngoài phạm vi môn Android)

Ứng dụng giao tiếp với backend **Go (Golang)** theo Clean Architecture, dùng PostgreSQL + Qdrant (vector DB) + Google Gemini API. Backend cung cấp các endpoint đồng bộ (`GET /tasks/sync`, `PUT /tasks/{id}` idempotent, xóa mềm) cho app offline-first. Chi tiết tại [`../backend/README.md`](../backend/README.md).
