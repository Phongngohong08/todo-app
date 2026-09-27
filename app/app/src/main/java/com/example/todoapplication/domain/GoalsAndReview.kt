package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * [TẦNG DOMAIN] Mục tiêu hằng ngày + chuỗi ngày (kiểu Todoist Karma) và Tổng kết tuần (kiểu "Weekly Review" của GTD).
 *
 * "Ngày hoàn hảo" cũ chỉ cần xong ≥1 việc nên quá dễ, không tạo động lực. Ở đây người dùng tự đặt mục tiêu
 * (vd 5 việc/ngày); chuỗi chỉ tăng khi đạt mục tiêu, và NGÀY NGHỈ (vd T7, CN) không làm đứt chuỗi.
 */

data class GoalProgress(
    val goal: Int,
    val doneToday: Int,
    /** Số ngày liên tiếp đạt mục tiêu (tính cả hôm nay nếu đã đạt; hôm nay chưa đạt thì chưa làm đứt chuỗi). */
    val currentStreak: Int,
    /** Chuỗi dài nhất trong dữ liệu được xét. */
    val bestStreak: Int,
    val isDayOffToday: Boolean
) {
    val metToday: Boolean get() = doneToday >= goal
    val remainingToday: Int get() = (goal - doneToday).coerceAtLeast(0)
}

object GoalCalculator {

    /** Số việc hoàn thành theo ngày lịch. */
    fun countsByDay(completedAt: List<Long>, zone: ZoneId): Map<LocalDate, Int> =
        completedAt.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount()

    fun progress(
        completedAt: List<Long>,
        goal: Int,
        daysOff: Set<DayOfWeek>,
        today: LocalDate,
        zone: ZoneId,
        lookbackDays: Int = 366
    ): GoalProgress {
        val safeGoal = goal.coerceAtLeast(1)
        val counts = countsByDay(completedAt, zone)
        fun met(d: LocalDate) = (counts[d] ?: 0) >= safeGoal
        fun off(d: LocalDate) = d.dayOfWeek in daysOff

        // Chuỗi hiện tại: đi lùi từ hôm nay. Hôm nay chưa đạt → bắt đầu từ hôm qua (vẫn còn cả ngày để đạt).
        var current = 0
        var day = if (met(today)) today else today.minusDays(1)
        var steps = 0
        while (steps < lookbackDays) {
            when {
                met(day) -> current++
                off(day) -> Unit // ngày nghỉ không làm được thì cũng không đứt chuỗi
                else -> break
            }
            day = day.minusDays(1)
            steps++
        }

        // Chuỗi dài nhất: quét xuôi trong khoảng dữ liệu.
        var best = 0
        var run = 0
        var d = today.minusDays(lookbackDays.toLong())
        while (!d.isAfter(today)) {
            when {
                met(d) -> { run++; best = maxOf(best, run) }
                off(d) || d == today -> Unit // hôm nay chưa kết thúc: không tính là đứt
                else -> run = 0
            }
            d = d.plusDays(1)
        }

        return GoalProgress(
            goal = safeGoal,
            doneToday = counts[today] ?: 0,
            currentStreak = current,
            bestStreak = maxOf(best, current),
            isDayOffToday = off(today)
        )
    }
}

/** Dữ liệu cho màn Tổng kết tuần: 7 ngày gần nhất so với 7 ngày trước đó. */
data class WeeklyReview(
    val from: LocalDate,
    val to: LocalDate,
    val completed: Int,
    val completedPreviousWeek: Int,
    /** 7 phần tử, [0] = [from]. */
    val dailyCounts: List<Int>,
    val bestDay: LocalDate?,
    val bestDayCount: Int,
    val byCategory: Map<String, Int>,
    /** Hoàn thành sau hạn chót. */
    val completedLate: Int,
    val goalDaysMet: Int,
    val overdueNow: Int,
    /** Việc đang chờ nhiều nhất theo danh mục — gợi ý nơi đang bị bỏ bê. */
    val mostNeglectedCategory: String?
) {
    val changePercent: Int?
        get() = if (completedPreviousWeek == 0) null
        else ((completed - completedPreviousWeek) * 100.0 / completedPreviousWeek).toInt()
}

object WeeklyReviewCalculator {

    fun compute(
        completed: List<Task>,
        pending: List<Task>,
        today: LocalDate,
        now: Long,
        zone: ZoneId,
        goal: Int
    ): WeeklyReview {
        val from = today.minusDays(6)
        val prevFrom = from.minusDays(7)
        fun dayOf(t: Task) = t.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        val thisWeek = completed.filter { dayOf(it)?.let { d -> !d.isBefore(from) && !d.isAfter(today) } == true }
        val prevWeek = completed.filter { dayOf(it)?.let { d -> !d.isBefore(prevFrom) && d.isBefore(from) } == true }

        val daily = (0..6).map { i -> thisWeek.count { dayOf(it) == from.plusDays(i.toLong()) } }
        val bestIndex = daily.indices.maxByOrNull { daily[it] }?.takeIf { daily[it] > 0 }

        return WeeklyReview(
            from = from,
            to = today,
            completed = thisWeek.size,
            completedPreviousWeek = prevWeek.size,
            dailyCounts = daily,
            bestDay = bestIndex?.let { from.plusDays(it.toLong()) },
            bestDayCount = bestIndex?.let { daily[it] } ?: 0,
            byCategory = thisWeek.groupingBy { it.category }.eachCount(),
            completedLate = thisWeek.count { t -> t.dueAt != null && t.completedAt != null && t.completedAt > t.dueAt },
            goalDaysMet = daily.count { it >= goal.coerceAtLeast(1) },
            overdueNow = pending.count { it.isOverdue(now) },
            mostNeglectedCategory = pending.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
        )
    }

    /** Câu gửi AI Coach để nhờ lập kế hoạch tuần tới dựa trên số liệu tuần này. */
    fun coachPrompt(review: WeeklyReview, categoryLabel: (String) -> String): String = buildString {
        append("Đây là tổng kết 7 ngày qua của tôi: hoàn thành ${review.completed} việc")
        review.changePercent?.let { append(" (${if (it >= 0) "+" else ""}$it% so với tuần trước)") }
        append(", đạt mục tiêu ngày ${review.goalDaysMet}/7 ngày")
        if (review.completedLate > 0) append(", ${review.completedLate} việc xong trễ hạn")
        if (review.overdueNow > 0) append(", hiện còn ${review.overdueNow} việc quá hạn")
        review.mostNeglectedCategory?.let { append(", danh mục còn tồn nhiều nhất là \"${categoryLabel(it)}\"") }
        append(". Hãy nhận xét ngắn gọn và giúp tôi lập kế hoạch cho tuần tới.")
    }
}
