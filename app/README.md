# Ứng dụng Android — Todo List hỗ trợ bởi AI

Ứng dụng Android (Jetpack Compose) cho hệ thống Quản lý Công việc tích hợp AI, xây theo kiến trúc **offline-first**: mọi thao tác ghi vào database trên máy (Room) trước nên phản hồi tức thì và dùng được khi mất mạng; một bộ đồng bộ chạy nền (WorkManager) đẩy/kéo thay đổi với [backend Go](../backend/README.md).

---

## 🧰 Công nghệ sử dụng

| Thành phần | Thư viện / Phiên bản |
| :--- | :--- |
| Giao diện | Jetpack Compose + Material 3 (pull-to-refresh, Snackbar, `animateItem`) |
| Điều hướng | Navigation Compose (+ `SavedStateHandle` cho tham số route) |
| Mạng | Retrofit 2 + OkHttp (Gson) |
| Bất đồng bộ | Kotlin Coroutines + Flow (`combine`, `flatMapLatest`, `debounce`, `stateIn`, `callbackFlow`) |
| Database cục bộ | Room 2.7.1 (KSP) — **nguồn dữ liệu duy nhất** cho UI, có migration + test |
| Đồng bộ & nhắc nhở nền | WorkManager (ràng buộc mạng, backoff, unique work) |
| Sắp xếp kéo-thả | `sh.calvin.reorderable:reorderable:2.4.3` |
| Lưu phiên | EncryptedSharedPreferences (Android Keystore) |
| Kiểm thử | JUnit 4, Mockito-Kotlin, kotlinx-coroutines-test, Room `MigrationTestHelper` |

- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`
- Package: `com.example.todoapplication`
- Dependency khai báo qua version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

---

## 🏛️ Kiến trúc: MVVM + Offline-first

```
┌────────────┐  StateFlow   ┌─────────────┐   Flow    ┌──────────────────────────────┐
│ Composable │ ◄─────────── │  ViewModel  │ ◄──────── │  Room (tasks, subtasks, ...)  │
│  (screens) │ ──hành động─►│ (ui/viewmodel)│─ghi────► │  = nguồn dữ liệu duy nhất     │
└────────────┘              └─────────────┘           └──────────────┬───────────────┘
                                                                     │ task "dirty"
                                         WorkManager (khi có mạng)   ▼
                                       ┌──────────────────────────────────────────┐
                                       │ SyncWorker → SyncEngine ⇄ Server (REST)  │
                                       │  PUSH: PUT/DELETE task dirty (idempotent)│
                                       │  PULL: GET /tasks/sync?since=<con trỏ>   │
                                       └──────────────────────────────────────────┘
```

**Luồng dữ liệu một chiều (UDF):** người dùng thao tác → ViewModel gọi repository → repository ghi Room trong một transaction và đánh dấu task là *dirty* → Room tự phát Flow mới → màn hình tự vẽ lại. ViewModel không bao giờ tự sửa danh sách trong state: dữ liệu chỉ đi theo **một đường**.

| Tầng | Thành phần | Vai trò |
| :--- | :--- | :--- |
| **View** | `ui/screens/*` | Chỉ render state + gửi hành động. Không gọi API, không chứa nghiệp vụ |
| **ViewModel** | `ui/viewmodel/*` | `StateFlow<UiState>` (dẫn xuất bằng `combine` + `stateIn`), sự kiện một lần qua `SharedFlow`. Nhóm/sắp xếp danh sách chạy trên `Dispatchers.Default` |
| **Domain** | `domain/*` | Logic thuần Kotlin, không phụ thuộc Android: nhóm/sắp xếp việc, quy tắc lặp lại, thứ tự kéo-thả, thống kê — unit test nhanh |
| **Repository** | `data/repository/*` | Đọc = Flow từ Room; ghi = Room + side effect (nhắc việc, widget, xin đồng bộ). Không gọi API cho công việc |
| **Sync** | `data/sync/*` | `SyncEngine` (push/pull), `SyncWorker` (WorkManager), `SyncScheduler`, `ConnectivityObserver` (`callbackFlow`), `SyncController` (facade cho ViewModel) |
| **Local / Remote** | `data/local/*`, `data/api/*` | Room (entity, DAO, migration, mapper) / Retrofit |
| **DI** | `di/ServiceLocator.kt` | Manual DI (lazy singleton); ViewModel nhận phụ thuộc qua constructor → test truyền bản giả |

Ba dạng dữ liệu tách biệt theo tầng, chuyển đổi trong [`Mappers.kt`](app/src/main/java/com/example/todoapplication/data/local/Mappers.kt):
`TaskDto` (JSON, thời gian là chuỗi RFC3339) ↔ `TaskEntity` (Room, epoch millis + cờ đồng bộ) ↔ `Task` (domain cho UI).

> ⚠️ Định hướng ban đầu dùng **Hilt** nhưng Hilt Gradle plugin (≤ 2.57.1) **không tương thích AGP 9** (lỗi *"Android BaseExtension not found"*). Đã chuyển sang **ServiceLocator** để đạt cùng mục tiêu DI mà vẫn build được. Chi tiết công nghệ: [CONG-NGHE-SU-DUNG.md](CONG-NGHE-SU-DUNG.md).

---

## 🔄 Đồng bộ offline-first (chi tiết)

| Vấn đề | Cách giải quyết |
| :--- | :--- |
| Tạo task khi offline | Id (UUID) sinh ngay trên máy; server nhận nguyên id qua `PUT /tasks/{id}` (tạo nếu chưa có) |
| Mất mạng giữa chừng / gửi lại | Mọi request đồng bộ **idempotent** (PUT ghi toàn bộ trạng thái, DELETE lần hai vẫn thành công) → gửi lại bao nhiêu lần cũng an toàn |
| Người dùng sửa tiếp khi request đang bay | Mỗi lần sửa tăng `localVersion`; chỉ xóa cờ dirty nếu version không đổi → bản sửa sau được gửi ở vòng kế tiếp, không bao giờ mất |
| Chỉ tải phần thay đổi | Con trỏ `server_time`: `GET /tasks/sync?since=...` trả task đã sửa + id đã xóa kể từ lần trước |
| Xóa rồi bấm Hoàn tác | Xóa để lại "bia mộ" (`isDeleted`) vài phút; Hoàn tác = ghi lại → server khôi phục (ghi sau cùng thắng) |
| Hai máy cùng sửa | Bản trên máy còn dirty được giữ khi kéo về, rồi gửi lên — "ghi sau cùng thắng" ở mức task |
| Hai lần đồng bộ chạy cùng lúc | `Mutex` trong `SyncEngine` (WorkManager, kéo-để-làm-mới và đăng xuất dùng chung) |
| Nhiều thao tác liên tiếp | `SyncScheduler.requestSync()` = unique work + REPLACE + trễ 1 giây → gộp thành một lần đồng bộ |
| Khi nào đồng bộ | Sau mỗi thay đổi, khi app trở lại màn hình (`ON_START`), khi có mạng trở lại, định kỳ 30 phút, kéo-để-làm-mới |
| Đăng xuất khi còn thay đổi chưa gửi | Thử đồng bộ trước; không được (offline) thì hỏi lại người dùng thay vì âm thầm làm mất dữ liệu |

**Việc lặp lại khi offline:** hoàn thành một việc lặp sẽ tạo ngay lần kế tiếp trên máy với **id tất định** `UUIDv3(namespace, id cha)`. Server dùng đúng thuật toán này (test chéo bằng cùng một vector ở cả Kotlin và Go) nên khi đồng bộ chỉ còn một bản. Hạn của lần kế tiếp luôn ở tương lai kể cả khi hoàn thành trễ; mở lại việc đã xong sẽ thu hồi lần lặp chưa làm.

---

## 🏗️ Cấu trúc thư mục

```
app/src/main/java/com/example/todoapplication/
  MainActivity.kt                # NavHost; xin quyền thông báo; forced-logout; đồng bộ khi ON_START
  TodoApplication.kt             # Khởi tạo DI; bật đồng bộ nền; đồng bộ lại khi có mạng
  domain/                        # Logic thuần Kotlin (không phụ thuộc Android)
    model/Task.kt                # Task, Subtask, TaskDraft, StatsSummary
    TaskListLogic.kt             # Quá hạn, nhóm Hôm nay/Tương lai/Đã xong, sắp xếp, gợi ý AI
    RecurrenceRules.kt           # Quy tắc lặp + id tất định (giống backend)
    SortOrder.kt                 # Fractional indexing cho kéo-thả
    StatsCalculator.kt           # Biểu đồ tuần / bản đồ nhiệt theo ngày lịch
  data/
    api/                         # ApiService (Retrofit) + NetworkClient (token, tự refresh)
    model/Models.kt              # DTO JSON (TaskDto, TaskInputDto, TaskChangesDto, ...)
    local/                       # Room — database version 5
      Entities.kt                # tasks, subtasks (FK CASCADE), categories, chat_messages, sync_state
      Daos.kt                    # Truy vấn (Flow) — kể cả thống kê, lịch, widget
      AppDatabase.kt             # RoomDatabase singleton
      Migrations.kt              # MIGRATION_4_5 (giữ dữ liệu cũ)
      Mappers.kt                 # DTO ↔ Entity ↔ Domain
    sync/                        # SyncEngine, SyncWorker, SyncScheduler, SyncController, ConnectivityObserver
    repository/
      TaskRepository.kt          # Offline-first: đọc Flow, ghi Room + đánh dấu dirty
      TaskEffects.kt             # Side effect: nhắc việc, widget, xin đồng bộ
      Repositories.kt            # Auth, Preferences, Plan, Ai, Category, Chat, Stats
      SessionManager.kt          # Token + thông tin user (mã hóa)
      UserDataCleaner.kt         # Xóa dữ liệu cục bộ khi đăng xuất
      ...                        # SessionEvents, QuickAddDraft, ThemeController, NetworkCallExt
    notifications/               # ReminderScheduler, ReminderWorker, NotificationActionReceiver
  widget/                        # App widget đọc thẳng Room
  di/ServiceLocator.kt           # Manual DI — đồ thị phụ thuộc ghi trong file
  ui/
    viewmodel/                   # 9 ViewModel
    screens/                     # 10 màn hình Compose
    components/, theme/, navigation/, utils/
app/schemas/                     # Schema Room đã export (4.json, 5.json) — dùng cho MigrationTest
```

---

## 📱 Các màn hình

| Màn hình | Chức năng |
| :--- | :--- |
| `LoginScreen` / `RegisterScreen` | Đăng nhập / đăng ký; báo lỗi rõ ràng (email đã dùng, sai mật khẩu, thao tác quá nhanh) |
| `TaskListScreen` | Tìm kiếm (debounce), **lọc danh mục**, nhóm **Hôm nay / Tương lai / Đã hoàn thành hôm nay**, tích/vuốt để hoàn thành (**Hoàn tác** qua Snackbar, tích lại để mở lại), xóa có Hoàn tác, cờ ưu tiên, sắp xếp, **kéo-thả lưu thứ tự**, **kéo-để-làm-mới**, banner offline/đang đồng bộ, biểu tượng ☁ trên thẻ chưa đồng bộ |
| `TaskDetailScreen` | Thêm/sửa task; **thêm bước con ngay cả khi đang tạo mới**; danh mục (chọn/thêm), lặp theo thứ, lời nhắc |
| `CalendarScreen` | Lịch tháng; lần lặp **dự kiến** hiển thị mờ, khác với task thật |
| `DailyPlanScreen` | Lịch trình do AI tạo (gửi kèm ngày/múi giờ của máy) |
| `AICoachScreen` | Chat với AI Coach — **lịch sử lưu trên server**, mở lại app vẫn còn |
| `StatsScreen` | Thống kê/biểu đồ/bản đồ nhiệt **tính từ Room**, cập nhật tức thì và xem được khi offline; Trí nhớ AI |
| `SettingsScreen` | Giao diện Sáng/Tối; giờ giấc cho lập lịch AI; **danh mục đồng bộ theo tài khoản** |
| `TemplatesScreen` | Thư viện mẫu nhiệm vụ |

---

## ✨ Tính năng Android nổi bật

- **Checklist (bước con)** — bảng `subtasks` có khóa ngoại `ON DELETE CASCADE`; thẻ task hiện tiến độ `☑ 2/5` đếm bằng subquery ngay trong SQL. Bước con nằm trong task khi đồng bộ (task là *aggregate root*).
- **Kéo-thả có lưu** — "fractional indexing": thả task giữa A và B chỉ gán `sortOrder` ở giữa → chỉ một dòng thay đổi và cần đồng bộ. Trong lúc kéo, thứ tự chỉ đổi trong bộ nhớ (mượt 60fps), thả tay mới ghi database; có rung phản hồi (haptic).
- **Thông báo có nút** "Hoàn thành" / "Hoãn 1 giờ" — "Hoàn thành" ghi vào Room nên chạy được khi offline; lượt hoãn có job WorkManager riêng nên không bị hủy khi đồng bộ lại nhắc việc.
- **Widget màn hình chính** — `RemoteViewsService` đọc thẳng Room, tự làm mới sau mỗi thay đổi.
- **AI Quick Add / AI Coach / Lịch trình AI** — báo rõ khi hết lượt AI (HTTP 429) hoặc AI chưa bật (503).
- **Hiệu năng danh sách** — nhóm/sắp xếp/gợi ý tính trong ViewModel trên `Dispatchers.Default`; thời gian là `Long` (không parse chuỗi ISO khi vẽ); `LazyColumn` dùng `key` + `contentType`; `DateTimeFormatter` dùng lại (thread-safe).

---

## 🔐 Xử lý token (Access + Refresh)

- Đăng nhập lưu `token` và `refresh_token` trong [`SessionManager`](app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt) (EncryptedSharedPreferences).
- [`NetworkClient`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt) gắn `Authorization: Bearer <access>` và cài **OkHttp `Authenticator`**: gặp `401` → gọi `/auth/refresh` (client phụ, tránh đệ quy) → phát lại request. Refresh thất bại → `SessionEvents.forcedLogout` → `MainActivity` đưa về Login.
- Đăng xuất gọi `POST /auth/logout` để server **thu hồi refresh token** (mọi thiết bị), rồi xóa toàn bộ dữ liệu cục bộ.

---

## ⚙️ Cấu hình

Địa chỉ backend trong [`NetworkClient.kt`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt):

```kotlin
private const val BASE_URL = "https://todo.phongngohong.online/api/v1/"
```

Chạy với backend trên máy tính: đổi thành `http://10.0.2.2:8080/api/v1/` (AVD), `http://10.0.3.2:8080/api/v1/` (Genymotion) hoặc `http://<IP_LAN>:8080/api/v1/` (điện thoại thật). Bản **release chặn HTTP** (chỉ HTTPS); bản **debug** cho phép HTTP tới các địa chỉ local trên qua [`src/debug/res/xml/network_security_config.xml`](app/src/debug/res/xml/network_security_config.xml).

> ⚠️ App offline-first cần backend đã có endpoint đồng bộ (`GET /tasks/sync`, `PUT /tasks/{id}` tạo mới, migration `000007`). Với backend cũ, thay đổi vẫn lưu an toàn trên máy nhưng không đồng bộ được.

---

## 🚀 Build, chạy & kiểm thử

**Android Studio**: mở thư mục `app/`, đồng bộ Gradle, chọn emulator (khuyến nghị API 33+) rồi Run.

**Dòng lệnh** (JAVA_HOME trỏ tới JDK 17+, vd `C:\Program Files\Android\Android Studio\jbr`):
```bash
./gradlew assembleDebug              # APK debug → app/build/outputs/apk/debug/
./gradlew installDebug               # cài lên thiết bị/emulator đang kết nối

./gradlew testDebugUnitTest          # unit test (JVM): domain + ViewModel
./gradlew connectedDebugAndroidTest  # instrumented test (cần emulator): Room migration + đồng bộ
```

| Bộ test | Nội dung |
| :--- | :--- |
| `domain/*Test` | Nhóm/sắp xếp việc, quy tắc lặp (cùng trường hợp với test Go), id tất định, thứ tự kéo-thả, thống kê theo ngày lịch |
| `TaskListViewModelTest` | Luồng state từ Room, debounce tìm kiếm, hoàn thành/xóa + Hoàn tác, kéo-thả, đăng xuất khi còn thay đổi chưa đồng bộ |
| `MigrationTest` (androidTest) | Dựng DB v4 từ schema JSON, chạy `MIGRATION_4_5`, kiểm tra schema khớp v5 và dữ liệu được giữ |
| `SyncEngineTest` (androidTest) | Room thật + server giả: tạo khi offline, mất mạng, thay đổi từ máy khác, hoàn tác xóa sau đồng bộ, việc lặp offline, sửa khi đang gửi |

> ⚙️ AGP 9 dùng "built-in Kotlin" nên cần flag `android.disallowKotlinSourceSets=false` trong `gradle.properties` để KSP (Room) thêm được source set sinh mã.

---

## 🔒 Quyền (Permissions)

| Quyền | Mục đích |
| :--- | :--- |
| `INTERNET` | Gọi REST API |
| `ACCESS_NETWORK_STATE` | Theo dõi trạng thái mạng: banner offline, đồng bộ lại khi có mạng |
| `POST_NOTIFICATIONS` | Hiển thị nhắc nhở công việc (Android 13+) |
| `VIBRATE` | Rung khi có thông báo nhắc việc |
