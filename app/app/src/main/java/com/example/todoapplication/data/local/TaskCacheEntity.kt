package com.example.todoapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bản sao offline của một task. Dùng để hiển thị danh sách khi mất mạng (offline-first read cache).
 */
@Entity(tableName = "task_cache")
data class TaskCacheEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dueDate: String?,
    val status: String,
    val category: String,
    val recurrence: String,
    val createdAt: String,
    val updatedAt: String,
    val cachedAt: Long          // mốc thời gian cache (epoch millis)
)
