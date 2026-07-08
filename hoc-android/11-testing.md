# Bài 11 — Kiểm thử (Testing)

Testing là chỗ dev backend thấy "về nhà" nhất. Nhờ kiến trúc phân tầng (bài 02), phần lớn logic app này **test được mà không cần thiết bị Android** — chạy thẳng trên JVM máy bạn, nhanh như test Go.

## 1. Hai loại test trong Android

| Loại | Ở đâu | Chạy trên | Tốc độ | Dùng khi |
|---|---|---|---|---|
| **Unit test** | [`src/test/`](../app/app/src/test/java/com/example/todoapplication/) | JVM máy bạn | Rất nhanh | logic thuần, ViewModel, repository |
| **Instrumented test** | [`src/androidTest/`](../app/app/src/androidTest/) | Máy ảo/thiết bị Android | Chậm | UI thật, DB thật, tích hợp |

Nguyên tắc: **đẩy càng nhiều logic xuống nơi test được bằng unit test càng tốt.** App này làm đúng vậy — logic nghiệp vụ nằm ở `domain/` và ViewModel (thuần JVM), còn phần dính Android (UI, Room) mỏng đi.

Chạy toàn bộ unit test:
```bash
cd app
./gradlew test        # Windows: gradlew.bat test
```

## 2. Test logic thuần — dễ nhất, làm trước

[TaskListLogicTest.kt](../app/app/src/test/java/com/example/todoapplication/domain/TaskListLogicTest.kt) test các hàm trong [domain/](../app/app/src/main/java/com/example/todoapplication/domain/). Vì đó là **hàm thuần** (vào → ra, không side-effect), test chỉ là "gọi hàm, so kết quả":

```kotlin
@Test
fun `isOverdue is false for completed task even with past due date`() {
    val t = task(status = "COMPLETED", dueDate = isoAt(-5))   // quá hạn 5 giờ nhưng ĐÃ XONG
    assertFalse(t.isOverdue())                                 // → không tính là trễ
}

@Test
fun `isOverdue is true when due date is in the past and not completed`() {
    val t = task(status = "TODO", dueDate = isoAt(-5))
    assertTrue(t.isOverdue())
}
```
Cú pháp cần biết:
- **`@Test`** — đánh dấu một hàm test (JUnit, giống `func TestXxx` trong Go).
- **Tên hàm trong backtick** `` `mô tả bằng câu` `` — Kotlin cho phép tên hàm có dấu cách, giúp báo cáo test đọc như tiếng người.
- **`assertEquals`, `assertTrue`, `assertFalse`** — khẳng định (assertion).
- Hàm `task(...)` là **test fixture** — helper tạo dữ liệu mẫu với tham số mặc định, đỡ lặp code. Mẫu rất đáng bắt chước.

> 💡 Đây là lý do tồn tại tầng `domain/`: gom logic "thò ra thụt vào" (sắp xếp, lọc, tính trễ hạn) thành hàm thuần để test cực rẻ. Là dev backend, bạn nên **ưu tiên nhét logic vào đây** khi thêm tính năng.

## 3. Test ViewModel — cần mock & xử lý coroutine

[TaskListViewModelTest.kt](../app/app/src/test/java/com/example/todoapplication/ui/viewmodel/TaskListViewModelTest.kt) khó hơn một chút vì ViewModel gọi repository (bất đồng bộ). Hai công cụ giải quyết:

### (a) Mock repository (Mockito)
ViewModel nhận repository **qua constructor** (bài 05) — nên test tiêm bản giả vào được:
```kotlin
@Before                                   // chạy trước mỗi test
fun setUp() {
    Dispatchers.setMain(dispatcher)
    taskRepository = mock()                // tạo repository GIẢ
    subtaskRepository = mock()
    aiRepository = mock()
    sessionManager = mock()
    whenever(sessionManager.getUserName()).thenReturn("Phong")   // "khi gọi X thì trả Y"
    viewModel = TaskListViewModel(taskRepository, subtaskRepository, aiRepository, sessionManager)
}
```
- **`mock()`** tạo một object giả của interface/class.
- **`whenever(x.foo()).thenReturn(y)`** — kịch bản: "khi ai gọi `foo()` thì trả `y`". Giống stub ở backend.
- **`verify(x).bar()`** / **`verifyBlocking(x) { bar() }`** — khẳng định "hàm `bar()` *đã* được gọi".

> 📌 Comment ở đầu file test tiết lộ một bài học kiến trúc quan trọng:
> *"trước đây không thể test vì ViewModel gọi thẳng ServiceLocator (singleton static) thay vì nhận qua constructor."*
> Đây chính là lý do ta **tiêm phụ thuộc qua constructor** thay vì gọi singleton toàn cục bên trong. Testability là hệ quả trực tiếp của thiết kế tốt — điều bạn đã thấm ở backend.

### (b) Điều khiển coroutine (kotlinx-coroutines-test)
ViewModel chạy việc trong `viewModelScope.launch` (bất đồng bộ). Test cần **kiểm soát thời gian ảo** để chờ nó xong:
```kotlin
@Test
fun `loadTasks populates state on success`() = runTest(dispatcher) {
    val tasks = listOf(task("1"), task("2", status = "COMPLETED"))
    whenever(taskRepository.loadTasks(null, null)).thenReturn(TaskListResult(tasks, isOffline = false))
    whenever(subtaskRepository.progressByTask()).thenReturn(emptyMap())

    viewModel.loadTasks("ALL", "")
    dispatcher.scheduler.advanceUntilIdle()      // "chạy hết mọi coroutine đang chờ"

    val state = viewModel.uiState.value          // đọc state cuối cùng
    assertEquals(2, state.tasks.size)
    assertFalse(state.isLoading)
    assertFalse(state.isOffline)
}
```
- **`runTest { }`** — môi trường test cho coroutine, có "đồng hồ ảo" (không phải chờ thật).
- **`Dispatchers.setMain(dispatcher)`** ở `@Before` + **`resetMain()`** ở `@After` — thay luồng chính bằng dispatcher test (vì máy JVM không có main thread của Android).
- **`advanceUntilIdle()`** — "tua nhanh" cho mọi coroutine đang chờ chạy xong, rồi mới kiểm tra kết quả.

### Các ca test đáng chú ý (kiểm chứng đúng nghiệp vụ)
- `loadTasks emits message event ... on failure` — repo ném lỗi → state **hết loading** *và* **phát event Message**. Đúng thiết kế "state vs event" ở bài 05. Test còn *thu* `viewModel.events` vào một list để khẳng định có event.
- `completeTask calls repository then reloads` — dùng `verifyBlocking` để chắc rằng hoàn thành xong **có gọi tải lại** với đúng filter cũ.
- `deleteTask emits message only when repository confirms deletion` — repo trả `false` (xóa hụt) → **không** phát Toast. Kiểm tra nhánh thất bại, thứ hay bị quên.
- `logout delegates to sessionManager` — chỉ cần `verify(sessionManager).logout()`.

## 4. Bạn nên viết test thế nào cho tính năng mới

Theo thứ tự ưu tiên (rẻ → đắt):
1. **Logic thuần** → tách vào `domain/`, viết unit test như mục 2. Nhanh, nhiều ca.
2. **Nghiệp vụ màn hình** → test ViewModel với repo mock như mục 3. Bao các nhánh success/failure và kiểm tra state + event.
3. **UI/DB thật** → chỉ khi cần, viết instrumented test trong `androidTest/` (chậm, để cho luồng quan trọng).

Đây đúng "kim tự tháp test" bạn đã quen: đáy nhiều (unit), đỉnh ít (integration/UI).

## 5. Tự kiểm tra
1. Vì sao unit test (`src/test/`) chạy nhanh hơn instrumented test (`src/androidTest/`) rất nhiều?
2. Nhờ đặc điểm thiết kế nào của ViewModel mà ta mock được repository trong test?
3. `advanceUntilIdle()` giải quyết vấn đề gì khi test code có coroutine?
4. Vì sao gọi ServiceLocator *bên trong* ViewModel lại làm nó khó test — và constructor injection sửa điều đó ra sao?
5. Bạn sẽ đặt logic "sắp xếp task theo độ ưu tiên" ở đâu để dễ test nhất?

➡️ Tiếp theo: [Bài 12 — Bài tập: thêm một tính năng từ A→Z](./12-bai-tap-thuc-hanh.md)
