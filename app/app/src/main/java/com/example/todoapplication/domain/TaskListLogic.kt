package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * [TẦNG DOMAIN] Logic nghiệp vụ THUẦN cho danh sách công việc (quá hạn, nhóm, sắp xếp, gợi ý...).
 * Vào dữ liệu → ra kết quả, KHÔNG đụng Android/mạng nên test cực nhanh (xem TaskListLogicTest).
 * Thời điểm "bây giờ" và múi giờ được truyền vào thay vì đọc đồng hồ bên trong → test lặp lại được.
 */

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean =
    !isCompleted && dueAt != null && dueAt < now

/** Hết ngày hôm nay (23:59:59.999) theo giờ địa phương, dạng epoch millis. */
fun endOfToday(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Long =
    today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

/** Nhóm theo hạn: true nếu việc thuộc "Tương lai" (hạn sau hôm nay). */
fun Task.isFuture(endOfToday: Long): Boolean = dueAt != null && dueAt > endOfToday

fun Task.isCompletedOn(day: LocalDate, zone: ZoneId): Boolean =
    completedAt != null && Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate() == day

/** Các nhóm hiển thị trên màn danh sách. */
data class TaskSections(
    val today: List<Task> = emptyList(),
    val future: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList()
) {
    val isEmpty: Boolean get() = today.isEmpty() && future.isEmpty() && completedToday.isEmpty()
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
        today = pending.filter { !it.isFuture(end) },
        future = pending.filter { it.isFuture(end) },
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

/** Điểm ưu tiên do AI gợi ý — rule-based dựa trên độ ưu tiên và thời gian còn lại tới hạn. */
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
 * Danh sách id các task được AI khuyến nghị ưu tiên (điểm cao nhất, chưa hoàn thành).
 * Số lượng gợi ý ~1/3 số việc đang chờ và tối đa 3 — để badge còn ý nghĩa "nổi bật" thay vì
 * dính lên mọi việc khi danh sách ngắn (vd 3 việc thì chỉ 1 việc được khuyến nghị).
 */
fun aiRecommendedIds(tasks: List<Task>, now: Long = System.currentTimeMillis()): Set<String> {
    val pending = tasks.filter { !it.isCompleted }
    // Dưới 3 việc thì không khuyến nghị: người dùng đã thấy hết, badge không thêm giá trị.
    if (pending.size < 3) return emptySet()
    val count = (pending.size / 3).coerceAtMost(3)
    return pending
        .sortedByDescending { computeAiScore(it, now) }
        .take(count)
        .map { it.id }
        .toSet()
}

/** Thời điểm (epoch millis) cách hôm nay [days] ngày, vào [hour]:[minute] giờ địa phương. */
fun dueAtDayOffset(
    days: Int,
    hour: Int = 9,
    minute: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
): Long = today.plusDays(days.toLong()).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

/** Số ngày tới Chủ nhật gần nhất (>=0). */
fun daysUntilSunday(today: LocalDate = LocalDate.now()): Int =
    (today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toEpochDay() - today.toEpochDay()).toInt()
