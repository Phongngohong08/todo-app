package com.example.todoapplication.data.api

import android.content.Context
import com.example.todoapplication.data.model.RefreshTokenInput
import com.example.todoapplication.data.repository.SessionEvents
import com.example.todoapplication.data.repository.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val BASE_URL = "https://todo.phongngohong.online/api/v1/"
    private var retrofit: Retrofit? = null

    // Khóa đồng bộ để nhiều request gặp 401 cùng lúc chỉ refresh một lần
    private val refreshLock = Any()

    fun getApiService(context: Context): ApiService {
        if (retrofit == null) {
            val appContext = context.applicationContext
            val sessionManager = SessionManager(appContext)

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Client "trần" chỉ dùng để gọi endpoint refresh - KHÔNG gắn authenticator để tránh đệ quy
            val bareClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
            val bareApi = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(bareClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                // Gắn access token vào mọi request
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                    val token = sessionManager.getAuthToken()
                    if (!token.isNullOrEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(requestBuilder.build())
                }
                // Khi gặp 401: thử refresh access token rồi phát lại request
                .authenticator { _, response ->
                    val path = response.request.url.encodedPath
                    // Không refresh cho chính các endpoint auth (login/register/refresh sai = 401 thật)
                    if (path.contains("/auth/")) return@authenticator null
                    // Tránh lặp vô hạn nếu đã thử refresh mà vẫn 401
                    if (responseCount(response) >= 2) return@authenticator null

                    synchronized(refreshLock) {
                        val currentToken = sessionManager.getAuthToken()
                        val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                        // Một luồng khác có thể đã refresh xong -> dùng token mới nhất, không refresh lại
                        if (!currentToken.isNullOrEmpty() && currentToken != usedToken) {
                            return@authenticator response.request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                        }

                        val refreshToken = sessionManager.getRefreshToken()
                        if (refreshToken.isNullOrEmpty()) {
                            sessionManager.logout()
                            SessionEvents.notifyForcedLogout()
                            return@authenticator null
                        }

                        val newAccessToken = try {
                            val refreshResp = bareApi.refreshToken(RefreshTokenInput(refreshToken)).execute()
                            val body = refreshResp.body()
                            if (refreshResp.isSuccessful && body != null) {
                                sessionManager.saveTokens(body.token, body.refreshToken)
                                body.token
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (newAccessToken == null) {
                            // Refresh token hết hạn/không hợp lệ -> xóa phiên, buộc đăng nhập lại
                            sessionManager.logout()
                            SessionEvents.notifyForcedLogout()
                            return@authenticator null
                        }

                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                    }
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }

    // Đếm số response trong chuỗi để giới hạn số lần thử lại
    private fun responseCount(response: okhttp3.Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
