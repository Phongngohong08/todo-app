# Thẻ 02 — Công việc (Danh sách · CRUD · Offline · Subtask)

⬅️ [Về mục lục thuyết trình](./README.md)

Đây là màn "lõi" của app — nhiều chức năng nhất, dễ bị hỏi nhất. File chính: [TaskListScreen.kt](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt), [TaskListViewModel.kt](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt), [TaskRepository.kt](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt).

---

## 🔹 Tải & hiển thị danh sách (kèm tìm kiếm / lọc)

**Một câu:** Màn quan sát `uiState`; khi filter hoặc từ khóa đổi, chờ 300ms (debounce) rồi gọi `GET tasks` với tham số lọc, đổ vào state, UI tự vẽ lại.

**Sơ đồ:**
```
TaskListScreen: collectAsStateWithLifecycle(uiState)                 TaskListScreen.kt:70
LaunchedEffect(filter, query) { delay(300); loadTasks(...) }         TaskListScreen.kt:112  (debounce)
   → TaskListViewModel.loadTasks(category, query)                    TaskListViewModel.kt:57
      → TaskRepository.loadTasks(category, query)                    TaskRepository.kt:25
         → api.listTasks(status, dueBefore, q, category)  GET tasks  ApiService.kt:30
   → _uiState.update { it.copy(tasks = ..., isLoading = false) }  → UI vẽ lại
```

**Điểm code:** debounce tìm kiếm ở [TaskListScreen.kt:112](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L112); tải + đổ state ở [TaskListViewModel.kt:57](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L57).

**Có thể bị hỏi:**
- *"Gõ tìm kiếm mỗi ký tự có gọi API liên tục không?"* → Không — `LaunchedEffect` + `delay(300)` gộp lại, chỉ gọi khi ngừng gõ 0,3s.
- *"Danh sách sắp xếp thế nào?"* → Logic thuần trong [domain/TaskListLogic.kt](../../app/app/src/main/java/com/example/todoapplication/domain/TaskListLogic.kt) (`sortTasks`, `isOverdue`...), gọi từ Screen. Tách ra domain để test được ([TaskListLogicTest.kt](../../app/app/src/test/java/com/example/todoapplication/domain/TaskListLogicTest.kt)).

---

## 🔹 Xem offline (điểm nên chủ động khoe)

**Một câu:** Tải mạng thành công thì ghi đè cache Room; mất mạng thì đọc cache và hiện cờ "offline".

**Sơ đồ:**
```
TaskRepository.loadTasks()                                          TaskRepository.kt:25
   thành công → TaskCacheRepository.cache(...)  (ghi Room)          TaskRepository.kt:31 → TaskCacheRepository.kt:15
   lỗi mạng  → TaskCacheRepository.getCached(...) + isOffline=true  TaskRepository.kt:38 → TaskCacheRepository.kt:24
```
```kotlin
} catch (e: Exception) {
    val cached = TaskCacheRepository.getCached(appContext)
    if (cached.isNotEmpty()) TaskListResult(cached, isOffline = true) else throw e
}
```
Cờ `isOffline` chảy vào `TaskListUiState.isOffline` → Screen hiện banner "đang offline".

**Có thể bị hỏi:**
- *"Cache lưu bằng gì?"* → Room (SQLite). Bảng `task_cache`, DAO [Daos.kt:11](../../app/app/src/main/java/com/example/todoapplication/data/local/Daos.kt#L11).
- *"Vì sao UI không cần biết đang online hay offline?"* → Vì Repository là *nguồn dữ liệu duy nhất*; nó tự quyết mạng/cache và chỉ trả danh sách + cờ.

---

## 🔹 Hoàn thành công việc

**Một câu:** Bấm tick → `POST tasks/{id}/complete` → tải lại danh sách.

```
completeTask(task)   TaskListViewModel.kt:82  →  api.completeTask(id)  TaskRepository.kt:63  →  ApiService.kt:47
```

## 🔹 Xóa công việc

**Một câu:** `DELETE tasks/{id}`; thành công thì báo Toast + hủy nhắc nhở + tải lại.

- [TaskListViewModel.deleteTask:89](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L89) → [TaskRepository.deleteTask:56](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L56) (kèm `ReminderScheduler.cancel`) → [ApiService.kt:44](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L44).

## 🔹 Đổi nhanh độ ưu tiên

**Một câu:** Bấm cờ trên thẻ → dựng `UpdateTaskInput` từ task hiện tại (chỉ đổi priority) → `PUT tasks/{id}`.

- [TaskListViewModel.setPriority:99](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L99).

## 🔹 Kéo-thả sắp xếp

**Một câu:** Thư viện `reorderable` báo chỉ số from→to; ViewModel chỉ đổi thứ tự *trong state* (không gọi mạng).

- Cấu hình kéo-thả: [TaskListScreen.kt:82](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L82) → gọi [TaskListViewModel.moveTask:130](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L130).
- *Có thể bị hỏi:* *"Đổi thứ tự có lưu server không?"* → Không, đây là sắp xếp cục bộ trong `uiState.tasks` — ví dụ đẹp cho "state là nguồn sự thật, UI phản chiếu".

---

## 🔹 Tạo / Sửa công việc (màn chi tiết)

**Một câu:** Màn chi tiết dùng `taskId`: `"new"` = tạo mới (`POST tasks`), ngược lại = tải rồi sửa (`PUT tasks/{id}`).

**Sơ đồ:**
```
Điều hướng: Screen.TaskDetail.createRoute(id)          Screen.kt:7   (route "task_detail/{taskId}")
TaskDetailScreen đọc taskId
   → nếu sửa: loadTask(id) → repo.getTask(id)          TaskDetailViewModel.kt:67 / TaskRepository.kt:45
   → lưu:   create(input) / update(id, input)          TaskDetailViewModel.kt:77,79
            → repo.createTask / updateTask              TaskRepository.kt:50,53
            → ApiService.kt:27 (POST) / :41 (PUT)
            → onSuccess: phát TaskDetailEvent.Saved → quay lại danh sách
```
- Hàm `save { }` gom logic lưu chung cho cả tạo lẫn sửa: [TaskDetailViewModel.kt:81](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskDetailViewModel.kt#L81).

**Có thể bị hỏi:**
- *"Phân biệt tạo mới và sửa ở đâu?"* → Bằng quy ước `taskId == "new"` truyền qua route ([MainActivity.kt:86](../../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L86)).
- *"Tạo/sửa xong có tự đặt nhắc nhở không?"* → Có: `createTask`/`updateTask` gọi `ReminderScheduler.schedule(...)` ([TaskRepository.kt:51](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L51)).

---

## 🔹 Bước con / checklist (Subtask) — lưu hoàn toàn cục bộ

**Một câu:** Mỗi task có danh sách bước con lưu trong Room (không lên server); thẻ task hiển thị tiến độ "x/y".

**Sơ đồ:**
```
TaskDetailScreen: thêm/tick/xóa bước con
   → TaskDetailViewModel.addSubtask/toggleSubtask/deleteSubtask   TaskDetailViewModel.kt:45,53,60
      → SubtaskRepository.add/toggle/delete                       SubtaskRepository.kt:17,29,31
         → SubtaskDao (Room, bảng "subtask")                      Daos.kt:22
Tiến độ trên thẻ list: SubtaskRepository.progressByTask()         SubtaskRepository.kt:34
   → TaskListUiState.subtaskProgress
```

**Có thể bị hỏi:**
- *"Vì sao subtask lưu cục bộ mà task lưu server?"* → Thiết kế: subtask là checklist cá nhân nhẹ, dùng Room cho nhanh/offline; đây cũng là ví dụ app dùng **cả hai** nguồn dữ liệu.
- *"Tiến độ x/y tính ở đâu?"* → `progressByTask()` gom nhóm theo `taskId` rồi đếm số `isDone` — [SubtaskRepository.kt:34](../../app/app/src/main/java/com/example/todoapplication/data/repository/SubtaskRepository.kt#L34).

---

## 🔹 Quick Add bằng AI
Đây là chức năng AI — xem [Thẻ 03](./03-tinh-nang-ai.md).

⬅️ [Về mục lục thuyết trình](./README.md)
