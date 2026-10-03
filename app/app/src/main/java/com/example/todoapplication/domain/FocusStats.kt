package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.FocusRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Thống kê tập trung cho tab Tôi. */
data class FocusStats(
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val weekSessions: Int = 0,
    /** Phút tập trung 7 ngày gần nhất, index 6 = hôm nay. */
    val last7: List<Int> = List(7) { 0 },
    /** Việc được tập trung nhiều nhất trong 7 ngày (tên → phút), tối đa 3. */
    val topTasks: List<Pair<String, Int>> = emptyList()
) {
    val isEmpty: Boolean get() = weekMinutes == 0 && todayMinutes == 0
}

object FocusCalculator {
    fun compute(records: List<FocusRecord>, today: LocalDate, zone: ZoneId): FocusStats {
        val start = today.minusDays(6)
        val byDay = IntArray(7)
        val week = records.filter {
            val day = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            val index = (day.toEpochDay() - start.toEpochDay()).toInt()
            if (index in 0..6) {
                byDay[index] += it.minutes
                true
            } else false
        }
        val top = week.filter { it.taskTitle.isNotBlank() }
            .groupBy { it.taskId ?: it.taskTitle }
            .map { (_, list) -> list.first().taskTitle to list.sumOf { it.minutes } }
            .sortedByDescending { it.second }
            .take(3)
        return FocusStats(
            todayMinutes = byDay[6],
            weekMinutes = byDay.sum(),
            weekSessions = week.size,
            last7 = byDay.toList(),
            topTasks = top
        )
    }
}
