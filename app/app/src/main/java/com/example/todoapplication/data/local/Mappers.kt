package com.example.todoapplication.data.local

import com.example.todoapplication.data.model.ChatMessage
import com.example.todoapplication.data.model.SubtaskDto
import com.example.todoapplication.data.model.TaskDto
import com.example.todoapplication.data.model.TaskInputDto
import com.example.todoapplication.domain.model.RecurrenceMode
import com.example.todoapplication.domain.model.Subtask
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskStatus
import com.example.todoapplication.ui.utils.parseIsoMillis
import com.example.todoapplication.ui.utils.toIsoString

/*
 * [TẦNG DATA · MAPPER] Chuyển đổi giữa 3 dạng dữ liệu, mỗi dạng phục vụ một tầng:
 *   TaskDto   (JSON của API, thời gian là chuỗi RFC3339)
 *   TaskEntity (dòng Room, thời gian là epoch millis + cờ đồng bộ)
 *   Task      (domain cho UI/logic, không biết gì về JSON hay SQL)
 */

// ── Room → Domain ──
fun TaskEntity.toDomain(subtaskDone: Int = 0, subtaskTotal: Int = 0) = Task(
    id = id,
    title = title,
    description = description,
    priority = priority,
    dueAt = dueAt,
    status = status,
    category = category,
    recurrence = recurrence,
    recurrenceDays = recurrenceDays,
    reminderOffsetMinutes = reminderOffsetMinutes,
    completedAt = completedAt,
    sortOrder = sortOrder,
    spawnedFrom = spawnedFrom,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subtaskDone = subtaskDone,
    subtaskTotal = subtaskTotal,
    hasPendingSync = isDirty,
    dueAllDay = dueAllDay,
    myDay = myDay,
    estimatedMinutes = estimatedMinutes,
    recurrenceInterval = recurrenceInterval,
    recurrenceMode = recurrenceMode,
    recurrenceUntil = recurrenceUntil,
    deletedAt = if (isDeleted) updatedAt else null
)

fun TaskWithProgress.toDomain() = task.toDomain(subtaskDone, subtaskTotal)

fun SubtaskEntity.toDomain() = Subtask(id = id, title = title, isDone = isDone, position = position)

// ── API → Room ──
fun TaskDto.toEntity(): TaskEntity {
    val created = parseIsoMillis(createdAt) ?: 0L
    return TaskEntity(
        id = id,
        title = title,
        description = description.orEmpty(),
        priority = priority,
        dueAt = parseIsoMillis(dueDate),
        status = status,
        category = category ?: "OTHER",
        recurrence = recurrence ?: "NONE",
        recurrenceDays = recurrenceDays.orEmpty(),
        reminderOffsetMinutes = reminderOffsetMinutes,
        // Bản ghi cũ trước khi server có completed_at: lấy tạm updated_at
        completedAt = parseIsoMillis(completedAt)
            ?: if (status == TaskStatus.COMPLETED) parseIsoMillis(updatedAt) else null,
        sortOrder = sortOrder ?: -created.toDouble(),
        spawnedFrom = spawnedFrom,
        createdAt = created,
        updatedAt = parseIsoMillis(updatedAt) ?: created,
        dueAllDay = dueAllDay ?: false,
        myDay = myDay?.takeIf { it.isNotBlank() },
        estimatedMinutes = estimatedMinutes ?: 0,
        recurrenceInterval = (recurrenceInterval ?: 1).coerceAtLeast(1),
        recurrenceMode = recurrenceMode ?: RecurrenceMode.SCHEDULE,
        recurrenceUntil = parseIsoMillis(recurrenceUntil),
        isDirty = false,
        isDeleted = false,
        localVersion = 0
    )
}

fun TaskDto.subtaskEntities(): List<SubtaskEntity> =
    subtasks.orEmpty().map { SubtaskEntity(id = it.id, taskId = id, title = it.title, isDone = it.done, position = it.position) }

// ── Room → API ──
fun TaskEntity.toInputDto(subtasks: List<SubtaskEntity>) = TaskInputDto(
    id = id,
    title = title,
    description = description,
    priority = priority,
    dueDate = dueAt?.let { toIsoString(it) },
    category = category,
    recurrence = recurrence,
    recurrenceDays = recurrenceDays,
    reminderOffsetMinutes = reminderOffsetMinutes,
    status = status,
    completedAt = completedAt?.let { toIsoString(it) },
    sortOrder = sortOrder,
    subtasks = subtasks.map { SubtaskDto(id = it.id, title = it.title, done = it.isDone, position = it.position) },
    spawnedFrom = spawnedFrom,
    dueAllDay = dueAllDay,
    myDay = myDay.orEmpty(),
    estimatedMinutes = estimatedMinutes,
    recurrenceInterval = recurrenceInterval,
    recurrenceMode = recurrenceMode,
    recurrenceUntil = recurrenceUntil?.let { toIsoString(it) }.orEmpty()
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    role = role,
    content = content,
    createdAt = parseIsoMillis(createdAt) ?: 0L
)
