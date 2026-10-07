package com.example.todoapplication.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.todoapplication.R
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.data.local.toDomain
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.summarize
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDueLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.todoapplication.i18n.str

/*
 * [TẦNG UI · WIDGET] Widget màn hình chính viết bằng Jetpack Glance (Compose cho widget):
 *  - TasksWidget: việc cần làm; mỗi widget chọn được nguồn riêng (Tất cả / Ngày của tôi / một danh sách) qua
 *    màn cấu hình — lưu trong state của chính widget đó (PreferencesGlanceStateDefinition).
 *  - CompactWidget (2×2): tiến độ mục tiêu hôm nay + việc tiếp theo.
 *  - HabitsWidget: thói quen hôm nay, bấm để đánh dấu.
 * Ô tích / đánh dấu chạy ActionCallback ngay trong tiến trình app (ghi Room → đồng bộ nền) — không cần mở app.
 * Màu lấy từ GlanceTheme → Android 12+ theo màu hình nền (Material You).
 */

/** Gọi sau mỗi thay đổi dữ liệu (TaskEffects, thói quen, đăng xuất) để mọi widget vẽ lại. */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { TasksWidget().updateAll(app) }
            runCatching { CompactWidget().updateAll(app) }
            runCatching { HabitsWidget().updateAll(app) }
        }
    }
}

/** Nguồn việc của một TasksWidget (giá trị lưu trong state của widget). */
object WidgetSource {
    val KEY = stringPreferencesKey("source")
    const val ALL = "all"
    const val MY_DAY = "my_day"
    private const val LIST_PREFIX = "list:"

    fun list(name: String) = LIST_PREFIX + name
    fun listName(source: String): String? = source.takeIf { it.startsWith(LIST_PREFIX) }?.removePrefix(LIST_PREFIX)
}

private val TASK_ID = ActionParameters.Key<String>("task_id")
private val HABIT_ID = ActionParameters.Key<String>("habit_id")
private const val MAX_ROWS = 30

private fun openApp(context: Context, open: String): Intent = AppIntents.main(context, open)

private fun priorityColor(priority: String) = ColorProvider(
    when (priority) {
        "HIGH" -> Color(0xFFFF6B6B)
        "MEDIUM" -> Color(0xFFFFA94D)
        else -> Color(0xFF51CF66)
    }
)

// ─── Danh sách việc ──────────────────────────────────────────────────────────

class TasksWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val source = prefs[WidgetSource.KEY] ?: WidgetSource.ALL
        val today = LocalDate.now().toString()
        val dao = ServiceLocator.database.taskDao()
        val listName = WidgetSource.listName(source)
        val tasks: List<TaskEntity> = when {
            source == WidgetSource.MY_DAY -> dao.getMyDayForWidget(today, MAX_ROWS)
            listName != null -> dao.getForWidgetInCategory(today, listName, MAX_ROWS)
            else -> dao.getForWidget(today, MAX_ROWS)
        }
        val title = when {
            source == WidgetSource.MY_DAY -> str(R.string.today_my_day)
            listName != null -> ServiceLocator.categoryRepository.getList(listName).let {
                (if (it.emoji.isNotBlank()) it.emoji + " " else "") + categoryLabel(listName)
            }
            else -> str(R.string.widget_tasks_title)
        }
        provideContent {
            GlanceTheme { TasksContent(context, title, tasks, today) }
        }
    }
}

@Composable
private fun TasksContent(context: Context, title: String, tasks: List<TaskEntity>, today: String) {
    val todayDate = LocalDate.parse(today)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$title (${tasks.size})",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context, AppIntents.OPEN_TODAY)))
            )
            Box(
                GlanceModifier.size(32.dp).cornerRadius(16.dp).background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(openApp(context, AppIntents.OPEN_ADD))),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (tasks.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(str(R.string.widget_no_tasks), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            }
        } else {
            LazyColumn {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    TaskRow(context, task, todayDate)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(context: Context, task: TaskEntity, today: LocalDate) {
    val overdue = task.dueAt != null && task.dueAt < System.currentTimeMillis() &&
        !(task.dueAllDay && Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate() == today)
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Ô tích: hoàn thành ngay trên widget
        Box(
            GlanceModifier.size(28.dp).clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TASK_ID to task.id))),
            contentAlignment = Alignment.Center
        ) {
            Box(GlanceModifier.size(18.dp).cornerRadius(9.dp).background(priorityColor(task.priority))) {
                Box(GlanceModifier.size(14.dp).cornerRadius(7.dp).padding(2.dp).background(GlanceTheme.colors.widgetBackground)) {}
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(
            GlanceModifier.defaultWeight()
                .clickable(actionStartActivity(openApp(context, AppIntents.openTask(task.id))))
        ) {
            Text(
                (if (task.myDay == today.toString()) "☀ " else "") + task.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1
            )
            task.dueAt?.let { due ->
                Text(
                    formatDueLabel(due, task.dueAllDay, today),
                    style = TextStyle(
                        color = if (overdue) ColorProvider(Color(0xFFE5484D)) else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        // Repository tự hủy nhắc việc, tạo lần lặp kế tiếp, xin đồng bộ và làm mới widget (TaskEffects)
        ServiceLocator.taskRepository.setCompleted(id, completed = true)
    }
}

class TasksWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()

    companion object {
        /** Giữ tên cũ cho các nơi gọi (TaskEffects, đăng xuất). */
        fun refresh(context: Context) = WidgetUpdater.refresh(context)
    }
}

// ─── Widget nhỏ 2×2 ──────────────────────────────────────────────────────────

class CompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dao = ServiceLocator.database.taskDao()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val done = dao.countCompletedBetween(from, to)
        val goal = ServiceLocator.localPrefs.dailyGoal.value.coerceAtLeast(1)
        val next = dao.getForWidget(today.toString(), limit = 1).firstOrNull()
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)
                        .clickable(actionStartActivity(openApp(context, AppIntents.OPEN_TODAY)))
                ) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$done/$goal",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Image(
                            ImageProvider(R.drawable.ic_shortcut_add), contentDescription = str(R.string.tile_quick_add),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                            modifier = GlanceModifier.size(28.dp).clickable(actionStartActivity(openApp(context, AppIntents.OPEN_ADD)))
                        )
                    }
                    Text(
                        if (done >= goal) str(R.string.goal_met_today) else str(R.string.widget_done_today),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (done.toFloat() / goal).coerceAtMost(1f),
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.secondaryContainer
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    if (next != null) {
                        Column(GlanceModifier.clickable(actionStartActivity(openApp(context, AppIntents.openTask(next.id))))) {
                            Text(
                                next.dueAt?.let { formatDueLabel(it, next.dueAllDay, today, zone) } ?: str(R.string.widget_next),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
                            )
                            Text(
                                next.title,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 2
                            )
                        }
                    } else {
                        Text(str(R.string.widget_nothing_left), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

class CompactWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
}

// ─── Thói quen hôm nay ───────────────────────────────────────────────────────

class HabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dao = ServiceLocator.database.habitDao()
        val since = today.minusDays(400).toString()
        val habits = dao.getActive().map { it.toDomain() }
        val checkins = dao.getCheckinsSince(since).groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() }
        val items = habits.filter { HabitLogic.showToday(it, checkins[it.id].orEmpty(), today) }
            .sortedBy { it.position }
            .map { h ->
                val created = Instant.ofEpochMilli(h.createdAt).atZone(zone).toLocalDate()
                HabitLogic.summarize(h, checkins[h.id].orEmpty(), today, created)
            }
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)
                ) {
                    Text(
                        str(R.string.today_habits) + "  ${items.count { it.doneToday }}/${items.size}",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.clickable(actionStartActivity(openApp(context, AppIntents.OPEN_HABITS)))
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    if (items.isEmpty()) {
                        Text(str(R.string.habits_none_today), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    } else {
                        LazyColumn {
                            items(items, itemId = { it.habit.id.hashCode().toLong() }) { s ->
                                Row(
                                    GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to s.habit.id))),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (s.doneToday) "✅" else "⬜", style = TextStyle(fontSize = 16.sp))
                                    Spacer(GlanceModifier.width(8.dp))
                                    Text(
                                        s.habit.emoji.ifBlank { "🌱" } + " " + s.habit.name,
                                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight()
                                    )
                                    if (s.streak > 0) {
                                        Text("🔥${s.streak}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_ID] ?: return
        ServiceLocator.habitRepository.toggle(id, LocalDate.now().toString())
    }
}

class HabitsWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}
