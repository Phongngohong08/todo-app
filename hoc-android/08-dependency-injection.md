# Bài 08 — Dependency Injection (ServiceLocator)

Khái niệm DI thì bạn đã nắm chắc từ backend (wire, fx, constructor injection). Bài này ngắn: chỉ cần thấy app này *hiện thực* DI ra sao và **vì sao chọn cách thủ công**.

## 1. Vấn đề

`TaskListViewModel` cần 4 repository. Mỗi repository lại cần `ApiService`, `Context`, DB... Nếu mỗi nơi tự `new` phụ thuộc của mình thì:
- Trùng lặp, khó thay đổi.
- Không chia sẻ được instance (mỗi chỗ tạo một `Retrofit` riêng — lãng phí).
- Không mock được khi test.

Giải pháp muôn thuở: **tạo phụ thuộc ở một chỗ, rồi *tiêm* vào nơi cần** (qua constructor).

## 2. Cách app làm: `object ServiceLocator`

App dùng mẫu **Service Locator** — một singleton toàn cục giữ sẵn mọi phụ thuộc. Xem [ServiceLocator.kt](../app/app/src/main/java/com/example/todoapplication/di/ServiceLocator.kt):

```kotlin
object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {                 // gọi 1 lần trong TodoApplication.onCreate()
        appContext = context.applicationContext
    }

    // ── Hạ tầng ──
    val sessionManager: SessionManager by lazy { SessionManager(appContext) }
    val apiService: ApiService        by lazy { NetworkClient.getApiService(sessionManager) }
    val database: AppDatabase         by lazy { AppDatabase.get(appContext) }

    // ── Repository ──
    val taskRepository: TaskRepository by lazy { TaskRepository(apiService, appContext) }
    val authRepository: AuthRepository by lazy { AuthRepository(apiService, sessionManager) }
    val statsRepository: StatsRepository by lazy { StatsRepository(apiService) }
    val subtaskRepository: SubtaskRepository by lazy { SubtaskRepository(appContext) }
    ...
}
```

Ba ý cần hiểu:

- **`object`** → singleton do ngôn ngữ bảo đảm (bài 01). Chỉ có đúng một `ServiceLocator` toàn app.
- **`by lazy { }`** → **khởi tạo lười**: phụ thuộc chỉ được tạo *lần đầu ai đó truy cập*, sau đó tái dùng. Nhờ vậy `Retrofit`, `Room` chỉ dựng một lần và được chia sẻ. Bạn không tốn công tạo `StatsRepository` nếu người dùng chẳng mở màn Thống kê.
- **Thứ tự phụ thuộc tự nối:** `taskRepository` dùng `apiService`, `apiService` dùng `sessionManager`. Khai báo kiểu này là một **đồ thị phụ thuộc** — chính là thứ mà wire/fx dựng cho bạn ở backend, ở đây làm tay.

## 3. Khởi động ở đâu?

`ServiceLocator.init(this)` được gọi một lần trong [TodoApplication.onCreate()](../app/app/src/main/java/com/example/todoapplication/TodoApplication.kt#L15) — điểm bootstrap sớm nhất (bài 03). Sau dòng đó, mọi nơi trong app dùng được `ServiceLocator.taskRepository`.

## 4. Đường dây tới ViewModel

Nối lại bài 05: ViewModel *nhận* phụ thuộc qua constructor, và **Factory** là nơi kéo chúng từ ServiceLocator:
```kotlin
// trong TaskListViewModel.companion object
val Factory = viewModelFactory {
    initializer {
        TaskListViewModel(
            ServiceLocator.taskRepository,
            ServiceLocator.subtaskRepository,
            ServiceLocator.aiRepository,
            ServiceLocator.sessionManager
        )
    }
}
```
Vậy chuỗi đầy đủ là:
```
TodoApplication.onCreate() → ServiceLocator.init()
                                     │ (giữ sẵn repo)
Screen: viewModel(factory = ...Factory)
                                     │ Factory kéo repo từ ServiceLocator
                             → new ViewModel(repo...)
```

## 5. Vì sao KHÔNG dùng Hilt?

Hilt là framework DI chính thống của Google (dùng annotation `@Inject`, sinh code tự động — "xịn" hơn). Nhưng comment ngay trong [ServiceLocator.kt](../app/app/src/main/java/com/example/todoapplication/di/ServiceLocator.kt#L18) ghi rõ lý do:

> *Vì Hilt Gradle plugin chưa tương thích với AGP 9 (lỗi "Android BaseExtension not found"), ta tự cung cấp các phụ thuộc dạng singleton lười.*

Bài học thực tế: đôi khi công cụ "chuẩn" chưa tương thích phiên bản, và **ServiceLocator thủ công là giải pháp thay thế hoàn toàn hợp lệ** — ít "ma thuật", dễ hiểu, không phụ thuộc code-gen. Với app cỡ này, nó thừa sức. Khi hệ sinh thái ổn định, có thể chuyển sang Hilt sau mà không đụng tầng UI/ViewModel (vì chúng chỉ phụ thuộc *interface* repository, không quan tâm ai tạo).

## 6. Đánh giá nhanh: Service Locator vs Constructor Injection thuần

| | Service Locator (ở đây) | DI framework (Hilt) |
|---|---|---|
| Độ phức tạp | Rất thấp, dễ đọc | Cao hơn, cần học annotation |
| Ma thuật lúc build | Không | Có (code-gen) |
| Test | Ổn (thay instance trong locator) | Rất tốt |
| Rủi ro | Phụ thuộc toàn cục ẩn | Ít hơn |

Với người mới, ServiceLocator ở đây là điểm khởi đầu **dễ hiểu nhất** để nắm khái niệm DI.

## 7. Tự kiểm tra
1. `by lazy` giúp gì về hiệu năng và chia sẻ instance?
2. Truy vết: `ServiceLocator.taskRepository` được tạo lúc nào, và ai "tiêm" nó vào `TaskListViewModel`?
3. Vì sao dự án này dùng ServiceLocator thay vì Hilt?
4. Nếu muốn test `TaskListViewModel` với một `TaskRepository` giả, bạn tận dụng điều gì trong thiết kế?

➡️ Tiếp theo: [Bài 09 — Điều hướng (Navigation)](./09-navigation.md)
