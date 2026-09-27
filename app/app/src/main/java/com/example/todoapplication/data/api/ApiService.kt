package com.example.todoapplication.data.api

import com.example.todoapplication.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * [TẦNG DATA · API] "Hợp đồng" giữa app và backend Go — khai báo mọi endpoint bằng annotation.
 * Retrofit tự sinh phần hiện thực. Đọc annotation như định nghĩa route: @POST/@GET + path,
 * @Path = tham số đường dẫn, @Query = query string, @Body = JSON body, suspend = chạy nền.
 *
 * Công việc KHÔNG được UI gọi trực tiếp: UI đọc/ghi Room, còn SyncEngine dùng các endpoint
 * `tasks/sync`, `PUT tasks/{id}`, `DELETE tasks/{id}` để đồng bộ hai chiều.
 */
interface ApiService {
    // Auth
    @POST("auth/register")
    suspend fun register(@Body input: RegisterInput): Response<User>

    @POST("auth/login")
    suspend fun login(@Body input: LoginInput): Response<AuthResponse>

    // Đổi refresh token lấy cặp token mới. Dùng Call (đồng bộ) để gọi trong OkHttp Authenticator.
    @POST("auth/refresh")
    fun refreshToken(@Body input: RefreshTokenInput): retrofit2.Call<AuthResponse>

    // Thu hồi refresh token phía server (all_devices=false: chỉ máy này). Gửi refresh token trong body nên vẫn
    // chạy được khi access token đã hết hạn.
    @POST("auth/logout")
    suspend fun logout(@Body input: LogoutInput): Response<Unit>

    // Preferences
    @GET("preferences")
    suspend fun getPreferences(): Response<UserPreferences>

    @PUT("preferences")
    suspend fun updatePreferences(@Body prefs: UserPreferences): Response<UserPreferences>

    // ── Đồng bộ công việc ──
    /** Thay đổi kể từ [since] (server_time lần trước); null = tải toàn bộ lần đầu. */
    @GET("tasks/sync")
    suspend fun syncTasks(@Query("since") since: String?): Response<TaskChangesDto>

    /** Ghi toàn bộ trạng thái task; server tạo mới nếu id chưa có. Gửi lại nhiều lần vẫn an toàn. */
    @PUT("tasks/{id}")
    suspend fun saveTask(@Path("id") id: String, @Body input: TaskInputDto): Response<TaskDto>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>

    // ── Danh mục tự tạo ──
    @GET("categories")
    suspend fun getCategories(): Response<CategoriesDto>

    @PUT("categories")
    suspend fun replaceCategories(@Body input: CategoriesDto): Response<CategoriesDto>

    // Daily Plans
    @GET("plans/daily")
    suspend fun getDailyPlan(
        @Query("date") date: String? = null,
        @Query("local_time") localTime: String? = null,
        @Query("tz") timezone: String? = null
    ): Response<DailyPlan>

    // date = ngày theo lịch của máy, để server không lấy nhầm ngày UTC (0h–7h sáng giờ VN vẫn là "hôm qua" ở UTC)
    @POST("plans/daily/generate")
    suspend fun generateDailyPlan(
        @Query("date") date: String? = null,
        @Query("local_time") localTime: String? = null,
        @Query("tz") timezone: String? = null
    ): Response<DailyPlan>

    // Lưu lịch người dùng chỉnh tay (đổi giờ, bỏ khung) — không gọi AI
    @PUT("plans/daily")
    suspend fun saveDailyPlan(
        @Query("date") date: String,
        @Query("tz") timezone: String?,
        @Body input: SavePlanInput
    ): Response<DailyPlan>

    // AI Coach
    @POST("ai/chat")
    suspend fun chat(@Body input: ChatInput): Response<ChatResponse>

    @GET("ai/chat/history")
    suspend fun chatHistory(@Query("limit") limit: Int): Response<List<ChatMessage>>

    // AI Quick Add: tách câu ngôn ngữ tự nhiên thành task có cấu trúc (chưa lưu)
    @POST("ai/parse-task")
    suspend fun parseTask(@Body input: ParseTaskInput): Response<ParsedTask>

    @GET("ai/memories")
    suspend fun listMemories(): Response<List<MemoryItem>>

    @POST("ai/memories/trigger-extraction")
    suspend fun triggerMemoryExtraction(): Response<MemoryExtractionResult>

    @DELETE("ai/memories/{id}")
    suspend fun deleteMemory(@Path("id") id: String): Response<Unit>
}
