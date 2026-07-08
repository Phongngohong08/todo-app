# Học Android qua chính dự án này 🎓

> Dành cho **lập trình viên backend** muốn học Android mà không phải đọc lý thuyết suông.
> Mỗi bài học đều trỏ thẳng vào code thật trong thư mục [`app/`](../app/) của bạn.

## Bạn đang đứng ở đâu?

Bạn đã biết: HTTP, REST, JSON, database, middleware, dependency injection, service/repository layering (bạn có cả một backend Go trong [`backend/`](../backend/)).

Bạn **chưa** biết: một app Android được cấu tạo thế nào, code chạy ra sao, UI vẽ bằng gì.

Tin tốt: **80% kiến thức backend của bạn dùng lại được**. App này có kiến trúc gần như một server thu nhỏ:

| Khái niệm backend bạn đã biết | Tương đương trong app Android này | File thật |
|---|---|---|
| HTTP handler / controller | **Screen** (Composable) | [TaskListScreen.kt](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt) |
| Service layer (business logic) | **ViewModel** | [TaskListViewModel.kt](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt) |
| Repository layer | **Repository** | [TaskRepository.kt](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt) |
| HTTP client gọi service khác | **Retrofit ApiService** | [ApiService.kt](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt) |
| Middleware (auth, logging) | **OkHttp Interceptor / Authenticator** | [NetworkClient.kt](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt) |
| ORM / query builder | **Room DAO** | [Daos.kt](../app/app/src/main/java/com/example/todoapplication/data/local/Daos.kt) |
| DI container (wire.go, fx...) | **ServiceLocator** | [ServiceLocator.kt](../app/app/src/main/java/com/example/todoapplication/di/ServiceLocator.kt) |
| main() / bootstrap | **Application + Activity** | [TodoApplication.kt](../app/app/src/main/java/com/example/todoapplication/TodoApplication.kt) |
| Router (mux, gin engine) | **NavHost** | [MainActivity.kt](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt) |
| Cron job / worker queue | **WorkManager** | [ReminderWorker.kt](../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderWorker.kt) |

Điểm khác biệt lớn nhất bạn cần làm quen: **UI vẽ theo kiểu "khai báo" (declarative)** và **mọi thứ đều bất đồng bộ, gắn với vòng đời (lifecycle)**. Hai bài 04 và 03 sẽ lo phần này.

## Lộ trình học (đọc theo thứ tự)

| # | Bài | Nội dung | Ưu tiên |
|---|-----|----------|---------|
| 00 | [Tổng quan & môi trường](./00-tong-quan-va-moi-truong.md) | Android là gì, Gradle, cấu trúc project, cách build & chạy | ⭐ Bắt buộc |
| 01 | [Kotlin cho dev backend](./01-kotlin-cho-dev-backend.md) | Kotlin trong 1 bài, đối chiếu với Go/Java | ⭐ Bắt buộc |
| 02 | [Kiến trúc tổng thể (MVVM)](./02-kien-truc-tong-the.md) | Luồng dữ liệu chạy xuyên suốt app | ⭐ Bắt buộc |
| 03 | [Vòng đời & điểm khởi động](./03-vong-doi-va-entry-points.md) | Application, Activity, Manifest, lifecycle | ⭐ Bắt buộc |
| 04 | [Jetpack Compose (UI)](./04-jetpack-compose.md) | Vẽ giao diện declarative, state, recomposition | ⭐ Bắt buộc |
| 05 | [ViewModel & quản lý State](./05-viewmodel-va-state.md) | StateFlow, UiState, luồng dữ liệu một chiều | ⭐ Bắt buộc |
| 06 | [Networking (Retrofit/OkHttp)](./06-networking-retrofit.md) | Gọi API, interceptor, tự động refresh token | ⭐ Bắt buộc |
| 07 | [Lưu trữ cục bộ (Room & Prefs)](./07-luu-tru-cuc-bo.md) | DB offline, cache, lưu token mã hóa | 🔸 Nên đọc |
| 08 | [Dependency Injection](./08-dependency-injection.md) | ServiceLocator, tại sao không dùng Hilt | 🔸 Nên đọc |
| 09 | [Điều hướng (Navigation)](./09-navigation.md) | Chuyển màn hình, truyền tham số, back stack | 🔸 Nên đọc |
| 10 | [Chạy nền & Thông báo](./10-background-va-thong-bao.md) | WorkManager, Notification, Widget | 🔹 Tham khảo |
| 11 | [Kiểm thử (Testing)](./11-testing.md) | Unit test ViewModel & logic thuần | 🔹 Tham khảo |
| 12 | [Bài tập: thêm 1 tính năng từ A→Z](./12-bai-tap-thuc-hanh.md) | Tự tay xâu chuỗi mọi tầng | 🎯 Thực hành |

## 🎤 Chuẩn bị thuyết trình?

Xem thư mục [thuyet-trinh/](./thuyet-trinh/) — **phương pháp truy vết code cho từng chức năng** để trả lời tự tin khi bị hỏi *"tính năng này làm thế nào, code ở đâu?"*. Có sẵn thẻ truy vết (sơ đồ + file:dòng + câu hỏi thường gặp) cho mọi chức năng của app.

## Cách học hiệu quả nhất

1. **Đọc bài học → mở file thật được trích dẫn → đối chiếu.** Đừng chỉ đọc lý thuyết.
2. **Chạy app trước tiên** (xem bài 00). Nhìn thấy nó chạy rồi mọi thứ dễ hiểu hơn nhiều.
3. Sau mỗi bài có mục **"Tự kiểm tra"** — trả lời được là đã hiểu.
4. Làm **bài 12** cuối cùng: tự thêm một tính năng nhỏ, đi xuyên từ UI xuống API. Đó là lúc kiến thức "dính" lại.

> 💡 Toàn bộ giải thích ở đây bám theo code **tại thời điểm viết**. Nếu sau này bạn refactor, tên file/hàm có thể đổi — hãy coi các đường link là điểm khởi đầu, không phải chân lý bất biến.
