package com.example.todoapplication.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoapplication.di.ServiceLocator

/**
 * [TẦNG DATA · SYNC] Công việc nền của WorkManager: chạy SyncEngine khi có mạng.
 * WorkManager lo phần khó: chờ có mạng, thử lại theo backoff, chạy tiếp sau khi app bị tắt hoặc máy khởi động lại.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = ServiceLocator.syncEngine.sync()) {
        SyncResult.Success, SyncResult.NotLoggedIn -> Result.success()
        SyncResult.AuthError -> Result.failure()   // phiên hết hạn: NetworkClient đã đưa người dùng về Login
        else -> if (result.isRetryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
