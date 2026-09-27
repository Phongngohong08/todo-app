package com.example.todoapplication.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.data.local.subtaskEntities
import com.example.todoapplication.data.local.toEntity
import com.example.todoapplication.data.local.toInputDto
import com.example.todoapplication.data.model.CategoriesDto
import com.example.todoapplication.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException

/** Kết quả một lần đồng bộ. */
sealed interface SyncResult {
    data object Success : SyncResult
    data object NotLoggedIn : SyncResult
    data object NetworkError : SyncResult
    data object AuthError : SyncResult
    data class ServerError(val code: Int) : SyncResult

    /** WorkManager có nên thử lại sau (backoff) không. */
    val isRetryable: Boolean get() = this is NetworkError || this is ServerError
}

/** Được gọi sau khi dữ liệu từ server thay đổi task trên máy (để đặt lại nhắc việc, cập nhật widget). */
fun interface TaskChangeListener {
    suspend fun onTasksChanged(changedIds: Collection<String>, removedIds: Collection<String>)
}

/**
 * [TẦNG DATA · SYNC] Đồng bộ hai chiều giữa Room và server.
 *
 * 1. PUSH: gửi mọi task "dirty" (PUT idempotent / DELETE). Nếu người dùng sửa tiếp trong lúc đang gửi,
 *    localVersion đổi → không xóa cờ dirty → vòng sau gửi tiếp. Không bao giờ mất thay đổi.
 * 2. PULL: lấy thay đổi kể từ con trỏ server_time lần trước. Bản trên máy còn dirty được ưu tiên
 *    (nó sẽ được gửi lên ở lần tới) — chiến lược xung đột "ghi sau cùng thắng" ở mức task.
 *
 * Mutex bảo đảm chỉ một lần đồng bộ chạy tại một thời điểm dù được gọi từ WorkManager, kéo-để-làm-mới
 * hay lúc đăng xuất. Mọi thao tác mạng đều idempotent nên bị hủy giữa chừng (vd WorkManager REPLACE) vẫn an toàn.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val api: ApiService,
    private val currentUserId: () -> String?,
    private val listener: TaskChangeListener,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val taskDao = db.taskDao()
    private val subtaskDao = db.subtaskDao()
    private val categoryDao = db.categoryDao()
    private val syncStateDao = db.syncStateDao()

    /** Chạy [block] khi không có lần đồng bộ nào đang chạy (vd xóa dữ liệu lúc đăng xuất). */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun sync(): SyncResult = mutex.withLock {
        val userId = currentUserId() ?: return@withLock SyncResult.NotLoggedIn
        try {
            val forceFullPull = pushTasks()
            pushCategories()
            pullTasks(userId, forceFullPull)
            pullCategories(userId)
            taskDao.purgeTombstones(before = clock() - TOMBSTONE_TTL_MS)
            syncStateDao.update { it.copy(lastSyncedAt = clock()) }
            SyncResult.Success
        } catch (e: SyncFailure) {
            e.result
        } catch (e: IOException) {
            SyncResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // JSON hỏng, lỗi logic... — không làm sập WorkManager, thử lại sau
            Log.e(TAG, "Sync failed", e)
            SyncResult.ServerError(-1)
        }
    }

    // ── PUSH ─────────────────────────────────────────────────────────────────

    /** Trả true nếu có thay đổi bị server từ chối vĩnh viễn → cần tải lại toàn bộ để khôi phục bản server. */
    private suspend fun pushTasks(): Boolean {
        var rejected = false
        repeat(MAX_PUSH_ROUNDS) {
            val dirty = taskDao.getDirty()
            if (dirty.isEmpty()) return rejected
            dirty.forEach { task -> if (!pushTask(task)) rejected = true }
        }
        return rejected
    }

    /** Trả false nếu server từ chối thay đổi này (dữ liệu không hợp lệ). */
    private suspend fun pushTask(task: TaskEntity): Boolean {
        if (task.isDeleted) {
            val resp = api.deleteTask(task.id)
            // 404: server chưa từng có task này (tạo rồi xóa khi offline) — coi như đã xong
            if (resp.isSuccessful || resp.code() == 404) {
                taskDao.markSynced(task.id, task.localVersion) // giữ lại bia mộ để còn Hoàn tác được
                return true
            }
            throw failure(resp)
        }

        val resp = api.saveTask(task.id, task.toInputDto(subtaskDao.getForTask(task.id)))
        val body = resp.body()
        return when {
            resp.isSuccessful && body != null -> {
                db.withTransaction {
                    // Chỉ nhận bản của server nếu người dùng không sửa thêm trong lúc chờ phản hồi
                    val current = taskDao.getById(task.id)
                    if (current != null && current.localVersion == task.localVersion && !current.isDeleted) {
                        taskDao.upsert(body.toEntity().copy(localVersion = current.localVersion))
                        subtaskDao.replaceForTask(task.id, body.subtaskEntities())
                    }
                }
                true
            }
            resp.code() in PERMANENT_REJECTIONS -> {
                Log.w(TAG, "Server rejected task ${task.id}: HTTP ${resp.code()}")
                taskDao.markSynced(task.id, task.localVersion)
                false
            }
            else -> throw failure(resp)
        }
    }

    private suspend fun pushCategories() {
        val state = syncStateDao.get() ?: return
        if (!state.categoriesDirty) return
        val resp = api.replaceCategories(CategoriesDto(categoryDao.getNames()))
        if (resp.isSuccessful || resp.code() in PERMANENT_REJECTIONS) {
            syncStateDao.markCategoriesSynced(state.categoriesVersion)
        } else {
            throw failure(resp)
        }
    }

    // ── PULL ─────────────────────────────────────────────────────────────────

    private suspend fun pullTasks(userId: String, forceFull: Boolean) {
        val cursor = if (forceFull) null else syncStateDao.get()?.tasksCursor
        val resp = api.syncTasks(cursor)
        val body = resp.body() ?: throw failure(resp)

        var changedIds: List<String> = emptyList()
        var removedIds: List<String> = emptyList()

        db.withTransaction {
            // Người dùng đã đăng xuất (hoặc đổi tài khoản) trong lúc chờ mạng → bỏ dữ liệu vừa tải
            if (currentUserId() != userId) return@withTransaction

            val dirty = taskDao.getDirtyIds().toHashSet()
            val tombstones = taskDao.getTombstoneIds().toHashSet()

            val incoming = body.tasks.orEmpty().filter { it.id !in dirty }
            if (incoming.isNotEmpty()) {
                taskDao.upsertAll(incoming.map { it.toEntity() })
                incoming.forEach { subtaskDao.replaceForTask(it.id, it.subtaskEntities()) }
            }

            // Xóa ở thiết bị khác → thành bia mộ để hiện trong Thùng rác (khôi phục được), không xóa hẳn
            val deletedElsewhere = body.deletedIds.orEmpty().filter { it !in dirty && it !in tombstones }
            deletedElsewhere.chunked(SQL_BATCH).forEach { taskDao.markDeletedFromServer(it, clock()) }

            val gone = mutableListOf<String>()
            if (cursor == null) {
                // Lần tải toàn bộ: task sạch trên máy mà server không còn → đã bị xóa ở nơi khác từ lâu
                val onServer = body.tasks.orEmpty().mapTo(HashSet()) { it.id }
                gone += taskDao.getCleanIds().filter { it !in onServer && it !in tombstones }
            }
            gone.chunked(SQL_BATCH).forEach { taskDao.deleteByIds(it) }

            syncStateDao.update { it.copy(tasksCursor = body.serverTime) }
            changedIds = incoming.map { it.id }
            removedIds = deletedElsewhere + gone
        }

        if (changedIds.isNotEmpty() || removedIds.isNotEmpty()) {
            listener.onTasksChanged(changedIds, removedIds)
        }
    }

    private suspend fun pullCategories(userId: String) {
        if (syncStateDao.get()?.categoriesDirty == true) return // thay đổi trên máy chưa gửi → giữ bản máy
        val resp = api.getCategories()
        val body = resp.body() ?: throw failure(resp)
        db.withTransaction {
            if (currentUserId() != userId) return@withTransaction
            if (syncStateDao.get()?.categoriesDirty == true) return@withTransaction
            categoryDao.replaceAll(body.categories)
        }
    }

    private fun failure(resp: Response<*>): SyncFailure = SyncFailure(
        when (resp.code()) {
            401 -> SyncResult.AuthError
            else -> SyncResult.ServerError(resp.code())
        }
    )

    private class SyncFailure(val result: SyncResult) : Exception()

    companion object {
        private const val TAG = "SyncEngine"
        private const val MAX_PUSH_ROUNDS = 3
        private const val SQL_BATCH = 500 // giới hạn số tham số trong một câu SQL (SQLite cũ: 999)
        /** Giữ bia mộ bằng thời gian Thùng rác (khôi phục được trong 30 ngày), rồi mới dọn. */
        const val TOMBSTONE_TTL_MS = TaskRepository.TRASH_RETENTION_MS
        /**
         * Lỗi do dữ liệu — gửi lại cũng không khác, nên bỏ thay đổi thay vì thử mãi.
         * Cố ý KHÔNG gồm 404: server cũ (chưa hỗ trợ PUT tạo mới) trả 404 cho task mới — coi là lỗi tạm thời
         * và giữ thay đổi, thay vì âm thầm làm mất việc người dùng vừa tạo.
         */
        private val PERMANENT_REJECTIONS = setOf(400, 409, 413, 422)
    }
}
