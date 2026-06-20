package com.example.todoapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.todoapplication.MainActivity
import com.example.todoapplication.R

/**
 * Widget màn hình chính hiển thị danh sách việc cần làm (đọc từ Room cache).
 * Dùng collection widget: ListView + RemoteViewsService/Factory.
 */
class TasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)

            // Gắn factory cung cấp dữ liệu cho ListView
            val serviceIntent = Intent(context, TasksWidgetService::class.java).apply {
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Bấm vào header hoặc item → mở app
            val openApp = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openPending)
            views.setPendingIntentTemplate(R.id.widget_list, openPending)

            manager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        /** Gọi để widget tải lại dữ liệu (sau khi app cập nhật cache task). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TasksWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }
    }
}
