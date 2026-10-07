package com.example.todoapplication.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.todoapplication.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Một sự kiện trong lịch của máy (Google Calendar, Outlook… đã đồng bộ vào máy). */
data class DeviceEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** Màu ARGB của sự kiện / lịch chứa nó. */
    val color: Int,
    val calendarName: String
)

/**
 * [TẦNG DATA · CONTENT PROVIDER] Đọc lịch hệ thống qua CalendarContract (ContentResolver + ContentProvider của
 * Android): chỉ ĐỌC, cần quyền READ_CALENDAR do người dùng cấp lúc chạy. Thêm việc vào lịch thì dùng Intent
 * ACTION_INSERT — mở app Lịch để người dùng xác nhận, không cần quyền ghi.
 */
object DeviceCalendar {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME
    )

    /**
     * Các lần diễn ra (Instances — đã tính sẵn sự kiện lặp) trong khoảng [fromMillis, toMillis).
     * Chưa có quyền / máy không có app lịch → danh sách rỗng.
     */
    suspend fun events(context: Context, fromMillis: Long, toMillis: Long): List<DeviceEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val out = mutableListOf<DeviceEvent>()
        runCatching {
            context.contentResolver.query(
                uri, PROJECTION,
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out += DeviceEvent(
                        id = c.getLong(0),
                        title = c.getString(1).orEmpty().ifBlank { "(Không tiêu đề)" },
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) == 1,
                        color = c.getInt(5),
                        calendarName = c.getString(6).orEmpty()
                    )
                }
            }
        }
        out
    }

    /** Các ngày (giờ địa phương) sự kiện chiếm. Sự kiện cả ngày lưu theo UTC nên đổi ngày theo UTC. */
    fun daysOf(event: DeviceEvent, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
        val z = if (event.allDay) ZoneOffset.UTC else zone
        val start = Instant.ofEpochMilli(event.begin).atZone(z).toLocalDate()
        // end là mốc loại trừ: sự kiện kết thúc đúng 00:00 không tính sang ngày đó
        val endInclusive = Instant.ofEpochMilli((event.end - 1).coerceAtLeast(event.begin)).atZone(z).toLocalDate()
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }.take(MAX_DAYS).toList()
    }

    /** Gom sự kiện theo ngày "yyyy-MM-dd". */
    fun byDay(events: List<DeviceEvent>, zone: ZoneId = ZoneId.systemDefault()): Map<String, List<DeviceEvent>> {
        val map = HashMap<String, MutableList<DeviceEvent>>()
        events.forEach { e -> daysOf(e, zone).forEach { map.getOrPut(it.toString()) { mutableListOf() } += e } }
        return map
    }

    /**
     * Intent mở app Lịch để thêm việc thành sự kiện: khung giờ (startAt → dueAt) nếu có; không thì một khối
     * thời lượng ước tính (mặc định 30 phút) kết thúc ở hạn; việc cả ngày → sự kiện cả ngày.
     */
    fun insertIntent(task: Task): Intent {
        val due = task.dueAt ?: System.currentTimeMillis()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, task.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, task.description)
        if (task.dueAllDay) {
            val day = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
            val begin = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 24 * 3600_000L)
        } else {
            val minutes = task.estimatedMinutes.takeIf { it > 0 } ?: 30
            val begin = task.startAt ?: (due - minutes * 60_000L)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, due)
        }
        return intent
    }

    private const val MAX_DAYS = 14
}
