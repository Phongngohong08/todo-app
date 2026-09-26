package com.example.todoapplication.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * [TẦNG DATA · SYNC] Mặt tiền (facade) lên WorkManager — phần còn lại của app chỉ cần "xin đồng bộ".
 *
 * - [requestSync]: mỗi lần dữ liệu trên máy đổi. Dùng unique work + REPLACE + trễ ngắn = "debounce":
 *   người dùng tích 5 việc liên tiếp chỉ tạo một lần đồng bộ. Ràng buộc CONNECTED: mất mạng thì WorkManager
 *   tự chờ, có mạng lại là chạy — kể cả khi app đã bị đóng.
 * - [schedulePeriodic]: kéo dữ liệu mới từ thiết bị khác định kỳ.
 */
class SyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun requestSync(delayMillis: Long = DEBOUNCE_MS) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    /** true khi WorkManager đang chạy một lần đồng bộ — để UI hiện chỉ báo. */
    val isSyncing: Flow<Boolean> = workManager.getWorkInfosByTagFlow(TAG)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
        .distinctUntilChanged()

    companion object {
        const val TAG = "task_sync"
        private const val ONE_TIME = "sync_once"
        private const val PERIODIC = "sync_periodic"
        private const val DEBOUNCE_MS = 1_000L
    }
}
