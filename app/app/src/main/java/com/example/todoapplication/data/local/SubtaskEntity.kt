package com.example.todoapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Một bước con (subtask/checklist item) của một task. Lưu cục bộ trong Room
 * (backend không hỗ trợ subtask) — minh họa quan hệ 1-nhiều theo `taskId`.
 */
@Entity(tableName = "subtask")
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val isDone: Boolean,
    val position: Int
)
