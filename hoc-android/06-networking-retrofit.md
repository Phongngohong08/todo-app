# Bài 06 — Networking (Retrofit & OkHttp)

Đây là tầng **thân thuộc nhất** với bạn: gọi HTTP tới chính backend Go của bạn. Kotlin làm việc này bằng bộ đôi **Retrofit** (khai báo API) + **OkHttp** (HTTP client thật, có middleware). Nếu bạn từng viết HTTP client + middleware ở backend, bài này chỉ là "cùng ý tưởng, khác cú pháp".

## 1. Retrofit: khai báo API bằng interface

Thay vì tự tay dựng request, Retrofit cho bạn **khai báo** endpoint bằng một interface có annotation, rồi **tự sinh phần hiện thực**. Xem [ApiService.kt](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt):

```kotlin
interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body input: LoginInput): Response<AuthResponse>

    @GET("tasks")
    suspend fun listTasks(
        @Query("status") status: String? = null,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null
    ): Response<List<Task>>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<Task>

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body input: UpdateTaskInput): Response<Task>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>
}
```

Đọc annotation đúng như định nghĩa route ở backend:
- **`@GET`, `@POST`, `@PUT`, `@DELETE`** — method + path (nối vào `BASE_URL`). `"tasks"` → `https://.../api/v1/tasks`.
- **`@Path("id")`** — thay `{id}` trong path bằng tham số. Giống `:id` trong router của bạn.
- **`@Query("status")`** — thêm query string `?status=...`. Để mặc định `null` → Retrofit **bỏ qua** query đó (khỏi phải viết nhiều overload). Đây là lý do `listTasks` linh hoạt lọc theo nhiều tiêu chí.
- **`@Body`** — serialize object thành JSON làm request body (dùng Gson).
- **`suspend`** — Retrofit tự chạy request trên luồng nền; bạn `await` như code đồng bộ (bài 01, mục 9).
- **`Response<T>`** — bọc kết quả kèm HTTP status. `response.isSuccessful`, `response.code()`, `response.body()`.

> 🔑 Tư duy: interface này là **hợp đồng** giữa app và backend Go. Nó phản chiếu 1-1 các route ở [`backend/`](../backend/). Thêm endpoint mới ở backend → thêm một dòng ở đây.

### DTO map JSON — nhắc lại
`LoginInput`, `AuthResponse`, `Task`... là các `data class` trong [Models.kt](../app/app/src/main/java/com/example/todoapplication/data/model/Models.kt), dùng `@SerializedName("due_date")` để khớp JSON `snake_case` của backend (bài 01, mục 4). Gson (khai báo `GsonConverterFactory`) lo chuyển JSON ↔ object.

## 2. Cách một endpoint được gọi thật

Chưa có gì "chạy" chỉ với interface. Ta cần một **thực thể** do Retrofit sinh ra. Xem [NetworkClient.kt](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt):

```kotlin
retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)                       // gốc URL
    .client(okHttpClient)                    // HTTP client (có middleware, mục 3-4)
    .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ object
    .build()

return retrofit!!.create(ApiService::class.java)  // ← sinh hiện thực của interface
```
`retrofit.create(ApiService::class.java)` trả về một object hiện thực `ApiService` — mọi hàm bạn khai báo giờ gọi được. Repository nhận object này (qua ServiceLocator) và dùng.

`BASE_URL` ở [NetworkClient.kt:18](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L18) — nhớ lưu ý `10.0.2.2` cho máy ảo ở bài 00.

## 3. OkHttp Interceptor = Middleware

Bạn viết middleware ở backend để thêm header, log, auth. OkHttp có **Interceptor** làm y hệt: chặn *mọi* request/response đi qua. App dùng 2 interceptor.

### (a) Logging — in request/response ra Logcat
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY   // log cả body
}
```
Cực hữu ích khi debug: bạn thấy đúng URL, header, JSON gửi/nhận trong cửa sổ **Logcat** của Android Studio. (Ở bản release nên hạ mức log — đừng in body.)

### (b) Gắn token vào MỌI request — [NetworkClient.kt:50](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L50)
```kotlin
.addInterceptor { chain ->
    val requestBuilder = chain.request().newBuilder()
    val token = sessionManager.getAuthToken()
    if (!token.isNullOrEmpty()) {
        requestBuilder.addHeader("Authorization", "Bearer $token")
    }
    chain.proceed(requestBuilder.build())
}
```
Đây **đúng là "auth middleware"**: đọc access token từ [SessionManager](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt) (bài 07) và đính `Authorization: Bearer <token>` vào từng request. `chain.proceed(...)` = "cho request đi tiếp" (như `next()` trong middleware). Nhờ nó, repository không phải tự thêm header ở mỗi lời gọi.

## 4. Authenticator: tự động refresh token (phần "cao cấp", đáng đọc)

Access token (JWT) thường hết hạn sau ít phút. Backend trả **401 Unauthorized** khi token hết hạn. App dùng cơ chế **refresh token** để lấy token mới mà **không bắt người dùng đăng nhập lại**. OkHttp có hook riêng cho việc này: **`authenticator`** — được gọi *tự động khi gặp 401*. Xem [NetworkClient.kt:59](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L59).

Luồng đầy đủ, đọc như một bài toán backend:

```
Request → 401 (token hết hạn)
   │
   ▼ authenticator được gọi
1. Nếu là endpoint /auth/* → thôi (401 thật, không refresh).   → trả null (bỏ cuộc)
2. Nếu đã thử refresh 2 lần → thôi (tránh lặp vô hạn).          → trả null
3. Vào khối synchronized(refreshLock):   ← KHÓA: nhiều request 401 cùng lúc chỉ refresh 1 lần
   3a. Token hiện tại đã khác token vừa dùng? → luồng khác đã refresh rồi → dùng token mới, phát lại.
   3b. Không có refresh token? → logout + báo forced-logout → trả null.
   3c. Gọi POST /auth/refresh (bằng "bareApi" KHÔNG có authenticator, tránh đệ quy):
        - Thành công → lưu cặp token mới → phát lại request cũ với token mới.
        - Thất bại (refresh token cũng hết hạn) → logout + forced-logout → trả null.
```

Vài chi tiết kỹ thuật đắt giá để bạn học:
- **`synchronized(refreshLock)`** ([dòng 66](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L66)) — nếu 5 request cùng dính 401 một lúc, ta **không** muốn refresh 5 lần. Khóa lại; luồng đầu refresh, các luồng sau thấy token đã mới thì xài luôn (3a). Đây là bài toán *concurrency* bạn đã quá quen ở backend, chỉ là ở client.
- **`bareApi`** ([dòng 31-42](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L31)) — một Retrofit "trần" *không* gắn authenticator, chuyên để gọi `/auth/refresh`. Nếu dùng client thường, request refresh lỡ 401 lại kích hoạt authenticator → **đệ quy vô hạn**. Tách client là cách né.
- **`responseCount(response) >= 2`** ([dòng 64](../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L64)) — đếm số lần đã thử để dừng nếu vẫn 401.
- **Buộc đăng nhập lại**: khi refresh cũng thất bại, gọi `SessionEvents.notifyForcedLogout()`. Sự kiện này được [MainActivity.kt:51](../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L51) lắng nghe để đá người dùng về màn Login và xóa back stack. Một ví dụ đẹp về "sự kiện toàn cục" bằng Flow.

> 🔑 Toàn bộ logic phức tạp này nằm gọn ở tầng network. **ViewModel và Repository hoàn toàn không biết token có bị refresh hay không** — chúng chỉ gọi API và nhận kết quả. Đó là sức mạnh của việc đặt "auth cross-cutting" vào middleware.

## 5. Repository dùng ApiService thế nào

Repository (bài 02) là nơi *duy nhất* chạm ApiService. Có 2 kiểu xử lý kết quả trong app:

**Kiểu 1 — ném lỗi, có fallback cache.** [TaskRepository.loadTasks](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L25):
```kotlin
suspend fun loadTasks(category: String?, query: String?): TaskListResult {
    return try {
        val response = api.listTasks(category = category, query = query)
        if (response.isSuccessful) {
            val fresh = response.body() ?: emptyList()
            ...
            TaskListResult(fresh, isOffline = false)
        } else throw IllegalStateException("HTTP ${response.code()}")
    } catch (e: Exception) {
        val cached = TaskCacheRepository.getCached(appContext)   // ← lỗi mạng → đọc cache
        if (cached.isNotEmpty()) TaskListResult(cached, isOffline = true) else throw e
    }
}
```
Đây là logic **offline-first**: online thì lấy mạng + lưu cache; mất mạng thì trả cache kèm cờ `isOffline` để UI hiện banner "đang offline" (bài 07).

**Kiểu 2 — gói vào `Result<T>`, không ném.** Dùng helper `safeApiCall` trong [NetworkCallExt.kt](../app/app/src/main/java/com/example/todoapplication/data/repository/NetworkCallExt.kt):
```kotlin
internal inline fun <T> safeApiCall(block: () -> retrofit2.Response<T>): Result<T> = try {
    val resp = block()
    val body = resp.body()
    if (resp.isSuccessful && body != null) Result.success(body)
    else Result.failure(IllegalStateException("HTTP ${resp.code()}"))
} catch (e: Exception) {
    Result.failure(e)
}
```
Dùng như [TaskRepository.kt:50](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L50):
```kotlin
suspend fun createTask(input: CreateTaskInput): Result<Task> =
    safeApiCall { api.createTask(input) }.onSuccess { ReminderScheduler.schedule(appContext, it) }
```
Rồi ViewModel xử lý bằng `.fold(onSuccess, onFailure)` (bài 01, mục 8). Cách này biến "lỗi" thành *giá trị trả về* thay vì exception — rất giống tinh thần `(result, err)` của Go, gọn cho luồng UI.

## 6. Tự kiểm tra
1. `@Path` khác `@Query` khác `@Body` thế nào? Cho ví dụ mỗi loại từ [ApiService.kt](../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt).
2. Interceptor "gắn token" hoạt động ra sao? Vì sao repository không cần tự thêm header `Authorization`?
3. Giải thích bằng lời: khi access token hết hạn, chuyện gì xảy ra để người dùng *không* phải đăng nhập lại?
4. Vì sao cần `bareApi` riêng cho endpoint refresh? Nếu bỏ nó thì bug gì xuất hiện?
5. Vì sao gọi refresh được đặt trong `synchronized(refreshLock)`?
6. So sánh 2 cách xử lý kết quả (ném-lỗi-fallback vs `Result`). Khi nào dùng cái nào?

➡️ Tiếp theo: [Bài 07 — Lưu trữ cục bộ (Room & Prefs)](./07-luu-tru-cuc-bo.md)
