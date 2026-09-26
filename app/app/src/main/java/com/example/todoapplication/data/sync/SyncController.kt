package com.example.todoapplication.data.sync

import com.example.todoapplication.data.local.SyncStateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [TẦNG DATA · SYNC] Điểm vào duy nhất về đồng bộ cho tầng ViewModel:
 * trạng thái mạng, đang đồng bộ hay không, đã từng đồng bộ chưa, và "đồng bộ ngay".
 */
class SyncController(
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val connectivity: ConnectivityObserver,
    private val syncStateDao: SyncStateDao
) {
    val isOnline: Flow<Boolean> get() = connectivity.isOnline

    val isSyncing: Flow<Boolean> get() = scheduler.isSyncing

    /** false cho tới khi lần đồng bộ đầu tiên (sau đăng nhập) thành công — để màn hình hiện "đang tải". */
    val hasSyncedOnce: Flow<Boolean> = syncStateDao.observe()
        .map { it?.lastSyncedAt != null }
        .distinctUntilChanged()

    /** Đồng bộ ngay và chờ kết quả (kéo-để-làm-mới, trước khi đăng xuất). */
    suspend fun syncNow(): SyncResult = engine.sync()

    /** Xin đồng bộ ở nền (WorkManager tự chờ có mạng). */
    fun requestSync(delayMillis: Long = 0) = scheduler.requestSync(delayMillis)

    /** Gọi sau khi đăng nhập / mở app khi đã đăng nhập. */
    fun start() {
        scheduler.schedulePeriodic()
        scheduler.requestSync(0)
    }

    fun stop() = scheduler.cancelAll()
}
