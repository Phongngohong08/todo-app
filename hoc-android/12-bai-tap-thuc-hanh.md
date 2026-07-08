# Bài 12 — Bài tập: thêm một tính năng từ A→Z

Lý thuyết đã đủ. Giờ tự tay đi xuyên **mọi tầng** một lần — đó là lúc kiến thức "dính" lại. Ta sẽ thêm tính năng **"Nhân bản công việc" (Duplicate task)**: bấm một nút trên thẻ task để tạo bản sao y hệt (title thêm "(bản sao)").

Vì sao chọn tính năng này để học:
- **Không cần đụng backend** — nó tái dùng endpoint `POST /tasks` đã có.
- **Đi qua đủ tầng**: UI → ViewModel → Repository → ApiService → (state cập nhật) → UI.
- Có phần mở rộng để bạn viết thêm **hàm domain thuần + unit test**.

> 🛠️ Chuẩn bị: mở dự án trong Android Studio và **chạy được app** (bài 00) trước khi bắt đầu. Sau mỗi bước, build lại và quan sát.

---

## Bước 0 — Lần theo mã, đừng viết gì vội

Trước khi thêm, hãy tự truy vết luồng **tạo task đã có** (chính là thứ ta sẽ tái dùng). Mở và đọc theo thứ tự:
1. [TaskListViewModel.createQuickTask](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L117) — ViewModel gọi gì?
2. [TaskRepository.createTask](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L50) — repo làm gì?
3. [ApiService.createTask](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L27) — request gì gửi đi?
4. `CreateTaskInput` trong [Models.kt](../app/app/src/main/java/com/example/todoapplication/data/model/Models.kt) — cần những field nào?

✅ **Checkpoint:** vẽ ra giấy sơ đồ "createQuickTask → ... → backend". Nếu vẽ được, bạn đã hiểu tầng cần đụng.

---

## Bước 1 — Tầng Model: xem `CreateTaskInput` cần gì

Mở [Models.kt](../app/app/src/main/java/com/example/todoapplication/data/model/Models.kt), tìm `data class CreateTaskInput`. Ghi ra các field của nó (title, description, priority, dueDate, category, recurrence...). Ta sẽ map từ một `Task` có sẵn sang `CreateTaskInput` để nhân bản.

*Không sửa gì ở bước này — chỉ đọc để biết đích cần điền.*

---

## Bước 2 — Tầng domain (tùy chọn nhưng nên làm): hàm thuần + test

Tạo một hàm thuần biến `Task` → `CreateTaskInput` cho bản sao. Đặt logic thuần ở [domain/](../app/app/src/main/java/com/example/todoapplication/domain/) để **test được không cần Android** (bài 11).

Tạo file `domain/DuplicateTask.kt` (điều chỉnh tên field cho khớp `CreateTaskInput` thật):
```kotlin
package com.example.todoapplication.domain

import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.Task

/** Dựng input tạo mới từ một task có sẵn — dùng để nhân bản. */
fun Task.toDuplicateInput(): CreateTaskInput = CreateTaskInput(
    title = "$title (bản sao)",
    description = description ?: "",
    priority = priority,
    dueDate = dueDate,
    category = category,
    recurrence = recurrence,
    recurrenceDays = recurrenceDays,
    reminderOffsetMinutes = reminderOffsetMinutes
    // KHÔNG sao chép id/status/createdAt — bản sao là task mới, chưa hoàn thành
)
```
> 💡 Mở [TaskListViewModel.setPriority](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L99) để xem một ví dụ dựng input tương tự từ `task` — bạn sẽ biết chính xác tên field của `CreateTaskInput`/`UpdateTaskInput`.

Viết test ngay, trong `src/test/.../domain/DuplicateTaskTest.kt`:
```kotlin
@Test
fun `toDuplicateInput copies fields and marks title as copy`() {
    val t = /* dùng helper task(...) như trong TaskListLogicTest */
    val input = t.toDuplicateInput()
    assertEquals("Task 1 (bản sao)", input.title)
    assertEquals(t.priority, input.priority)
}
```
Chạy: `./gradlew test`.

✅ **Checkpoint:** test xanh. Bạn vừa thêm logic *có kiểm chứng* mà chưa cần chạy app.

---

## Bước 3 — Tầng ViewModel: thêm hành động `duplicateTask`

Mở [TaskListViewModel.kt](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt). Bắt chước y hệt `createQuickTask` ([dòng 117](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L117)), thêm hàm:
```kotlin
/** Nhân bản một công việc: tạo bản sao rồi tải lại danh sách. */
fun duplicateTask(task: Task) {
    viewModelScope.launch {
        repo.createTask(task.toDuplicateInput()).fold(
            onSuccess = {
                _events.emit(TaskListEvent.Message("Đã nhân bản: ${it.title}"))
                reload()
            },
            onFailure = { _events.emit(TaskListEvent.Message("Không thể nhân bản")) }
        )
    }
}
```
Nhớ `import com.example.todoapplication.domain.toDuplicateInput`.

Ôn lại tại sao mỗi dòng như vậy:
- `viewModelScope.launch` — chạy nền, không chặn UI (bài 01/05).
- `.fold(onSuccess, onFailure)` — repo trả `Result<Task>` (bài 06).
- `_events.emit(Message(...))` — **event dùng-một-lần** để Toast (bài 05), *không* để trong state.
- `reload()` — cập nhật `_uiState` → UI tự vẽ lại (bài 05).

✅ **Checkpoint:** build lại (Ctrl/Cmd+F9). Chưa thấy gì trên màn vì chưa có nút — nhưng phải build sạch.

---

## Bước 4 — Tầng UI: thêm nút gọi lên ViewModel

Mở [TaskListScreen.kt](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt). Tìm Composable vẽ mỗi thẻ task (nơi đã có nút hoàn thành/xóa/đổi ưu tiên). Ở đó bạn sẽ thấy các `IconButton { ... }` gọi `taskListViewModel.completeTask(task)` v.v.

Thêm một nút nhân bản, ví dụ dùng icon `Icons.Filled.ContentCopy`:
```kotlin
IconButton(onClick = { taskListViewModel.duplicateTask(task) }) {
    Icon(Icons.Filled.ContentCopy, contentDescription = "Nhân bản")
}
```
Nhớ nguyên tắc **"event lên"** (bài 02/04): UI chỉ *gọi hàm* ViewModel, không tự tạo task. Toast "Đã nhân bản..." sẽ tự hiện nhờ `LaunchedEffect` thu `events` đã có sẵn ở [đầu màn](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L97).

✅ **Checkpoint:** chạy app trên máy ảo → bấm nút nhân bản trên một task → thấy Toast, và một task "(bản sao)" xuất hiện trong danh sách (nhờ `reload()`).

---

## Bước 5 — Kiểm chứng end-to-end

- Mở **Logcat** trong Android Studio, lọc theo `okhttp`. Bấm nhân bản → bạn thấy request `POST .../tasks` và response JSON (nhờ logging interceptor, bài 06). Bạn đang *nhìn thấy* app nói chuyện với backend Go của mình.
- Tắt mạng máy ảo → bấm nhân bản → quan sát nhánh `onFailure` chạy (Toast "Không thể nhân bản"). Đây là lúc `Result.failure` phát huy.
- Xoay màn hình sau khi nhân bản → danh sách **không tải lại từ đầu, không mất** (nhờ ViewModel sống-dai, bài 05). Toast **không lặp lại** (nhờ dùng event chứ không phải state, bài 05).

✅ **Checkpoint cuối:** bạn vừa đi trọn vòng **UI → ViewModel → Repository → API → state → UI**, đúng sơ đồ bài 02.

---

## Bạn vừa vận dụng lại những gì

| Việc bạn làm | Bài liên quan |
|---|---|
| Đọc `CreateTaskInput`, dùng `copy`/map field | 01 (data class), 06 (DTO) |
| Viết hàm domain thuần + unit test | 02 (tầng domain), 11 (test) |
| Thêm hàm ViewModel, `launch`, `fold`, `emit` event | 01 (coroutine), 05 (state/event), 06 (Result) |
| Thêm nút, "event lên", Toast qua `LaunchedEffect` | 02, 04 |
| Xem log mạng, thử nhánh lỗi/offline | 06, 07 |

---

## Thử thách nâng cao (tự làm)

1. **Nút nhân bản ở màn chi tiết.** Thêm hành động tương tự vào [TaskDetailViewModel](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskDetailViewModel.kt) + [TaskDetailScreen](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskDetailScreen.kt), sau khi nhân bản thì `navController.popBackStack()` về danh sách (bài 09).
2. **Xác nhận trước khi nhân bản.** Dùng `AlertDialog` với một `remember { mutableStateOf(false) }` để mở/đóng (bài 04) — bắt chước mẫu `taskToDelete` đã có trong màn danh sách.
3. **Đồng bộ nhắc nhở.** Kiểm tra: bản sao có `dueDate` thì `ReminderScheduler.schedule` có tự chạy không? Truy vết trong [TaskRepository.createTask](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L50) (bài 10).
4. **Test ViewModel.** Viết test cho `duplicateTask` theo mẫu [TaskListViewModelTest](../app/app/src/test/java/com/example/todoapplication/ui/viewmodel/TaskListViewModelTest.kt): mock `repo.createTask(any())` trả `Result.success(...)`, khẳng định có phát `TaskListEvent.Message` và có gọi `reload()` (bài 11).

---

## 🎓 Tổng kết cả khóa

Bạn đã đi từ "chưa biết gì về Android" tới chỗ **thêm được một tính năng xuyên suốt mọi tầng**. Bản đồ trong đầu bạn bây giờ:

```
Compose UI  ──(event lên)──►  ViewModel  ──►  Repository  ──►  ApiService/Room
     ▲                            │
     └────(state xuống)───────────┘
```

Ba câu "thần chú" mang theo:
- **"UI = f(state)"** — giao diện là hàm của trạng thái (bài 04).
- **"State xuống, Event lên"** — luồng dữ liệu một chiều (bài 02/05).
- **"Repository là nguồn dữ liệu duy nhất"** — mạng hay cache, UI không cần biết (bài 06/07).

Phần lớn kiến trúc này bạn đã có sẵn từ backend — chỉ khác lớp vỏ declarative UI và vòng đời do OS điều khiển. Giờ hãy chọn một tính năng thật trong app, đọc kỹ nó, rồi tự cải tiến. Chúc bạn học vui! 🚀

⬅️ Quay lại [Mục lục](./README.md)
