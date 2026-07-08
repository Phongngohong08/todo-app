# Bài 02 — Kiến trúc tổng thể (MVVM)

Bài này là **bản đồ lớn**. Đọc xong bạn sẽ biết một cú bấm nút chạy xuyên qua những tầng nào. Các bài sau chỉ là zoom vào từng tầng.

## 1. Bức tranh toàn cảnh

App theo mẫu **MVVM** (Model – View – ViewModel) mà Google khuyến nghị. Với dev backend, hãy đọc nó là **kiến trúc phân tầng quen thuộc**:

```
   NGƯỜI DÙNG bấm/nhập
          │
          ▼
┌──────────────────────┐   quan sát state ▲
│  VIEW (Composable)    │   gọi hàm       │      ui/screens/*
│  "vẽ theo state"      │─────────────────┘
└──────────┬───────────┘
           │ gọi hàm (vd: completeTask)
           ▼
┌──────────────────────┐   phát ra StateFlow<UiState>
│  VIEWMODEL            │   giữ state + logic màn hình   ui/viewmodel/*
│  "bộ não của màn"     │
└──────────┬───────────┘
           │ gọi
           ▼
┌──────────────────────┐   nguồn dữ liệu DUY NHẤT
│  REPOSITORY           │   quyết định: mạng? hay cache? data/repository/*
└─────┬──────────┬─────┘
      │          │
      ▼          ▼
┌──────────┐  ┌──────────┐
│ ApiService│  │  Room DB │      data/api/*   |   data/local/*
│ (Retrofit)│  │ (offline)│
└────┬─────┘  └──────────┘
     ▼
  BACKEND Go  ([backend/](../backend/))
```

Đối chiếu với server của bạn:

| Tầng Android | Vai trò | Tầng backend tương ứng |
|---|---|---|
| **View** (Composable) | Nhận input, hiển thị output. **Không chứa nghiệp vụ.** | HTTP handler (chỉ parse request, trả response) |
| **ViewModel** | Nghiệp vụ của một màn hình, giữ trạng thái, gọi repo | Service layer |
| **Repository** | Nguồn dữ liệu duy nhất, phối hợp API + cache | Repository layer |
| **ApiService / DAO** | Chi tiết truy cập dữ liệu | HTTP client / ORM |
| **Model (data class)** | DTO | struct DTO |

## 2. Nguyên tắc vàng: **Unidirectional Data Flow** (luồng dữ liệu một chiều)

Đây là ý tưởng cốt lõi, và nó **khác** cách UI cổ điển. Chỉ có hai chiều:

- **State đi XUỐNG:** ViewModel giữ một `StateFlow<UiState>`. View *quan sát* và **vẽ lại chính nó** mỗi khi state đổi. View không tự "sửa" gì trên màn hình cả — nó chỉ là một hàm `f(state) = giao diện`.
- **Event đi LÊN:** Khi người dùng bấm/nhập, View **gọi một hàm** trên ViewModel. ViewModel xử lý → cập nhật state → (vòng lặp) View tự vẽ lại.

```
State (dữ liệu)  ─────────►  View vẽ
                              │
Event (hành động) ◄───────────┘  người dùng bấm → gọi VM
```

> 🔑 Nhớ một câu: **"State xuống, Event lên."** Toàn bộ app này (và mọi app Compose hiện đại) xoay quanh vòng lặp đó. Bài 04 & 05 sẽ cho bạn thấy nó bằng code.

Lợi ích cho bạn: **một nguồn sự thật duy nhất** (single source of truth) cho mỗi màn — chính là `uiState`. Không có chuyện "UI và dữ liệu lệch nhau" vì UI *luôn* được vẽ từ state.

## 3. Đi theo một luồng thật: "Hoàn thành một công việc"

Hãy lần theo hành động bấm nút hoàn thành task. Mở song song 3 file: Screen → ViewModel → Repository.

**Bước 1 — View gọi lên (event đi lên).** Trong [TaskListScreen.kt](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt), thẻ task có callback gọi:
```kotlin
taskListViewModel.completeTask(task)
```

**Bước 2 — ViewModel xử lý.** [TaskListViewModel.kt:82](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L82):
```kotlin
fun completeTask(task: Task) {
    viewModelScope.launch {        // mở coroutine (không chặn UI)
        repo.completeTask(task)    // gọi xuống repository
        reload()                   // tải lại danh sách → cập nhật state
    }
}
```

**Bước 3 — Repository quyết định "lấy dữ liệu ở đâu".** [TaskRepository.kt:63](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L63):
```kotlin
suspend fun completeTask(task: Task): Boolean =
    api.completeTask(task.id).isSuccessful      // gọi backend qua Retrofit
```

**Bước 4 — ApiService gửi HTTP.** [ApiService.kt:47](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L47):
```kotlin
@POST("tasks/{id}/complete")
suspend fun completeTask(@Path("id") id: String): Response<Task>
```
Retrofit biến khai báo này thành một request `POST /api/v1/tasks/{id}/complete` tới backend Go của bạn.

**Bước 5 — State đổi → View tự vẽ lại (state đi xuống).** `reload()` cập nhật `_uiState`. Vì Screen đang *quan sát* `uiState` (bằng `collectAsStateWithLifecycle()`), Compose **tự động vẽ lại** danh sách — không dòng nào bảo View "hãy cập nhật". Đó là điều kỳ diệu của declarative UI.

Xong. Một vòng khép kín: **View → ViewModel → Repository → Api → (state) → View**. Mọi tính năng trong app đều theo đúng mạch này.

## 4. Vì sao tách tầng như vậy? (bạn đã biết câu trả lời)

Y hệt lý do bạn tách handler/service/repo ở backend:
- **Test được:** ViewModel test không cần Android thật (bài 11 — [TaskListViewModelTest.kt](../app/app/src/test/java/com/example/todoapplication/ui/viewmodel/TaskListViewModelTest.kt)). Logic thuần tách ra [domain/](../app/app/src/main/java/com/example/todoapplication/domain/) test cực nhanh ([TaskListLogicTest.kt](../app/app/src/test/java/com/example/todoapplication/domain/TaskListLogicTest.kt)).
- **Đổi nguồn dữ liệu không ảnh hưởng UI:** Repository có thể chuyển từ mạng sang cache (chính là tính năng offline) mà Screen không cần biết.
- **Chia việc rõ ràng:** UI lo hiển thị, ViewModel lo nghiệp vụ, Repository lo dữ liệu.

## 5. Tầng `domain/` — logic thuần, món quà cho dev backend

Để ý thư mục [domain/](../app/app/src/main/java/com/example/todoapplication/domain/). Đây là các hàm **thuần túy** (pure function): vào dữ liệu → ra kết quả, không đụng Android, không mạng. Ví dụ: sắp xếp task, kiểm tra quá hạn, gợi ý AaI. Vì thuần túy nên test cực dễ và nhanh. Đây là nơi bạn — dev backend — sẽ thấy "như ở nhà" nhất.

## 6. Tự kiểm tra
1. Vẽ lại từ trí nhớ: một cú bấm "hoàn thành task" đi qua những file/tầng nào?
2. "State xuống, Event lên" nghĩa là gì? View có được tự sửa giao diện không?
3. Vì sao ViewModel *không* import gì từ `ui/screens`? (gợi ý: chiều phụ thuộc)
4. Nếu muốn thêm cache offline cho một API mới, bạn nên sửa tầng nào và KHÔNG cần đụng tầng nào?

➡️ Tiếp theo: [Bài 03 — Vòng đời & điểm khởi động](./03-vong-doi-va-entry-points.md)
