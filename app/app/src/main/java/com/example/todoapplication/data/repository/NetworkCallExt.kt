package com.example.todoapplication.data.repository

/** Lỗi HTTP kèm mã trạng thái, để tầng trên phân biệt (vd 429 = rate limit) và hiển thị thông báo phù hợp. */
class ApiException(val code: Int) : Exception("HTTP $code") {
    /** true nếu server báo quá tải / hết hạn mức AI (HTTP 429). */
    val isRateLimited: Boolean get() = code == 429
}

/**
 * Thông báo cho lỗi khi gọi tính năng AI. Backend trả 429 khi người dùng gửi quá nhanh / hết lượt AI
 * trong ngày / Gemini hết quota, và 503 khi server chưa cấu hình AI; các lỗi khác dùng [fallback].
 */
fun Throwable.aiFailureMessage(fallback: String): String = when {
    this is ApiException && isRateLimited ->
        "Bạn đã dùng hết lượt AI hoặc thao tác quá nhanh. Vui lòng thử lại sau."
    this is ApiException && code == 503 -> "Tính năng AI tạm thời chưa khả dụng."
    else -> fallback
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
