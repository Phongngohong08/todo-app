# Bài 04 — Jetpack Compose (UI)

Đây là phần **mới lạ nhất** với dev backend, và cũng là phần "phép màu" của app hiện đại. Đọc chậm bài này.

## 1. Đổi tư duy: UI "khai báo" thay vì "ra lệnh"

Cách UI **cũ** (imperative): bạn giữ tham chiếu tới từng widget rồi *ra lệnh* sửa nó khi dữ liệu đổi:
```
textView.setText("Xin chào")   // "widget ơi, đổi chữ đi"
button.setEnabled(false)
```
Bạn phải tự đồng bộ UI với dữ liệu — nguồn gốc vô số bug.

Cách của **Compose** (declarative): bạn viết một **hàm mô tả UI trông thế nào ứng với state hiện tại**. Khi state đổi, framework **tự vẽ lại**. Bạn không bao giờ "sửa" widget.
```kotlin
Text(if (isLoggedIn) "Xin chào $name" else "Vui lòng đăng nhập")
```
> 🔑 Công thức để nhớ: **UI = f(state)**. Giao diện là một *hàm* của trạng thái. State đổi → gọi lại hàm → giao diện mới. Giống React nếu bạn từng nghe qua.

Với dev backend, một ẩn dụ: viết Compose giống viết một **template** (như html/template trong Go) — bạn mô tả kết quả theo dữ liệu, engine lo phần render. Chỉ khác: template này *chạy lại tự động* khi dữ liệu đổi.

## 2. `@Composable` — viên gạch xây UI

Một hàm gắn `@Composable` là một "mảnh giao diện". Nó **không trả về gì** — nó *phát ra* (emit) UI khi được gọi. Ví dụ nhỏ trong [CommonComponents.kt:18](../app/app/src/main/java/com/example/todoapplication/ui/components/CommonComponents.kt#L18):

```kotlin
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,          // ← callback: "event đi lên" (bài 02)
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(onClick = onClick, enabled = enabled && !loading, ...) {
        if (loading) {
            CircularProgressIndicator(...)   // đang tải → hiện spinner
        } else {
            Text(text)                        // bình thường → hiện chữ
        }
    }
}
```
Nhận xét quan trọng:
- Composable **lồng nhau**: `Button` chứa `Text`. UI là một **cây** các composable, ghép từ nhỏ đến lớn.
- Nó nhận **dữ liệu vào** (`text`, `loading`) và **callback ra** (`onClick`). Đây đúng tinh thần "state xuống, event lên".
- Logic hiển thị chỉ là Kotlin thường: `if`, `when`, `for`. Muốn hiện danh sách? Lặp. Muốn ẩn/hiện? `if`.

Compose có sẵn thư viện component **Material 3**: `Button`, `Text`, `Card`, `Scaffold`, `TextField`, `CircularProgressIndicator`... App gói lại thành component riêng trong [ui/components/](../app/app/src/main/java/com/example/todoapplication/ui/components/) để tái dùng và đồng bộ giao diện.

## 3. `Modifier` — "trang trí" mọi thành phần

Gần như mọi Composable nhận tham số `modifier`. Đây là chuỗi các "chỉnh sửa" về kích thước, khoảng cách, nền, sự kiện chạm... ghép nối kiểu fluent:

```kotlin
Text(
    "Xin chào",
    modifier = Modifier
        .fillMaxWidth()          // rộng hết chiều ngang
        .padding(16.dp)          // đệm 16dp mỗi phía
        .clickable { onClick() } // biến thành vùng bấm được
)
```
- **Thứ tự có ý nghĩa!** `.padding().background()` khác `.background().padding()` (một cái đệm trong màu, một cái đệm ngoài màu).
- **`dp`** = "density-independent pixel" — đơn vị kích thước tự co giãn theo mật độ màn hình, để trên máy nào cũng "to bằng nhau về mặt vật lý". `sp` là bản tương tự cho cỡ chữ (còn theo cả cỡ chữ hệ thống người dùng chọn).

Bạn thấy `Modifier` dày đặc trong [TaskListScreen.kt](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt).

## 4. Bố cục cơ bản: Column, Row, Box, Lazy*

- **`Column { }`** — xếp con **theo chiều dọc**.
- **`Row { }`** — xếp con **theo chiều ngang**.
- **`Box { }`** — chồng con lên nhau (như position layers).
- **`Spacer(Modifier.height(8.dp))`** — chèn khoảng trống.
- **`LazyColumn { }`** — danh sách cuộn **chỉ tạo item đang thấy** (như RecyclerView). Dùng cho danh sách dài, hiệu năng cao. Đây là cách list task được vẽ:

```kotlin
LazyColumn(state = lazyListState) {
    items(tasks) { task ->            // với mỗi task trong danh sách
        TaskCard(
            task = task,
            onComplete = { taskListViewModel.completeTask(task) },  // event lên
            onClick = { navController.navigate(...) }
        )
    }
}
```
Xem thật trong [TaskListScreen.kt:11](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L11) (`import ...lazy.items`) và phần thân màn hình. `Scaffold` (khung Material chuẩn: top bar + nội dung + bottom bar) cũng được dùng ở đây.

## 5. `remember` và `mutableStateOf` — state cục bộ của UI

Một số trạng thái chỉ thuộc về UI, không đáng đưa lên ViewModel: ô tìm kiếm đang gõ gì, dialog đang mở hay đóng, filter đang chọn. Dùng `remember { mutableStateOf(...) }`:

```kotlin
var searchQuery by remember { mutableStateOf("") }   // TaskListScreen.kt:77
```
Hai phần, hiểu tách bạch:
- **`mutableStateOf("")`** — tạo một "ô state có thể quan sát". Khi giá trị đổi, mọi Composable *đọc* nó sẽ được vẽ lại. **Đây là cơ chế kích hoạt recomposition.**
- **`remember { }`** — "nhớ" giá trị này qua **các lần vẽ lại**. Không có `remember`, mỗi lần recompose sẽ tạo lại `""` và bạn mất chữ đang gõ.

`by` là cú pháp uỷ quyền để đọc/ghi `searchQuery` như biến thường (`searchQuery = "abc"`) thay vì `searchQuery.value`.

> ⚠️ `remember` chỉ sống qua recomposition, **KHÔNG** sống qua khi Activity bị tạo lại (xoay màn). State quan trọng (danh sách task) phải ở **ViewModel** (bài 05). State lặt vặt của UI (dialog mở/đóng) thì `remember` là đủ.

Trong [TaskListScreen.kt:76-94](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L76-L94) bạn thấy một loạt:
```kotlin
var selectedCategoryFilter by remember { mutableStateOf("ALL") }
var showQuickAdd by remember { mutableStateOf(false) }
var taskToDelete by remember { mutableStateOf<Task?>(null) }
```
Đó đều là "trạng thái riêng của màn hình này".

## 6. Recomposition — "vẽ lại" là gì

**Recomposition** = Compose gọi lại các hàm `@Composable` bị ảnh hưởng khi state chúng đọc thay đổi, để cập nhật giao diện. Framework đủ thông minh để **chỉ vẽ lại phần cần thiết**, không vẽ lại cả màn.

Hệ quả bạn cần nhớ:
- Hàm Composable **có thể bị gọi lại rất nhiều lần**, bất kỳ lúc nào → **không đặt việc nặng/hiệu ứng phụ (gọi API, ghi DB) trực tiếp trong thân Composable.** Việc đó thuộc về ViewModel, hoặc gói trong `LaunchedEffect` (mục 8).
- Muốn tính toán đắt đỏ mà khỏi lặp lại mỗi lần vẽ: `remember(khoá) { tính() }` — chỉ tính lại khi `khoá` đổi. Ví dụ [TaskListScreen.kt:86](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L86):
```kotlin
val aiRecommendedIds = remember(tasks) { aiRecommendedIds(tasks) }  // chỉ tính lại khi tasks đổi
```

## 7. Kết nối UI với ViewModel: `collectAsStateWithLifecycle`

Đây là mấu chốt nối bài 04 với bài 05. Trong [TaskListScreen.kt:70](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L70):

```kotlin
val state by taskListViewModel.uiState.collectAsStateWithLifecycle()
val tasks = state.tasks
val isLoading = state.isLoading
```
- `uiState` là `StateFlow` trong ViewModel (bài 01, mục 9).
- `collectAsStateWithLifecycle()` **"cắm" luồng đó vào Compose**: mỗi lần ViewModel phát state mới → biến `state` đổi → Compose **vẽ lại** phần dùng nó. Bản `...WithLifecycle` còn tự ngừng thu khi màn không hiển thị (tiết kiệm pin).
- Từ đây, cả màn hình chỉ là `UI = f(state)`. Ví dụ:
```kotlin
if (isLoading) {
    LoadingState()
} else if (tasks.isEmpty()) {
    EmptyState(...)
} else {
    LazyColumn { items(tasks) { ... } }
}
```

## 8. Side-effect: `LaunchedEffect`

Đôi khi cần "làm gì đó một lần khi màn xuất hiện" (mở kết nối, lắng nghe event, chạy timer) — chứ không phải mỗi lần vẽ. Dùng `LaunchedEffect(khoá)`: chạy coroutine bên trong **một lần khi khoá xuất hiện/đổi**.

Xem [TaskListScreen.kt:97](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L97) — lắng nghe sự kiện dù-một-lần từ ViewModel:
```kotlin
LaunchedEffect(Unit) {                       // Unit = chạy đúng một lần
    taskListViewModel.events.collect { event ->
        when (event) {
            is TaskListEvent.Message ->
                Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            is TaskListEvent.QuickAddReady -> {
                ...
                navController.navigate(...)
            }
        }
    }
}
```
Và [TaskListScreen.kt:112](../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L112) — "debounce" tìm kiếm: chờ 300ms sau khi filter/search đổi rồi mới gọi tải, khỏi bắn API mỗi ký tự:
```kotlin
LaunchedEffect(selectedCategoryFilter, searchQuery) {  // chạy lại mỗi khi 2 khoá này đổi
    kotlinx.coroutines.delay(300)
    taskListViewModel.loadTasks(selectedCategoryFilter, searchQuery)
}
```
Đây là mẫu **cực kỳ phổ biến**: dùng `LaunchedEffect` để chạy tác vụ bất đồng bộ *đúng lúc* trong vòng đời của UI.

## 9. Theme: màu/chữ/hình khối tập trung

Mọi component lấy màu/chữ/shape từ `MaterialTheme` (định nghĩa trong [ui/theme/](../app/app/src/main/java/com/example/todoapplication/ui/theme/)):
```kotlin
containerColor = MaterialTheme.colorScheme.primary
style = MaterialTheme.typography.labelLarge
shape = MaterialTheme.shapes.medium
```
Nhờ vậy đổi theme/màu một chỗ là cả app đổi theo — và hỗ trợ chế độ tối (dark mode) tự nhiên. Toàn bộ UI được bọc trong `TodoApplicationTheme { }` ở [MainActivity.kt:32](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L32).

## 10. Tự kiểm tra
1. Câu "UI = f(state)" nghĩa là gì? Bạn có bao giờ gọi `textView.setText(...)` trong Compose không?
2. Khác nhau giữa state để trong `remember { mutableStateOf() }` và state trong ViewModel? Cái nào sống sót khi xoay màn?
3. Vì sao **không** được gọi API trực tiếp trong thân một hàm `@Composable`? Đặt nó ở đâu?
4. `collectAsStateWithLifecycle()` làm gì để giao diện tự cập nhật khi dữ liệu đổi?
5. `LaunchedEffect(Unit)` khác `LaunchedEffect(searchQuery)` ở điểm nào?

➡️ Tiếp theo: [Bài 05 — ViewModel & quản lý State](./05-viewmodel-va-state.md)
