package com.example.todoapplication.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todoapplication.R
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.ui.utils.formatDueLabel
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class TasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TasksRemoteViewsFactory(applicationContext)
}

/**
 * Cung cấp từng dòng cho ListView của widget — đọc việc chưa hoàn thành từ Room ("Ngày của tôi" lên đầu),
 * nên widget luôn khớp với màn hình, kể cả khi offline.
 * Mỗi dòng có hai vùng bấm: ô tích (hoàn thành ngay) và phần còn lại (mở việc đó trong app).
 */
class TasksRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<TaskEntity> = emptyList()
    private var today: LocalDate = LocalDate.now()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // onDataSetChanged chạy trên luồng nền của widget nên được phép chặn chờ truy vấn
        today = LocalDate.now()
        items = runBlocking { AppDatabase.get(context).taskDao().getForWidget(today.toString(), limit = 50) }
    }

    override fun onDestroy() { items = emptyList() }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)
        val inMyDay = task.myDay == today.toString()
        rv.setTextViewText(R.id.item_title, (if (inMyDay) "☀ " else "") + task.title)
        rv.setTextViewText(
            R.id.item_due,
            task.dueAt?.let { formatDueLabel(it, task.dueAllDay, today) } ?: "Không có hạn"
        )
        val overdue = task.dueAt != null && task.dueAt < System.currentTimeMillis()
        rv.setTextColor(R.id.item_due, if (overdue) 0xFFE5484D.toInt() else 0xFF6B7280.toInt())
        val dotColor = when (task.priority) {
            "HIGH" -> 0xFFFF6B6B.toInt()
            "MEDIUM" -> 0xFFFFA94D.toInt()
            else -> 0xFF51CF66.toInt()
        }
        rv.setInt(R.id.item_dot, "setBackgroundColor", dotColor)

        // Kết hợp với pendingIntentTemplate ở Provider (chỉ fill-in extras khác nhau)
        rv.setOnClickFillInIntent(R.id.item_check, WidgetActionActivity.fillIn(WidgetActionActivity.ACTION_COMPLETE, task.id))
        rv.setOnClickFillInIntent(R.id.item_body, WidgetActionActivity.fillIn(WidgetActionActivity.ACTION_OPEN, task.id))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
}
