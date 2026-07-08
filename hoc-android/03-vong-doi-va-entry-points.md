# Bài 03 — Vòng đời & Điểm khởi động

Bài này trả lời câu hỏi backend-dev hay vướng nhất: **"App bắt đầu chạy từ đâu, và ai điều khiển nó?"**

## 1. AndroidManifest.xml — "bản khai báo" của app

Server của bạn có `main()` tự gọi các thứ. App Android thì ngược lại: bạn **khai báo** các thành phần trong [AndroidManifest.xml](../app/app/src/main/AndroidManifest.xml), rồi **hệ điều hành mới là người gọi chúng**. Manifest giống một file cấu hình khai báo cho OS biết:

```xml
<manifest ...>
    <!-- 1. Quyền app cần xin (như scope/permission) -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".TodoApplication"        <!-- 2. Lớp Application tùy biến -->
        android:theme="@style/Theme.TodoApplication" ... >

        <!-- 3. Activity khởi động (màn hình đầu tiên) -->
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 4. Các điểm vào khác: receiver nhận sự kiện, service chạy nền -->
        <receiver android:name=".data.notifications.NotificationActionReceiver" .../>
        <receiver android:name=".widget.TasksWidgetProvider" .../>
        <service  android:name=".widget.TasksWidgetService" .../>
    </application>
</manifest>
```

Bốn thứ cần hiểu:
- **`<uses-permission>`** — quyền cần xin. `INTERNET` để gọi API; `POST_NOTIFICATIONS` để nhắc việc (Android 13+ còn phải xin *lúc chạy* — xem mục 4).
- **`android:name=".TodoApplication"`** — chỉ định lớp Application tùy biến (mục 2).
- **`<intent-filter>` với `MAIN` + `LAUNCHER`** — dòng này nói với OS: *"Đây là màn hình mở ra khi người dùng chạm icon."* Chính là "điểm vào" của app.
- **`<receiver>` / `<service>`** — các điểm vào phụ. App này có Receiver để xử lý nút "Hoàn thành/Hoãn" trên thông báo, và Service+Provider cho **widget** trên màn hình chính. Chúng có thể được OS đánh thức **ngay cả khi app đang đóng**.

> 🔑 Ý niệm quan trọng: một app Android **có nhiều điểm vào**, không chỉ một. OS gọi vào bất kỳ điểm nào tùy tình huống (mở app, chạm thông báo, đến giờ nhắc, cập nhật widget...). Đây là khác biệt tư duy lớn so với server một-process.

## 2. `Application` — bootstrap chạy MỘT LẦN

[TodoApplication.kt](../app/app/src/main/java/com/example/todoapplication/TodoApplication.kt) là object sống *lâu nhất* — được tạo một lần khi process app sinh ra, trước cả màn hình đầu tiên. Đây là nơi khởi tạo những gì "toàn cục":

```kotlin
class TodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)   // dựng DI container (bài 08)
        ThemeController.init(this)   // nạp cài đặt theme
        CategoryStore.init(this)     // nạp danh mục
    }
}
```
Đây chính là **`func main()` / hàm bootstrap** của app. Đặt ở đây những thứ cần sẵn sàng trước mọi màn hình. Lưu ý `this` (Application) là một **`Context`** đặc biệt sống suốt đời app — dùng nó khi cần context lâu dài (mở DB, đọc file), tránh dùng context của Activity (chết theo màn hình).

### "Context" là gì?
`Context` là "cây cầu" nối code của bạn với hệ điều hành Android: để mở file, truy cập DB, xin quyền, hiện thông báo... Bạn sẽ thấy `context: Context` được truyền khắp nơi (vd [SessionManager.kt:9](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L9)). Coi nó như "handle tới OS". Có 2 loại chính: **Application context** (sống lâu) và **Activity context** (sống theo màn hình).

## 3. `Activity` — một "màn hình"/cửa sổ

[MainActivity.kt](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt) là điểm vào UI. Một Activity ≈ một cửa sổ mà OS quản lý. Điểm mấu chốt: **bạn không kiểm soát khi nào nó sống/chết** — OS quyết định.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {   // OS gọi khi tạo màn
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {                    // ← "toàn bộ UI vẽ bằng Compose bắt đầu ở đây"
            TodoApplicationTheme { ... }
        }
    }
}
```

### Vòng đời (lifecycle) — thứ khác biệt nhất với server
OS gọi các "hàm callback" theo trạng thái màn hình:

```
onCreate()  → màn được tạo (dựng UI, đọc tham số)
onStart()   → sắp hiển thị
onResume()  → đang ở tiền cảnh, người dùng tương tác được
   ⋮  (người dùng dùng app)
onPause()   → mất tiêu điểm (có dialog, chuyển app)
onStop()    → không còn nhìn thấy
onDestroy() → bị hủy (thoát màn, HOẶC OS thu hồi RAM)
```

**Vì sao bạn phải quan tâm?** Vì OS có thể **hủy và tạo lại** Activity bất cứ lúc nào — kinh điển là khi **xoay màn hình**. Nếu bạn giữ dữ liệu ngay trong Activity, xoay một cái là **mất sạch**. Đây chính là lý do tồn tại **ViewModel**: nó **sống sót qua các lần tạo lại đó** (bài 05). Nhờ vậy danh sách task không bị tải lại mỗi lần xoay máy.

> 💡 App này chỉ có **một Activity** ([MainActivity](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt)) và mọi "màn hình" (Login, TaskList, Stats...) là các **Composable** chuyển đổi bên trong nó qua Navigation (bài 09). Đây là kiến trúc hiện đại "single-activity" — nhẹ và nhất quán.

## 4. Xin quyền lúc chạy (runtime permission)

Từ Android 6 (và siết thêm ở 13), các quyền "nhạy cảm" phải được **người dùng đồng ý lúc chạy**, khai báo trong manifest thôi chưa đủ. Xem [MainActivity.kt:41](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L41):

```kotlin
val notifPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { /* người dùng chọn xong: nếu từ chối thì đơn giản không nhắc */ }

LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // Android 13+
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```
Mẫu này rất phổ biến: **hỏi phiên bản OS** (`Build.VERSION.SDK_INT >= ...`) rồi mới làm việc chỉ có ở bản mới. Bạn sẽ gặp kiểu "phân nhánh theo API level" này thường xuyên vì Android phải chạy trên rất nhiều phiên bản (nhớ `minSdk = 29`).

## 5. Các điểm vào chạy nền (đọc lướt, chi tiết ở bài 10)
- **`BroadcastReceiver`** — [NotificationActionReceiver](../app/app/src/main/java/com/example/todoapplication/data/notifications/NotificationActionReceiver.kt): OS đánh thức khi người dùng bấm nút trên thông báo. Chạy ngắn rồi tắt.
- **Widget** — [TasksWidgetProvider](../app/app/src/main/java/com/example/todoapplication/widget/TasksWidgetProvider.kt) + [TasksWidgetService](../app/app/src/main/java/com/example/todoapplication/widget/TasksWidgetService.kt): vẽ danh sách việc ngay trên màn hình chính điện thoại.
- **WorkManager** (bài 10): lên lịch chạy nền đáng tin cậy — dùng để bắn nhắc việc đúng giờ kể cả khi app đóng.

## 6. Tự kiểm tra
1. Ai gọi `onCreate()` của Activity — bạn hay hệ điều hành?
2. Khi xoay màn hình, Activity bị `onDestroy()` rồi `onCreate()` lại. Dữ liệu để trong Activity sẽ ra sao, và ViewModel giải quyết vấn đề đó thế nào?
3. Vì sao khai báo `POST_NOTIFICATIONS` trong manifest vẫn chưa đủ để gửi thông báo trên Android 13?
4. App này có mấy Activity? Các "màn hình" khác được hiện thực bằng gì?
5. Khi nào dùng Application context thay vì Activity context?

➡️ Tiếp theo: [Bài 04 — Jetpack Compose (UI)](./04-jetpack-compose.md)
