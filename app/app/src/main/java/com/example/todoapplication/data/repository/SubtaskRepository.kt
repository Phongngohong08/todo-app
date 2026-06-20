package com.example.todoapplication.data.repository

import android.content.Context
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.SubtaskEntity
import java.util.UUID

/** Tiến độ checklist của một task: số bước đã xong / tổng số bước. */
data class SubtaskProgress(val done: Int, val total: Int)

/** Quản lý các bước con (checklist) lưu cục bộ trong Room. */
class SubtaskRepository(private val appContext: Context) {
    private val dao get() = AppDatabase.get(appContext).subtaskDao()

    suspend fun getForTask(taskId: String): List<SubtaskEntity> = dao.getForTask(taskId)

    suspend fun add(taskId: String, title: String, position: Int) {
        dao.upsert(
            SubtaskEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                title = title.trim(),
                isDone = false,
                position = position
            )
        )
    }

    suspend fun toggle(item: SubtaskEntity) = dao.update(item.copy(isDone = !item.isDone))

    suspend fun delete(item: SubtaskEntity) = dao.delete(item)

    /** Bản đồ taskId → tiến độ, dùng để hiển thị chỉ báo trên thẻ công việc. */
    suspend fun progressByTask(): Map<String, SubtaskProgress> =
        dao.getAll()
            .groupBy { it.taskId }
            .mapValues { (_, list) -> SubtaskProgress(list.count { it.isDone }, list.size) }
}
