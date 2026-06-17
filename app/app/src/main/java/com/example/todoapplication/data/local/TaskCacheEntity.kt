package com.example.todoapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bản sao offline của một task. Lưu các trường JSON dạng chuỗi (tags) để đơn giản.
 * Dùng để hiển thị danh sách khi mất mạng (offline-first read cache).
 */
@Entity(tableName = "task_cache")
data class TaskCacheEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dueDate: String?,
    val estimatedDuration: Int,
    val preferredTimeStart: String?,
    val preferredTimeEnd: String?,
    val status: String,
    val tagsCsv: String,        // tags nối bằng dấu phẩy
    val recurrence: String,
    val createdAt: String,
    val updatedAt: String,
    val cachedAt: Long          // mốc thời gian cache (epoch millis)
)
