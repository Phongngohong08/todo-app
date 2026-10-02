package com.example.todoapplication.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.example.todoapplication.R
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.utils.formatDueLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget nhỏ (2×2): tiến độ mục tiêu hôm nay (đã xong / mục tiêu), việc tiếp theo, nút "+".
 * Không cần ListView nên tự vẽ lại bằng [update] mỗi khi dữ liệu task đổi (gọi từ TasksWidgetProvider.refresh).
 */
class CompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Vẽ lại mọi widget nhỏ đang đặt trên màn hình chính. */
        fun update(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, CompactWidgetProvider::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(app, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dao = AppDatabase.get(context).taskDao()
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val done = dao.countCompletedBetween(from, to)
            val goal = LocalPrefs(context).dailyGoal.value.coerceAtLeast(1)
            val next = dao.getForWidget(today.toString(), limit = 1).firstOrNull()

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_compact)
                views.setTextViewText(R.id.compact_progress, "$done/$goal")
                views.setProgressBar(R.id.compact_bar, goal, done.coerceAtMost(goal), false)
                views.setTextViewText(
                    R.id.compact_caption,
                    if (done >= goal) "Đạt mục tiêu hôm nay 🎉" else "việc xong hôm nay"
                )
                if (next != null) {
                    views.setTextViewText(R.id.compact_next, next.title)
                    views.setTextViewText(
                        R.id.compact_next_due,
                        next.dueAt?.let { formatDueLabel(it, next.dueAllDay, today, zone) } ?: "Tiếp theo"
                    )
                    views.setOnClickPendingIntent(R.id.compact_next_box, openApp(context, 11, AppIntents.openTask(next.id)))
                } else {
                    views.setTextViewText(R.id.compact_next, "Không còn việc nào")
                    views.setTextViewText(R.id.compact_next_due, "")
                    views.setOnClickPendingIntent(R.id.compact_next_box, openApp(context, 11, AppIntents.OPEN_TODAY))
                }
                views.setOnClickPendingIntent(R.id.compact_root, openApp(context, 10, AppIntents.OPEN_TODAY))
                views.setOnClickPendingIntent(R.id.compact_add, openApp(context, 12, AppIntents.OPEN_ADD))
                manager.updateAppWidget(id, views)
            }
        }

        private fun openApp(context: Context, requestCode: Int, open: String): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, AppIntents.main(context, open),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
