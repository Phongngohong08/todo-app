package com.example.todoapplication.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [TẦNG DOMAIN] Gom thời điểm hoàn thành (completedAt) thành số liệu biểu đồ, theo NGÀY LỊCH của người dùng.
 * (Bản cũ lấy updatedAt và chia 24 giờ, nên sửa một việc đã xong làm nó "nhảy" sang ngày sửa,
 * và việc xong lúc 23h hôm qua có thể bị tính là hôm nay.)
 */
object StatsCalculator {

    /** 7 phần tử: index 6 = hôm nay, index 0 = 6 ngày trước. */
    fun weekly(completedAt: List<Long>, today: LocalDate, zone: ZoneId): List<Int> {
        val counts = IntArray(7)
        completedAt.forEach { millis ->
            val daysAgo = today.toEpochDay() - Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
            if (daysAgo in 0..6) counts[6 - daysAgo.toInt()]++
        }
        return counts.toList()
    }

    /** "yyyy-MM-dd" → số việc hoàn thành trong ngày đó, chỉ tính năm [year]. */
    fun yearly(completedAt: List<Long>, year: Int, zone: ZoneId): Map<String, Int> =
        completedAt
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .filter { it.year == year }
            .groupingBy { it.toString() }
            .eachCount()

    /** Mốc đầu năm (epoch millis) để chỉ truy vấn dữ liệu cần cho bản đồ nhiệt. */
    fun startOfYear(year: Int, zone: ZoneId): Long =
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
}
