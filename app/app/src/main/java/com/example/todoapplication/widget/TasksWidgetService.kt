package com.example.todoapplication.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todoapplication.R
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.TaskCacheEntity
import com.example.todoapplication.ui.utils.formatUtcToLocal
import kotlinx.coroutines.runBlocking

class TasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TasksRemoteViewsFactory(applicationContext)
}

/**
 * Cung cấp từng dòng cho ListView của widget — đọc danh sách việc chưa hoàn thành
 * từ Room cache (cập nhật mỗi lần app tải task).
 */
class TasksRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<TaskCacheEntity> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = runBlocking {
            AppDatabase.get(context).taskCacheDao().getAll()
        }.filter { it.status != "COMPLETED" }
            .sortedBy { it.dueDate ?: "9999" }
    }

    override fun onDestroy() { items = emptyList() }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_task_item)
        rv.setTextViewText(R.id.item_title, task.title)
        rv.setTextViewText(
            R.id.item_due,
            task.dueDate?.let { "Hạn: ${formatUtcToLocal(it)}" } ?: "Không có hạn"
        )
        val dotColor = when (task.priority) {
            "HIGH" -> 0xFFFF6B6B.toInt()
            "MEDIUM" -> 0xFFFFA94D.toInt()
            else -> 0xFF51CF66.toInt()
        }
        rv.setInt(R.id.item_dot, "setBackgroundColor", dotColor)

        // Bấm dòng → mở app (kết hợp với pendingIntentTemplate ở Provider)
        rv.setOnClickFillInIntent(R.id.item_root, Intent())
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
}
