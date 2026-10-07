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
            (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id) AS subtaskTotal,
            (SELECT s.dueDate FROM subtasks s WHERE s.taskId = t.id AND s.isDone = 0 AND s.dueDate IS NOT NULL
                ORDER BY s.dueDate LIMIT 1) AS nextStepDue,
            (SELECT s.title FROM subtasks s WHERE s.taskId = t.id AND s.isDone = 0 AND s.dueDate IS NOT NULL
                ORDER BY s.dueDate LIMIT 1) AS nextStepTitle
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

    /** Dọn bia mộ đã đồng bộ xong và hết thời gian nằm trong Thùng rác. */
    @Query("DELETE FROM tasks WHERE isDeleted = 1 AND isDirty = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(before: Long)

    /** Người dùng xóa hẳn khỏi Thùng rác — chỉ bia mộ đã gửi việc xóa lên server. */
    @Query("DELETE FROM tasks WHERE id IN (:ids) AND isDeleted = 1 AND isDirty = 0")
    suspend fun purgeSyncedTombstones(ids: List<String>)

    /**
     * Task bị xóa ở thiết bị khác → giữ lại dạng bia mộ (sạch) để nó hiện trong Thùng rác máy này
     * và có thể khôi phục. updatedAt = lúc nhận để tính hạn 30 ngày.
     */
    @Query("UPDATE tasks SET isDeleted = 1, isDirty = 0, updatedAt = :now WHERE id IN (:ids) AND isDeleted = 0")
    suspend fun markDeletedFromServer(ids: List<String>, now: Long)

    /**
     * Thùng rác: việc đã xóa gần đây. Loại lần lặp bị "thu hồi" tự động khi mở lại việc cha (người dùng không
     * xóa nó; khôi phục sẽ tạo bản trùng).
     */
    @Query(
        """
        SELECT * FROM tasks t
        WHERE t.isDeleted = 1 AND t.updatedAt >= :since
            AND NOT (t.spawnedFrom IS NOT NULL AND EXISTS (
                SELECT 1 FROM tasks p WHERE p.id = t.spawnedFrom AND p.isDeleted = 0 AND p.status != 'COMPLETED'))
        ORDER BY t.updatedAt DESC
        """
    )
    fun observeTrash(since: Long): Flow<List<TaskEntity>>

    /** Lịch sử: việc đã hoàn thành, mới nhất trước. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND completedAt IS NOT NULL
        ORDER BY completedAt DESC LIMIT :limit
        """
    )
    fun observeCompleted(limit: Int): Flow<List<TaskEntity>>

    /** Lịch sử dạng phân trang (Paging 3): Room tự sinh PagingSource, chỉ đọc từng trang khi cuộn tới. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0 AND status = 'COMPLETED' AND completedAt IS NOT NULL
        ORDER BY completedAt DESC
        """
    )
    fun pagingCompleted(): androidx.paging.PagingSource<Int, TaskEntity>

    /** Việc chưa xong có hạn tới [until] (quá hạn + hôm nay) — cho thông báo buổi sáng. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND dueAt IS NOT NULL AND dueAt <= :until")
    suspend fun getPendingDueBefore(until: Long): List<TaskEntity>

    /** Việc chưa xong nằm trong "Ngày của tôi" của [day]. */
    @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND myDay = :day")
    suspend fun countMyDayPending(day: String): Int

    /** Thời điểm hoàn thành + hạn chót của việc xong từ [since] — cho Tổng kết tuần. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status = 'COMPLETED' AND completedAt >= :since")
    fun observeCompletedSince(since: Long): Flow<List<TaskEntity>>

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

    /** Việc chưa xong có hạn — đặt lại nhắc việc sau khi khởi động lại máy. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND dueAt IS NOT NULL")
    suspend fun getPendingWithDue(): List<TaskEntity>

    /** Số việc hoàn thành trong khoảng [from, to) — cho widget nhỏ. */
    @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND status = 'COMPLETED' AND completedAt >= :from AND completedAt < :to")
    suspend fun countCompletedBetween(from: Long, to: Long): Int

    /** Widget: việc chưa xong, "Ngày của tôi" hôm nay lên đầu, rồi theo hạn chót. */
    @Query(
        """
        SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED'
        ORDER BY (myDay = :today) DESC, dueAt IS NULL, dueAt ASC LIMIT :limit
        """
    )
    suspend fun getForWidget(today: String, limit: Int): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND myDay = :today ORDER BY sortOrder ASC LIMIT :limit")
    suspend fun getMyDayForWidget(today: String, limit: Int): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks WHERE isDeleted = 0 AND status != 'COMPLETED' AND category = :category
        ORDER BY (myDay = :today) DESC, dueAt IS NULL, dueAt ASC LIMIT :limit
        """
    )
    suspend fun getForWidgetInCategory(today: String, category: String, limit: Int): List<TaskEntity>

    @Query("SELECT id FROM tasks")
    suspend fun getAllIds(): List<String>

    /** Sao lưu: mọi việc chưa xóa. */
    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun getAllAlive(): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE isDeleted = 0 AND category = :category AND section = :section")
    suspend fun getIdsInSection(category: String, section: String): List<String>

    @Query("SELECT id FROM tasks WHERE isDeleted = 0 AND category = :category")
    suspend fun getIdsInCategory(category: String): List<String>
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    fun observeForTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getForTask(taskId: String): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks")
    suspend fun getAll(): List<SubtaskEntity>

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

@Dao
interface ListMetaDao {
    @Query("SELECT * FROM list_meta")
    fun observeAll(): Flow<List<ListMetaEntity>>

    @Query("SELECT * FROM list_meta")
    suspend fun getAll(): List<ListMetaEntity>

    @Query("SELECT * FROM list_meta WHERE name = :name")
    suspend fun get(name: String): ListMetaEntity?

    @Upsert
    suspend fun upsert(meta: ListMetaEntity)

    @Query("DELETE FROM list_meta WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM list_meta")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ListMetaEntity>) {
        deleteAll()
        items.forEach { upsert(it) }
    }
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isDeleted = 0 ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isDeleted = 0 AND archived = 0")
    suspend fun getActive(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Upsert
    suspend fun upsertAll(habits: List<HabitEntity>)

    @Query("SELECT * FROM habits WHERE isDirty = 1")
    suspend fun getDirty(): List<HabitEntity>

    @Query("SELECT id FROM habits WHERE isDirty = 1")
    suspend fun getDirtyIds(): List<String>

    @Query("UPDATE habits SET isDirty = 0 WHERE id = :id AND localVersion = :version")
    suspend fun markSynced(id: String, version: Int): Int

    @Query("DELETE FROM habits WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM habits WHERE isDirty = 0")
    suspend fun getCleanIds(): List<String>

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    @Query("SELECT * FROM habits WHERE isDeleted = 0")
    suspend fun getAllAlive(): List<HabitEntity>

    @Query("SELECT * FROM habit_checkins WHERE done = 1")
    suspend fun getAllDoneCheckins(): List<HabitCheckinEntity>

    // ── Đánh dấu theo ngày ──

    /** Các ngày đã đánh dấu từ [since] ("yyyy-MM-dd") của mọi thói quen còn sống. */
    @Query(
        """
        SELECT c.* FROM habit_checkins c JOIN habits h ON h.id = c.habitId
        WHERE h.isDeleted = 0 AND c.done = 1 AND c.date >= :since
        """
    )
    fun observeCheckinsSince(since: String): Flow<List<HabitCheckinEntity>>

    @Query(
        """
        SELECT c.* FROM habit_checkins c JOIN habits h ON h.id = c.habitId
        WHERE h.isDeleted = 0 AND c.done = 1 AND c.date >= :since
        """
    )
    suspend fun getCheckinsSince(since: String): List<HabitCheckinEntity>

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId AND date = :date")
    suspend fun getCheckin(habitId: String, date: String): HabitCheckinEntity?

    @Upsert
    suspend fun upsertCheckin(checkin: HabitCheckinEntity)

    @Upsert
    suspend fun upsertCheckins(checkins: List<HabitCheckinEntity>)

    @Query("SELECT * FROM habit_checkins WHERE isDirty = 1")
    suspend fun getDirtyCheckins(): List<HabitCheckinEntity>

    @Query("UPDATE habit_checkins SET isDirty = 0 WHERE habitId = :habitId AND date = :date AND done = :done")
    suspend fun markCheckinSynced(habitId: String, date: String, done: Boolean)

    @Query("SELECT habitId || '|' || date FROM habit_checkins WHERE isDirty = 1")
    suspend fun getDirtyCheckinKeys(): List<String>

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCheckin(habitId: String, date: String)

    /** Đã đánh dấu thói quen này hôm nay chưa (cho nhắc thói quen). */
    @Query("SELECT COUNT(*) FROM habit_checkins WHERE habitId = :habitId AND date = :date AND done = 1")
    suspend fun isDone(habitId: String, date: String): Int

    /** Số lần đã làm trong khoảng ngày [from, to] (cho thói quen "N lần/tuần"). */
    @Query("SELECT COUNT(*) FROM habit_checkins WHERE habitId = :habitId AND done = 1 AND date >= :from AND date <= :to")
    suspend fun countDone(habitId: String, from: String, to: String): Int
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun observeForTask(taskId: String): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sessions: List<FocusSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<FocusSessionEntity>

    @Query("UPDATE focus_sessions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAll(): List<FocusSessionEntity>
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun observeForTask(taskId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Insert
    suspend fun insert(item: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM task_locations WHERE taskId = :taskId")
    fun observeForTask(taskId: String): Flow<TaskLocationEntity?>

    @Upsert
    suspend fun setTaskLocation(item: TaskLocationEntity)

    @Query("DELETE FROM task_locations WHERE taskId = :taskId")
    suspend fun clearTaskLocation(taskId: String)

    /** Nhắc theo địa điểm của các việc còn chờ (để đăng ký geofence và hiện thông báo). */
    @Query(
        """
        SELECT l.taskId, t.title, p.id AS placeId, p.name AS placeName, p.latitude, p.longitude, p.radiusMeters, l.`trigger`
        FROM task_locations l
        JOIN tasks t ON t.id = l.taskId
        JOIN places p ON p.id = l.placeId
        WHERE t.isDeleted = 0 AND t.status != 'COMPLETED'
        """
    )
    suspend fun activeReminders(): List<LocationReminder>
}
