package com.example.todoapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.R

/**
 * Widget màn hình chính hiển thị danh sách việc cần làm (đọc từ Room).
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

            // Tiêu đề → mở tab Hôm nay; nút "+" → mở thanh tạo nhanh
            views.setOnClickPendingIntent(R.id.widget_header, openApp(context, 0, AppIntents.OPEN_TODAY))
            views.setOnClickPendingIntent(R.id.widget_add, openApp(context, 1, AppIntents.OPEN_ADD))

            // Mẫu cho từng dòng: ô tích / nội dung điền thêm extras khác nhau (fill-in) → phải MUTABLE
            val template = PendingIntent.getActivity(
                context, 2, Intent(context, WidgetActionActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, template)

            manager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private fun openApp(context: Context, requestCode: Int, open: String): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, AppIntents.main(context, open),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** Gọi để widget tải lại dữ liệu (sau mỗi thay đổi task — xem TaskEffects). */
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
