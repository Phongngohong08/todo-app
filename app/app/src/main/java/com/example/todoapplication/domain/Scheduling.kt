package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import androidx.annotation.StringRes
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/*
 * [TẦNG DOMAIN] Các quy tắc thời gian dùng ở nhiều nơi: giờ yên tĩnh, dời việc sang ngày khác
 * (kéo-thả trên lịch, vuốt trái để hoãn), ma trận Eisenhower. Thuần Kotlin — xem SchedulingTest.
 */

/** Giờ yên tĩnh: không nhắc trong khoảng [start, end) — khoảng có thể vắt qua nửa đêm (22:00 → 07:00). */
object QuietHours {

    fun isQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> false                                  // khoảng rỗng = tắt
        start < end -> time >= start && time < end             // trong ngày: 13:00 → 14:00
        else -> time >= start || time < end                    // qua đêm: 22:00 → 07:00
    }

    /**
     * Thời điểm được phép nhắc lại nếu [now] đang trong giờ yên tĩnh (= lúc kết thúc gần nhất), null nếu không.
     * Ví dụ 23:30 với khoảng 22:00 → 07:00 → 07:00 sáng hôm sau.
     */
    fun deferUntil(now: LocalDateTime, start: LocalTime, end: LocalTime): LocalDateTime? {
        if (!isQuiet(now.toLocalTime(), start, end)) return null
        val endToday = now.toLocalDate().atTime(end)
        return if (endToday.isAfter(now)) endToday else endToday.plusDays(1)
    }
}

/** Dời việc sang ngày khác mà giữ nguyên tính chất của hạn. */
object Reschedule {

    /**
     * Hạn mới khi chuyển việc sang [target]:
     *  - việc "cả ngày" hoặc chưa có hạn → cả ngày của [target];
     *  - việc có giờ → cùng giờ đó vào [target].
     * Trả về (dueAt, allDay).
     */
    fun moveToDate(dueAt: Long?, allDay: Boolean, target: LocalDate, zone: ZoneId): Pair<Long, Boolean> {
        if (dueAt == null || allDay) return allDayDue(target, zone) to true
        val time = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalTime()
        return target.atTime(time).atZone(zone).toInstant().toEpochMilli() to false
    }

    /** Một lựa chọn "hoãn sang…" khi vuốt trái trên thẻ việc. */
    data class Option(val label: String, val date: LocalDate)

    /** Ngày mai · Cuối tuần (nếu chưa tới) · Thứ 2 tuần sau · 1 tuần nữa (trùng ngày thì bỏ). */
    fun postponeOptions(today: LocalDate): List<Option> {
        val candidates = listOf(
            Option(str(R.string.postpone_tomorrow), today.plusDays(1)),
            Option(str(R.string.postpone_weekend), today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))),
            Option(str(R.string.postpone_next_monday), today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))),
            Option(str(R.string.postpone_one_week), today.plusWeeks(1))
        )
        return candidates
            .filter { it.date.isAfter(today) }
            .distinctBy { it.date }
    }
}

/**
 * Ma trận Eisenhower (như TickTick): chia việc đang chờ theo Quan trọng × Gấp.
 *  - Quan trọng = ưu tiên Cao.
 *  - Gấp = quá hạn, hoặc hạn trong hôm nay / ngày mai.
 * Kéo việc sang ô khác sẽ đổi đúng thuộc tính tạo nên ô đó (ưu tiên và/hoặc hạn).
 */
object Eisenhower {

    enum class Quadrant(@StringRes private val titleRes: Int, @StringRes private val hintRes: Int, val important: Boolean, val urgent: Boolean) {
        DO_FIRST(R.string.quadrant_do_first, R.string.quadrant_do_first_hint, important = true, urgent = true),
        SCHEDULE(R.string.quadrant_schedule, R.string.quadrant_schedule_hint, important = true, urgent = false),
        QUICK(R.string.quadrant_quick, R.string.quadrant_quick_hint, important = false, urgent = true),
        LATER(R.string.quadrant_later, R.string.quadrant_later_hint, important = false, urgent = false);

        val title: String get() = str(titleRes)
        val hint: String get() = str(hintRes)
    }

    /** Một ô không còn gấp nữa → hạn được dời thêm chừng này ngày. */
    const val NOT_URGENT_SHIFT_DAYS = 7L

    fun isImportant(task: Task) = task.priority == "HIGH"

    /** Hạn tới hết ngày mai (gồm cả quá hạn). */
    fun isUrgent(task: Task, today: LocalDate, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        return due <= endOfToday(zone, today.plusDays(1))
    }

    fun quadrantOf(task: Task, today: LocalDate, zone: ZoneId): Quadrant {
        val important = isImportant(task)
        val urgent = isUrgent(task, today, zone)
        return Quadrant.entries.first { it.important == important && it.urgent == urgent }
    }

    /** Việc chưa xong theo từng ô (ô nào cũng có mặt), trong mỗi ô sắp theo hạn. */
    fun group(tasks: List<Task>, today: LocalDate, zone: ZoneId): Map<Quadrant, List<Task>> {
        val pending = tasks.filter { !it.isCompleted }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        val byQuadrant = pending.groupBy { quadrantOf(it, today, zone) }
        return Quadrant.entries.associateWith { byQuadrant[it].orEmpty() }
    }

    /** Thay đổi cần ghi khi kéo việc sang [target]. Trường null = giữ nguyên. */
    data class Change(val priority: String? = null, val dueAt: Long? = null, val allDay: Boolean? = null) {
        val isEmpty: Boolean get() = priority == null && dueAt == null
    }

    fun move(task: Task, target: Quadrant, today: LocalDate, zone: ZoneId): Change {
        val priority = when {
            target.important && !isImportant(task) -> "HIGH"
            !target.important && isImportant(task) -> "MEDIUM"
            else -> null
        }
        val urgentNow = isUrgent(task, today, zone)
        val due: Pair<Long, Boolean>? = when {
            // Vào ô gấp: đặt hạn hôm nay (giữ giờ nếu việc có giờ)
            target.urgent && !urgentNow -> Reschedule.moveToDate(task.dueAt, task.dueAllDay, today, zone)
            // Ra khỏi ô gấp: dời hạn thêm một tuần tính từ hôm nay
            !target.urgent && urgentNow ->
                Reschedule.moveToDate(task.dueAt, task.dueAllDay, today.plusDays(NOT_URGENT_SHIFT_DAYS), zone)
            else -> null
        }
        return Change(priority = priority, dueAt = due?.first, allDay = due?.second)
    }
}
