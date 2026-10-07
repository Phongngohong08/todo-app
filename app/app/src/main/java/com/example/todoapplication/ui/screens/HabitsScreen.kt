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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.HabitSummary
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitDraft
import com.example.todoapplication.domain.model.HabitFrequency
import com.example.todoapplication.ui.components.ColorPickerRow
import com.example.todoapplication.ui.components.EmojiPickerRow
import com.example.todoapplication.ui.components.HABIT_EMOJIS
import com.example.todoapplication.ui.components.PALETTE
import com.example.todoapplication.ui.components.parseHexColor
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.theme.StateCompleted
import com.example.todoapplication.ui.utils.WEEKDAY_SHORT
import com.example.todoapplication.ui.utils.weekdayShort
import com.example.todoapplication.ui.viewmodel.HabitsEvent
import com.example.todoapplication.ui.viewmodel.HabitsViewModel
import java.time.LocalDate

/**
 * [TẦNG UI · MÀN HÌNH] Thói quen: mỗi thẻ có chuỗi 🔥, tiến độ tuần và 7 ô của 7 ngày gần nhất (bấm để đánh dấu
 * hoặc bỏ đánh dấu, kể cả ngày đã qua). Bấm vào thẻ để sửa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // null = đóng; Habit giả id rỗng = tạo mới
    var editing by remember { mutableStateOf<Habit?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HabitsEvent.Deleted -> {
                    val r = snackbar.showSnackbar("Đã xóa thói quen \"${e.habit.name}\"", "Hoàn tác", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) viewModel.restore(e.habit.id)
                }
                is HabitsEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Thói quen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = Habit(id = "", name = "") },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, contentDescription = "Thêm thói quen", tint = MaterialTheme.colorScheme.onPrimary) }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") { HabitsSummaryCard(state.todayDone, state.todayDue) }
            if (!state.isLoading && state.active.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌱", fontSize = 40.sp)
                            Text("Chưa có thói quen nào", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                "Nhấn + để bắt đầu một thói quen nhỏ: uống nước, đọc sách 10 phút…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            items(state.active, key = { it.habit.id }) { s ->
                HabitCard(
                    summary = s,
                    onToggle = { date -> viewModel.toggle(s.habit.id, date) },
                    onEdit = { editing = s.habit },
                    modifier = Modifier.animateItem()
                )
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived-header") {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text((if (showArchived) "Ẩn" else "Xem") + " ${state.archived.size} thói quen đã lưu trữ")
                    }
                }
                if (showArchived) {
                    items(state.archived, key = { "a-" + it.habit.id }) { s ->
                        HabitCard(s, onToggle = {}, onEdit = { editing = s.habit }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    editing?.let { habit ->
        HabitEditorSheet(
            habit = habit.takeIf { it.id.isNotEmpty() },
            onDismiss = { editing = null },
            onSave = { draft ->
                viewModel.save(habit.id.ifEmpty { null }, draft)
                editing = null
            },
            onDelete = {
                viewModel.delete(habit)
                editing = null
            }
        )
    }
}

@Composable
private fun HabitsSummaryCard(done: Int, total: Int) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(20.dp), color = primary.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (total == 0) 0f else done / total.toFloat() },
                    modifier = Modifier.size(52.dp),
                    color = primary,
                    trackColor = primary.copy(alpha = 0.15f),
                    strokeWidth = 5.dp
                )
                Text("$done/$total", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = primary)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Hôm nay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    when {
                        total == 0 -> "Không có thói quen nào cần làm hôm nay"
                        done >= total -> "Đã xong hết — tuyệt vời! 🎉"
                        else -> "Còn ${total - done} thói quen cần làm"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HabitCard(summary: HabitSummary, onToggle: (LocalDate) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val habit = summary.habit
    val color = parseHexColor(habit.color, MaterialTheme.colorScheme.primary)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Text(habit.emoji.ifBlank { "🌱" }, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(habit.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val week = if (habit.frequency == HabitFrequency.WEEKLY) " · tuần này ${summary.weekDone}/${summary.weekTarget}" else ""
                    Text(
                        HabitLogic.describe(habit) + week + (if (habit.reminderTime.isNotBlank()) " · ⏰ ${habit.reminderTime}" else ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (summary.streak > 0) {
                    Text("🔥 ${summary.streak} ${HabitLogic.streakUnit(habit)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                summary.lastDays.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(weekdayShort(day.date), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day.done -> color
                                        day.scheduled -> color.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(1.dp, color.copy(alpha = if (day.scheduled || day.done) 0.6f else 0.2f), CircleShape)
                                .semantics {
                                    contentDescription = weekdayShort(day.date) + " " + day.date.dayOfMonth + "/" + day.date.monthValue +
                                        if (day.done) ": đã làm" else if (day.scheduled) ": chưa làm" else ": không cần làm"
                                }
                                .clickable(enabled = !habit.archived) { onToggle(day.date) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (day.done) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            else Text("${day.date.dayOfMonth}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (summary.rate30 > 0f) {
                Text(
                    "30 ngày qua: ${(summary.rate30 * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** Thêm / sửa thói quen. [habit] = null là tạo mới. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditorSheet(habit: Habit?, onDismiss: () -> Unit, onSave: (HabitDraft) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(habit?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(habit?.emoji ?: HABIT_EMOJIS.first()) }
    var color by remember { mutableStateOf(habit?.color?.ifBlank { null } ?: PALETTE.first()) }
    var frequency by remember { mutableStateOf(habit?.frequency ?: HabitFrequency.DAILY) }
    var weekdays by remember { mutableStateOf(habit?.weekdays?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: setOf("MON", "WED", "FRI")) }
    var target by remember { mutableIntStateOf(habit?.targetPerWeek?.takeIf { habit.frequency == HabitFrequency.WEEKLY } ?: 3) }
    var reminder by remember { mutableStateOf(habit?.reminderTime.orEmpty()) }
    var archived by remember { mutableStateOf(habit?.archived ?: false) }
    val askPermission = rememberNotificationPermissionRequest()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (habit == null) "Thói quen mới" else "Sửa thói quen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(100) },
                label = { Text("Tên thói quen") },
                placeholder = { Text("VD: Uống 2 lít nước") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            EmojiPickerRow(HABIT_EMOJIS, emoji) { emoji = it }
            ColorPickerRow(color) { color = it }

            Text("Tần suất", fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(HabitFrequency.DAILY to "Mỗi ngày", HabitFrequency.WEEKDAYS to "Các thứ", HabitFrequency.WEEKLY to "Số lần/tuần")
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = frequency == value,
                        onClick = { frequency = value },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            when (frequency) {
                HabitFrequency.WEEKDAYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEKDAY_SHORT.forEach { (code, label) ->
                        val sel = code in weekdays
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { weekdays = if (sel) weekdays - code else weekdays + code },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 11.sp, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HabitFrequency.WEEKLY -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { target = (target - 1).coerceAtLeast(1) }) { Text("−", fontSize = 20.sp) }
                    Text("$target lần mỗi tuần", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { target = (target + 1).coerceAtMost(7) }) { Text("+", fontSize = 20.sp) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nhắc mỗi ngày", fontWeight = FontWeight.SemiBold)
                    Text("Chỉ nhắc vào ngày còn cần làm và chưa đánh dấu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = reminder.isNotBlank(), onCheckedChange = {
                    reminder = if (it) "20:00" else ""
                    if (it) askPermission("Bật thông báo để được nhắc thói quen.")
                })
            }
            if (reminder.isNotBlank()) TimePickerField(label = "Giờ nhắc", value = reminder) { reminder = it }

            if (habit != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lưu trữ", fontWeight = FontWeight.SemiBold)
                        Text("Tạm dừng: ẩn khỏi Hôm nay, giữ nguyên lịch sử", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habit != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Xóa", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    enabled = name.isNotBlank() && (frequency != HabitFrequency.WEEKDAYS || weekdays.isNotEmpty()),
                    onClick = {
                        onSave(
                            HabitDraft(
                                name = name.trim(),
                                emoji = emoji,
                                color = color,
                                frequency = frequency,
                                weekdays = weekdays.joinToString(","),
                                targetPerWeek = target,
                                reminderTime = reminder,
                                archived = archived
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Lưu") }
            }
        }
    }
}

/** Ô trạng thái thói quen gọn cho tab Hôm nay: bấm để đánh dấu hôm nay. */
@Composable
fun HabitTodayChip(summary: HabitSummary, onToggle: () -> Unit) {
    val habit = summary.habit
    val color = parseHexColor(habit.color, MaterialTheme.colorScheme.primary)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (summary.doneToday) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        shadowElevation = if (summary.doneToday) 0.dp else 1.dp,
        modifier = Modifier.widthIn(min = 140.dp).clickable(onClick = onToggle)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (summary.doneToday) color else color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (summary.doneToday) Icon(Icons.Default.Check, contentDescription = "Đã làm", tint = Color.White, modifier = Modifier.size(16.dp))
                else Text(habit.emoji.ifBlank { "🌱" }, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(habit.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                if (summary.streak > 0) Text("🔥 ${summary.streak} ${HabitLogic.streakUnit(habit)}", fontSize = 11.sp, color = if (summary.doneToday) StateCompleted else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
