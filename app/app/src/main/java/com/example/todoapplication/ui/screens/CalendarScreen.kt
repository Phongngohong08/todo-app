package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.ui.components.DragDropContainer
import com.example.todoapplication.ui.components.DragDropState
import com.example.todoapplication.ui.components.DragPayload
import com.example.todoapplication.ui.components.DropTarget
import com.example.todoapplication.ui.components.dragSource
import com.example.todoapplication.ui.components.rememberDragDropState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatTime
import com.example.todoapplication.ui.utils.weekdayShort
import com.example.todoapplication.ui.viewmodel.CalendarEntry
import com.example.todoapplication.ui.viewmodel.CalendarEvent
import com.example.todoapplication.ui.viewmodel.CalendarUiState
import com.example.todoapplication.ui.viewmodel.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Ba cách xem lịch. */
private enum class CalendarMode(val label: String) { MONTH("Tháng"), WEEK("Tuần"), AGENDA("Lịch trình") }

private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

/** Số ngày tới hiển thị ở chế độ "Lịch trình". */
private const val AGENDA_DAYS = 30L

/**
 * [TẦNG UI · MÀN HÌNH] Lịch: Tháng (lưới + việc của ngày chọn) · Tuần (nhấn giữ việc rồi kéo sang ngày khác
 * để dời hạn) · Lịch trình (mọi việc 30 ngày tới theo ngày). Lần lặp dự kiến hiện mờ, không kéo được.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
) {
    val state by calendarViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var modeName by rememberSaveable { mutableStateOf(CalendarMode.MONTH.name) }
    val mode = CalendarMode.valueOf(modeName)
    val openTask: (String) -> Unit = { navController.navigate(Screen.TaskDetail.createRoute(it)) }

    LaunchedEffect(Unit) {
        calendarViewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.Moved -> {
                    val result = snackbarHostState.showSnackbar(
                        "Đã dời sang ${weekdayShort(event.date)} ${event.date.format(DAY_MONTH)}",
                        actionLabel = "Hoàn tác",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) calendarViewModel.undoMove(event.previous)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomNavigationBar(navController, activeTab = 2) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                "Lịch công việc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                CalendarMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { modeName = m.name },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size)
                    ) { Text(m.label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else when (mode) {
                CalendarMode.MONTH -> MonthView(state, openTask)
                CalendarMode.WEEK -> WeekView(state, openTask, calendarViewModel::moveTask)
                CalendarMode.AGENDA -> AgendaView(state, openTask, calendarViewModel::complete)
            }
        }
    }
}

// ─── Tháng ───────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MonthView(state: CalendarUiState, openTask: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val today = remember { LocalDate.now() }
    var shown by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    val month = LocalDate.parse(shown)
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { shown = month.minusMonths(1).toString() }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Tháng trước", tint = primary)
        }
        Text("Tháng ${month.monthValue}, ${month.year}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { shown = month.plusMonths(1).toString() }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Tháng sau", tint = primary)
        }
    }

    WeekdayHeader()

    // Ô trống trước ngày 1 (tuần bắt đầu từ thứ 2)
    val offset = month.dayOfWeek.value - 1
    val cells: List<LocalDate?> = buildList {
        repeat(offset) { add(null) }
        for (d in 1..month.lengthOfMonth()) add(month.withDayOfMonth(d))
        while (size % 7 != 0) add(null)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1.1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val key = date.toString()
                            val entries = state.entriesByDay[key].orEmpty()
                            val isToday = date == today
                            val isSelected = key == selectedDay
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> primary
                                            isToday -> primary.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { selectedDay = key }
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    fontSize = 14.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (entries.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else priorityDotColor(entries))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

    val date = LocalDate.parse(selectedDay)
    val selected = state.entriesByDay[selectedDay].orEmpty()
    Text(
        "Công việc ${dayTitle(date, today)}",
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
    if (selected.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📭", fontSize = 36.sp)
                Text("Không có công việc nào", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selected, key = { it.task.id + it.isProjected }) { entry ->
                EntryRow(entry, onClick = { openTask(entry.task.id) })
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { d ->
            Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Tuần (kéo thả đổi ngày) ─────────────────────────────────────────────────

@Composable
private fun ColumnScope.WeekView(state: CalendarUiState, openTask: (String) -> Unit, onMove: (String, LocalDate) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val today = remember { LocalDate.now() }
    var weekStartKey by rememberSaveable { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    val weekStart = LocalDate.parse(weekStartKey)
    val weekEnd = weekStart.plusDays(6)
    val dragState = rememberDragDropState<LocalDate>()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { weekStartKey = weekStart.minusWeeks(1).toString() }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Tuần trước", tint = primary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${weekStart.format(DAY_MONTH)} – ${weekEnd.format(DAY_MONTH)}",
                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface
            )
            if (today !in weekStart..weekEnd) {
                Text(
                    "Về tuần này", fontSize = 12.sp, color = primary,
                    modifier = Modifier.clickable {
                        weekStartKey = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                    }
                )
            }
        }
        IconButton(onClick = { weekStartKey = weekStart.plusWeeks(1).toString() }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Tuần sau", tint = primary)
        }
    }
    Text(
        "Nhấn giữ một việc rồi kéo sang ngày khác để dời hạn.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp)
    )

    DragDropContainer(
        state = dragState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        ghost = { DragGhost(it.label) }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (0L..6L).map { weekStart.plusDays(it) }.forEach { date ->
                WeekDayRow(
                    date = date,
                    today = today,
                    entries = state.entriesByDay[date.toString()].orEmpty(),
                    dragState = dragState,
                    openTask = openTask,
                    onMove = onMove
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekDayRow(
    date: LocalDate,
    today: LocalDate,
    entries: List<CalendarEntry>,
    dragState: DragDropState<LocalDate>,
    openTask: (String) -> Unit,
    onMove: (String, LocalDate) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val isToday = date == today
    DropTarget(dragState, date, Modifier.fillMaxWidth()) { hovered ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        hovered -> primary.copy(alpha = 0.18f)
                        isToday -> primary.copy(alpha = 0.07f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
                .border(
                    if (hovered) 2.dp else 0.dp,
                    if (hovered) primary else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .padding(8.dp)
        ) {
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weekdayShort(date), fontSize = 11.sp, color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) primary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (entries.isEmpty()) {
                Text(
                    "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp)
                )
            } else {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entries.forEach { entry ->
                        val task = entry.task
                        val draggable = !entry.isProjected && !task.isCompleted
                        key(task.id, entry.isProjected) {
                            TaskChip(
                                entry = entry,
                                modifier = if (draggable) {
                                    Modifier.dragSource(dragState, DragPayload(task.id, task.title)) { target -> onMove(task.id, target) }
                                } else Modifier,
                                onClick = { openTask(task.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskChip(entry: CalendarEntry, modifier: Modifier, onClick: () -> Unit) {
    val task = entry.task
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = priorityColor(task.priority).copy(alpha = 0.14f),
        modifier = modifier
            .alpha(if (entry.isProjected || task.isCompleted) 0.5f else 1f)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(priorityColor(task.priority)))
            Spacer(Modifier.width(5.dp))
            val time = task.dueAt?.takeIf { !task.dueAllDay }?.let { (task.startAt?.let { s -> formatTime(s) + "–" } ?: "") + formatTime(it) }
            Text(
                listOfNotNull(time, task.title).joinToString(" ") + if (entry.isProjected) " · dự kiến" else "",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
}

// ─── Lịch trình ──────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AgendaView(state: CalendarUiState, openTask: (String) -> Unit, onToggle: (com.example.todoapplication.domain.model.Task) -> Unit) {
    val today = remember { LocalDate.now() }
    val days = remember(state.entriesByDay, today) {
        (0L until AGENDA_DAYS).map { today.plusDays(it) }
            .mapNotNull { d -> state.entriesByDay[d.toString()]?.takeIf { it.isNotEmpty() }?.let { d to it } }
    }
    if (days.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🗓️", fontSize = 36.sp)
                Text("$AGENDA_DAYS ngày tới chưa có việc nào có hạn", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEach { (date, entries) ->
            item(key = "h-$date") {
                Text(
                    dayTitle(date, today).replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(entries, key = { "$date-${it.task.id}-${it.isProjected}" }) { entry ->
                EntryRow(entry, onClick = { openTask(entry.task.id) }, onToggle = if (entry.isProjected) null else ({ onToggle(entry.task) }))
            }
        }
    }
}

// ─── Dùng chung ──────────────────────────────────────────────────────────────

@Composable
private fun EntryRow(entry: CalendarEntry, onClick: () -> Unit, onToggle: (() -> Unit)? = null) {
    val task = entry.task
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().alpha(if (entry.isProjected) 0.55f else 1f).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onToggle != null) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) StateCompleted else Color.Transparent)
                        .border(1.5.dp, if (task.isCompleted) StateCompleted else priorityColor(task.priority), CircleShape)
                        .clickable(onClick = onToggle)
                )
            } else {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor(task.priority)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                val time = task.dueAt?.takeIf { !task.dueAllDay }?.let { (task.startAt?.let { s -> formatTime(s) + "–" } ?: "") + formatTime(it) }
                val meta = listOfNotNull(time ?: "Cả ngày", if (entry.isProjected) "Lần lặp dự kiến" else null)
                Text(meta.joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date.toEpochDay() - today.toEpochDay()) {
    0L -> "hôm nay"
    1L -> "ngày mai"
    -1L -> "hôm qua"
    else -> "${weekdayShort(date)} ${date.format(DAY_MONTH)}"
}

private fun priorityDotColor(entries: List<CalendarEntry>): Color {
    return when {
        entries.any { it.task.priority == "HIGH" } -> PriorityHighColor
        entries.any { it.task.priority == "MEDIUM" } -> PriorityMediumColor
        else -> PriorityLowColor
    }
}
