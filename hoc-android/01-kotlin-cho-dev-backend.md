# Bài 01 — Kotlin cho lập trình viên backend

Mục tiêu: đọc hiểu **mọi** cú pháp Kotlin xuất hiện trong dự án này. Không dạy Kotlin toàn tập, chỉ những gì bạn sẽ thật sự gặp. Tôi đối chiếu với Go ở đâu có thể.

## 1. Biến: `val` và `var`

```kotlin
val name = "Phong"   // bất biến (immutable) — như hằng, KHÔNG gán lại được
var count = 0        // biến đổi được
count = 1            // OK
```
- `val` ≈ khai báo rồi không cho gán lại (giống `const` nhưng cho object). **Ưu tiên `val`** — code Android dùng `val` ở khắp nơi.
- Kiểu dữ liệu tự suy luận. Ghi rõ khi cần: `val userName: String = "..."`.

## 2. Null safety — điểm KHÁC BIỆT lớn nhất với Go

Trong Kotlin, một biến **mặc định không thể null**. Muốn cho phép null, thêm `?`:

```kotlin
val a: String = null     // ❌ compile lỗi
val b: String? = null    // ✅ "String có thể null"
```
Từ đó có bộ toán tử null:

```kotlin
val len = b?.length              // "?." — nếu b null thì cả biểu thức = null (không nổ)
val len2 = b?.length ?: 0        // "?:" (Elvis) — nếu vế trái null thì lấy 0
val forced = b!!.length          // "!!" — ÉP: "tôi chắc chắn không null". Null → CRASH
```
Bạn thấy đầy trong code, ví dụ [Models.kt](../app/app/src/main/java/com/example/todoapplication/data/model/Models.kt):
```kotlin
val description: String?    // task có thể không có mô tả
```
và trong [TaskRepository.kt:29](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L29):
```kotlin
val fresh = response.body() ?: emptyList()   // body có thể null → thay bằng list rỗng
```
> 🔑 Đây là "billion dollar mistake" mà Go né bằng zero-value, còn Kotlin né bằng kiểu `?`. Khi thấy `?`, `?.`, `?:`, `!!` — đó đều là xử lý null.

## 3. Hàm

```kotlin
fun add(a: Int, b: Int): Int {
    return a + b
}
fun addShort(a: Int, b: Int): Int = a + b    // thân 1 biểu thức, bỏ {}
fun greet(name: String = "bạn") { ... }       // tham số mặc định
greet()                    // dùng mặc định
greet(name = "Phong")      // gọi bằng tên tham số (named argument)
```
**Tham số mặc định + gọi theo tên** dùng cực nhiều trong app. Ví dụ [ApiService.kt:31](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L31):
```kotlin
suspend fun listTasks(
    status: String? = null,
    category: String? = null,
    ...
)
// gọi: api.listTasks(category = "WORK")   ← chỉ truyền cái cần
```
Đây là cách Kotlin thay cho "hàm nạp chồng" (overload) mà bạn hay viết ở Java/Go.

## 4. `data class` — DTO/struct của Kotlin

```kotlin
data class Task(
    val id: String,
    val title: String,
    val priority: String
)
```
Tương đương một `struct` trong Go, nhưng compiler tự sinh: `equals()`, `hashCode()`, `toString()`, và đặc biệt **`copy()`**:

```kotlin
val t2 = task.copy(priority = "HIGH")   // tạo bản sao, chỉ đổi 1 field
```
`copy()` là "vũ khí" chính để cập nhật state bất biến (bài 05). Xem [TaskListViewModel.kt:65](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L65):
```kotlin
_uiState.update { it.copy(tasks = result.tasks, isLoading = false) }
```

### `@SerializedName` — map JSON
Backend Go của bạn trả JSON `snake_case`, Kotlin quen `camelCase`. Thư viện Gson dùng annotation để nối:
```kotlin
@SerializedName("due_date") val dueDate: String?
```
Giống hệt struct tag trong Go: `` `json:"due_date"` ``.

## 5. `class`, `object`, `companion object`

```kotlin
class SessionManager(context: Context) { ... }   // class thường, có constructor
```
- **`object`** = **singleton** do ngôn ngữ đảm bảo (chỉ có đúng 1 thực thể). Ví dụ [ServiceLocator.kt:23](../app/app/src/main/java/com/example/todoapplication/di/ServiceLocator.kt#L23): `object ServiceLocator { ... }` và [NetworkClient.kt:12](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L12). Dùng khi bạn muốn "một cái duy nhất toàn app".
- **`companion object`** = vùng "static" gắn với một class. Ví dụ trong [TaskListViewModel.kt:155](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L155) có `companion object { val Factory = ... }` — truy cập qua `TaskListViewModel.Factory` mà không cần tạo object.

## 6. `sealed` — enum "có dữ liệu"

`sealed interface`/`sealed class` = một tập **hữu hạn, đóng** các kiểu con. Compiler biết hết mọi trường hợp → `when` không cần nhánh `else`. Rất mạnh để mô hình hóa trạng thái.

Xem [UiState.kt](../app/app/src/main/java/com/example/todoapplication/ui/state/UiState.kt):
```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```
và cách dùng (bài 04/05):
```kotlin
when (state) {
    is UiState.Loading -> Spinner()
    is UiState.Success -> ShowData(state.data)   // "smart cast": trong nhánh này state CÓ .data
    is UiState.Error   -> ShowError(state.message)
}   // KHÔNG cần else — compiler biết đã đủ 3 nhánh
```
Đây là cách "type-safe" thay cho việc trả `(data, error)` như Go. Cũng thấy ở [Screen.kt](../app/app/src/main/java/com/example/todoapplication/ui/navigation/Screen.kt) (`sealed class Screen`) và các `TaskListEvent` trong ViewModel.

## 7. `when` — switch nâng cấp

```kotlin
val label = when (priority) {
    "HIGH" -> "Cao"
    "MEDIUM" -> "Vừa"
    else -> "Thấp"
}
```
`when` là **biểu thức** (trả về giá trị) và không "rơi xuyên" (không cần `break`).

## 8. Lambda & higher-order function

Kotlin coi hàm là giá trị. Cú pháp lambda: `{ tham_số -> thân }`.
```kotlin
list.filter { it.status != "COMPLETED" }   // "it" = tham số ngầm định khi chỉ có 1
```
**Quy ước "trailing lambda":** nếu tham số cuối của hàm là lambda, đưa nó ra ngoài `()`:
```kotlin
_uiState.update { it.copy(isLoading = true) }
// thực chất là: _uiState.update({ it -> it.copy(isLoading = true) })
```
Compose và coroutines dựa hoàn toàn vào cú pháp này — bạn sẽ thấy `{ ... }` ở khắp nơi. Ví dụ [TaskListViewModel.kt:119](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L119):
```kotlin
repo.createTask(input).fold(
    onSuccess = { _events.emit(...) },
    onFailure = { _events.emit(...) }
)
```

### `apply`, `let`, `run` (scope functions)
Đường tắt hay gặp:
```kotlin
prefs.edit().apply {          // "apply": chạy block với "this" = prefs.edit(), trả lại chính nó
    putString(KEY_TOKEN, token)
    apply()
}
```
```kotlin
result.getOrNull()?.let { r ->   // "let": chạy block nếu KHÔNG null, "it"/"r" = giá trị
    doSomething(r)
}
```
Thấy trong [SessionManager.kt:26](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L26) và [TaskListViewModel.kt:75](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L75).

## 9. Coroutines: `suspend`, `launch`, `Flow` — phần QUAN TRỌNG NHẤT

Android **cấm chặn luồng chính** (main/UI thread). Nếu bạn gọi mạng đồng bộ trên UI thread, app đơ và bị OS kill. Giải pháp của Kotlin là **coroutines** — code bất đồng bộ nhưng viết như đồng bộ.

### `suspend fun`
Hàm gắn `suspend` = "hàm này có thể *tạm dừng* rồi chạy tiếp mà không chặn luồng". Chỉ gọi được từ trong coroutine hoặc một `suspend fun` khác.
```kotlin
suspend fun loadTasks(...): TaskListResult { ... }   // TaskRepository
```
Retrofit hiểu `suspend`: bạn khai báo `suspend fun listTasks(): Response<...>` là nó tự chạy nền. So với Go: `suspend` ≈ tinh thần của goroutine, nhưng do compiler biến đổi, không phải OS thread.

### `viewModelScope.launch { }`
"Khởi động một coroutine". `viewModelScope` là phạm vi gắn với ViewModel — khi ViewModel bị hủy, coroutine tự bị hủy theo (không rò rỉ). Xem [TaskListViewModel.kt:61](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L61):
```kotlin
fun loadTasks(...) {
    _uiState.update { it.copy(isLoading = true) }   // chạy ngay (đồng bộ)
    viewModelScope.launch {                          // mở coroutine
        try {
            val result = repo.loadTasks(...)         // suspend: chờ mạng, KHÔNG chặn UI
            _uiState.update { it.copy(tasks = result.tasks, isLoading = false) }
        } catch (e: Exception) {
            _events.emit(TaskListEvent.Message("Lỗi: ${e.message}"))
        }
    }
}
```
Đọc từ trên xuống như code tuần tự, nhưng dòng `repo.loadTasks(...)` **không chặn** — luồng UI vẫn mượt.

### `Flow` và `StateFlow` — luồng dữ liệu theo thời gian
- `Flow<T>` = một "dòng chảy" các giá trị bất đồng bộ (như channel Go, nhưng lười và có nhiều toán tử).
- `StateFlow<T>` = Flow **luôn có 1 giá trị hiện tại** — hoàn hảo để giữ "state của màn hình". UI "quan sát" nó và tự vẽ lại khi đổi (bài 05).
- `SharedFlow<T>` = luồng cho **sự kiện dùng-một-lần** (hiện Toast, điều hướng) — không giữ state.

```kotlin
private val _uiState = MutableStateFlow(TaskListUiState())  // bản ghi được (private)
val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()  // bản chỉ-đọc (public)
```
Quy ước `_tên` (private, sửa được) + `tên` (public, chỉ đọc) lặp lại khắp mọi ViewModel. Đây là cách đóng gói: UI chỉ được **đọc**, chỉ ViewModel được **ghi**.

## 10. Vài cú pháp lặt vặt sẽ gặp
- **String template:** `"Xin chào $name, bạn có ${tasks.size} việc"` — nhúng biến bằng `$`, biểu thức bằng `${}`.
- **`is` / `as`:** kiểm tra & ép kiểu. `if (x is Task)` rồi trong đó `x` tự thành `Task` (smart cast).
- **`Result<T>`:** kiểu bọc thành công/thất bại, có `.fold(onSuccess, onFailure)`, `.getOrNull()`. Repository dùng để không ném exception lên UI. Xem `safeApiCall` trong [NetworkCallExt.kt](../app/app/src/main/java/com/example/todoapplication/data/repository/NetworkCallExt.kt).
- **Extension function:** `fun String.foo()` — "gắn thêm" hàm vào kiểu có sẵn. Ví dụ `formatUtcToLocal()` trong [DateTimeUtils.kt](../app/app/src/main/java/com/example/todoapplication/ui/utils/DateTimeUtils.kt) hay các hàm `Task.isOverdue()` trong [domain/](../app/app/src/main/java/com/example/todoapplication/domain/).

## 11. Tự kiểm tra
1. Khác nhau giữa `val b: String?` và `val b: String`? Khi nào dùng `?.`, `?:`, `!!`?
2. `task.copy(status = "COMPLETED")` làm gì, và vì sao cách này quan trọng với state bất biến?
3. Vì sao `listTasks` phải là `suspend`? Điều gì xảy ra nếu ta gọi mạng đồng bộ trên UI thread?
4. Phân biệt `StateFlow` và `SharedFlow` — cái nào để giữ trạng thái màn, cái nào để bắn Toast?
5. Trong [TaskListViewModel.kt](../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt), tại sao `_uiState` là `private` còn `uiState` thì `public`?

➡️ Tiếp theo: [Bài 02 — Kiến trúc tổng thể (MVVM)](./02-kien-truc-tong-the.md)
