package com.example.todoapplication.domain

import com.example.todoapplication.data.model.Task
import com.example.todoapplication.ui.utils.parseIso8601
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Logic nghiệp vụ cho danh sách công việc — tách khỏi UI để test được độc lập. */

fun Task.isOverdue(): Boolean {
    if (status == "COMPLETED") return false
    val dueDateStr = dueDate ?: return false
    val date = parseIso8601(dueDateStr) ?: return false
    return date.before(Date())
}

/** Nhóm theo hạn: true nếu việc thuộc "Tương lai" (hạn sau hôm nay); ngược lại thuộc "Hôm nay". */
fun Task.isFuture(): Boolean {
    val date = dueDate?.let { parseIso8601(it) } ?: return false
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }
    return date.after(cal.time)
}

/** true nếu task được cập nhật trong hôm nay (dùng cho nhóm "Đã hoàn thành hôm nay"). */
fun Task.isUpdatedToday(): Boolean {
    val d = parseIso8601(updatedAt) ?: return false
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = d }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

/** Sắp xếp danh sách theo lựa chọn của người dùng (giữ nguyên thứ tự gốc nếu DEFAULT). */
fun sortTasks(tasks: List<Task>, sortBy: String): List<Task> = when (sortBy) {
    "DUE" -> tasks.sortedBy { it.dueDate ?: "9999-12-31" }
    "PRIORITY" -> tasks.sortedBy { when (it.priority) { "HIGH" -> 0; "MEDIUM" -> 1; else -> 2 } }
    "TITLE" -> tasks.sortedBy { it.title.lowercase() }
    else -> tasks
}

fun sortLabel(sortBy: String): String = when (sortBy) {
    "DUE" -> "Hạn chót"
    "PRIORITY" -> "Ưu tiên"
    "TITLE" -> "Tên (A-Z)"
    else -> "Mặc định"
}

/** Điểm ưu tiên do AI gợi ý — rule-based dựa trên độ ưu tiên và thời gian còn lại tới hạn. */
fun computeAiScore(task: Task): Double {
    var score = 0.0
    score += when (task.priority) {
        "HIGH" -> 100.0
        "MEDIUM" -> 50.0
        else -> 20.0
    }
    val now = Date()
    task.dueDate?.let { due ->
        parseIso8601(due)?.let { dueDate ->
            val hoursLeft = (dueDate.time - now.time) / 3600000.0
            score += when {
                hoursLeft < 0 -> 200.0
                hoursLeft < 24 -> 150.0
                hoursLeft < 72 -> 80.0
                hoursLeft < 168 -> 40.0
                else -> 10.0
            }
        }
    }
    if (task.status == "TODO") score += 5.0
    return score
}

/** Danh sách id của các task được AI khuyến nghị ưu tiên (top 3 điểm cao nhất, chưa hoàn thành). */
fun aiRecommendedIds(tasks: List<Task>): Set<String> = tasks
    .filter { it.status != "COMPLETED" }
    .sortedByDescending { computeAiScore(it) }
    .take(3)
    .map { it.id }
    .toSet()

/** Chuỗi RFC3339 (UTC) cho ngày cách hôm nay [days] ngày, vào giờ [hour]:[minute] theo giờ máy. */
fun dueAtDayOffset(days: Int, hour: Int = 9, minute: Int = 0): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, days)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(cal.time)
}

/** Số ngày tới Chủ nhật gần nhất (>=0). */
fun daysUntilSunday(): Int {
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK) // CN = 1
    return if (dow == Calendar.SUNDAY) 0 else (Calendar.SATURDAY - dow + 1)
}
