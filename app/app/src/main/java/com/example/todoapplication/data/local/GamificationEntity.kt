package com.example.todoapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Một dòng duy nhất giữ trạng thái gamification của người dùng trên thiết bị.
 * Dữ liệu này thuần client-side (không có ở backend) nên Room là nơi lưu lý tưởng.
 */
@Entity(tableName = "gamification")
data class GamificationEntity(
    @PrimaryKey val id: Int = 0,          // luôn = 0: chỉ có một dòng
    val totalXp: Int = 0,                 // tổng điểm kinh nghiệm
    val completedCount: Int = 0,          // tổng số việc đã hoàn thành
    val currentStreak: Int = 0,           // chuỗi ngày liên tiếp hiện tại
    val longestStreak: Int = 0,           // chuỗi dài nhất từng đạt
    val lastCompletionDate: String = "",  // "yyyy-MM-dd" của lần hoàn thành gần nhất
    val unlockedBadges: String = ""       // CSV id huy hiệu đã mở khóa
)
