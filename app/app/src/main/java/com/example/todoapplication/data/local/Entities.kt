package com.example.todoapplication.data.local

import androidx.room.ColumnInfo
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
    // ── Thêm ở v6 (khớp cột migration 000008 của backend) ──
    /** Hạn chỉ có ngày: dueAt lưu ở 23:59 giờ địa phương, hiển thị không kèm giờ. */
    @ColumnInfo(defaultValue = "0") val dueAllDay: Boolean = false,
    /** "Ngày của tôi": ngày (yyyy-MM-dd, giờ địa phương) người dùng chọn làm việc này. */
    val myDay: String? = null,
    @ColumnInfo(defaultValue = "0") val estimatedMinutes: Int = 0,
    @ColumnInfo(defaultValue = "1") val recurrenceInterval: Int = 1,
    @ColumnInfo(defaultValue = "SCHEDULE") val recurrenceMode: String = "SCHEDULE",
    /** Không sinh lần lặp có hạn sau mốc này (epoch millis). */
    val recurrenceUntil: Long? = null,
    // ── Thêm ở v7 (khớp migration 000009 của backend) ──
    /** Nhãn, nối bằng dấu phẩy ("nhà,gấp") — đã chuẩn hóa bằng TagRules. */
    @ColumnInfo(defaultValue = "") val tags: String = "",
    /** Mục trong danh sách (cột Kanban), "" = chưa phân mục. */
    @ColumnInfo(defaultValue = "") val section: String = "",
    /** Giờ bắt đầu (epoch millis) — cùng dueAt tạo khung thời gian. */
    val startAt: Long? = null,
    /** Lời nhắc thêm (phút trước mốc nhắc), nối bằng dấu phẩy ("60,1440"). */
    @ColumnInfo(defaultValue = "") val extraReminders: String = "",
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
    val position: Int,
    /** Hạn của riêng bước này ("yyyy-MM-dd"), thêm ở v7. */
    val dueDate: String? = null
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
    val categoriesVersion: Int = 0,
    // ── v7 ──
    val habitsCursor: String? = null,
    val focusCursor: String? = null
)

/** Kết quả truy vấn: task kèm tiến độ checklist (đếm bằng subquery ngay trong SQL). */
data class TaskWithProgress(
    @Embedded val task: TaskEntity,
    val subtaskDone: Int,
    val subtaskTotal: Int,
    /** Bước con chưa xong có hạn sớm nhất ("yyyy-MM-dd") và tên của nó. */
    val nextStepDue: String?,
    val nextStepTitle: String?
)

data class StatusCounts(val completed: Int, val pending: Int)

data class CategoryCount(val category: String, val count: Int)

/**
 * Cài đặt hiển thị của một danh sách (kể cả danh sách mặc định): màu, emoji, các mục (cột Kanban).
 * Đồng bộ cùng danh mục (PUT /categories, cờ categoriesDirty).
 */
@Entity(tableName = "list_meta")
data class ListMetaEntity(
    @PrimaryKey val name: String,
    val color: String,
    val emoji: String,
    /** Các mục theo thứ tự, nối bằng xuống dòng (tên mục có thể chứa dấu phẩy). */
    val sections: String
)

/** Thói quen — đồng bộ giống task (cờ dirty + localVersion, bia mộ khi xóa). */
@Entity(tableName = "habits", indices = [Index(value = ["isDeleted", "position"])])
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: String,
    /** DAILY | WEEKDAYS | WEEKLY — xem HabitFrequency. */
    val frequency: String,
    val weekdays: String,
    val targetPerWeek: Int,
    /** "HH:mm" hoặc "" (không nhắc). */
    val reminderTime: String,
    val position: Double,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isDirty: Boolean = false,
    val isDeleted: Boolean = false,
    val localVersion: Int = 0
)

/** Đánh dấu thói quen trong một ngày. done = false là "đã bỏ đánh dấu" (cần gửi lên server). */
@Entity(
    tableName = "habit_checkins",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["date"])]
)
data class HabitCheckinEntity(
    val habitId: String,
    /** "yyyy-MM-dd" theo lịch của máy. */
    val date: String,
    val done: Boolean,
    val isDirty: Boolean = false
)

/** Một phiên tập trung đã xong. Chỉ thêm; isSynced = đã gửi lên server. */
@Entity(tableName = "focus_sessions", indices = [Index(value = ["startedAt"])])
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val taskTitle: String,
    val startedAt: Long,
    val minutes: Int,
    val isSynced: Boolean = false
)

/**
 * Tệp đính kèm của một việc (ảnh chụp / tài liệu quét) — CHỈ lưu trên máy, trong bộ nhớ riêng của app
 * (filesDir/attachments), mở ra ngoài qua FileProvider. Xóa việc hẳn thì đính kèm tự xóa theo (CASCADE).
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["taskId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    /** Tên tệp trong filesDir/attachments. */
    val fileName: String,
    /** MIME, vd "image/jpeg", "application/pdf". */
    val mimeType: String,
    val createdAt: Long
)

/** Địa điểm đã lưu (Nhà, Công ty…) cho nhắc theo vị trí — chỉ trên máy. */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
)

/** Nhắc việc khi ĐẾN (ENTER) hoặc RỜI (EXIT) một địa điểm. Mỗi việc tối đa một nhắc theo địa điểm. */
@Entity(
    tableName = "task_locations",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["placeId"])]
)
data class TaskLocationEntity(
    @PrimaryKey val taskId: String,
    val placeId: String,
    /** ENTER | EXIT */
    val trigger: String
)

/** Kết quả truy vấn: nhắc theo địa điểm của các việc còn chờ. */
data class LocationReminder(
    val taskId: String,
    val title: String,
    val placeId: String,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val trigger: String
)
