package com.example.todoapplication.data.api

import com.example.todoapplication.BuildConfig
import com.example.todoapplication.data.model.RefreshTokenInput
import com.example.todoapplication.data.repository.SessionEvents
import com.example.todoapplication.data.repository.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    // LƯU Ý chọn đúng địa chỉ theo nơi chạy app:
    //  - Máy ảo Android Studio (AVD):     http://10.0.2.2:8080/api/v1/   (10.0.2.2 = máy tính host)
    //  - Máy ảo Genymotion:               http://10.0.3.2:8080/api/v1/
    //  - Điện thoại thật (cùng Wi-Fi):    http://192.168.0.102:8080/api/v1/  (IP LAN của máy tính)
    //  KHÔNG dùng "localhost" vì trên thiết bị Android nó trỏ về chính thiết bị, không phải PC.
    private const val BASE_URL = "https://todo.phongngohong.online/api/v1/"
    private var retrofit: Retrofit? = null

    // Khóa đồng bộ để nhiều request gặp 401 cùng lúc chỉ refresh một lần
    private val refreshLock = Any()

    fun getApiService(sessionManager: SessionManager): ApiService {
        if (retrofit == null) {
            // Chỉ log ở bản debug: body chứa mật khẩu, header chứa token -> không được lọt ra Logcat bản release
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            }

            // Client "trần" chỉ dùng để gọi endpoint refresh - KHÔNG gắn authenticator để tránh đệ quy
            val bareClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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
                // Daily Plan / phân tích trí nhớ gọi Gemini nên có thể mất 15-30s
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
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

                        val refreshResp = try {
                            bareApi.refreshToken(RefreshTokenInput(refreshToken)).execute()
                        } catch (e: IOException) {
                            // Lỗi mạng tạm thời: GIỮ phiên (refresh token vẫn còn hạn), chỉ bỏ qua request này
                            return@authenticator null
                        }

                        val body = refreshResp.body()
                        if (refreshResp.isSuccessful && body != null) {
                            sessionManager.saveTokens(body.token, body.refreshToken)
                            return@authenticator response.request.newBuilder()
                                .header("Authorization", "Bearer ${body.token}")
                                .build()
                        }

                        if (refreshResp.code() in 400..499) {
                            // Server từ chối refresh token (hết hạn/không hợp lệ) -> xóa phiên, buộc đăng nhập lại
                            sessionManager.logout()
                            SessionEvents.notifyForcedLogout()
                        }
                        // 5xx: lỗi phía server, giữ phiên để lần sau thử lại
                        null
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
