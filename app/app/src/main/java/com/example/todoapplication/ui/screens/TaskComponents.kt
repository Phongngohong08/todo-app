package com.example.todoapplication.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoapplication.domain.QuickAddParser
import com.example.todoapplication.domain.allDayDueInDays
import com.example.todoapplication.domain.daysUntilSunday
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.utils.formatDuration
import com.example.todoapplication.ui.utils.formatTime
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.recurrenceDescription
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * [TẦNG UI · COMPONENT] Phần giao diện dùng chung cho màn "Hôm nay" và "Việc làm":
 * thẻ công việc, thanh tạo nhanh (phân tích câu ngay khi gõ), tiêu đề nhóm, vuốt để hoàn thành, banner đồng bộ.
 */

// ─── Task Card ───────────────────────────────────────────────────────────────

/**
 * Thẻ một công việc.
 * @param recommendation lý do "Nên làm trước" (null = không gợi ý) — hiển thị rõ VÌ SAO thay vì chỉ gắn nhãn.
 * @param onToggleMyDay null = ẩn lựa chọn "Ngày của tôi" trong menu.
 * @param selectionMode đang chọn nhiều việc: ô tròn thành ô chọn, bấm thẻ = chọn/bỏ chọn (caller xử lý).
 * @param onLongClick nhấn giữ thẻ (vd bắt đầu chọn nhiều việc).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: Task,
    onCardClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    recommendation: String? = null,
    onSetPriority: (String) -> Unit = {},
    dragHandleModifier: Modifier? = null,
    isInMyDay: Boolean = false,
    onToggleMyDay: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now()
) {
    val isCompleted = task.isCompleted
    val isOverdue = task.isOverdue()
    val accentColor = priorityColor(task.priority)
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onCardClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.border(2.dp, primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (recommendation != null && !isCompleted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Nên làm trước · $recommendation",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            if (dragHandleModifier != null) {
                Box(
                    modifier = dragHandleModifier.width(28.dp).fillMaxHeight().padding(start = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Kéo", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            // Ô tích tròn: tích để hoàn thành, tích lại để mở lại. Khi đang chọn nhiều: ô chọn.
            Box(modifier = Modifier.padding(start = 8.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) primary else Color.Transparent)
                            .then(if (selected) Modifier else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)))
                            .clickable(onClick = onCardClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.Check, contentDescription = "Đã chọn", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                } else Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) StateCompleted else Color.Transparent)
                        .then(if (isCompleted) Modifier else Modifier.border(2.dp, accentColor.copy(alpha = 0.6f), CircleShape))
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = "Mở lại", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp, top = 13.dp, bottom = 12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isInMyDay) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.WbSunny, contentDescription = "Trong Ngày của tôi", modifier = Modifier.size(14.dp), tint = PriorityMediumColor)
                    }
                    if (task.hasPendingSync) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = "Chưa đồng bộ",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    if (task.isRecurring) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Refresh, contentDescription = "Lặp lại", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                    // Đang chọn nhiều: ẩn menu từng thẻ, thao tác nằm ở thanh chọn phía trên
                    if (!selectionMode) {
                        PriorityFlagMenu(accentColor = accentColor, onSetPriority = onSetPriority)
                        TaskOverflowMenu(
                            isInMyDay = isInMyDay,
                            isCompleted = isCompleted,
                            onToggleMyDay = onToggleMyDay,
                            onFocus = onFocus,
                            onDuplicate = onDuplicate,
                            onEdit = onCardClick,
                            onDelete = onDeleteClick
                        )
                    }
                }

                if (task.description.isNotEmpty()) {
                    Text(
                        task.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetaChip(
                        categoryLabel(task.category) + if (task.section.isNotEmpty()) " › " + task.section else "",
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                    task.dueAt?.let { due ->
                        val color = if (isOverdue) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                        MetaChip(
                            formatDueLabel(due, task.dueAllDay, today) +
                                (task.startAt?.takeIf { !task.dueAllDay }?.let { " (từ " + formatTime(it) + ")" } ?: ""),
                            color,
                            if (isOverdue) StateOverdue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            icon = Icons.Default.DateRange
                        )
                    }
                    if (task.estimatedMinutes > 0) {
                        MetaChip("⏱ ${formatDuration(task.estimatedMinutes)}", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                    }
                    if (task.subtaskTotal > 0) {
                        val allDone = task.subtaskDone == task.subtaskTotal
                        MetaChip(
                            "☑ ${task.subtaskDone}/${task.subtaskTotal}",
                            if (allDone) StateCompleted else MaterialTheme.colorScheme.onSurfaceVariant,
                            if (allDone) StateCompleted.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    task.tags.forEach { tag ->
                        MetaChip("@" + tag, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                    }
                    if (task.isRecurring && (task.recurrenceInterval > 1 || task.recurrenceMode == "COMPLETION")) {
                        MetaChip(
                            recurrenceDescription(task.recurrence, "", task.recurrenceInterval, task.recurrenceMode),
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color, container: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(11.dp), tint = color)
                Spacer(Modifier.width(4.dp))
            }
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

fun priorityColor(priority: String): Color = when (priority) {
    "HIGH" -> PriorityHighColor
    "MEDIUM" -> PriorityMediumColor
    else -> PriorityLowColor
}

@Composable
private fun PriorityFlagMenu(accentColor: Color, onSetPriority: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Flag, contentDescription = "Đổi ưu tiên", modifier = Modifier.size(16.dp), tint = accentColor)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            listOf("HIGH" to PriorityHighColor, "MEDIUM" to PriorityMediumColor, "LOW" to PriorityLowColor).forEach { (p, color) ->
                DropdownMenuItem(
                    text = { Text(priorityLabel(p), color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)) },
                    onClick = { expanded = false; onSetPriority(p) }
                )
            }
        }
    }
}

@Composable
private fun TaskOverflowMenu(
    isInMyDay: Boolean,
    isCompleted: Boolean,
    onToggleMyDay: (() -> Unit)?,
    onFocus: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Thêm", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (onToggleMyDay != null) {
                DropdownMenuItem(
                    text = { Text(if (isInMyDay) "Bỏ khỏi Ngày của tôi" else "Thêm vào Ngày của tôi", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = PriorityMediumColor) },
                    onClick = { expanded = false; onToggleMyDay() }
                )
            }
            if (onFocus != null && !isCompleted) {
                DropdownMenuItem(
                    text = { Text("Bắt đầu tập trung 25'", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    onClick = { expanded = false; onFocus() }
                )
            }
            DropdownMenuItem(
                text = { Text("Chỉnh sửa", color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { expanded = false; onEdit() }
            )
            if (onDuplicate != null) {
                DropdownMenuItem(
                    text = { Text("Nhân bản", color = MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false; onDuplicate() }
                )
            }
            DropdownMenuItem(
                text = { Text("Xóa", color = PriorityHighColor) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PriorityHighColor) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

// ─── Nhóm, vuốt, banner ──────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    action: (@Composable () -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$title ($count)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Vuốt sang phải để hoàn thành; vuốt sang trái để hoãn (nếu có [onPostpone]).
 * Thẻ luôn bật lại chỗ cũ — danh sách tự chuyển nó sang nhóm mới khi dữ liệu đổi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToCompleteBox(
    onComplete: () -> Unit,
    onPostpone: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val haptics = LocalHapticFeedback.current
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnPostpone by rememberUpdatedState(onPostpone)
    val dismissState = rememberSwipeToDismissBoxState()
    // Thay cho confirmValueChange (đã deprecated): phản ứng khi vuốt đủ xa rồi đưa thẻ về chỗ cũ
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                currentOnComplete()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnPostpone?.invoke()
            }
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = onPostpone != null,
        backgroundContent = {
            val postponing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = if (postponing) PriorityMediumColor else StateCompleted
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.2f))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (postponing) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (postponing) Icons.Default.Schedule else Icons.Default.Check, contentDescription = null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(if (postponing) "Hoãn" else "Hoàn thành", color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        content()
    }
}

/** Chọn ngày hoãn việc tới: Ngày mai / Cuối tuần / Thứ 2 tuần sau / 1 tuần nữa / Chọn ngày… */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostponeSheet(taskTitle: String, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    var showPicker by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Hoãn sang…", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(taskTitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            com.example.todoapplication.domain.Reschedule.postponeOptions(today).forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    supportingContent = { Text(com.example.todoapplication.ui.utils.weekdayShort(option.date) + " " + option.date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))) },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = null, tint = PriorityMediumColor) },
                    modifier = Modifier.clickable { onPick(option.date) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            ListItem(
                headlineContent = { Text("Chọn ngày…") },
                leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { showPicker = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    state.selectedDateMillis?.let {
                        onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate())
                    }
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Hủy") } }
        ) { DatePicker(state = state) }
    }
}

/**
 * Thanh thao tác khi đang chọn nhiều việc (thay cho thẻ đầu trang): đếm số đã chọn + các thao tác hàng loạt.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onComplete: () -> Unit,
    onMyDay: () -> Unit,
    onPostpone: () -> Unit,
    onPriority: (String) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Thoát chọn", tint = Color.White) }
                Text("Đã chọn $count", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll) { Text("Chọn tất cả", color = Color.White) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BulkAction(Icons.Default.Check, "Xong", count > 0, onComplete)
                BulkAction(Icons.Outlined.WbSunny, "Hôm nay", count > 0, onMyDay)
                BulkAction(Icons.Default.Schedule, "Dời", count > 0, onPostpone)
                Box {
                    var open by remember { mutableStateOf(false) }
                    BulkAction(Icons.Default.Flag, "Ưu tiên", count > 0) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        listOf("HIGH", "MEDIUM", "LOW").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(p)) },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = priorityColor(p)) },
                                onClick = { open = false; onPriority(p) }
                            )
                        }
                    }
                }
                BulkAction(Icons.Default.Delete, "Xóa", count > 0, onDelete)
            }
        }
    }
}

@Composable
private fun BulkAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 11.sp)
    }
}

/** Banner trạng thái đồng bộ: offline / còn thay đổi chưa gửi. Không có gì để báo thì không vẽ. */
@Composable
fun SyncBanner(isOnline: Boolean, pendingSyncCount: Int, modifier: Modifier = Modifier) {
    val (icon, text) = when {
        !isOnline && pendingSyncCount > 0 ->
            Icons.Outlined.CloudOff to "Đang offline — $pendingSyncCount thay đổi đã lưu trên máy, sẽ tự đồng bộ khi có mạng"
        !isOnline -> Icons.Outlined.CloudOff to "Đang offline — bạn vẫn thêm/sửa công việc bình thường"
        pendingSyncCount > 0 -> Icons.Outlined.CloudUpload to "Đang đồng bộ $pendingSyncCount thay đổi…"
        else -> return
    }
    Surface(shape = RoundedCornerShape(12.dp), color = StatePostponed.copy(alpha = 0.13f), modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = StatePostponed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = StatePostponed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController, activeTab: Int) =
    com.example.todoapplication.ui.components.AppBottomBar(navController, activeTab)

// ─── Thanh tạo nhanh ─────────────────────────────────────────────────────────

/** Kết quả thanh tạo nhanh. [newCategory]: tên danh mục mới gõ sau "#" (cần tạo trước khi lưu task). */
data class QuickCreateResult(val draft: TaskDraft, val addToMyDay: Boolean, val newCategory: String?)

// Mẫu nhiệm vụ gợi ý (emoji, tiêu đề, danh mục)
private val QUICK_TEMPLATES = listOf(
    Triple("💧", "Uống nước", "PERSONAL"),
    Triple("🦷", "Đánh răng", "PERSONAL"),
    Triple("😴", "Đi ngủ sớm", "PERSONAL"),
    Triple("💊", "Uống thuốc", "PERSONAL"),
    Triple("📚", "Học tập", "WORK"),
    Triple("🏃", "Tập thể dục", "PERSONAL"),
    Triple("📧", "Gửi email", "WORK"),
    Triple("🛒", "Đi mua sắm", "OTHER")
)

/**
 * Thanh tạo nhanh kiểu Todoist: gõ "Nộp báo cáo mai 3h chiều !cao #Công việc" → phần ngày giờ / ưu tiên /
 * danh mục được tô màu và hiện thành chip xem trước ngay khi gõ (phân tích trên máy, không cần mạng).
 * Chip chọn tay vẫn dùng được khi câu không nói gì về ngày / danh mục / ưu tiên.
 */
/**
 * Nút micro: mở trình nhận giọng nói của hệ thống (tiếng Việt), trả câu nói được về [onResult].
 * Máy không có dịch vụ nhận giọng nói → báo bằng Toast thay vì crash.
 */
@Composable
fun VoiceInputButton(onResult: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spoken.isNullOrEmpty()) onResult(spoken)
    }
    IconButton(onClick = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Nói việc cần làm, vd: \"Gọi mẹ 8h tối mai\"")
        }
        try {
            launcher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Máy chưa có dịch vụ nhận giọng nói", android.widget.Toast.LENGTH_SHORT).show()
        }
    }) {
        Icon(Icons.Default.Mic, contentDescription = "Nhập bằng giọng nói", tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCreateSheet(
    categories: List<String>,
    onDismiss: () -> Unit,
    onCreate: (QuickCreateResult) -> Unit,
    onMoreDetails: (QuickCreateResult) -> Unit,
    onAiAdd: (text: String) -> Unit,
    onTemplates: () -> Unit,
    initialText: String = "",
    defaultAddToMyDay: Boolean = false
) {
    var text by remember { mutableStateOf(initialText) }
    // Preset ngày: 0=Hôm nay,1=Ngày mai,3=3 ngày sau, -2=Cuối tuần, -1=Không
    var datePreset by remember { mutableIntStateOf(-1) }
    var category by remember { mutableStateOf("OTHER") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var addToMyDay by remember { mutableStateOf(defaultAddToMyDay) }
    val focus = remember { FocusRequester() }

    val options = remember(categories) {
        categories.map { QuickAddParser.CategoryOption(it, categoryLabel(it)) } +
            // Tên cũ / tiếng Anh của Hộp thư đến vẫn gõ được
            listOf(QuickAddParser.CategoryOption("OTHER", "Khác"), QuickAddParser.CategoryOption("OTHER", "Inbox"))
    }
    val parsed = remember(text, options) { QuickAddParser.parse(text, LocalDateTime.now(), categories = options) }
    val highlight = MaterialTheme.colorScheme.primary
    val transformation = remember(parsed, highlight) { TokenHighlight(parsed.tokens, highlight) }

    fun result(): QuickCreateResult {
        val presetDue: Long? = when (datePreset) {
            0 -> allDayDueInDays(0)
            1 -> allDayDueInDays(1)
            3 -> allDayDueInDays(3)
            -2 -> allDayDueInDays(daysUntilSunday())
            else -> null
        }
        val finalCategory = parsed.category ?: category
        val draft = TaskDraft(
            title = parsed.title.ifBlank { text.trim() },
            priority = parsed.priority ?: priority,
            dueAt = parsed.dueAt ?: presetDue,
            dueAllDay = if (parsed.dueAt != null) parsed.allDay else presetDue != null,
            category = finalCategory,
            recurrence = parsed.recurrence ?: "NONE",
            recurrenceDays = parsed.recurrenceDays,
            tags = parsed.tags
        )
        val isNew = finalCategory !in categories
        return QuickCreateResult(draft, addToMyDay, if (isNew) finalCategory else null)
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("VD: Nộp báo cáo mai 3h chiều !cao #Công việc @gấp", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                shape = RoundedCornerShape(16.dp),
                visualTransformation = transformation,
                trailingIcon = {
                    VoiceInputButton { spoken ->
                        text = if (text.isBlank()) spoken else text.trimEnd() + " " + spoken
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Xem trước những gì app hiểu được từ câu
            if (parsed.hasAnything) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hiểu là:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    parsed.dueAt?.let { PreviewChip("📅 " + formatDueLabel(it, parsed.allDay)) }
                    parsed.recurrence?.let { PreviewChip("🔁 " + recurrenceDescription(it, parsed.recurrenceDays, 1, "SCHEDULE")) }
                    parsed.priority?.let { PreviewChip("🚩 " + priorityLabel(it)) }
                    parsed.category?.let { PreviewChip("🏷 " + categoryLabel(it) + if (it !in categories) " (mới)" else "") }
                    parsed.tags.forEach { PreviewChip("@" + it) }
                }
            } else if (text.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_TEMPLATES.forEach { (emoji, tmplTitle, tmplCat) ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { text = tmplTitle; category = tmplCat }
                        ) {
                            Text(
                                "$emoji $tmplTitle",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (parsed.dueAt == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hạn", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(0 to "Hôm nay", 1 to "Ngày mai", 3 to "3 ngày sau", -2 to "Cuối tuần", -1 to "Không").forEach { (value, label) ->
                        StatusFilterChip(text = label, selected = datePreset == value, onClick = { datePreset = value })
                    }
                }
            }
            if (parsed.category == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Danh mục", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    categories.forEach { c ->
                        StatusFilterChip(text = categoryLabel(c), selected = category == c, onClick = { category = c })
                    }
                }
            }
            if (parsed.priority == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ưu tiên", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                        StatusFilterChip(text = priorityLabel(p), selected = priority == p, onClick = { priority = p })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { addToMyDay = !addToMyDay }) {
                Checkbox(checked = addToMyDay, onCheckedChange = { addToMyDay = it })
                Text("☀ Thêm vào Ngày của tôi", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTemplates, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Mẫu", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                TextButton(onClick = { onMoreDetails(result()) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Chi tiết", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAiAdd(text) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text("AI", color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                val canCreate = parsed.title.isNotBlank() || (text.isNotBlank() && !parsed.hasAnything)
                FloatingActionButton(
                    onClick = { if (canCreate) onCreate(result()) },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (canCreate) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Thêm")
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Tô màu các đoạn bộ phân tích nhận ra (không đổi nội dung → OffsetMapping.Identity). */
private class TokenHighlight(
    private val tokens: List<QuickAddParser.Token>,
    private val color: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            tokens.forEach { t ->
                if (t.range.last < text.length) {
                    addStyle(
                        SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.12f)),
                        t.range.first, t.range.last + 1
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiQuickAddSheet(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Phân tích bằng AI", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Dành cho câu dài/phức tạp — cần mạng", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("VD: Họp với sếp thứ 6 lúc 3h chiều, khoảng 1 tiếng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            val enabled = !isLoading && text.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                        else Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .clickable(enabled = enabled, onClick = onSubmit),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Phân tích bằng AI", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
