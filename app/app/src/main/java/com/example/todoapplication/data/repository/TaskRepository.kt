package com.example.todoapplication.data.repository

import androidx.room.withTransaction
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.SubtaskEntity
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.SortOrder
import com.example.todoapplication.domain.model.Subtask
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/** Việc cần làm sau khi task trên máy thay đổi — tách ra để repository không phụ thuộc Android framework. */
fun interface LocalChangeEffects {
    /** [changed]: bản mới của các task vừa ghi; [removedIds]: task vừa bị xóa. */
    fun afterLocalWrite(changed: List<TaskEntity>, removedIds: List<String>)
}

/**
 * [TẦNG DATA · REPOSITORY] Nguồn dữ liệu duy nhất về công việc cho tầng UI — theo kiến trúc offline-first:
 *
 *  - ĐỌC: trả về Flow từ Room. Màn hình tự cập nhật khi dữ liệu đổi (do người dùng, do đồng bộ, do máy khác).
 *  - GHI: ghi thẳng vào Room trong một transaction, đánh dấu "dirty", rồi xin đồng bộ ở nền.
 *    Không chờ mạng → giao diện phản hồi tức thì, dùng được cả khi offline.
 *
 * Không hàm nào ở đây gọi API. Việc gửi/nhận server là của SyncEngine.
 */
class TaskRepository(
    private val db: AppDatabase,
    private val effects: LocalChangeEffects,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private val taskDao = db.taskDao()
    private val subtaskDao = db.subtaskDao()

    // ── Đọc ──────────────────────────────────────────────────────────────────

    fun observeTasks(category: String?, query: String?): Flow<List<Task>> =
        taskDao.observeTasks(category, query?.trim()?.takeIf { it.isNotEmpty() })
            .map { rows -> rows.map { it.toDomain() } }

    fun observeTask(id: String): Flow<Task?> = taskDao.observeById(id).map { it?.toDomain() }

    fun observeSubtasks(taskId: String): Flow<List<Subtask>> =
        subtaskDao.observeForTask(taskId).map { rows -> rows.map { it.toDomain() } }

    /** Task có hạn chót — cho màn Lịch. */
    fun observeScheduled(): Flow<List<Task>> =
        taskDao.observeScheduled().map { rows -> rows.map { it.toDomain() } }

    /** Số task có thay đổi chưa gửi lên server. */
    fun observePendingSyncCount(): Flow<Int> = taskDao.observeDirtyCount().distinctUntilChanged()

    suspend fun pendingSyncCount(): Int = taskDao.dirtyCount()

    suspend fun getTask(id: String): Task? = taskDao.getById(id)?.takeUnless { it.isDeleted }?.toDomain()

    // ── Ghi ──────────────────────────────────────────────────────────────────

    /** Tạo task (id sinh ngay trên máy — server nhận nguyên id này khi đồng bộ). */
    suspend fun create(draft: TaskDraft, subtasks: List<Subtask> = emptyList()): Task {
        val now = clock()
        val entity = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = draft.title.trim(),
            description = draft.description,
            priority = draft.priority,
            dueAt = draft.dueAt,
            status = TaskStatus.TODO,
            category = draft.category,
            recurrence = draft.recurrence,
            recurrenceDays = draft.recurrenceDays,
            reminderOffsetMinutes = draft.reminderOffsetMinutes,
            completedAt = null,
            sortOrder = SortOrder.forNewTask(now),
            spawnedFrom = null,
            createdAt = now,
            updatedAt = now,
            isDirty = true,
            localVersion = 1
        )
        db.withTransaction {
            taskDao.upsert(entity)
            subtaskDao.upsertAll(subtasks.mapIndexed { i, s ->
                SubtaskEntity(id = s.id, taskId = entity.id, title = s.title.trim(), isDone = s.isDone, position = i)
            })
        }
        effects.afterLocalWrite(listOf(entity), emptyList())
        return entity.toDomain()
    }

    /** Sửa nội dung task. Trả false nếu task không còn (đã bị xóa ở nơi khác). */
    suspend fun update(id: String, draft: TaskDraft): Boolean = mutate(id) { current ->
        listOf(
            current.copy(
                title = draft.title.trim(),
                description = draft.description,
                priority = draft.priority,
                dueAt = draft.dueAt,
                category = draft.category,
                recurrence = draft.recurrence,
                recurrenceDays = draft.recurrenceDays,
                reminderOffsetMinutes = draft.reminderOffsetMinutes
            )
        )
    }

    suspend fun setPriority(id: String, priority: String): Boolean = mutate(id) { current ->
        if (current.priority == priority) emptyList() else listOf(current.copy(priority = priority))
    }

    /**
     * Hoàn thành / mở lại. Với việc lặp, hoàn thành sẽ tạo NGAY lần kế tiếp (id tất định, giống server) để
     * người dùng thấy nó cả khi offline; mở lại thì thu hồi lần kế tiếp nếu nó chưa được làm.
     */
    suspend fun setCompleted(id: String, completed: Boolean): Boolean = mutate(id) { current ->
        when {
            completed && current.status != TaskStatus.COMPLETED -> {
                val now = clock()
                listOfNotNull(
                    current.copy(status = TaskStatus.COMPLETED, completedAt = now),
                    spawnNextOccurrence(current, now)
                )
            }
            !completed && current.status == TaskStatus.COMPLETED ->
                listOfNotNull(
                    current.copy(status = TaskStatus.TODO, completedAt = null),
                    retractNextOccurrence(current)
                )
            else -> emptyList()
        }
    }

    /** Xóa (giữ "bia mộ" để đồng bộ việc xóa và cho phép [restore] trong vài phút). */
    suspend fun delete(id: String): Boolean = mutate(id) { listOf(it.copy(isDeleted = true)) }

    /** Hoàn tác xóa. */
    suspend fun restore(id: String): Boolean = mutate(id, includeDeleted = true) { current ->
        if (current.isDeleted) listOf(current.copy(isDeleted = false)) else emptyList()
    }

    /**
     * Lưu thứ tự sau khi kéo-thả. [orderedIds] là thứ tự đang hiển thị (đã có vị trí mới của [movedId]).
     * Chỉ task được kéo đổi sortOrder (giá trị nằm giữa hai hàng xóm), trừ khi hết độ chính xác mới đánh số lại.
     */
    suspend fun move(orderedIds: List<String>, movedId: String) {
        val index = orderedIds.indexOf(movedId)
        if (index < 0) return
        val written = db.withTransaction {
            val moved = taskDao.getById(movedId) ?: return@withTransaction emptyList()
            val before = orderedIds.getOrNull(index - 1)?.let { taskDao.getById(it)?.sortOrder }
            val after = orderedIds.getOrNull(index + 1)?.let { taskDao.getById(it)?.sortOrder }
            val value = SortOrder.between(before, after)
            val changed = if (value != null) {
                listOf(moved.copy(sortOrder = value))
            } else {
                val orders = SortOrder.rebalanced(orderedIds.size)
                taskDao.getByIds(orderedIds).map { it.copy(sortOrder = orders[orderedIds.indexOf(it.id)]) }
            }
            changed.map { touch(it) }.also { taskDao.upsertAll(it) }
        }
        if (written.isNotEmpty()) effects.afterLocalWrite(written, emptyList())
    }

    // ── Bước con (nằm trong task: sửa bước con = sửa task, cùng được đồng bộ) ──

    suspend fun addSubtask(taskId: String, title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        return mutate(taskId) { current ->
            val position = subtaskDao.getForTask(taskId).size
            subtaskDao.upsert(SubtaskEntity(UUID.randomUUID().toString(), taskId, clean, isDone = false, position = position))
            listOf(current)
        }
    }

    suspend fun toggleSubtask(taskId: String, subtaskId: String): Boolean = mutate(taskId) { current ->
        val item = subtaskDao.getForTask(taskId).firstOrNull { it.id == subtaskId } ?: return@mutate emptyList()
        subtaskDao.upsert(item.copy(isDone = !item.isDone))
        listOf(current)
    }

    suspend fun deleteSubtask(taskId: String, subtaskId: String): Boolean = mutate(taskId) { current ->
        val remaining = subtaskDao.getForTask(taskId).filter { it.id != subtaskId }
        subtaskDao.replaceForTask(taskId, remaining.mapIndexed { i, s -> s.copy(position = i) })
        listOf(current)
    }

    // ── Nội bộ ───────────────────────────────────────────────────────────────

    /**
     * Khung chung cho mọi thao tác sửa: đọc task, để [block] trả về các task cần ghi (có thể kèm task khác,
     * vd lần lặp kế tiếp), đánh dấu dirty + tăng version, ghi tất cả trong MỘT transaction rồi chạy side effect.
     */
    private suspend fun mutate(
        id: String,
        includeDeleted: Boolean = false,
        block: suspend (TaskEntity) -> List<TaskEntity>
    ): Boolean {
        val written = db.withTransaction {
            val current = taskDao.getById(id)
                ?.takeIf { includeDeleted || !it.isDeleted }
                ?: return@withTransaction null
            block(current).map { touch(it) }.also { taskDao.upsertAll(it) }
        } ?: return false
        if (written.isNotEmpty()) {
            effects.afterLocalWrite(written, written.filter { it.isDeleted }.map { it.id })
        }
        return true
    }

    private fun touch(entity: TaskEntity) = entity.copy(
        isDirty = true,
        localVersion = entity.localVersion + 1,
        updatedAt = clock()
    )

    /** Tạo lần lặp kế tiếp (gọi bên trong transaction của [setCompleted]). */
    private suspend fun spawnNextOccurrence(parent: TaskEntity, now: Long): TaskEntity? {
        val due = parent.dueAt ?: return null
        if (!RecurrenceRules.isRecurring(parent.recurrence)) return null

        val childId = RecurrenceRules.nextOccurrenceId(parent.id)
        val existing = taskDao.getById(childId)
        if (existing != null && !existing.isDeleted) return null // đã có (vd đồng bộ về từ server)

        val child = TaskEntity(
            id = childId,
            title = parent.title,
            description = parent.description,
            priority = parent.priority,
            dueAt = RecurrenceRules.nextDueAfter(due, parent.recurrence, parent.recurrenceDays, now, zone()),
            status = TaskStatus.TODO,
            category = parent.category,
            recurrence = parent.recurrence,
            recurrenceDays = parent.recurrenceDays,
            reminderOffsetMinutes = parent.reminderOffsetMinutes,
            completedAt = null,
            sortOrder = parent.sortOrder,
            spawnedFrom = parent.id,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            localVersion = existing?.localVersion ?: 0
        )
        taskDao.upsert(child) // phải có task trước khi thêm bước con (khóa ngoại)
        subtaskDao.replaceForTask(childId, subtaskDao.getForTask(parent.id).map {
            it.copy(id = RecurrenceRules.nextOccurrenceId(it.id), taskId = childId, isDone = false)
        })
        return child
    }

    /** Mở lại việc đã xong → bỏ lần lặp đã sinh nếu nó vẫn chưa được làm. */
    private suspend fun retractNextOccurrence(parent: TaskEntity): TaskEntity? {
        val child = taskDao.getById(RecurrenceRules.nextOccurrenceId(parent.id)) ?: return null
        if (child.isDeleted || child.status != TaskStatus.TODO) return null
        return child.copy(isDeleted = true)
    }
}
