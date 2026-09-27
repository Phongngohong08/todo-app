package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/*
 * [TẦNG DOMAIN] Logic nghiệp vụ THUẦN cho danh sách công việc (quá hạn, nhóm, sắp xếp, gợi ý...).
 * Vào dữ liệu → ra kết quả, KHÔNG đụng Android/mạng nên test cực nhanh (xem TaskListLogicTest).
 * Thời điểm "bây giờ" và múi giờ được truyền vào thay vì đọc đồng hồ bên trong → test lặp lại được.
 *
 * Việc "cả ngày" lưu hạn ở 23:59 giờ địa phương, nên mọi phép so sánh dưới đây tự đúng: nó chỉ quá hạn
 * khi đã sang ngày hôm sau.
 */

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && dueAt != null && dueAt < now

/** Hết ngày hôm nay (23:59:59.999) theo giờ địa phương, dạng epoch millis. */
fun endOfToday(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Long =
    today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

/** Hạn của việc "cả ngày" vào [date]: 23:59 giờ địa phương. */
fun allDayDue(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(ALL_DAY_TIME).atZone(zone).toInstant().toEpochMilli()

/** Mốc giờ lưu cho hạn "cả ngày". */
val ALL_DAY_TIME: LocalTime = LocalTime.of(23, 59)

/** Nhóm theo hạn: true nếu việc thuộc "Sắp tới" (hạn sau hôm nay). */
fun Task.isFuture(endOfToday: Long): Boolean = dueAt != null && dueAt > endOfToday

fun Task.isCompletedOn(day: LocalDate, zone: ZoneId): Boolean =
    completedAt != null && Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate() == day

/**
 * Các nhóm hiển thị trên màn danh sách (theo Todoist): tách Quá hạn khỏi Hôm nay để "Hôm nay" không bị ngập,
 * và việc chưa có hạn vào nhóm riêng thay vì dồn hết vào Hôm nay mãi mãi.
 */
data class TaskSections(
    val overdue: List<Task> = emptyList(),
    val today: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val noDate: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList()
) {
    val isEmpty: Boolean
        get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && noDate.isEmpty() && completedToday.isEmpty()
}

fun groupTasks(
    tasks: List<Task>,
    sortBy: String,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): TaskSections {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val end = endOfToday(zone, today)
    val pending = sortTasks(tasks.filter { !it.isCompleted }, sortBy)
    return TaskSections(
        overdue = pending.filter { it.isOverdue(now) },
        today = pending.filter { it.dueAt != null && !it.isOverdue(now) && it.dueAt <= end },
        upcoming = pending.filter { it.isFuture(end) },
        noDate = pending.filter { it.dueAt == null },
        completedToday = tasks
            .filter { it.isCompleted && it.isCompletedOn(today, zone) }
            .sortedByDescending { it.completedAt }
    )
}

/** Sắp xếp theo lựa chọn của người dùng. DEFAULT = thứ tự kéo-thả (sortOrder) mà Room đã trả về. */
fun sortTasks(tasks: List<Task>, sortBy: String): List<Task> = when (sortBy) {
    "DUE" -> tasks.sortedBy { it.dueAt ?: Long.MAX_VALUE }
    "PRIORITY" -> tasks.sortedBy { priorityRank(it.priority) }
    "TITLE" -> tasks.sortedBy { it.title.lowercase() }
    else -> tasks
}

private fun priorityRank(priority: String) = when (priority) {
    "HIGH" -> 0
    "MEDIUM" -> 1
    else -> 2
}

fun sortLabel(sortBy: String): String = when (sortBy) {
    "DUE" -> "Hạn chót"
    "PRIORITY" -> "Ưu tiên"
    "TITLE" -> "Tên (A-Z)"
    else -> "Thủ công"
}

// ── Bộ lọc thông minh ────────────────────────────────────────────────────────

/** Bộ lọc nhanh bên cạnh bộ lọc danh mục (như "Bộ lọc" của Todoist / "Danh sách thông minh" của TickTick). */
enum class SmartFilter(val label: String) {
    NONE("Tất cả"),
    OVERDUE("Quá hạn"),
    HIGH_PRIORITY("Ưu tiên cao"),
    NO_DATE("Chưa có hạn"),
    RECURRING("Việc lặp");

    fun matches(task: Task, now: Long): Boolean = when (this) {
        NONE -> true
        OVERDUE -> task.isOverdue(now)
        HIGH_PRIORITY -> task.priority == "HIGH"
        NO_DATE -> task.dueAt == null
        RECURRING -> task.isRecurring
    }
}

// ── "Nên làm trước" ──────────────────────────────────────────────────────────

/** Điểm ưu tiên — rule-based dựa trên độ ưu tiên và thời gian còn lại tới hạn. */
fun computeAiScore(task: Task, now: Long = System.currentTimeMillis()): Double {
    var score = when (task.priority) {
        "HIGH" -> 100.0
        "MEDIUM" -> 50.0
        else -> 20.0
    }
    task.dueAt?.let { due ->
        val hoursLeft = (due - now) / 3_600_000.0
        score += when {
            hoursLeft < 0 -> 200.0
            hoursLeft < 24 -> 150.0
            hoursLeft < 72 -> 80.0
            hoursLeft < 168 -> 40.0
            else -> 10.0
        }
    }
    if (!task.isCompleted) score += 5.0
    return score
}

/**
 * Việc nên làm trước (điểm cao nhất, chưa hoàn thành) kèm LÝ DO để người dùng hiểu vì sao — trước đây
 * chỉ có nhãn "AI khuyến nghị" không giải thích, dễ mất tin khi gợi ý không hợp lý.
 * Số lượng ~1/3 số việc đang chờ và tối đa 3 — để nhãn còn ý nghĩa "nổi bật".
 */
fun recommendations(
    tasks: List<Task>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Map<String, String> {
    val pending = tasks.filter { !it.isCompleted }
    // Dưới 3 việc thì không gợi ý: người dùng đã thấy hết, nhãn không thêm giá trị.
    if (pending.size < 3) return emptyMap()
    val count = (pending.size / 3).coerceAtMost(3)
    return pending
        .sortedByDescending { computeAiScore(it, now) }
        .take(count)
        .associate { it.id to recommendationReason(it, now, zone) }
}

/** Danh sách id được gợi ý (giữ cho code/test cũ). */
fun aiRecommendedIds(tasks: List<Task>, now: Long = System.currentTimeMillis()): Set<String> =
    recommendations(tasks, now).keys

/** Vd "Quá hạn 2 ngày · Ưu tiên cao", "Hạn trong 5 giờ", "Hạn hôm nay". */
fun recommendationReason(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val parts = mutableListOf<String>()
    task.dueAt?.let { due ->
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val days = (dueDay.toEpochDay() - today.toEpochDay()).toInt()
        parts += when {
            due < now && days < 0 -> "Quá hạn ${-days} ngày"
            due < now -> "Quá hạn từ ${formatHourMinute(due, zone)}"
            days == 0 && task.dueAllDay -> "Hạn hôm nay"
            days == 0 -> {
                val hours = ((due - now) / 3_600_000.0).roundToInt()
                if (hours <= 1) "Hạn trong 1 giờ" else "Hạn trong $hours giờ"
            }
            days == 1 -> "Hạn ngày mai"
            else -> "Hạn trong $days ngày"
        }
    }
    when (task.priority) {
        "HIGH" -> parts += "Ưu tiên cao"
        "MEDIUM" -> if (parts.isEmpty()) parts += "Ưu tiên trung bình"
    }
    return parts.joinToString(" · ").ifEmpty { "Đang chờ lâu" }
}

private fun formatHourMinute(millis: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

// ── "Ngày của tôi" ───────────────────────────────────────────────────────────

/** Một gợi ý đưa vào "Ngày của tôi" (như Microsoft To Do). */
data class MyDaySuggestion(val task: Task, val reason: String)

/**
 * Gợi ý việc cho "Ngày của tôi" hôm nay, theo thứ tự nên cân nhắc:
 * quá hạn → đến hạn hôm nay → hôm qua đã chọn mà chưa xong → ưu tiên cao chưa có hạn.
 */
fun myDaySuggestions(
    tasks: List<Task>,
    today: LocalDate,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    limit: Int = 8
): List<MyDaySuggestion> {
    val todayKey = today.toString()
    val yesterdayKey = today.minusDays(1).toString()
    val end = endOfToday(zone, today)
    val candidates = tasks.filter { !it.isCompleted && it.myDay != todayKey }

    fun reasonFor(t: Task): Pair<Int, String>? = when {
        t.isOverdue(now) -> 0 to "Quá hạn"
        t.dueAt != null && t.dueAt <= end -> 1 to "Đến hạn hôm nay"
        t.myDay == yesterdayKey -> 2 to "Hôm qua chưa xong"
        t.dueAt == null && t.priority == "HIGH" -> 3 to "Ưu tiên cao, chưa có hạn"
        else -> null
    }

    return candidates
        .mapNotNull { t -> reasonFor(t)?.let { (rank, reason) -> Triple(t, rank, reason) } }
        .sortedWith(compareBy<Triple<Task, Int, String>> { it.second }.thenByDescending { computeAiScore(it.first, now) })
        .take(limit)
        .map { MyDaySuggestion(it.first, it.third) }
}

// ── Chọn nhanh hạn chót ──────────────────────────────────────────────────────

/** Thời điểm (epoch millis) cách hôm nay [days] ngày, vào [hour]:[minute] giờ địa phương. */
fun dueAtDayOffset(
    days: Int,
    hour: Int = 9,
    minute: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): Long = today.plusDays(days.toLong()).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

/** Hạn "cả ngày" cách hôm nay [days] ngày — chọn "Hôm nay" lúc 14h không còn bị quá hạn ngay. */
fun allDayDueInDays(days: Int, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Long =
    allDayDue(today.plusDays(days.toLong()), zone)

/** Số ngày tới Chủ nhật gần nhất (>=0). */
fun daysUntilSunday(today: LocalDate = LocalDate.now()): Int =
    (today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toEpochDay() - today.toEpochDay()).toInt()
