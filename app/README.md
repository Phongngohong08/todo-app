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
| Xóa rồi bấm Hoàn tác / Thùng rác | Xóa để lại "bia mộ" (`isDeleted`) **30 ngày** — việc bị xóa ở máy khác (`deleted_ids`) cũng thành bia mộ — nên hiện trong **Thùng rác** và khôi phục được; khôi phục = ghi lại → server "hồi sinh" task (ghi sau cùng thắng). Xóa vĩnh viễn chỉ áp dụng cho bia mộ đã gửi việc xóa lên server |
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
  MainActivity.kt                # NavHost 4 tab; deep link (thông báo, widget, shortcut, chia sẻ); forced-logout; đồng bộ khi ON_START
  TodoApplication.kt             # Khởi tạo DI; kênh thông báo; hẹn tóm tắt sáng/tổng kết tuần; đồng bộ nền
  domain/                        # Logic thuần Kotlin (không phụ thuộc Android)
    model/Task.kt                # Task (cả ngày, Ngày của tôi, thời lượng, lặp nâng cao), Subtask, TaskDraft
    TaskListLogic.kt             # Nhóm Quá hạn/Hôm nay/Sắp tới/Chưa có hạn, bộ lọc thông minh, "Nên làm trước" + lý do, gợi ý Ngày của tôi
    QuickAddParser.kt            # Phân tích câu tạo nhanh tiếng Việt trên máy (ngày, giờ, !ưu tiên, #danh mục, lặp)
    RecurrenceRules.kt           # Quy tắc lặp (mỗi N, theo lịch/ngày hoàn thành, ngày kết thúc) + id tất định — giống backend
    GoalsAndReview.kt            # Mục tiêu ngày + chuỗi (ngày nghỉ), Tổng kết tuần
    PlanLogic.kt                 # Chỉnh lịch trình AI (dồn khung), phát hiện lịch đã cũ
    SnoozeOptions.kt             # Các mốc "Hoãn…"
    SortOrder.kt                 # Fractional indexing cho kéo-thả
    StatsCalculator.kt           # Biểu đồ tuần / bản đồ nhiệt theo ngày lịch
  data/
    api/                         # ApiService (Retrofit) + NetworkClient (token, tự refresh)
    model/Models.kt              # DTO JSON (TaskDto, TaskInputDto, TaskChangesDto, ...)
    local/                       # Room — database version 6
      Entities.kt                # tasks, subtasks (FK CASCADE), categories, chat_messages, sync_state
      Daos.kt                    # Truy vấn (Flow) — kể cả thống kê, lịch, widget
      AppDatabase.kt             # RoomDatabase singleton
      Migrations.kt              # MIGRATION_4_5, MIGRATION_5_6 (giữ dữ liệu cũ)
      Mappers.kt                 # DTO ↔ Entity ↔ Domain
    sync/                        # SyncEngine, SyncWorker, SyncScheduler, SyncController, ConnectivityObserver
    repository/
      TaskRepository.kt          # Offline-first: đọc Flow, ghi Room + đánh dấu dirty
      TaskEffects.kt             # Side effect: nhắc việc, widget, xin đồng bộ
      Repositories.kt            # Auth, Preferences, Plan, Ai, Category, Chat, Stats
      SessionManager.kt          # Token + thông tin user (mã hóa)
      UserDataCleaner.kt         # Xóa dữ liệu cục bộ khi đăng xuất
      LocalPrefs.kt              # Cài đặt cục bộ (mục tiêu đệm, thông báo) dạng StateFlow
      CoachActionApplier.kt      # Áp dụng hành động AI Coach đề xuất (sau khi người dùng xác nhận)
      ...                        # SessionEvents, QuickAddDraft, ThemeController, NetworkCallExt
    notifications/               # Nhắc việc (+ Hoãn…), DigestWorker (tóm tắt sáng, tổng kết tuần), FocusSession (Pomodoro)
  widget/                        # App widget đọc thẳng Room; WidgetActionActivity (tích xong / mở việc)
  di/ServiceLocator.kt           # Manual DI — đồ thị phụ thuộc ghi trong file
  ui/
    viewmodel/                   # ViewModel của từng màn (Today, TaskList, TaskDetail, Review/History/Trash, ...)
    screens/                     # Màn hình Compose + TaskComponents (thẻ việc, thanh tạo nhanh dùng chung) + SnoozeActivity
    components/, theme/, navigation/, utils/
app/schemas/                     # Schema Room đã export (4.json, 5.json, 6.json) — dùng cho MigrationTest
```

---

## 📱 Các màn hình

| Màn hình | Chức năng |
| :--- | :--- |
| `LoginScreen` / `RegisterScreen` | Đăng nhập / đăng ký; báo lỗi rõ ràng (email đã dùng, sai mật khẩu, thao tác quá nhanh) |
| `TodayScreen` | Tab mặc định: **Ngày của tôi** (danh sách tự chọn mỗi ngày), gợi ý thêm việc (quá hạn / đến hạn / hôm qua chưa xong / ưu tiên cao), vòng **mục tiêu ngày + chuỗi 🔥**, **lịch trình AI** tích xong trên lịch, đổi giờ (dồn khung sau), báo lịch đã cũ, thanh đếm ngược tập trung |
| `TaskListScreen` | Tab "Việc làm": nhóm **Quá hạn (Dời tất cả) / Hôm nay / Sắp tới / Chưa có hạn / Đã xong hôm nay**, **bộ lọc thông minh** + danh mục + tìm kiếm (debounce), "⚡ Nên làm trước · lý do", tích/vuốt để hoàn thành (**Hoàn tác**), kéo-thả lưu thứ tự, kéo-để-làm-mới, banner offline, ☁ trên thẻ chưa đồng bộ |
| `QuickCreateSheet` | Thanh tạo nhanh: **bộ phân tích câu tiếng Việt chạy trên máy** (`QuickAddParser`) tô màu ngày/giờ/ưu tiên/#danh mục/lặp ngay khi gõ, không cần mạng; nút AI cho câu phức tạp |
| `TaskDetailScreen` | Thêm/sửa task: hạn **ngày + giờ tùy chọn** (không giờ = cả ngày), **thời lượng ước tính**, Ngày của tôi, lặp **mỗi N / theo lịch hoặc ngày hoàn thành / ngày kết thúc**, **Bỏ qua lần này**, lời nhắc, bước con (kể cả khi đang tạo mới) |
| `FocusScreen` | Hẹn giờ tập trung 25'/5' cho một việc; lưu mốc kết thúc nên đóng app vẫn đúng, hết giờ có thông báo |
| `CalendarScreen` | Lịch tháng; lần lặp **dự kiến** hiển thị mờ, khác với task thật |
| `AICoachScreen` | Chat với AI Coach — lịch sử lưu trên server; **thẻ hành động đề xuất** (dời hạn, chia bước con, tạo việc, đổi ưu tiên, thêm vào Ngày của tôi) chỉ áp dụng khi bấm "Áp dụng" (`CoachActionApplier`) |
| `StatsScreen` | Tab "Tôi": mục tiêu + chuỗi ngày, lối tắt, thống kê/biểu đồ/bản đồ nhiệt **tính từ Room** (xem được khi offline), Trí nhớ AI |
| `WeeklyReviewScreen` · `HistoryScreen` · `TrashScreen` | Tổng kết 7 ngày so với tuần trước (+ nhờ AI lập kế hoạch tuần tới) · việc đã xong theo ngày · thùng rác 30 ngày |
| `SettingsScreen` | Giao diện Sáng/Tối; giờ giấc cho lập lịch AI; **mục tiêu ngày + ngày nghỉ**; danh mục; **thông báo** (tóm tắt sáng, tổng kết tuần, giờ nhắc việc cả ngày); **đăng xuất máy này / mọi thiết bị** |
| `TemplatesScreen` | Thư viện mẫu nhiệm vụ |

---

## ✨ Tính năng Android nổi bật

- **Checklist (bước con)** — bảng `subtasks` có khóa ngoại `ON DELETE CASCADE`; thẻ task hiện tiến độ `☑ 2/5` đếm bằng subquery ngay trong SQL. Bước con nằm trong task khi đồng bộ (task là *aggregate root*).
- **Kéo-thả có lưu** — "fractional indexing": thả task giữa A và B chỉ gán `sortOrder` ở giữa → chỉ một dòng thay đổi và cần đồng bộ. Trong lúc kéo, thứ tự chỉ đổi trong bộ nhớ (mượt 60fps), thả tay mới ghi database; có rung phản hồi (haptic).
- **Thông báo có nút** "Hoàn thành" / "Hoãn 1 giờ" / **"Hoãn…"** (hộp chọn `SnoozeActivity`: 15 phút, tối nay, sáng mai…) — "Hoàn thành" ghi vào Room nên chạy được khi offline; lượt hoãn có job WorkManager riêng nên không bị hủy khi đồng bộ lại nhắc việc; việc đã xong ở máy khác thì không nhắc nữa.
- **Tóm tắt buổi sáng & tổng kết tuần** — `DigestWorker` đọc Room (không cần mạng), tự hẹn lần kế tiếp.
- **Quyền thông báo xin đúng lúc** — chỉ hỏi (kèm giải thích) khi người dùng đặt hạn/nhắc việc, bật tóm tắt hoặc bắt đầu tập trung, không hỏi ngay khi mở app.
- **App shortcut & chia sẻ** — nhấn giữ icon: Thêm việc / Hôm nay / Tập trung; "Chia sẻ → Thêm vào TaskFlow" từ app khác mở thanh tạo nhanh đã điền sẵn.
- **Widget màn hình chính** — `RemoteViewsService` đọc thẳng Room ("Ngày của tôi" lên đầu), tự làm mới sau mỗi thay đổi; **ô tích hoàn thành ngay trên widget** và nút **+** (qua `WidgetActionActivity` trong suốt — Android 12+ cấm broadcast tự mở Activity).
- **AI Quick Add / AI Coach / Lịch trình AI** — báo rõ khi hết lượt AI (HTTP 429) hoặc AI chưa bật (503).
- **Icon & màn khởi động** — adaptive icon vẽ bằng vector (nền gradient thương hiệu + "thẻ công việc" có dấu tích và tia sáng AI) kèm lớp **monochrome** cho themed icon Android 13+; màn khởi động dùng **SplashScreen API** (`core-splashscreen`) với icon **AnimatedVectorDrawable** trên Android 12+ (dấu tích tự vẽ bằng `trimPathEnd`, tia sáng bật ra) và hiệu ứng thoát mượt (`setOnExitAnimationListener`). Logo trong app (`AppLogo`) và icon thông báo dùng chung hình.
- **Hiệu năng danh sách** — nhóm/sắp xếp/gợi ý tính trong ViewModel trên `Dispatchers.Default`; thời gian là `Long` (không parse chuỗi ISO khi vẽ); `LazyColumn` dùng `key` + `contentType`; `DateTimeFormatter` dùng lại (thread-safe).

---

## 🔐 Xử lý token (Access + Refresh)

- Đăng nhập lưu `token` và `refresh_token` trong [`SessionManager`](app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt) (EncryptedSharedPreferences).
- [`NetworkClient`](app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt) gắn `Authorization: Bearer <access>` và cài **OkHttp `Authenticator`**: gặp `401` → gọi `/auth/refresh` (client phụ, tránh đệ quy) → phát lại request. Refresh thất bại → `SessionEvents.forcedLogout` → `MainActivity` đưa về Login.
- Đăng xuất gọi `POST /auth/logout` để server **thu hồi refresh token** — mặc định chỉ của máy này, hoặc mọi thiết bị (`all_devices`) — rồi xóa toàn bộ dữ liệu cục bộ của tài khoản.

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
| `domain/*Test` | Nhóm việc (quá hạn / hôm nay / sắp tới / chưa có hạn), việc cả ngày, lý do gợi ý, gợi ý Ngày của tôi, **bộ phân tích câu tiếng Việt**, quy tắc lặp (mỗi N, theo ngày hoàn thành, ngày kết thúc, bỏ qua — cùng vector với test Go), chuỗi ngày + ngày nghỉ, tổng kết tuần, chỉnh lịch trình, lựa chọn hoãn |
| `CoachActionApplierTest` | Áp dụng hành động AI Coach: hạn cả ngày, Ngày của tôi, tạo việc, bỏ qua đề xuất sai |
| `TaskListViewModelTest` · `SettingsViewModelTest` | Luồng state từ Room, bộ lọc thông minh, debounce, hoàn thành/xóa + Hoàn tác, dời việc quá hạn + Hoàn tác, tạo nhanh vào Ngày của tôi, kéo-thả · đăng xuất máy này/mọi thiết bị khi còn thay đổi chưa đồng bộ |
| `MigrationTest` (androidTest) | Dựng DB v4/v5 từ schema JSON, chạy `MIGRATION_4_5` / `MIGRATION_5_6`, kiểm tra schema khớp và dữ liệu được giữ (cột mới có giá trị mặc định) |
| `SyncEngineTest` (androidTest) | Room thật + server giả: tạo khi offline, mất mạng, thay đổi từ máy khác, hoàn tác xóa sau đồng bộ, việc lặp offline, sửa khi đang gửi |

> ⚙️ AGP 9 dùng "built-in Kotlin" nên cần flag `android.disallowKotlinSourceSets=false` trong `gradle.properties` để KSP (Room) thêm được source set sinh mã.

---

## 🔒 Quyền (Permissions)

| Quyền | Mục đích |
| :--- | :--- |
| `INTERNET` | Gọi REST API |
| `ACCESS_NETWORK_STATE` | Theo dõi trạng thái mạng: banner offline, đồng bộ lại khi có mạng |
| `POST_NOTIFICATIONS` | Nhắc việc, tóm tắt buổi sáng, tổng kết tuần, hết giờ tập trung (Android 13+) — **xin đúng lúc**, không xin khi mở app |
| `VIBRATE` | Rung khi có thông báo nhắc việc |
