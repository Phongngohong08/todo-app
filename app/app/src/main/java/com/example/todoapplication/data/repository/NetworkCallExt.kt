package com.example.todoapplication.data.repository

/** Lỗi HTTP kèm mã trạng thái, để tầng trên phân biệt (vd 429 = rate limit) và hiển thị thông báo phù hợp. */
class ApiException(val code: Int) : Exception("HTTP $code") {
    /** true nếu server báo quá tải / hết hạn mức AI (HTTP 429). */
    val isRateLimited: Boolean get() = code == 429
}

/** Bọc một lời gọi Retrofit thành [Result], dùng chung cho mọi repository. */
internal inline fun <T> safeApiCall(block: () -> retrofit2.Response<T>): Result<T> = try {
    val resp = block()
    val body = resp.body()
    if (resp.isSuccessful && body != null) Result.success(body)
    else Result.failure(ApiException(resp.code()))
} catch (e: Exception) {
    Result.failure(e)
}
