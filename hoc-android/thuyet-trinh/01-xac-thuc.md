# Thẻ 01 — Xác thực (Đăng nhập / Đăng ký / Phiên / Token)

⬅️ [Về mục lục thuyết trình](./README.md)

---

## 🔹 Đăng nhập

**Một câu:** Nhập email + mật khẩu → gọi `POST auth/login` → nhận cặp token + thông tin user → lưu vào phiên mã hóa → điều hướng vào app.

**Sơ đồ:**
```
LoginScreen (nút Đăng nhập)
   → LoginViewModel.login(email, password)         AuthViewModels.kt:30
      → AuthRepository.login(email, password)        Repositories.kt:22
         → api.login(LoginInput)  ── POST auth/login  ApiService.kt:13
         → sessionManager.saveTokens(...) + saveUser(...)   (lưu phiên)
      → phát AuthEvent.Success → màn hình điều hướng sang TaskList
```

**Điểm code cần chỉ:**
- ViewModel — kiểm tra rỗng, bật loading, gọi repo, phát sự kiện: [AuthViewModels.kt:30](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AuthViewModels.kt#L30)
```kotlin
fun login(email: String, password: String) {
    if (email.isBlank() || password.isBlank()) { ...Error("Vui lòng điền đầy đủ..."); return }
    _isLoading.value = true
    viewModelScope.launch {
        val result = authRepository.login(email.trim(), password)
        _isLoading.value = false
        result.fold(
            onSuccess = { _events.emit(AuthEvent.Success("Chào mừng quay trở lại, ${it.user.name}!")) },
            onFailure = { _events.emit(AuthEvent.Error("Tài khoản hoặc mật khẩu không chính xác")) }
        )
    }
}
```
- Repository — gọi API *và lưu phiên khi thành công*: [Repositories.kt:22](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L22)
```kotlin
suspend fun login(email, password): Result<AuthResponse> {
    val result = safeApiCall { api.login(LoginInput(email, password)) }
    result.getOrNull()?.let { auth ->
        sessionManager.saveTokens(auth.token, auth.refreshToken)   // ← lưu token
        sessionManager.saveUser(auth.user.id, auth.user.email, auth.user.name)
    }
    return result
}
```
- Endpoint: [ApiService.kt:13](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L13) — `@POST("auth/login")`

**Có thể bị hỏi:**
- *"Token lưu ở đâu, có an toàn không?"* → Lưu bằng **EncryptedSharedPreferences**, mã hóa bằng khóa trong Android Keystore — [SessionManager.saveTokens:26](../../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L26), phần mã hóa [SessionManager.kt:73](../../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L73).
- *"Sao dùng `Result` mà không throw?"* → `safeApiCall` bọc kết quả để UI xử lý bằng `.fold` gọn gàng, không phải try/catch — [NetworkCallExt.kt](../../app/app/src/main/java/com/example/todoapplication/data/repository/NetworkCallExt.kt).
- *"Tại sao thông báo lỗi/thành công dùng event chứ không để trong state?"* → Đó là sự kiện dùng-một-lần (Toast/điều hướng); để trong state sẽ bị lặp khi xoay màn.

---

## 🔹 Đăng ký

**Một câu:** Tương tự đăng nhập nhưng gọi `POST auth/register`, thành công thì báo và cho quay về đăng nhập (không tự đăng nhập).

- ViewModel: [RegisterViewModel.register:60](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AuthViewModels.kt#L60)
- Repository: [AuthRepository.register:31](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L31)
- Endpoint: [ApiService.kt:10](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L10) — `@POST("auth/register")`

---

## 🔹 "Auth guard" — chọn màn khởi đầu theo trạng thái đăng nhập

**Một câu:** Khi mở app, kiểm tra đã có token chưa để quyết định vào thẳng TaskList hay ra màn Login.

- [MainActivity.kt:61](../../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L61)
```kotlin
val startDestination = if (sessionManager.isLoggedIn()) Screen.TaskList.route else Screen.Login.route
```
- `isLoggedIn()` = có token hay không: [SessionManager.kt:63](../../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L63).

---

## 🔹 Tự động làm mới token (điểm "ăn tiền" nhất — nên chủ động khoe)

**Một câu:** Mọi request tự đính token; khi token hết hạn (401), app *tự* gọi refresh lấy token mới rồi phát lại request — người dùng không phải đăng nhập lại.

**Sơ đồ:**
```
Mỗi request → interceptor gắn "Authorization: Bearer <token>"        NetworkClient.kt:50
Request bị 401 → authenticator kích hoạt                             NetworkClient.kt:59
   → gọi POST auth/refresh (bằng client "trần" tránh đệ quy)         NetworkClient.kt:85
   → lưu token mới, phát lại request cũ
   → nếu refresh cũng hỏng → logout + báo forcedLogout → về Login
```

**Điểm code cần chỉ:**
- Gắn token vào mọi request: [NetworkClient.kt:50](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L50)
- Xử lý 401 + refresh: [NetworkClient.kt:59](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L59)
- Khóa để nhiều request 401 cùng lúc chỉ refresh 1 lần: [NetworkClient.kt:66](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L66) (`synchronized(refreshLock)`)
- Buộc về Login khi phiên chết: [MainActivity.kt:51](../../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L51) lắng nghe `SessionEvents.forcedLogout`.

**Có thể bị hỏi:**
- *"Vì sao cần client 'trần' (`bareApi`) riêng cho refresh?"* → Nếu dùng client thường, request refresh lỡ 401 sẽ lại kích hoạt authenticator → đệ quy vô hạn. [NetworkClient.kt:31](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L31).
- *"Nhiều màn cùng gọi API và cùng dính 401 thì sao?"* → Khóa `synchronized`: luồng đầu refresh, các luồng sau thấy token đã mới thì dùng luôn ([NetworkClient.kt:71](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L71)).
- *"ViewModel có biết token được refresh không?"* → **Không.** Toàn bộ logic nằm ở tầng network (middleware), ViewModel chỉ gọi API bình thường. Đây là điểm mạnh kiến trúc.

---

## 🔹 Đăng xuất

**Một câu:** Xóa toàn bộ phiên rồi điều hướng về Login.

- Gọi từ ViewModel: [TaskListViewModel.logout:153](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L153) → [SessionManager.logout:59](../../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L59) (`prefs.edit().clear()`).

⬅️ [Về mục lục thuyết trình](./README.md)
