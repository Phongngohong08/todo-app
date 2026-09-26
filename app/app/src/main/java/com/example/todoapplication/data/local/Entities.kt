package com.example.todoapplication.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * [TẦNG DATA · ROOM] Các bảng cục bộ. Room là NGUỒN DỮ LIỆU DUY NHẤT cho giao diện (single source of truth):
 * UI chỉ đọc Room qua Flow; mọi thao tác ghi vào Room trước (tức thì, chạy được khi offline), rồi
 * SyncEngine đẩy thay đổi lên server ở nền.
 */

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["isDeleted", "sortOrder"]),
        Index(value = ["dueAt"]),
        Index(value = ["completedAt"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priority: String,
    val dueAt: Long?,
    val status: String,
    val category: String,
    val recurrence: String,
    val recurrenceDays: String,
    val reminderOffsetMinutes: Int,
    val completedAt: Long?,
    val sortOrder: Double,
    val spawnedFrom: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // ── Siêu dữ liệu đồng bộ (chỉ có trên máy) ──
    /** Có thay đổi chưa gửi lên server. */
    val isDirty: Boolean = false,
    /** Đã xóa trên máy. Giữ lại như "bia mộ" để đồng bộ việc xóa và cho phép Hoàn tác. */
    val isDeleted: Boolean = false,
    /** Tăng mỗi lần sửa trên máy; chỉ xóa cờ dirty nếu version không đổi trong lúc đang gửi. */
    val localVersion: Int = 0
)

/** Bước con thuộc một task — xóa task thì bước con tự xóa theo (ON DELETE CASCADE). */
@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isDone: Boolean,
    val position: Int
)

/** Danh mục do người dùng tự tạo (danh mục mặc định không lưu). */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val position: Int
)

/** Bộ nhớ đệm lịch sử chat với AI Coach — server giữ bản gốc. */
@Entity(tableName = "chat_messages", indices = [Index(value = ["createdAt"])])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,           // "user" | "assistant"
    val content: String,
    val createdAt: Long,
    val isPending: Boolean = false
)

/** Trạng thái đồng bộ (một dòng duy nhất, id = 0). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    /** server_time của lần kéo trước; null = chưa từng đồng bộ → tải toàn bộ. */
    val tasksCursor: String? = null,
    val lastSyncedAt: Long? = null,
    val categoriesDirty: Boolean = false,
    val categoriesVersion: Int = 0
)

/** Kết quả truy vấn: task kèm tiến độ checklist (đếm bằng subquery ngay trong SQL). */
data class TaskWithProgress(
    @Embedded val task: TaskEntity,
    val subtaskDone: Int,
    val subtaskTotal: Int
)

data class StatusCounts(val completed: Int, val pending: Int)

data class CategoryCount(val category: String, val count: Int)
