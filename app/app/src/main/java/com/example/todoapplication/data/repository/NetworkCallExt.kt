package com.example.todoapplication.data.repository

/** Bọc một lời gọi Retrofit thành [Result], dùng chung cho mọi repository. */
internal inline fun <T> safeApiCall(block: () -> retrofit2.Response<T>): Result<T> = try {
    val resp = block()
    val body = resp.body()
    if (resp.isSuccessful && body != null) Result.success(body)
    else Result.failure(IllegalStateException("HTTP ${resp.code()}"))
} catch (e: Exception) {
    Result.failure(e)
}
