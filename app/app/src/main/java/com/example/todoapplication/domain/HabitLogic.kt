package com.example.todoapplication.domain

import com.example.todoapplication.ui.utils.weekdayShortLabels

import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.plural

/**
 * Logic thói quen (thuần Kotlin, có unit test): hôm nay có cần làm không, chuỗi, tiến độ tuần.
 *
 * [done] luôn là tập ngày "yyyy-MM-dd" đã đánh dấu của MỘT thói quen.
 *  - DAILY / WEEKDAYS: chuỗi tính theo NGÀY cần làm (ngày không cần làm thì bỏ qua, không làm đứt chuỗi).
 *  - WEEKLY (N lần/tuần): chuỗi tính theo TUẦN đạt đủ N lần.
 * Hôm nay chưa làm thì chưa tính là đứt — chuỗi được tính tới hôm qua.
 */
object HabitLogic {

    private val CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /** Nhìn lại tối đa chừng này ngày khi tính chuỗi (đủ hơn một năm). */
    private const val MAX_LOOKBACK_DAYS = 400L

    fun code(date: LocalDate): String = CODES[date.dayOfWeek.value - 1]

    /** "wed, mon" → "MON,WED" (thứ tự trong tuần, bỏ trùng, bỏ mã lạ) — giống backend. */
    fun normalizeWeekdays(raw: String): String {
        val set = raw.split(",").map { it.trim().uppercase() }.toSet()
        return CODES.filter { it in set }.joinToString(",")
    }

    /** Ngày [date] có phải ngày cần làm theo lịch không (WEEKLY: ngày nào cũng được). */
    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean = when (habit.frequency) {
        HabitFrequency.WEEKDAYS -> code(date) in habit.weekdays.split(",")
        else -> true
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Số lần đã làm trong tuần chứa [today] (tuần bắt đầu thứ 2). */
    fun doneThisWeek(done: Set<String>, today: LocalDate): Int {
        val start = weekStart(today)
        return (0L..6L).count { start.plusDays(it).toString() in done }
    }

    /** Mục tiêu trong tuần: WEEKLY = N lần; WEEKDAYS = số thứ đã chọn; DAILY = 7. */
    fun weeklyTarget(habit: Habit): Int = when (habit.frequency) {
        HabitFrequency.WEEKLY -> habit.targetPerWeek.coerceIn(1, 7)
        HabitFrequency.WEEKDAYS -> habit.weekdays.split(",").count { it.isNotBlank() }.coerceAtLeast(1)
        else -> 7
    }

    /** Hôm nay còn cần làm không (để hiện ở tab Hôm nay và quyết định có nhắc không). */
    fun isDueToday(habit: Habit, done: Set<String>, today: LocalDate): Boolean {
        if (habit.archived || today.toString() in done) return false
        return when (habit.frequency) {
            HabitFrequency.WEEKLY -> doneThisWeek(done, today) < weeklyTarget(habit)
            else -> isScheduledOn(habit, today)
        }
    }

    /** Có hiện ở danh sách hôm nay không: cần làm, hoặc đã làm hôm nay (để còn bỏ đánh dấu được). */
    fun showToday(habit: Habit, done: Set<String>, today: LocalDate): Boolean =
        !habit.archived && (today.toString() in done || isDueToday(habit, done, today))

    fun streak(habit: Habit, done: Set<String>, today: LocalDate): Int =
        if (habit.frequency == HabitFrequency.WEEKLY) weeklyStreak(habit, done, today) else dailyStreak(habit, done, today)

    /** Chuỗi để hiển thị: "5 ngày" / "2 tuần" (en: "5 days" / "2 weeks"). */
    fun streakLabel(habit: Habit, streak: Int): String =
        plural(if (habit.frequency == HabitFrequency.WEEKLY) R.plurals.habit_streak_weeks else R.plurals.habit_streak_days, streak)

    private fun dailyStreak(habit: Habit, done: Set<String>, today: LocalDate): Int {
        var day = if (today.toString() in done) today else today.minusDays(1)
        var count = 0
        val stop = today.minusDays(MAX_LOOKBACK_DAYS)
        while (day.isAfter(stop)) {
            if (isScheduledOn(habit, day)) {
                if (day.toString() in done) count++ else break
            }
            day = day.minusDays(1)
        }
        return count
    }

    private fun weeklyStreak(habit: Habit, done: Set<String>, today: LocalDate): Int {
        val target = weeklyTarget(habit)
        var week = weekStart(today)
        // Tuần này chưa đủ thì chưa tính là đứt — xét từ tuần trước
        if (doneThisWeek(done, week) < target) week = week.minusWeeks(1)
        var count = 0
        repeat((MAX_LOOKBACK_DAYS / 7).toInt()) {
            if (doneThisWeek(done, week) >= target) count++ else return count
            week = week.minusWeeks(1)
        }
        return count
    }

    /**
     * Tỉ lệ hoàn thành trong [days] ngày gần nhất (0..1), không tính những ngày trước khi tạo thói quen.
     * WEEKLY: số lần đã làm / số lần cần làm theo tỉ lệ tuần.
     */
    fun completionRate(habit: Habit, done: Set<String>, today: LocalDate, createdOn: LocalDate, days: Int = 30): Float {
        val from = maxOf(today.minusDays(days - 1L), createdOn)
        if (from.isAfter(today)) return 0f
        val range = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val doneCount = range.count { it.toString() in done }
        val expected = if (habit.frequency == HabitFrequency.WEEKLY) {
            range.size * weeklyTarget(habit) / 7f
        } else {
            range.count { isScheduledOn(habit, it) }.toFloat()
        }
        return if (expected <= 0f) 0f else (doneCount / expected).coerceAtMost(1f)
    }

    /** Mô tả lịch: "Mỗi ngày", "T2, T4, T6", "3 lần/tuần". */
    fun describe(habit: Habit): String = when (habit.frequency) {
        HabitFrequency.WEEKDAYS -> weekdayShortLabels().let { labels ->
            habit.weekdays.split(",").filter { it.isNotBlank() }.joinToString(", ") { labels[it] ?: it }
        }
        HabitFrequency.WEEKLY -> plural(R.plurals.habit_times_per_week, weeklyTarget(habit))
        else -> str(R.string.habit_every_day)
    }
}

/** Mọi số liệu một thói quen cần để hiển thị (tính sẵn trong ViewModel, ngoài luồng UI). */
data class HabitSummary(
    val habit: Habit,
    val doneToday: Boolean,
    val dueToday: Boolean,
    val streak: Int,
    val weekDone: Int,
    val weekTarget: Int,
    val rate30: Float,
    /** 7 ngày gần nhất (cũ → mới): ngày, có cần làm theo lịch không, đã làm chưa. */
    val lastDays: List<HabitDay>
)

data class HabitDay(val date: LocalDate, val scheduled: Boolean, val done: Boolean)

fun HabitLogic.summarize(habit: Habit, done: Set<String>, today: LocalDate, createdOn: LocalDate): HabitSummary =
    HabitSummary(
        habit = habit,
        doneToday = today.toString() in done,
        dueToday = isDueToday(habit, done, today),
        streak = streak(habit, done, today),
        weekDone = doneThisWeek(done, today),
        weekTarget = weeklyTarget(habit),
        rate30 = completionRate(habit, done, today, createdOn),
        lastDays = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            HabitDay(d, isScheduledOn(habit, d), d.toString() in done)
        }
    )
