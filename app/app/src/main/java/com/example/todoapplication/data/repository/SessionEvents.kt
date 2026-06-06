package com.example.todoapplication.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Kênh sự kiện phiên đăng nhập ở cấp ứng dụng.
 * Cho phép tầng mạng (chạy ở thread nền, không có NavController) báo cho UI biết
 * khi phiên hết hiệu lực để điều hướng về màn Login.
 */
object SessionEvents {
    // extraBufferCapacity = 1 để tryEmit() không bị bỏ qua khi chưa có collector đang treo
    private val _forcedLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forcedLogout: SharedFlow<Unit> = _forcedLogout.asSharedFlow()

    /** Gọi khi không thể làm mới phiên (refresh token hết hạn/không hợp lệ). An toàn từ mọi thread. */
    fun notifyForcedLogout() {
        _forcedLogout.tryEmit(Unit)
    }
}
