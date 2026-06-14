package com.example.todoapplication.data.api

import com.example.todoapplication.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Auth
    @POST("auth/register")
    suspend fun register(@Body input: RegisterInput): Response<User>

    @POST("auth/login")
    suspend fun login(@Body input: LoginInput): Response<AuthResponse>

    // Đổi refresh token lấy cặp token mới. Dùng Call (đồng bộ) để gọi trong OkHttp Authenticator.
    @POST("auth/refresh")
    fun refreshToken(@Body input: RefreshTokenInput): retrofit2.Call<AuthResponse>

    // Preferences
    @GET("preferences")
    suspend fun getPreferences(): Response<UserPreferences>

    @PUT("preferences")
    suspend fun updatePreferences(@Body prefs: UserPreferences): Response<UserPreferences>

    // Tasks
    @POST("tasks")
    suspend fun createTask(@Body input: CreateTaskInput): Response<Task>

    @GET("tasks")
    suspend fun listTasks(
        @Query("status") status: String? = null,
        @Query("due_date_before") dueDateBefore: String? = null,
        @Query("q") query: String? = null,
        @Query("tag") tag: String? = null
    ): Response<List<Task>>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: String): Response<Task>

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body input: UpdateTaskInput): Response<Task>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>

    @POST("tasks/{id}/start")
    suspend fun startTask(@Path("id") id: String): Response<Task>

    @POST("tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): Response<Task>

    @POST("tasks/{id}/postpone")
    suspend fun postponeTask(@Path("id") id: String, @Body input: PostponeTaskInput): Response<Task>

    @POST("tasks/{id}/cancel")
    suspend fun cancelTask(@Path("id") id: String): Response<Task>

    // Daily Plans
    @GET("plans/daily")
    suspend fun getDailyPlan(
        @Query("date") date: String? = null,
        @Query("local_time") localTime: String? = null
    ): Response<DailyPlan>

    @POST("plans/daily/generate")
    suspend fun generateDailyPlan(
        @Query("local_time") localTime: String? = null
    ): Response<DailyPlan>

    // AI Coach
    @POST("ai/chat")
    suspend fun chat(@Body input: ChatInput): Response<ChatResponse>

    // AI Quick Add: tách câu ngôn ngữ tự nhiên thành task có cấu trúc (chưa lưu)
    @POST("ai/parse-task")
    suspend fun parseTask(@Body input: ParseTaskInput): Response<ParsedTask>

    @GET("ai/memories")
    suspend fun listMemories(): Response<List<MemoryItem>>

    @POST("ai/memories/trigger-extraction")
    suspend fun triggerMemoryExtraction(): Response<MemoryExtractionResult>

    @DELETE("ai/memories/{id}")
    suspend fun deleteMemory(@Path("id") id: String): Response<Unit>

    // Statistics
    @GET("stats/summary")
    suspend fun getStatsSummary(): Response<StatsSummary>
}
