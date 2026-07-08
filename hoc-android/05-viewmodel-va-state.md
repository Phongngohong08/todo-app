# Bài 05 — ViewModel & Quản lý State

Nếu bài 04 dạy "vẽ giao diện", bài này dạy **"bộ não đằng sau giao diện"**. Đây là tầng bạn — dev backend — sẽ thấy thân thuộc nhất, vì nó chính là **service layer** của một màn hình.

## 1. ViewModel giải quyết vấn đề gì?

Nhắc lại bài 03: Activity/Composable **bị OS hủy và tạo lại** liên tục (xoay màn, đa nhiệm). Nếu để dữ liệu trong đó, nó bay mất và ta phải tải lại API mỗi lần → tệ.

**ViewModel là một object sống dai hơn màn hình.** Framework đảm bảo nó **tồn tại xuyên qua các lần Activity/Composable tái tạo**, chỉ bị dọn khi màn thật sự biến mất hẳn. Nhờ đó:
- Danh sách task **không bị tải lại** khi xoay máy.
- Coroutine đang chạy (`viewModelScope`) **không bị hủy** giữa chừng vì xoay màn.

Nó cũng là nơi **đặt toàn bộ nghiệp vụ của màn**, để Composable chỉ còn lo hiển thị.

## 2. Giải phẫu một ViewModel

Mở [TaskListViewModel.kt](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt) và soi từng phần.

### (a) Định nghĩa "hình dạng" của state — một `data class` duy nhất
```kotlin
data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val quickAddLoading: Boolean = false,
    val subtaskProgress: Map<String, SubtaskProgress> = emptyMap()
)
```
**Toàn bộ những gì màn hình cần để tự vẽ, gom vào MỘT object.** Đây là "single source of truth" cho màn TaskList. Muốn biết màn đang hiển thị gì? Chỉ cần nhìn object này. Rất dễ suy luận, dễ test.

### (b) Giữ state bằng `StateFlow` — công khai bản chỉ-đọc
```kotlin
private val _uiState = MutableStateFlow(TaskListUiState())      // sửa được, RIÊNG TƯ
val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow() // chỉ-đọc, CÔNG KHAI
```
- `MutableStateFlow` khởi tạo với state mặc định (`isLoading = true`).
- UI chỉ nhìn thấy `uiState` (không sửa được) → **chỉ ViewModel mới được đổi state**. Đóng gói sạch sẽ, đúng "event lên, state xuống".

### (c) Nhận phụ thuộc qua constructor (đúng kiểu DI bạn quen)
```kotlin
class TaskListViewModel(
    private val repo: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val aiRepository: AiRepository,
    private val sessionManager: SessionManager
) : ViewModel() { ... }
```
ViewModel **không tự tạo** repository — nó *nhận vào*. Nhờ vậy test có thể tiêm bản giả (mock). Ai đưa repo thật vào? → mục 5 (Factory + ServiceLocator).

### (d) Cập nhật state bằng `.update { it.copy(...) }`
Đây là **mẫu quan trọng nhất** của bài. Xem [TaskListViewModel.kt:57](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L57):
```kotlin
fun loadTasks(categoryFilter: String, query: String) {
    _uiState.update { it.copy(isLoading = true) }     // 1. bật loading NGAY (đồng bộ)
    viewModelScope.launch {                            // 2. mở coroutine
        try {
            val result = repo.loadTasks(lastCategory, lastQuery)   // 3. chờ mạng (không chặn)
            val progress = subtaskRepository.progressByTask()
            _uiState.update {                          // 4. đổ dữ liệu vào state, tắt loading
                it.copy(tasks = result.tasks, isLoading = false, isOffline = result.isOffline,
                        subtaskProgress = progress)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false) }
            _events.emit(TaskListEvent.Message("Lỗi kết nối: ${e.message}"))  // 5. báo lỗi
        }
    }
}
```
Hiểu dòng `_uiState.update { it.copy(...) }`:
- `it` = state **hiện tại**. `it.copy(...)` = tạo state **mới** chỉ đổi vài field (bài 01, mục 4). State là **bất biến** — ta không sửa tại chỗ mà thay bằng bản mới.
- Vì sao bất biến? Để Compose *so sánh nhanh* state cũ/mới và biết chính xác cần vẽ lại gì. Cũng tránh bug do nhiều nơi sửa chung một object.

Chuỗi sự kiện người dùng thấy: bấm tải → spinner hiện *tức thì* → (chờ mạng) → danh sách hiện ra. Tất cả chỉ nhờ đổi `isLoading` rồi `tasks` trong state, còn UI tự phản ứng (bài 04, mục 7).

## 3. Hai loại "đầu ra" của ViewModel: State vs Event

Có thứ là **trạng thái bền** (màn phải nhớ): danh sách task, đang loading. → để trong `StateFlow`.

Có thứ là **sự kiện dùng-một-lần** (làm rồi thôi): hiện một Toast "Đã xóa", điều hướng sang màn khác. Những cái này **không nên** để trong state — vì nếu để, khi màn vẽ lại (xoay máy) nó sẽ *lặp lại* Toast. Giải pháp: **`SharedFlow` cho event**.

```kotlin
sealed interface TaskListEvent {
    data class Message(val text: String) : TaskListEvent
    data class QuickAddReady(val parsed: ParsedTask) : TaskListEvent
}

private val _events = MutableSharedFlow<TaskListEvent>()
val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()
```
ViewModel *phát* event: `_events.emit(TaskListEvent.Message("Đã xóa công việc"))`.
UI *thu* event một lần trong `LaunchedEffect` (bài 04, mục 8) rồi hiện Toast / điều hướng.

> 🔑 Quy tắc: **State = "màn đang trông thế nào" (bền). Event = "vừa có chuyện gì xảy ra" (thoáng qua).** Dùng sai loại là nguồn bug kinh điển (Toast hiện lặp, điều hướng nhảy 2 lần).

## 4. Các hàm public = "API" của màn hình

Mọi hành động người dùng gọi tới đều là một hàm public trên ViewModel — đây chính là "endpoint" mà View gọi:
```kotlin
fun completeTask(task: Task)                    // hoàn thành
fun deleteTask(task: Task)                       // xóa
fun setPriority(task: Task, priority: String)    // đổi ưu tiên
fun createQuickTask(input: CreateTaskInput)      // tạo nhanh
fun parseQuickAdd(text: String, localTime: String) // nhờ AI tách câu thành task
fun moveTask(fromIndex: Int, toIndex: Int)       // kéo-thả sắp xếp (chỉ đổi state cục bộ)
fun logout()
```
Để ý `moveTask` ([TaskListViewModel.kt:130](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L130)) chỉ đổi thứ tự **trong state**, không gọi mạng — một ví dụ đẹp cho "state là nguồn sự thật, UI phản chiếu".

## 5. Ai tạo ViewModel? — `Factory` + `viewModel()`

Composable không `new` ViewModel bằng tay (sẽ mất lợi ích sống-dai). Thay vào đó khai báo ở tham số và để Compose lấy/giữ hộ. Ở [TaskListScreen.kt:65](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L65):
```kotlin
@Composable
fun TaskListScreen(
    navController: NavController,
    taskListViewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory)
) { ... }
```
- `viewModel(...)` — hàm của Compose: nếu ViewModel cho màn này **đã tồn tại** thì trả lại cái cũ (sống-dai!); nếu chưa thì tạo mới.
- Nhưng ViewModel này cần 4 repository trong constructor — `viewModel()` không tự biết lấy đâu ra. Nên ta cấp một **Factory** dạy nó cách tạo. Xem [TaskListViewModel.kt:155](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L155):
```kotlin
companion object {
    val Factory = viewModelFactory {
        initializer {
            TaskListViewModel(
                ServiceLocator.taskRepository,      // ← lấy phụ thuộc từ ServiceLocator (bài 08)
                ServiceLocator.subtaskRepository,
                ServiceLocator.aiRepository,
                ServiceLocator.sessionManager
            )
        }
    }
}
```
Đây là mắt xích nối **UI ↔ DI**: Factory kéo repository thật từ [ServiceLocator](../app/app/src/main/java/com/example/todoapplication/di/ServiceLocator.kt) nhét vào ViewModel. Mọi ViewModel trong app đều có `companion object { val Factory ... }` y hệt.

## 6. `viewModelScope` — vòng đời coroutine gắn với màn

Mọi coroutine mở bằng `viewModelScope.launch { }` sẽ **tự bị hủy** khi ViewModel bị dọn. Bạn không phải tự quản lý huỷ/dọn — hết một nguồn rò rỉ (leak) kinh điển. So sánh: ở Go bạn phải tự truyền `context.Context` để hủy goroutine; ở đây scope lo giúp.

## 7. Mẫu chung của MỌI ViewModel trong app

Mở bất kỳ file nào trong [ui/viewmodel/](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/) — [AICoachViewModel](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AICoachViewModel.kt), [StatsViewModel](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt), [CalendarViewModel](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/CalendarViewModel.kt)... bạn sẽ thấy đúng khuôn:

```
data class XxxUiState( ... )                    // hình dạng state
class XxxViewModel(repo...) : ViewModel() {
    private val _uiState = MutableStateFlow(XxxUiState())
    val uiState = _uiState.asStateFlow()         // state ra ngoài (chỉ đọc)
    // (tùy chọn) _events: SharedFlow cho sự kiện một lần
    fun someAction() { viewModelScope.launch { ... _uiState.update { it.copy(...) } } }
    companion object { val Factory = viewModelFactory { initializer { XxxViewModel(ServiceLocator...) } } }
}
```
Học thuộc khuôn này là bạn đọc được **mọi** ViewModel trong dự án.

## 8. Tự kiểm tra
1. Vì sao đặt danh sách task trong ViewModel thay vì trong Composable? Điều gì xảy ra khi xoay màn với mỗi cách?
2. Giải thích `_uiState.update { it.copy(isLoading = false) }` từng phần: `it` là gì, `copy` làm gì, vì sao không sửa trực tiếp?
3. Khi nào dùng `StateFlow`, khi nào dùng `SharedFlow`? Cho ví dụ trong app.
4. Factory dùng để làm gì? Nó lấy các repository ở đâu?
5. Nếu người dùng thoát màn giữa lúc đang tải task, coroutine kia ra sao? Nhờ đâu?

➡️ Tiếp theo: [Bài 06 — Networking (Retrofit/OkHttp)](./06-networking-retrofit.md)
