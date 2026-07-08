# Bài 09 — Điều hướng (Navigation Compose)

App có nhiều màn: Login, Register, TaskList, TaskDetail, DailyPlan, Stats, Settings... Nhưng nhớ bài 03: app này chỉ có **một Activity**. Vậy "chuyển màn" thực chất là **đổi Composable nào đang hiển thị** bên trong Activity đó. Thư viện lo việc này là **Navigation Compose**.

Với dev backend: coi `NavHost` như **router (mux/gin)**, mỗi `route` là một path, và `NavController` là thứ bạn dùng để "redirect" giữa các path.

## 1. Ba mảnh của Navigation

1. **Danh mục route** — chuỗi định danh mỗi màn.
2. **`NavController`** — bộ điều khiển: `.navigate(...)`, quản lý *back stack*.
3. **`NavHost`** — bảng ánh xạ `route → Composable`.

## 2. Route: định nghĩa tập trung, type-safe

Thay vì rải chuỗi `"task_detail"` khắp nơi (dễ gõ sai), app gom vào một `sealed class`. Xem [Screen.kt](../app/app/src/main/java/com/example/todoapplication/ui/navigation/Screen.kt):
```kotlin
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object TaskList : Screen("task_list")
    object TaskDetail : Screen("task_detail/{taskId}") {   // {taskId} = tham số động
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    object Stats : Screen("stats")
    ...
}
```
- Mỗi màn là một `object` với chuỗi `route`. Dùng `Screen.TaskList.route` thay vì gõ tay `"task_list"` — sai là compiler bắt.
- **`{taskId}`** là **tham số đường dẫn** (giống `:id` trong router). `createRoute("abc")` sinh ra `"task_detail/abc"` — một helper để khỏi tự nối chuỗi.

## 3. NavHost: bảng định tuyến

Toàn bộ bảng route nằm trong [MainActivity.kt:67](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L67):
```kotlin
val navController = rememberNavController()          // tạo & nhớ NavController

val startDestination = if (sessionManager.isLoggedIn())   // chọn màn khởi đầu
    Screen.TaskList.route else Screen.Login.route

NavHost(navController = navController, startDestination = startDestination) {
    composable(Screen.Login.route)    { LoginScreen(navController) }
    composable(Screen.TaskList.route) { TaskListScreen(navController) }

    composable(                                        // route CÓ tham số
        route = Screen.TaskDetail.route,               // "task_detail/{taskId}"
        arguments = listOf(navArgument("taskId") { type = NavType.StringType })
    ) { backStackEntry ->
        val taskId = backStackEntry.arguments?.getString("taskId") ?: "new"   // đọc tham số
        TaskDetailScreen(navController, taskId)
    }
    composable(Screen.Stats.route)    { StatsScreen(navController) }
    ...
}
```
Đọc như một `switch` router: URL nào → render Composable nào. `startDestination` quyết định "trang chủ" — ở đây phụ thuộc **đã đăng nhập hay chưa** (đọc từ [SessionManager](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt), bài 07). Đây là "auth guard" ở mức điều hướng.

## 4. Chuyển màn & truyền tham số

Bất kỳ màn nào cầm `navController` đều điều hướng được:
```kotlin
// mở màn chi tiết của một task cụ thể (truyền id qua path)
navController.navigate(Screen.TaskDetail.createRoute(task.id))

// mở màn tạo task mới (dùng id giả "new")
navController.navigate(Screen.TaskDetail.createRoute("new"))

// quay lại màn trước (như nút Back)
navController.popBackStack()
```
Phía nhận (`TaskDetailScreen`) đọc `taskId` từ `arguments` như trên. Nếu `taskId == "new"` thì là chế độ tạo mới, ngược lại là sửa — một quy ước gọn của app.

## 5. Back stack — ngăn xếp màn hình

Navigation giữ một **ngăn xếp (stack)** các màn đã mở, để nút Back của Android hoạt động tự nhiên: `A → B → C`, bấm Back về `B`, rồi `A`. `navController.navigate()` **đẩy** màn mới lên đỉnh; `popBackStack()` **lấy ra**.

Đôi khi bạn muốn *xóa* lịch sử — ví dụ sau khi đăng nhập, không cho Back về màn Login. Xem xử lý "forced logout" ở [MainActivity.kt:51](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L51):
```kotlin
LaunchedEffect(Unit) {
    SessionEvents.forcedLogout.collect {                 // phiên hết hạn (bài 06)
        navController.navigate(Screen.Login.route) {
            popUpTo(navController.graph.id) { inclusive = true }  // xóa TOÀN BỘ back stack
            launchSingleTop = true                                // không tạo Login trùng
        }
    }
}
```
- **`popUpTo(...) { inclusive = true }`** — gỡ hết mọi màn trước đó khỏi stack, để người dùng không Back ngược về màn đã đăng xuất.
- **`launchSingleTop = true`** — nếu màn đích đã ở đỉnh thì đừng chồng thêm bản nữa (tránh mở 2 Login).

Đây là mẫu chuẩn cho các luồng "đăng nhập/đăng xuất": chuyển màn *và dọn lịch sử*.

## 6. Chuyển tab dưới đáy (Bottom Bar)

App có thanh điều hướng đáy ([AppBottomBar](../app/app/src/main/java/com/example/todoapplication/ui/components/AppBottomBar.kt)) để nhảy giữa các mục chính (Việc, Kế hoạch, Thống kê...). Bấm mỗi tab cũng chỉ là gọi `navController.navigate(Screen.Xxx.route)`. Để tránh chồng chất back stack khi bấm tab qua lại, các app thường thêm `launchSingleTop` và `popUpTo` — bạn sẽ thấy cấu hình tương tự trong component đó.

## 7. Tự kiểm tra
1. App có bao nhiêu Activity? "Chuyển màn" thực chất là gì?
2. `NavHost` giống thành phần nào ở backend?
3. Truyền `taskId` từ màn danh sách sang màn chi tiết đi qua những bước nào (nơi gửi, nơi khai báo, nơi đọc)?
4. `startDestination` được quyết định thế nào, và điều đó thể hiện "auth guard" ở đâu?
5. Sau khi đăng nhập, làm sao để nút Back không quay về màn Login? (giải thích `popUpTo` + `inclusive`)

➡️ Tiếp theo: [Bài 10 — Chạy nền & Thông báo](./10-background-va-thong-bao.md)
