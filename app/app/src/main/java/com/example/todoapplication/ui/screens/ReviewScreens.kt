package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.domain.WeeklyReview
import com.example.todoapplication.domain.WeeklyReviewCalculator
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.ui.components.EmptyState
import com.example.todoapplication.ui.components.LoadingState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.PriorityHighColor
import com.example.todoapplication.ui.theme.StateCompleted
import com.example.todoapplication.ui.theme.StateOverdue
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.utils.formatTime
import com.example.todoapplication.ui.utils.weekdayShort
import com.example.todoapplication.ui.viewmodel.HistoryViewModel
import com.example.todoapplication.ui.viewmodel.TrashViewModel
import com.example.todoapplication.ui.viewmodel.WeeklyReviewViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * [TẦNG UI · MÀN HÌNH] Tổng kết tuần · Lịch sử hoàn thành · Thùng rác.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(
    title: String,
    navController: NavController,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = actions
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

// ─── Tổng kết tuần ───────────────────────────────────────────────────────────

@Composable
fun WeeklyReviewScreen(navController: NavController, viewModel: WeeklyReviewViewModel = viewModel(factory = WeeklyReviewViewModel.Factory)) {
    val review by viewModel.review.collectAsStateWithLifecycle()
    BackScaffold("Tổng kết tuần", navController) { padding ->
        val r = review
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            return@BackScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "${r.from.format(DAY_MONTH)} – ${r.to.format(DAY_MONTH)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { ReviewHeadline(r) }
            item { WeekBars(r) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewStat("Ngày đạt mục tiêu", "${r.goalDaysMet}/7", Modifier.weight(1f))
                    ReviewStat("Xong trễ hạn", r.completedLate.toString(), Modifier.weight(1f), if (r.completedLate > 0) StateOverdue else null)
                    ReviewStat("Đang quá hạn", r.overdueNow.toString(), Modifier.weight(1f), if (r.overdueNow > 0) StateOverdue else null)
                }
            }
            if (r.byCategory.isNotEmpty()) {
                item {
                    ReviewCard("Đã làm theo danh mục") {
                        r.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, n) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(categoryLabel(cat), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                Text("$n việc", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            r.mostNeglectedCategory?.let { cat ->
                item {
                    ReviewCard("Cần để ý") {
                        Text(
                            "Danh mục \"${categoryLabel(cat)}\" đang tồn nhiều việc nhất. Cân nhắc dành một khoảng thời gian cho nó tuần tới.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val prompt = WeeklyReviewCalculator.coachPrompt(r, ::categoryLabel)
                        navController.navigate(Screen.AICoach.createRoute(prompt))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nhờ AI Coach lập kế hoạch tuần tới")
                }
            }
        }
    }
}

@Composable
private fun ReviewHeadline(r: WeeklyReview) {
    ReviewCard(null) {
        Text("${r.completed} việc hoàn thành", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        val change = r.changePercent
        Text(
            when {
                change == null && r.completedPreviousWeek == 0 -> "Tuần trước chưa có dữ liệu để so sánh"
                change == null -> ""
                change > 0 -> "▲ $change% so với tuần trước (${r.completedPreviousWeek} việc)"
                change < 0 -> "▼ ${-change}% so với tuần trước (${r.completedPreviousWeek} việc)"
                else -> "Bằng tuần trước"
            },
            color = when {
                change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                change >= 0 -> StateCompleted
                else -> StateOverdue
            },
            fontSize = 13.sp
        )
        r.bestDay?.let {
            Text("Ngày năng suất nhất: ${weekdayShort(it)} ${it.format(DAY_MONTH)} (${r.bestDayCount} việc)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekBars(r: WeeklyReview) {
    ReviewCard("7 ngày qua") {
        val max = (r.dailyCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
            r.dailyCounts.forEachIndexed { i, n ->
                val day = r.from.plusDays(i.toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                    Text(n.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((70f * n / max).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (day == r.to) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    )
                    Text(weekdayShort(day), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewStat(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color ?: MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

// ─── Lịch sử ─────────────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    BackScaffold("Đã hoàn thành", navController) { padding ->
        val list = days
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(emoji = "📜", title = "Chưa có việc nào hoàn thành", subtitle = "Việc bạn làm xong sẽ được lưu ở đây.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                list.forEach { (day, tasks) ->
                    item(key = "d-$day") {
                        Text(dayTitle(day, tasks.size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(tasks, key = { it.id }) { task ->
                        HistoryRow(
                            task = task,
                            trailing = task.completedAt?.let { formatTime(it) }.orEmpty(),
                            actionIcon = { Icon(Icons.Default.Restore, contentDescription = "Mở lại") },
                            onAction = { viewModel.reopen(task) },
                            onClick = { navController.navigate(Screen.TaskDetail.createRoute(task.id)) }
                        )
                    }
                }
            }
        }
    }
}

private fun dayTitle(day: LocalDate, count: Int): String {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Hôm nay"
        today.minusDays(1) -> "Hôm qua"
        else -> "${weekdayShort(day)} ${day.format(if (day.year == today.year) DAY_MONTH else DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
    }
    return "$label · $count việc"
}

@Composable
private fun HistoryRow(
    task: Task,
    trailing: String,
    actionIcon: @Composable () -> Unit,
    onAction: () -> Unit,
    onClick: (() -> Unit)? = null,
    struck: Boolean = true,
    extraAction: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    listOfNotNull(categoryLabel(task.category), trailing.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            extraAction?.invoke()
            IconButton(onClick = onAction) { actionIcon() }
        }
    }
}

// ─── Thùng rác ───────────────────────────────────────────────────────────────

@Composable
fun TrashScreen(navController: NavController, viewModel: TrashViewModel = viewModel(factory = TrashViewModel.Factory)) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { scope.launch { snackbar.showSnackbar(it) } } }

    BackScaffold(
        "Thùng rác", navController, snackbar,
        actions = {
            if (!items.isNullOrEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Dọn sạch", color = PriorityHighColor) }
            }
        }
    ) { padding ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(emoji = "🗑️", title = "Thùng rác trống", subtitle = "Việc đã xóa được giữ ở đây 30 ngày để bạn khôi phục.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Việc đã xóa được giữ 30 ngày rồi tự xóa hẳn.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { it.id }) { task ->
                    HistoryRow(
                        task = task,
                        trailing = task.deletedAt?.let { "Xóa " + formatDueLabel(it, allDay = false) }.orEmpty(),
                        actionIcon = { Icon(Icons.Default.Restore, contentDescription = "Khôi phục", tint = MaterialTheme.colorScheme.primary) },
                        onAction = { viewModel.restore(task) },
                        struck = false,
                        extraAction = {
                            IconButton(onClick = { viewModel.deleteForever(listOf(task.id)) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Xóa vĩnh viễn", tint = PriorityHighColor)
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Dọn sạch thùng rác?") },
            text = { Text("Các việc trong thùng rác sẽ bị xóa vĩnh viễn, không khôi phục được.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.deleteForever(items.orEmpty().map { it.id })
                }) { Text("Xóa vĩnh viễn", color = PriorityHighColor) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Hủy") } }
        )
    }
}
