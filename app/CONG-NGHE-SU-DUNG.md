# Công nghệ & Kỹ thuật Android sử dụng

Tài liệu liệt kê các công nghệ, thư viện và **kỹ thuật lập trình Android** được áp dụng trong TaskFlow AI — ứng dụng quản lý công việc cá nhân tích hợp AI. Trọng tâm là phần client Android: kiến trúc **offline-first** (Room là nguồn dữ liệu duy nhất, đồng bộ nền bằng WorkManager) và việc khai thác các API đặc thù của nền tảng (Service, ContentProvider, cảm biến, camera + ML, vị trí, widget, Quick Settings, thông báo nâng cao, giao diện thích ứng).

---

## 0. Bản đồ nhanh: tính năng ↔ kỹ thuật Android

### Bốn thành phần chính của Android

| Thành phần | Trong app |
| :--- | :--- |
| **Activity** | `MainActivity` (single-activity + NavHost, là `FragmentActivity` cho BiometricPrompt), `AlarmActivity` (báo thức trên màn khóa), `SnoozeActivity` (hộp chọn hoãn trong suốt), `TasksWidgetConfigActivity` (cấu hình widget) |
| **Service** | `FocusService` — **Foreground Service** (`specialUse`) đếm ngược Pomodoro; `FocusTileService`, `QuickAddTileService` — **TileService** (Quick Settings) |
| **BroadcastReceiver** | `NotificationActionReceiver` (Hoàn thành / Hoãn / trả lời trên thông báo), `QuickAddReceiver`, `HabitActionReceiver`, `AlarmReceiver`, `BootReceiver` (đặt lại nhắc / geofence sau khởi động), `GeofenceReceiver`, các `GlanceAppWidgetReceiver` |
| **ContentProvider** | **Cung cấp:** `FileProvider` (chia sẻ tệp đính kèm, tệp sao lưu qua `content://`). **Dùng:** `CalendarContract` (lịch của máy), Storage Access Framework (`CreateDocument` / `OpenDocument`), Photo Picker |

### Tính năng → API

| Tính năng | Kỹ thuật / API |
| :--- | :--- |
| Pomodoro chạy nền, đếm ngược trên màn khóa | Foreground Service + ongoing notification + `setUsesChronometer` / `setChronometerCountDown` |
| Tự bật "Không làm phiền" khi tập trung | `NotificationManager.setInterruptionFilter` + quyền `ACCESS_NOTIFICATION_POLICY` |
| Ô "Tập trung" / "Thêm việc" trong cài đặt nhanh | `TileService`, `startActivityAndCollapse(PendingIntent)` (Android 14), `StatusBarManager.requestAddTileService` (Android 13) |
| Thêm việc / hoãn ngay trên thông báo | `RemoteInput` (direct reply) + `setChoices` (gợi ý trả lời nhanh) |
| Báo thức toàn màn hình cho việc quan trọng | `AlarmManager.setAlarmClock`, full-screen intent, `showWhenLocked` / `turnScreenOn` |
| Nhắc việc, nhắc dai, giờ yên lặng, nhắc thói quen | WorkManager (`OneTimeWorkRequest`, unique work, `APPEND_OR_REPLACE`, tag) |
| Nhắc theo địa điểm (đến / rời) | Geofencing API + FusedLocationProvider, quyền vị trí 2 bước (foreground → background) |
| Chụp giấy / bảng trắng thành việc | ML Kit **Document Scanner** (không cần quyền CAMERA) + ML Kit **Text Recognition** chạy trên máy |
| Đính kèm ảnh vào việc | **Photo Picker** (`PickMultipleVisualMedia`, không cần quyền bộ nhớ), bộ nhớ riêng app, `FileProvider` |
| Hiện lịch Google của máy, AI tránh giờ bận | `CalendarContract.Instances` (ContentResolver) + quyền `READ_CALENDAR`; thêm sự kiện bằng `Intent.ACTION_INSERT` |
| Úp máy để bắt đầu tập trung, lắc để hoàn tác | `SensorManager` (gia tốc kế) đăng ký theo vòng đời composable |
| Widget màn hình chính | **Jetpack Glance** (Compose cho widget), `ActionCallback`, cấu hình theo từng widget (`PreferencesGlanceStateDefinition`), màu Material You |
| Khóa ứng dụng | `BiometricPrompt` (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) |
| Tablet / màn ngang / máy gập | `WindowSizeClass` → `NavigationRail` + giao diện danh sách \| chi tiết |
| Thẻ việc "nở" thành màn chi tiết, cử chỉ quay lại xem trước | Shared element transition (`SharedTransitionLayout`, `sharedBounds`), Predictive Back (`enableOnBackInvokedCallback`) |
| Sao lưu / xuất CSV / khôi phục | Storage Access Framework + `FileProvider` + bảng Chia sẻ |
| Hỗ trợ TalkBack | Semantics: `customActions` thay cho vuốt / kéo thả, `stateDescription`, `heading`, `contentDescription` |
| Nhập bằng giọng nói | `RecognizerIntent` (theo ngôn ngữ của app) qua Activity Result API |
| Song ngữ Việt – Anh, đổi ngôn ngữ ngay trong app | String resources (`values/` tiếng Anh, `values-vi/` tiếng Việt, `plurals`, `string-array`), **per-app language**: `LocaleManager` (Android 13+, kèm `locales_config.xml` → hiện trong Cài đặt hệ thống) và `createConfigurationContext` trong `attachBaseContext` (Android 10–12) |
| Khởi động nhanh | Baseline Profile + ProfileInstaller, đo bằng Macrobenchmark |

---

## 1. Ngôn ngữ & Nền tảng

| Hạng mục | Giá trị |
| :--- | :--- |
| Ngôn ngữ | **Kotlin** 2.2.10 |
| Build tool | **Android Gradle Plugin (AGP)** 9.2.1 + Gradle 9.4 (Kotlin DSL) |
| Quản lý dependency | **Version Catalog** (`gradle/libs.versions.toml`) |
| Annotation processor | **KSP** 2.2.10-2.0.2 |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 29 (Android 10) |
| Module | `:app` (ứng dụng), `:macrobenchmark` (đo hiệu năng + sinh Baseline Profile) |
| Build type | `debug`, `release` (R8 + shrink resources), `benchmark` (như release, ký debug, không đổi tên lớp) |

---

## 2. Giao diện (UI)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Jetpack Compose** (BOM 2026.02.01) | Toàn bộ UI khai báo, không dùng XML layout |
| **Material 3** | `Scaffold`, `ModalBottomSheet`, `SwipeToDismissBox`, `SegmentedButton`, `NavigationRail`, `PullToRefreshBox`, `DatePicker`… |
| **material3-window-size-class** | Giao diện thích ứng theo kích thước cửa sổ |
| **Compose Animation** | `animateItem()`, **shared element transition** giữa danh sách và chi tiết |
| **Jetpack Glance** 1.1.1 | Widget viết bằng Compose |
| **SplashScreen API**, **Adaptive icon** (monochrome) | Màn khởi động, themed icon Android 13+ |

### Kỹ thuật UI áp dụng
- **Theme Sáng / Tối / Theo hệ thống**, **edge-to-edge** + WindowInsets.
- **Giao diện thích ứng**: Compact → thanh dưới; Medium → NavigationRail; Expanded → Rail + tab Việc làm chia đôi *danh sách | chi tiết*. Màn chi tiết nhúng được vào khung phải nhờ ViewModel tạo với `CreationExtras` (`DEFAULT_ARGS_KEY` chứa `taskId`).
- **Kéo thả tự viết** (`ui/components/DragDrop.kt`): vùng thả ghi tọa độ gốc màn hình (`boundsInRoot`), dùng chung cho lịch tuần, ma trận Eisenhower, bảng Kanban.
- **Cử chỉ**: vuốt phải hoàn thành, vuốt trái hoãn, nhấn giữ chọn nhiều, kéo thả, lắc máy để hoàn tác.
- **Undo pattern**: thao tác thực hiện ngay + Snackbar "Hoàn tác".
- **LazyColumn tối ưu**: `key` ổn định + `contentType`; tính toán nặng trong ViewModel trên `Dispatchers.Default`.
- **Song ngữ (i18n)**: không còn chữ hiển thị nào viết cứng trong code. Composable dùng `stringResource` / `pluralStringResource` / `stringArrayResource`; code ngoài Compose (ViewModel, Worker, thông báo, widget, logic tạo nhãn) dùng `str()` / `plural()` trong `i18n/AppStrings.kt` (đọc tài nguyên qua Context của Application). Ngày tháng định dạng theo mẫu lấy từ tài nguyên (`T6 10/07` ↔ `Fri, Jul 10`), số nhiều đúng ngữ pháp tiếng Anh (`1 task` / `3 tasks`). Đổi ngôn ngữ → Activity được tạo lại và **bỏ các ViewModel cũ** (`viewModelStore.clear()` trước `super.onCreate`) để chữ ViewModel đã tính sẵn cũng đổi theo; kênh thông báo, widget và thông báo cố định được làm mới. Bộ phân tích thêm nhanh hiểu cả câu tiếng Việt lẫn tiếng Anh ("mai 3h chiều !cao" / "tomorrow 3pm !high"). App Bundle tắt tách gói theo ngôn ngữ để đổi ngôn ngữ trong app luôn đủ chuỗi.
- **Trợ năng**: mọi thao tác cử chỉ đều có `CustomAccessibilityAction` tương đương (Hoàn thành, Hoãn, Chuyển sang mục / ô khác…); tiêu đề nhóm có `heading()`.

---

## 3. Kiến trúc & Điều hướng

| Công nghệ | Vai trò |
| :--- | :--- |
| **Navigation Compose** 2.8.5 | Single-activity, tham số route, deep link nội bộ (`AppIntents`) |
| **ViewModel** + **SavedStateHandle** | MVVM, sống sót qua xoay màn và khi tiến trình bị giết |
| **Lifecycle** 2.10 | `collectAsStateWithLifecycle`, `LifecycleEventEffect(ON_START / ON_RESUME)` |

### Kiến trúc **MVVM + Offline-first**

```
View (Composable) ──hành động──► ViewModel ──ghi──► Repository ──► Room (đánh dấu dirty)
       ▲                              │                               │
       └──────── StateFlow ◄── combine/stateIn ◄──── Flow ◄───────────┘
                                                                      │ WorkManager
                                                      SyncEngine ◄────┘ ⇄ Server (REST)
```

- **View** (`ui/screens/`): chỉ render state và gửi hành động.
- **ViewModel** (`ui/viewmodel/`): `StateFlow<…UiState>` dẫn xuất từ Flow của Room bằng `combine` + `stateIn(WhileSubscribed(5000))`; sự kiện một lần qua `SharedFlow`.
- **Domain** (`domain/`): logic thuần Kotlin có unit test — `TaskListLogic`, `RecurrenceRules`, `QuickAddParser` (phân tích câu tiếng Việt), `HabitLogic`, `Scheduling` (giờ yên lặng, hoãn, Eisenhower), `BoardLogic`, `TagRules`, `ScanTextParser`, `SnoozeText`, `FocusStats`…
- **Repository** (`data/repository/`): đọc = Flow; ghi = transaction Room + side effect (`TaskEffects`: nhắc việc, widget, geofence, xin đồng bộ).
- **Sync** (`data/sync/`): `SyncEngine` đẩy / kéo task, danh mục + cài đặt danh sách, thói quen + check-in, phiên tập trung; bỏ qua êm phần server cũ chưa hỗ trợ (404).
- **Dữ liệu chỉ trên máy** (không đồng bộ vì riêng tư): tệp đính kèm, địa điểm và nhắc theo địa điểm.

### Dependency Injection — ServiceLocator (manual DI)
`di/ServiceLocator.kt` cung cấp singleton `by lazy`; ViewModel nhận phụ thuộc qua `viewModelFactory { initializer { … } }`. (Hilt Gradle plugin chưa tương thích AGP 9 — lỗi *"Android BaseExtension not found"* — nên dùng DI thủ công.)

---

## 4. Mạng (Networking)

| Công nghệ | Vai trò |
| :--- | :--- |
| **Retrofit 2** + **OkHttp** + **Gson** | REST API kiểu interface |
| **Interceptor / Authenticator** | Gắn Bearer token; tự làm mới token khi 401 (thread-safe) |
| **EncryptedSharedPreferences** | Access + refresh token (khóa trong Android Keystore) |
| **Network Security Config** | Release chỉ HTTPS |

Mọi request đồng bộ **idempotent** (`PUT /{resource}/{id}` tạo nếu chưa có, `DELETE` lặp lại vẫn thành công) nên gửi lại sau khi mất mạng luôn an toàn.

---

## 5. Bất đồng bộ (Asynchronous)

- **Coroutines**: `viewModelScope`, `goAsync()` + coroutine trong BroadcastReceiver, `suspendCancellableCoroutine` bọc Task của Play services / ML Kit.
- **Flow**: `combine`, `debounce` + `distinctUntilChanged` (tìm kiếm), `flatMapLatest`, `flowOn`, `stateIn`, `callbackFlow` (theo dõi mạng), `first { }` (FocusService chờ phiên đổi).
- **Mutex**: chỉ một lần đồng bộ tại một thời điểm.

---

## 6. Lưu trữ cục bộ

| Công nghệ | Vai trò |
| :--- | :--- |
| **Room** 2.7.1 (KSP) | Nguồn dữ liệu duy nhất — **version 8**, 12 bảng |
| **Room Paging** + **Paging 3** | Lịch sử hoàn thành tải dần từng trang (`PagingSource` do Room sinh, `insertSeparators` chèn tiêu đề ngày, `cachedIn`) |
| **DataStore (Preferences)** | Cài đặt trên máy (thay SharedPreferences, tự chuyển dữ liệu cũ bằng `SharedPreferencesMigration`) |
| **EncryptedSharedPreferences** | Phiên đăng nhập |
| **Bộ nhớ riêng của app** (`filesDir/attachments`) | Tệp đính kèm |

### Kỹ thuật áp dụng
- **Bảng**: `tasks`, `subtasks`, `categories`, `list_meta`, `chat_messages`, `sync_state`, `habits`, `habit_checkins`, `focus_sessions`, `attachments`, `places`, `task_locations`.
- **Cờ đồng bộ** `isDirty` + `localVersion` + bia mộ `isDeleted` — không bao giờ mất thay đổi khi người dùng sửa tiếp lúc đang gửi.
- **Khóa ngoại `ON DELETE CASCADE`**, **`@Upsert`** (thay `REPLACE` vốn xóa rồi chèn → kích hoạt CASCADE).
- **Subquery trong SQL** cho tiến độ checklist và "bước con đến hạn sớm nhất".
- **Migration có kiểm thử** 4→5→6→7→8, mỗi bước chỉ thêm cột / bảng; schema export ra `app/schemas/` và `MigrationTest` so khớp trên SQLite thật.

---

## 7. Tác vụ nền

| Công nghệ | Áp dụng |
| :--- | :--- |
| **WorkManager** | Đồng bộ (unique + debounce + backoff + periodic 30 phút), nhắc việc (nhiều lời nhắc / việc bằng tag), nhắc dai, giờ yên lặng, tóm tắt sáng, tổng kết tuần, nhắc thói quen, hết giờ tập trung |
| **AlarmManager** | Báo thức chính xác cho việc ưu tiên Cao |
| **Foreground Service** | Phiên tập trung (`FOREGROUND_SERVICE_SPECIAL_USE`); không mở được từ nền (Android 12+) thì WorkManager vẫn báo hết giờ |
| **Geofencing** | Nhắc khi đến / rời địa điểm; đăng ký lại khi khởi động máy |

> Job được hẹn từ chính worker đang chạy dùng `APPEND_OR_REPLACE` — `REPLACE` sẽ hủy luôn worker hiện tại.

---

## 8. Camera, ML, cảm biến, vị trí

| Công nghệ | Áp dụng |
| :--- | :--- |
| **ML Kit Document Scanner** (Play services) | Giao diện chụp tài liệu tự bắt mép / khử nghiêng; app không cần quyền CAMERA |
| **ML Kit Text Recognition** (Latin, chạy trên máy) | Nhận chữ tiếng Việt có dấu, offline → tách dòng thành việc / chép vào mô tả |
| **SensorManager** (gia tốc kế) | Úp máy để bắt đầu Pomodoro, lắc để Hoàn tác |
| **Fused Location + Geofencing** | Lưu "Nhà", "Công ty" từ vị trí hiện tại; nhắc theo vùng |
| **SpeechRecognizer** (`RecognizerIntent`) | Nhập việc bằng giọng nói |

---

## 9. Thư viện

| Thư viện | Mục đích |
| :--- | :--- |
| `androidx.glance` 1.1.1 | Widget Compose |
| `androidx.datastore:datastore-preferences` 1.1.7 | Cài đặt |
| `androidx.paging` 3.3.6 + `room-paging` | Danh sách dài |
| `com.google.mlkit:text-recognition` · `play-services-mlkit-document-scanner` | Nhận chữ · quét tài liệu |
| `play-services-location` 21.3 | Vị trí + geofence |
| `androidx.biometric` 1.1.0 | Khóa ứng dụng |
| `androidx.profileinstaller` · `androidx.benchmark:benchmark-macro-junit4` | Baseline Profile · đo hiệu năng |
| `sh.calvin.reorderable` 2.4.3 | Kéo-thả sắp xếp trong `LazyColumn` |

---

## 10. Quyền (Permissions)

| Quyền | Mục đích | Xin khi nào |
| :--- | :--- | :--- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Gọi API, biết lúc có mạng để đồng bộ | — |
| `POST_NOTIFICATIONS` | Mọi thông báo (Android 13+) | Lúc người dùng đặt nhắc lần đầu |
| `VIBRATE` | Rung nhắc việc | — |
| `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT` | Báo thức đúng phút, hiện trên màn khóa | Khi bật báo thức trong Thiết lập (mở trang cài đặt hệ thống) |
| `RECEIVE_BOOT_COMPLETED` | Đặt lại nhắc / geofence sau khởi động | — |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Phiên tập trung | — |
| `ACCESS_NOTIFICATION_POLICY` | Bật "Không làm phiền" khi tập trung | Khi bật tùy chọn (trang cài đặt hệ thống) |
| `READ_CALENDAR` | Đọc lịch của máy | Khi bật "📆 Lịch của máy" |
| `ACCESS_FINE_LOCATION` → `ACCESS_BACKGROUND_LOCATION` | Nhắc theo địa điểm | Khi đặt nhắc địa điểm (2 bước, có giải thích trước bước nền) |

Không cần quyền camera / bộ nhớ: Document Scanner, Photo Picker và Storage Access Framework do hệ thống cung cấp giao diện, app chỉ nhận đúng tệp người dùng chọn.

---

## 11. Kiểm thử & chất lượng

| Loại | Công cụ | Nội dung |
| :--- | :--- | :--- |
| **Unit test (JVM)** | JUnit 4, Mockito-Kotlin, coroutines-test | Domain (lặp, phân tích câu, thói quen, Kanban, nhãn, bộ lọc, quét chữ, câu hoãn, CSV, ngày của sự kiện lịch…), ViewModel (Hoàn tác, chọn nhiều, debounce, kéo thả), song ngữ (hai file chuỗi cùng khóa và cùng số tham số, nhãn tiếng Anh, câu thêm nhanh tiếng Anh) — 125 test. Test nạp thẳng `values-vi/strings.xml` (`TestStrings`) vì JVM không có Android Resources |
| **Instrumented test** | AndroidJUnit4 + Room in-memory + `FakeServer : ApiService` | Giao thức đồng bộ: offline, mất mạng giữa chừng, máy khác sửa, thói quen, server cũ không có API |
| **Migration test** | `MigrationTestHelper` | Từng bước 4→5→6→7→8 trên SQLite thật |
| **Compose UI test** | `createComposeRule` | Thẻ việc + hành động TalkBack, thanh chọn nhiều, thẻ thói quen, phân tích câu khi gõ (so với chuỗi tài nguyên → chạy đúng ở mọi ngôn ngữ máy) |
| **Ngôn ngữ riêng của app** | `LocaleManager` trên máy thật / giả lập | Đổi vi ↔ en → chuỗi đọc ngoài Compose đổi theo |
| **Macrobenchmark** | `:macrobenchmark` | Khởi động lạnh có / không Baseline Profile, độ mượt khi cuộn; `BaselineProfileGenerator` sinh `app/src/main/baseline-prof.txt` |
| **CI** | GitHub Actions (`.github/workflows/ci.yml`) | Go vet + test; unit test + build debug/release; test trên giả lập (android-emulator-runner) |

Lệnh:
```bash
./gradlew :app:testDebugUnitTest                 # unit test
./gradlew :app:connectedDebugAndroidTest         # cần giả lập / máy thật
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR   # đo trên giả lập
```
> Số đo Macrobenchmark trên giả lập (GPU phần mềm) chỉ mang tính tham khảo — nên đo trên máy thật. Bài đo cuộn danh sách cần sẵn một tài khoản đã đăng nhập trên máy đo.

---

## 12. Backend (tóm tắt — ngoài phạm vi môn Android)

Backend **Go** theo Clean Architecture, PostgreSQL + Qdrant + Google Gemini. Cung cấp API đồng bộ idempotent cho task, danh sách, thói quen, phiên tập trung, và lập lịch AI tránh giờ bận lấy từ lịch của máy. Chi tiết tại [`../backend/README.md`](../backend/README.md).
