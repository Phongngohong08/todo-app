package com.example.todoapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * [TẦNG DATA · ROOM/DAO] Tập truy vấn database cục bộ (SQLite). Ta chỉ viết interface + câu SQL,
 * Room sinh phần hiện thực lúc build và KIỂM TRA SQL ngay khi biên dịch (sai cột là báo lỗi liền).
 * Hàm trả về Flow được Room tự phát lại mỗi khi bảng liên quan thay đổi → UI luôn khớp dữ liệu.
 *
 * Dùng @Upsert thay vì @Insert(REPLACE) cho bảng tasks: REPLACE thực chất là DELETE + INSERT,
 * sẽ kích hoạt ON DELETE CASCADE và xóa sạch bước con của task.
 */

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.*,
            (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id AND s.isDone = 1) AS subtaskDone,
            (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id) AS subtaskTotal
        FROM tasks t
        WHERE t.isDeleted = 0
            AND (:category IS NULL OR t.category = :category)
            AND (:query IS NULL OR t.title LIKE '%' || :query || '%' OR t.description LIKE '%' || :query || '%')
        ORDER BY t.sortOrder ASC, t.createdAt DESC
        """
    )
    fun observeTasks(category: String?, query: String?): Flow<List<TaskWithProgress>>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    fun observeById(id: String): Flow<TaskEntity?>

    /** Kể cả task đã xóa (bia mộ) — dùng cho ghi/đồng bộ. */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    // ── Đồng bộ ──
    @Query("SELECT * FROM tasks WHERE isDirty = 1")
    suspend fun getDirty(): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE isDirty = 1")
    suspend fun getDirtyIds(): List<String>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDirty = 1")
    suspend fun dirtyCount(): Int

    /** Xóa cờ dirty chỉ khi task không bị sửa thêm trong lúc đang gửi (so localVersion). Trả số dòng cập nhật. */
    @Query("UPDATE tasks SET isDirty = 0 WHERE id = :id AND localVersion = :version")
    suspend fun markSynced(id: String, version: Int): Int

    @Query("SELECT id FROM tasks WHERE isDirty = 0")
    suspend fun getCleanIds(): List<String>

    /** Id các "bia mộ" (task đã xóa trên máy, còn giữ để có thể Hoàn tác). */
    @Query("SELECT id FROM tasks WHERE isDeleted = 1")
    suspend fun getTombstoneIds(): List<String>

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Dọn bia mộ đã đồng bộ xong và hết thời gian cho phép Hoàn tác. */
    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND isDirty = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(before: Long)

    // ── Thống kê ──
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed,
               COALESCE(SUM(CASE WHEN status != 'COMPLETED' THEN 1 ELSE 0 END), 0) AS pending
        FROM tasks WHERE isDeleted = 0
        """
    )
    fun observeStatusCounts(): Flow<StatusCounts>

    @Query(
        """
        SELECT category, COUNT(*) AS count FROM tasks
        WHERE isDeleted = 0 AND status != 'COMPLETED'
        GROUP BY category
        """
    )
    fun observePendingByCategory(): Flow<List<CategoryCount>>

    @Query("SELECT completedAt FROM tasks WHERE isDeleted = 0 AND completedAt IS NOT NULL AND completedAt >= :since")
    fun observeCompletedTimes(since: Long): Flow<List<Long>>

    // ── Lịch, widget, nhắc việc ──
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND dueAt IS NOT NULL")
    fun observeScheduled(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' ORDER BY dueAt IS NULL, dueAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<TaskEntity>

    @Query("SELECT id FROM tasks")
    suspend fun getAllIds(): List<String>
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    fun observeForTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getForTask(taskId: String): List<SubtaskEntity>

    @Upsert
    suspend fun upsert(item: SubtaskEntity)

    @Upsert
    suspend fun upsertAll(items: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    /** Thay toàn bộ checklist của một task (khi nhận bản mới từ server). */
    @Transaction
    suspend fun replaceForTask(taskId: String, items: List<SubtaskEntity>) {
        deleteForTask(taskId)
        upsertAll(items)
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT name FROM categories ORDER BY position ASC, name ASC")
    fun observeNames(): Flow<List<String>>

    @Query("SELECT name FROM categories ORDER BY position ASC, name ASC")
    suspend fun getNames(): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(names: List<String>) {
        deleteAll()
        names.forEachIndexed { i, name -> insert(CategoryEntity(name, i)) }
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Upsert
    suspend fun upsert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(messages: List<ChatMessageEntity>) {
        deleteAll()
        messages.forEach { upsert(it) }
    }
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun get(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 0")
    fun observe(): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    /** Đọc-sửa-ghi trong một transaction (tạo dòng mặc định nếu chưa có). */
    @Transaction
    suspend fun update(transform: (SyncStateEntity) -> SyncStateEntity) {
        upsert(transform(get() ?: SyncStateEntity()))
    }

    /** Đánh dấu danh mục đã đổi và cần gửi lên server. */
    suspend fun markCategoriesDirty() = update {
        it.copy(categoriesDirty = true, categoriesVersion = it.categoriesVersion + 1)
    }

    @Query("UPDATE sync_state SET categoriesDirty = 0 WHERE id = 0 AND categoriesVersion = :version")
    suspend fun markCategoriesSynced(version: Int)
}
