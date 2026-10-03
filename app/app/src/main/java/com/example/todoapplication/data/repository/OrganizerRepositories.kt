package com.example.todoapplication.data.repository

import androidx.room.withTransaction
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.FocusSessionEntity
import com.example.todoapplication.data.local.HabitCheckinEntity
import com.example.todoapplication.data.local.HabitEntity
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.SortOrder
import com.example.todoapplication.domain.model.FocusRecord
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitDraft
import com.example.todoapplication.domain.model.HabitFrequency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/*
 * [TẦNG DATA · REPOSITORY] Thói quen và lịch sử tập trung — offline-first như task:
 * ghi Room trước (có cờ dirty / chưa đồng bộ) rồi xin SyncEngine đẩy lên server ở nền.
 */

class HabitRepository(
    private val db: AppDatabase,
    private val sync: SyncController,
    /** Đặt lại nhắc thói quen sau khi thói quen đổi (HabitReminderScheduler cần Context — test truyền rỗng). */
    private val rescheduleReminders: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = db.habitDao()

    fun observeHabits(): Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Các ngày đã làm (habitId → tập "yyyy-MM-dd") kể từ [since]. */
    fun observeCheckins(since: String): Flow<Map<String, Set<String>>> =
        dao.observeCheckinsSince(since).map { list ->
            list.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() }
        }

    suspend fun create(draft: HabitDraft): Habit {
        val now = clock()
        val entity = HabitEntity(
            id = UUID.randomUUID().toString(),
            position = SortOrder.forNewTask(now),
            createdAt = now,
            updatedAt = now,
            isDirty = true,
            localVersion = 1,
            name = "", emoji = "", color = "", frequency = HabitFrequency.DAILY, weekdays = "",
            targetPerWeek = 7, reminderTime = "", archived = false
        ).withDraft(draft)
        dao.upsert(entity)
        afterWrite()
        return entity.toDomain()
    }

    suspend fun update(id: String, draft: HabitDraft): Boolean {
        val current = dao.getById(id)?.takeUnless { it.isDeleted } ?: return false
        dao.upsert(current.withDraft(draft).touch())
        afterWrite()
        return true
    }

    /** Xóa (bia mộ tới khi server xác nhận). Hoàn tác bằng [restore]. */
    suspend fun delete(id: String): Boolean {
        val current = dao.getById(id)?.takeUnless { it.isDeleted } ?: return false
        dao.upsert(current.copy(isDeleted = true).touch())
        afterWrite()
        return true
    }

    suspend fun restore(id: String): Boolean {
        val current = dao.getById(id)?.takeIf { it.isDeleted } ?: return false
        dao.upsert(current.copy(isDeleted = false).touch())
        afterWrite()
        return true
    }

    /** Đánh dấu / bỏ đánh dấu thói quen trong ngày [date] ("yyyy-MM-dd"). Trả trạng thái mới. */
    suspend fun toggle(habitId: String, date: String): Boolean {
        val done = db.withTransaction {
            val current = dao.getCheckin(habitId, date)
            val next = !(current?.done ?: false)
            dao.upsertCheckin(HabitCheckinEntity(habitId, date, next, isDirty = true))
            next
        }
        sync.requestSync(delayMillis = SYNC_DELAY_MS)
        return done
    }

    private fun HabitEntity.withDraft(d: HabitDraft): HabitEntity {
        val frequency = d.frequency.takeIf {
            it == HabitFrequency.WEEKDAYS || it == HabitFrequency.WEEKLY
        } ?: HabitFrequency.DAILY
        val weekdays = if (frequency == HabitFrequency.WEEKDAYS) HabitLogic.normalizeWeekdays(d.weekdays) else ""
        return copy(
            name = d.name.trim().take(MAX_NAME),
            emoji = d.emoji.trim(),
            color = d.color.trim(),
            // Chọn "các thứ" mà bỏ trống hết → coi như mỗi ngày
            frequency = if (frequency == HabitFrequency.WEEKDAYS && weekdays.isEmpty()) HabitFrequency.DAILY else frequency,
            weekdays = weekdays,
            targetPerWeek = when (frequency) {
                HabitFrequency.WEEKLY -> d.targetPerWeek.coerceIn(1, 7)
                HabitFrequency.WEEKDAYS -> weekdays.split(",").count { it.isNotBlank() }.coerceAtLeast(1)
                else -> 7
            },
            reminderTime = d.reminderTime.trim(),
            archived = d.archived
        )
    }

    private fun HabitEntity.touch() = copy(isDirty = true, localVersion = localVersion + 1, updatedAt = clock())

    private fun afterWrite() {
        rescheduleReminders()
        sync.requestSync(delayMillis = SYNC_DELAY_MS)
    }

    private companion object {
        const val MAX_NAME = 100
        const val SYNC_DELAY_MS = 1_500L
    }
}

/** Lịch sử tập trung: mỗi phiên Pomodoro xong được ghi lại (chỉ thêm). */
class FocusHistoryRepository(
    private val db: AppDatabase,
    private val sync: SyncController
) {
    private val dao = db.focusDao()

    fun observeSince(since: Long): Flow<List<FocusRecord>> = dao.observeSince(since).map { l -> l.map { it.toDomain() } }

    fun observeForTask(taskId: String): Flow<List<FocusRecord>> =
        dao.observeForTask(taskId).map { l -> l.map { it.toDomain() } }

    suspend fun record(taskId: String?, taskTitle: String, startedAt: Long, minutes: Int) {
        if (minutes <= 0) return
        dao.insert(
            FocusSessionEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                taskTitle = taskTitle.take(255),
                startedAt = startedAt,
                minutes = minutes.coerceAtMost(240)
            )
        )
        sync.requestSync(delayMillis = 5_000)
    }
}
