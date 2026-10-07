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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.todoapplication.ui.viewmodel.HistoryItem
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
import com.example.todoapplication.ui.utils.shortDate
import com.example.todoapplication.ui.viewmodel.HistoryViewModel
import com.example.todoapplication.ui.viewmodel.TrashViewModel
import com.example.todoapplication.ui.viewmodel.WeeklyReviewViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.localizedFormatter

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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


// ─── Tổng kết tuần ───────────────────────────────────────────────────────────

@Composable
fun WeeklyReviewScreen(navController: NavController, viewModel: WeeklyReviewViewModel = viewModel(factory = WeeklyReviewViewModel.Factory)) {
    val review by viewModel.review.collectAsStateWithLifecycle()
    BackScaffold(stringResource(R.string.review_title), navController) { padding ->
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
                    localizedFormatter(R.string.fmt_day_month).let { f -> "${r.from.format(f)} – ${r.to.format(f)}" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { ReviewHeadline(r) }
            item { WeekBars(r) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewStat(stringResource(R.string.review_goal_days), "${r.goalDaysMet}/7", Modifier.weight(1f))
                    ReviewStat(stringResource(R.string.review_completed_late), r.completedLate.toString(), Modifier.weight(1f), if (r.completedLate > 0) StateOverdue else null)
                    ReviewStat(stringResource(R.string.review_overdue_now), r.overdueNow.toString(), Modifier.weight(1f), if (r.overdueNow > 0) StateOverdue else null)
                }
            }
            if (r.byCategory.isNotEmpty()) {
                item {
                    ReviewCard(stringResource(R.string.review_by_list)) {
                        r.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, n) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(categoryLabel(cat), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                Text(pluralStringResource(R.plurals.count_tasks, n, n), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            r.mostNeglectedCategory?.let { cat ->
                item {
                    ReviewCard(stringResource(R.string.review_attention)) {
                        Text(
                            stringResource(R.string.review_neglected, categoryLabel(cat)),
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
                    Text(stringResource(R.string.review_ask_coach))
                }
            }
        }
    }
}

@Composable
private fun ReviewHeadline(r: WeeklyReview) {
    ReviewCard(null) {
        Text(pluralStringResource(R.plurals.review_completed, r.completed, r.completed), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        val change = r.changePercent
        Text(
            when {
                change == null && r.completedPreviousWeek == 0 -> stringResource(R.string.review_no_previous)
                change == null -> ""
                change > 0 -> stringResource(R.string.review_change_up, change, r.completedPreviousWeek)
                change < 0 -> stringResource(R.string.review_change_down, -change, r.completedPreviousWeek)
                else -> stringResource(R.string.review_same_as_last)
            },
            color = when {
                change == null -> MaterialTheme.colorScheme.onSurfaceVariant
                change >= 0 -> StateCompleted
                else -> StateOverdue
            },
            fontSize = 13.sp
        )
        r.bestDay?.let {
            Text(stringResource(R.string.review_best_day, shortDate(it), r.bestDayCount), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekBars(r: WeeklyReview) {
    ReviewCard(stringResource(R.string.review_last_7_days)) {
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
    // Paging 3: chỉ đọc từng trang 40 việc khi cuộn tới — lịch sử bao lâu cũng mở nhanh
    val items = viewModel.history.collectAsLazyPagingItems()
    BackScaffold(stringResource(R.string.history_title), navController) { padding ->
        val refresh = items.loadState.refresh
        when {
            refresh is LoadState.Loading && items.itemCount == 0 ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            refresh is LoadState.NotLoading && items.itemCount == 0 -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(emoji = "📜", title = stringResource(R.string.history_empty_title), subtitle = stringResource(R.string.history_empty_subtitle))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { item ->
                        when (item) {
                            is HistoryItem.Header -> "d-" + item.day
                            is HistoryItem.Row -> item.task.id
                        }
                    },
                    contentType = items.itemContentType { if (it is HistoryItem.Header) "header" else "row" }
                ) { index ->
                    when (val item = items[index]) {
                        is HistoryItem.Header -> Text(
                            dayTitle(item.day), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
                        )
                        is HistoryItem.Row -> HistoryRow(
                            task = item.task,
                            trailing = item.task.completedAt?.let { formatTime(it) }.orEmpty(),
                            actionIcon = { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.history_reopen)) },
                            onAction = { viewModel.reopen(item.task) },
                            onClick = { navController.navigate(Screen.TaskDetail.createRoute(item.task.id)) }
                        )
                        null -> Spacer(Modifier.height(56.dp)) // trang chưa tải xong
                    }
                }
                if (items.loadState.append is LoadState.Loading) {
                    item(key = "loading-more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> str(R.string.day_today)
        today.minusDays(1) -> str(R.string.day_yesterday)
        else -> shortDate(day, today)
    }
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
        stringResource(R.string.trash_title), navController, snackbar,
        actions = {
            if (!items.isNullOrEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_action), color = PriorityHighColor) }
            }
        }
    ) { padding ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingState() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(emoji = "🗑️", title = stringResource(R.string.trash_empty_title), subtitle = stringResource(R.string.trash_empty_subtitle))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(stringResource(R.string.trash_retention), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { it.id }) { task ->
                    HistoryRow(
                        task = task,
                        trailing = task.deletedAt?.let { stringResource(R.string.trash_deleted_at, formatDueLabel(it, allDay = false)) }.orEmpty(),
                        actionIcon = { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.backup_restore), tint = MaterialTheme.colorScheme.primary) },
                        onAction = { viewModel.restore(task) },
                        struck = false,
                        extraAction = {
                            IconButton(onClick = { viewModel.deleteForever(listOf(task.id)) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever), tint = PriorityHighColor)
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
            title = { Text(stringResource(R.string.trash_confirm_title)) },
            text = { Text(stringResource(R.string.trash_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.deleteForever(items.orEmpty().map { it.id })
                }) { Text(stringResource(R.string.trash_delete_forever), color = PriorityHighColor) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
