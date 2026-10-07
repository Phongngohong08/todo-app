package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.domain.ALL_DAY_TIME
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.ReminderRules
import com.example.todoapplication.domain.TagRules
import com.example.todoapplication.domain.allDayDue
import com.example.todoapplication.domain.daysUntilSunday
import com.example.todoapplication.domain.model.RecurrenceMode
import com.example.todoapplication.domain.model.Subtask
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.ui.components.rememberNotificationPermissionRequest
import com.example.todoapplication.ui.components.sharedTaskBounds
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.RECURRENCE_OPTIONS
import com.example.todoapplication.ui.utils.WEEKDAY_SHORT
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.utils.formatDuration
import com.example.todoapplication.ui.utils.formatTime
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.recurrenceDescription
import com.example.todoapplication.ui.utils.recurrenceLabel
import com.example.todoapplication.ui.viewmodel.TaskDetailEvent
import com.example.todoapplication.ui.viewmodel.TaskDetailViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [TẦNG UI · MÀN HÌNH] Chi tiết công việc — tạo mới (taskId="new") hoặc sửa; kèm danh sách bước con.
 * Hạn chót tách NGÀY và GIỜ (việc "cả ngày" không bị quá hạn giữa ngày), thời lượng ước tính cho lịch AI,
 * lặp mỗi N / theo ngày hoàn thành / ngày kết thúc, "Bỏ qua lần này", "Ngày của tôi" và nút tập trung.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: String,
    taskDetailViewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory),
    /** Khung chi tiết bên phải (tablet): đóng khung thay vì quay lại màn trước. */
    onClose: (() -> Unit)? = null,
    /** Khung chi tiết: mở việc khác (vd bản sao) ngay trong khung. */
    onOpenTask: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val close: () -> Unit = onClose ?: { navController.popBackStack() }
    val zone = remember { ZoneId.systemDefault() }
    val isNewTask = taskDetailViewModel.isNew
    val isLoading by taskDetailViewModel.isBusy.collectAsStateWithLifecycle()
    val askNotificationPermission = rememberNotificationPermissionRequest()

    // Mỗi ô trong form là một state cục bộ (sống qua xoay màn). Khi SỬA, được điền từ sự kiện Loaded.
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("MEDIUM") }
    var dueAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueAllDay by rememberSaveable { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf("OTHER") }
    var recurrence by rememberSaveable { mutableStateOf("NONE") }
    var recurrenceDays by rememberSaveable { mutableStateOf("") }
    var recurrenceInterval by rememberSaveable { mutableIntStateOf(1) }
    var recurrenceMode by rememberSaveable { mutableStateOf(RecurrenceMode.SCHEDULE) }
    var recurrenceUntil by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminderOffset by rememberSaveable { mutableIntStateOf(0) }
    var estimatedMinutes by rememberSaveable { mutableIntStateOf(0) }
    // Giai đoạn 2 — lưu dạng chuỗi để rememberSaveable giữ được qua xoay màn
    var tagsText by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("") }
    var startAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var remindersText by rememberSaveable { mutableStateOf("0") }
    var showStartPicker by remember { mutableStateOf(false) }
    var subtaskDuePicker by remember { mutableStateOf<Subtask?>(null) }
    val tags = TagRules.decode(tagsText)
    val reminders = ReminderRules.decode(remindersText).toSet()
    var inMyDay by rememberSaveable { mutableStateOf(false) }
    var isCompleted by rememberSaveable { mutableStateOf(false) }
    var subtaskInput by rememberSaveable { mutableStateOf("") }
    var formInitialized by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    val subtasks by taskDetailViewModel.subtasks.collectAsStateWithLifecycle()
    val categories by taskDetailViewModel.categories.collectAsStateWithLifecycle()
    val aiWorking by taskDetailViewModel.aiWorking.collectAsStateWithLifecycle()
    val lists by taskDetailViewModel.lists.collectAsStateWithLifecycle()
    val allTags by taskDetailViewModel.allTags.collectAsStateWithLifecycle()
    val focusHistory by taskDetailViewModel.focusHistory.collectAsStateWithLifecycle()
    val attachments by taskDetailViewModel.attachments.collectAsStateWithLifecycle()
    val places by taskDetailViewModel.places.collectAsStateWithLifecycle()
    val taskLocation by taskDetailViewModel.taskLocation.collectAsStateWithLifecycle()
    var breakdown by remember { mutableStateOf<TaskDetailEvent.BreakdownReady?>(null) }

    val dueDate: LocalDate? = dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val dueTime: LocalTime? = if (dueAt != null && !dueAllDay) Instant.ofEpochMilli(dueAt!!).atZone(zone).toLocalTime() else null

    fun setDue(date: LocalDate?, time: LocalTime?) {
        if (date == null) {
            dueAt = null
            dueAllDay = true
            startAt = null
            return
        }
        dueAllDay = time == null
        val newDue = date.atTime(time ?: ALL_DAY_TIME).atZone(zone).toInstant().toEpochMilli()
        dueAt = newDue
        // Khung giờ đi theo ngày của hạn; hết giờ (cả ngày) hoặc bắt đầu sau hạn thì bỏ giờ bắt đầu
        startAt = startAt?.let { old ->
            val startTime = Instant.ofEpochMilli(old).atZone(zone).toLocalTime()
            date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        }?.takeIf { time != null && it < newDue }
    }

    LaunchedEffect(Unit) {
        taskDetailViewModel.events.collect { event ->
            when (event) {
                is TaskDetailEvent.Loaded -> {
                    val task = event.task
                    title = task.title
                    description = task.description
                    priority = task.priority
                    dueAt = task.dueAt
                    dueAllDay = task.dueAllDay || task.dueAt == null
                    category = task.category
                    recurrence = task.recurrence
                    recurrenceDays = task.recurrenceDays
                    recurrenceInterval = task.recurrenceInterval
                    recurrenceMode = task.recurrenceMode
                    recurrenceUntil = task.recurrenceUntil
                    reminderOffset = task.reminderOffsetMinutes
                    estimatedMinutes = task.estimatedMinutes
                    tagsText = TagRules.encode(task.tags)
                    section = task.section
                    startAt = task.startAt
                    remindersText = ReminderRules.encode(task.reminderOffsets)
                    inMyDay = task.myDay == taskDetailViewModel.todayKey()
                    isCompleted = task.isCompleted
                    formInitialized = true
                }
                TaskDetailEvent.Saved -> {
                    Toast.makeText(context, "Đã lưu công việc", Toast.LENGTH_SHORT).show()
                    close()
                }
                TaskDetailEvent.Deleted -> {
                    Toast.makeText(context, "Đã chuyển vào Thùng rác", Toast.LENGTH_SHORT).show()
                    close()
                }
                is TaskDetailEvent.Info -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is TaskDetailEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                is TaskDetailEvent.BreakdownReady -> breakdown = event
                is TaskDetailEvent.EstimateReady -> {
                    estimatedMinutes = event.minutes
                    Toast.makeText(context, "AI gợi ý: ${formatDuration(event.minutes)}", Toast.LENGTH_SHORT).show()
                }
                is TaskDetailEvent.Duplicated -> {
                    Toast.makeText(context, "Đã nhân bản — đang mở bản sao", Toast.LENGTH_SHORT).show()
                    if (onOpenTask != null) onOpenTask(event.taskId) else navController.navigate(Screen.TaskDetail.createRoute(event.taskId)) {
                        popUpTo(Screen.TaskDetail.route) { inclusive = true }
                    }
                }
            }
        }
    }

    LaunchedEffect(taskId) {
        if (!isNewTask) {
            // Sau khi xoay màn, form đã được khôi phục → KHÔNG tải lại kẻo ghi đè phần đang sửa.
            if (!formInitialized) taskDetailViewModel.loadTask()
        } else if (!formInitialized) {
            formInitialized = true
            // Điền sẵn từ thanh tạo nhanh / AI / mẫu / nội dung chia sẻ (consume = lấy ra rồi xóa)
            QuickAddDraft.consume()?.let { (draft, addToMyDay) ->
                title = draft.title
                description = draft.description
                priority = draft.priority.ifBlank { "MEDIUM" }
                dueAt = draft.dueAt
                dueAllDay = draft.dueAllDay || draft.dueAt == null
                category = draft.category.ifBlank { "OTHER" }
                recurrence = draft.recurrence
                recurrenceDays = draft.recurrenceDays
                tagsText = TagRules.encode(draft.tags)
                section = draft.section
                inMyDay = addToMyDay
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Scaffold(
        modifier = Modifier.sharedTaskBounds(taskId),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Text(
                        text = if (isNewTask) "Công việc mới ✨" else "Chỉnh sửa công việc",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isNewTask) {
                        if (!isCompleted) {
                            IconButton(onClick = { navController.navigate(Screen.Focus.createRoute(taskId)) }) {
                                Icon(Icons.Default.Timer, contentDescription = "Bắt đầu tập trung", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        DetailOverflowMenu(
                            canSkip = recurrence != "NONE" && dueAt != null && !isCompleted,
                            onSkip = taskDetailViewModel::skipOccurrence,
                            onDuplicate = taskDetailViewModel::duplicate,
                            onAddToCalendar = if (dueAt == null) null else ({
                                // Mở app Lịch của máy với sự kiện điền sẵn (Intent ACTION_INSERT — không cần quyền ghi lịch)
                                val draftTask = com.example.todoapplication.domain.model.Task(
                                    id = taskId, title = title, description = description, dueAt = dueAt, dueAllDay = dueAllDay,
                                    startAt = startAt, estimatedMinutes = estimatedMinutes
                                )
                                runCatching { context.startActivity(com.example.todoapplication.data.calendar.DeviceCalendar.insertIntent(draftTask)) }
                                    .onFailure { Toast.makeText(context, "Máy chưa có ứng dụng Lịch", Toast.LENGTH_SHORT).show() }
                            }),
                            onDelete = taskDetailViewModel::delete
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (!isLoading) Brush.horizontalGradient(listOf(primary, tertiary))
                            else Brush.horizontalGradient(listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ))
                        )
                        .clickable(enabled = !isLoading) {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Tiêu đề không được để trống", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            if (recurrence != "NONE" && dueAt == null) {
                                Toast.makeText(context, "Vui lòng đặt hạn chót cho công việc lặp lại", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            // Lời nhắc sát giờ nhất là lời nhắc chính (client cũ chỉ hiểu một lời nhắc)
                            val mainAndExtra = ReminderRules.split(reminders)
                            taskDetailViewModel.save(
                                TaskDraft(
                                    title = title,
                                    description = description,
                                    priority = priority,
                                    dueAt = dueAt,
                                    dueAllDay = dueAllDay,
                                    category = category,
                                    recurrence = recurrence,
                                    recurrenceDays = if (recurrence == "WEEKLY") recurrenceDays else "",
                                    reminderOffsetMinutes = reminderOffset,
                                    estimatedMinutes = estimatedMinutes,
                                    recurrenceInterval = if (recurrence == "NONE") 1 else recurrenceInterval,
                                    recurrenceMode = if (recurrence == "NONE") RecurrenceMode.SCHEDULE else recurrenceMode,
                                    recurrenceUntil = if (recurrence == "NONE") null else recurrenceUntil,
                                    tags = tags,
                                    section = section,
                                    startAt = startAt,
                                    extraReminders = mainAndExtra.second
                                ).copy(reminderOffsetMinutes = mainAndExtra.first),
                                inMyDay
                            )
                            if (dueAt != null) askNotificationPermission("Bật thông báo để được nhắc khi việc này đến hạn.")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isNewTask) "Tạo công việc" else "Lưu thay đổi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Tên & Mô tả ──
            DetailSection(emoji = "📝", title = "Tên & Mô tả") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề công việc") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả chi tiết (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                // Web / email / số điện thoại trong mô tả → bấm để mở
                com.example.todoapplication.ui.components.LinkChips(description, Modifier.padding(top = 8.dp))
            }

            // ── Ngày của tôi ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (inMyDay) PriorityMediumColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable { inMyDay = !inMyDay }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = PriorityMediumColor)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (inMyDay) "Trong Ngày của tôi" else "Thêm vào Ngày của tôi", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Danh sách việc bạn chọn làm hôm nay", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = inMyDay, onCheckedChange = { inMyDay = it })
                }
            }

            // ── Độ ưu tiên ──
            DetailSection(emoji = "🎯", title = "Độ ưu tiên") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("LOW", priorityLabel("LOW"), PriorityLowColor),
                        Triple("MEDIUM", priorityLabel("MEDIUM"), PriorityMediumColor),
                        Triple("HIGH", priorityLabel("HIGH"), PriorityHighColor)
                    ).forEach { (p, label, color) ->
                        val isSelected = priority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color else color.copy(alpha = 0.12f))
                                .clickable { priority = p }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSelected) Color.White else color, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Hạn chót: ngày + (tùy chọn) giờ ──
            DetailSection(emoji = "📅", title = "Hạn chót") {
                val today = LocalDate.now(zone)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Hôm nay", 1 to "Ngày mai", 3 to "3 ngày sau",
                        daysUntilSunday(today) to "Cuối tuần", -1 to "Không"
                    ).forEach { (offset, label) ->
                        val target = if (offset < 0) null else today.plusDays(offset.toLong())
                        StatusFilterChip(
                            text = label,
                            selected = dueDate == target,
                            onClick = { setDue(target, dueTime) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        icon = Icons.Default.DateRange,
                        text = dueAt?.let { formatDueLabel(it, allDay = true, today = today, zone = zone) } ?: "Chọn ngày...",
                        active = dueAt != null,
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker = true },
                        onClear = if (dueAt != null) ({ setDue(null, null) }) else null
                    )
                    PickerField(
                        icon = Icons.Default.Schedule,
                        text = dueTime?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Cả ngày",
                        active = dueTime != null,
                        enabled = dueAt != null,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimePicker = true },
                        onClear = if (dueTime != null) ({ setDue(dueDate, null) }) else null
                    )
                }
                // Khung giờ: giờ bắt đầu (tùy chọn) khi hạn có giờ — vd họp 09:00–10:30
                if (dueTime != null) {
                    Spacer(Modifier.height(10.dp))
                    PickerField(
                        icon = Icons.Default.PlayArrow,
                        text = startAt?.let { "Bắt đầu " + formatTime(it) + " → " + formatTime(dueAt!!) } ?: "Thêm giờ bắt đầu (khung giờ)",
                        active = startAt != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showStartPicker = true },
                        onClear = if (startAt != null) ({ startAt = null }) else null
                    )
                }
                if (dueAt != null && dueAllDay) {
                    Text(
                        "Việc cả ngày: chỉ quá hạn khi sang ngày hôm sau; nhắc theo giờ mặc định trong Cài đặt.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ── Thời lượng ước tính ──
            DetailSection(emoji = "⏱", title = "Thời lượng ước tính") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiChip(
                        label = "✨ AI gợi ý",
                        working = aiWorking == TaskDetailViewModel.AiJob.ESTIMATE,
                        onClick = { taskDetailViewModel.requestEstimate(title, description) }
                    )
                    val presets = listOf(0, 15, 25, 30, 45, 60, 90, 120, 180)
                    // Giá trị lẻ (AI gợi ý / tạo từ máy khác) vẫn hiện thành một chip đang chọn
                    if (estimatedMinutes !in presets) {
                        StatusFilterChip(text = formatDuration(estimatedMinutes), selected = true, onClick = {})
                    }
                    presets.forEach { m ->
                        StatusFilterChip(
                            text = if (m == 0) "Chưa rõ" else formatDuration(m),
                            selected = estimatedMinutes == m,
                            onClick = { estimatedMinutes = m }
                        )
                    }
                }
                Text(
                    "Giúp AI xếp lịch trong ngày đúng độ dài của việc.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ── Danh mục ──
            DetailSection(emoji = "🏷️", title = "Danh mục") {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c ->
                        StatusFilterChip(text = categoryLabel(c), selected = category == c, onClick = { category = c })
                    }
                }
                Spacer(Modifier.height(8.dp))
                var newCategory by remember { mutableStateOf("") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Thêm danh mục mới") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        singleLine = true
                    )
                    SmallActionButton("Thêm") {
                        taskDetailViewModel.addCategory(newCategory) { added -> category = added }
                        newCategory = ""
                    }
                }
                // Mục (cột Kanban) của danh sách đang chọn
                val sections = lists.firstOrNull { it.name == category }?.sections.orEmpty()
                if (sections.isNotEmpty()) {
                    Text("Mục", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusFilterChip(text = "Chưa phân mục", selected = section !in sections, onClick = { section = "" })
                        sections.forEach { s -> StatusFilterChip(text = s, selected = section == s, onClick = { section = s }) }
                    }
                }
            }

            // ── Nhãn ──
            DetailSection(emoji = "🔖", title = "Nhãn") {
                if (tags.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tagsText = TagRules.encode(tags - tag) },
                                label = { Text("@$tag") },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Bỏ nhãn", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                var newTag by remember { mutableStateOf("") }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Thêm nhãn (vd: nhà, gấp)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        singleLine = true
                    )
                    SmallActionButton("Thêm") {
                        tagsText = TagRules.encode(TagRules.normalize(tags + newTag.split(",")))
                        newTag = ""
                    }
                }
                val suggestions = allTags.filter { it !in tags }.take(8)
                if (suggestions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { t ->
                            SuggestionChip(onClick = { tagsText = TagRules.encode(TagRules.normalize(tags + t)) }, label = { Text("@$t", fontSize = 12.sp) })
                        }
                    }
                }
            }

            // ── Lặp lại ──
            DetailSection(emoji = "🔁", title = "Lặp lại") {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RECURRENCE_OPTIONS.forEach { r ->
                        StatusFilterChip(text = recurrenceLabel(r), selected = recurrence == r, onClick = { recurrence = r })
                    }
                }
                if (recurrence != "NONE") {
                    val unit = when (recurrence) {
                        "DAILY" -> "ngày"
                        "WEEKLY" -> "tuần"
                        else -> "tháng"
                    }
                    Spacer(Modifier.height(12.dp))
                    // Mỗi N ngày/tuần/tháng
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mỗi", color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { recurrenceInterval = (recurrenceInterval - 1).coerceAtLeast(1) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Giảm")
                        }
                        Text("$recurrenceInterval", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { recurrenceInterval = (recurrenceInterval + 1).coerceAtMost(RecurrenceRules.MAX_INTERVAL) }) {
                            Icon(Icons.Default.Add, contentDescription = "Tăng")
                        }
                        Text(unit, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (recurrence == "WEEKLY") {
                        Text("Vào các thứ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                        val selectedDays = recurrenceDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WEEKDAY_SHORT.forEach { (code, label) ->
                                val isSel = code in selectedDays
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val ns = if (isSel) selectedDays - code else selectedDays + code
                                            recurrenceDays = WEEKDAY_SHORT.keys.filter { it in ns }.joinToString(",")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // Theo lịch cố định hay tính từ ngày hoàn thành (Todoist "every" vs "every!")
                    Text("Lần tiếp theo tính từ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusFilterChip("Lịch cố định", recurrenceMode == RecurrenceMode.SCHEDULE) { recurrenceMode = RecurrenceMode.SCHEDULE }
                        StatusFilterChip("Ngày hoàn thành", recurrenceMode == RecurrenceMode.COMPLETION) { recurrenceMode = RecurrenceMode.COMPLETION }
                    }
                    Text(
                        if (recurrenceMode == RecurrenceMode.SCHEDULE) "VD: họp mỗi thứ 2 — làm trễ cũng không đổi lịch."
                        else "VD: tưới cây 3 ngày sau lần tưới trước — làm trễ thì lần sau dời theo.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    PickerField(
                        icon = Icons.Default.EventBusy,
                        text = recurrenceUntil?.let { "Kết thúc: " + formatDueLabel(it, allDay = true, zone = zone) } ?: "Lặp mãi (chọn ngày kết thúc)",
                        active = recurrenceUntil != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showUntilPicker = true },
                        onClear = if (recurrenceUntil != null) ({ recurrenceUntil = null }) else null
                    )
                    Text(
                        recurrenceDescription(recurrence, recurrenceDays, recurrenceInterval, recurrenceMode),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (dueAt == null) {
                        Text("⚠ Cần đặt hạn chót để dùng lặp lại.", color = PriorityMediumColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // ── Lời nhắc ──
            DetailSection(emoji = "🔔", title = "Lời nhắc") {
                if (dueAt == null) {
                    Text("Đặt hạn chót để bật nhắc nhở.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    val options = if (dueAllDay) listOf(0 to "Buổi sáng của ngày đến hạn", 24 * 60 to "Trước 1 ngày")
                    else listOf(0 to "Đúng giờ", 5 to "Trước 5 phút", 10 to "Trước 10 phút", 30 to "Trước 30 phút", 60 to "Trước 1 giờ", 24 * 60 to "Trước 1 ngày")
                    // Chọn được nhiều lời nhắc (tối đa 5); luôn giữ ít nhất một
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (mins, label) ->
                            StatusFilterChip(text = label, selected = mins in reminders, onClick = {
                                val next = if (mins in reminders) reminders - mins else reminders + mins
                                if (next.isNotEmpty() && next.size <= ReminderRules.MAX_REMINDERS) {
                                    remindersText = ReminderRules.encode(ReminderRules.normalize(next))
                                }
                            })
                        }
                    }
                    Text(
                        if (startAt != null && !dueAllDay) "Chọn được nhiều lời nhắc · tính theo giờ bắt đầu" else "Chọn được nhiều lời nhắc",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ── Nhắc theo địa điểm (Geofencing) ──
            DetailSection(emoji = "📍", title = "Nhắc theo địa điểm") {
                LocationSection(
                    enabled = !isNewTask,
                    places = places,
                    current = taskLocation,
                    onSet = taskDetailViewModel::setLocation,
                    onClear = taskDetailViewModel::clearLocation
                )
            }

            // ── Đính kèm (ảnh / tài liệu quét) ──
            DetailSection(emoji = "📎", title = "Đính kèm") {
                AttachmentsSection(
                    attachments = attachments,
                    enabled = !isNewTask,
                    onAdd = taskDetailViewModel::addAttachments,
                    onDelete = taskDetailViewModel::deleteAttachment,
                    onTextRecognized = { text -> description = if (description.isBlank()) text else description.trimEnd() + "\n\n" + text }
                )
            }

            // ── Lịch sử tập trung ──
            if (focusHistory.isNotEmpty()) {
                DetailSection(emoji = "🍅", title = "Đã tập trung") {
                    val total = focusHistory.sumOf { it.minutes }
                    Text(
                        "${focusHistory.size} phiên · ${formatDuration(total)}" +
                            (if (estimatedMinutes > 0) " / ước tính ${formatDuration(estimatedMinutes)}" else ""),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Gần nhất: " + formatDueLabel(focusHistory.first().startedAt, allDay = false, zone = zone),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Các bước con ──
            DetailSection(emoji = "✅", title = "Các bước con") {
                val doneCount = subtasks.count { it.isDone }
                if (subtasks.isNotEmpty()) {
                    val fraction = doneCount.toFloat() / subtasks.size.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(primary, tertiary))))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("$doneCount/${subtasks.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                    }
                }
                subtasks.forEach { sub ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sub.isDone, onCheckedChange = { taskDetailViewModel.toggleSubtask(sub) }, colors = CheckboxDefaults.colors(checkedColor = primary))
                        Text(
                            sub.title,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (sub.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                        // Hạn riêng của bước: bấm để chọn ngày, giữ để bỏ hạn
                        val subDue = sub.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        if (subDue != null) {
                            val late = !sub.isDone && subDue.isBefore(LocalDate.now(zone))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (late) StateOverdue else primary).copy(alpha = 0.12f),
                                modifier = Modifier.combinedClickable(
                                    onClick = { subtaskDuePicker = sub },
                                    onLongClick = { taskDetailViewModel.setSubtaskDue(sub, null) }
                                )
                            ) {
                                Text(
                                    formatDueLabel(allDayDue(subDue, zone), allDay = true, zone = zone),
                                    fontSize = 11.sp,
                                    color = if (late) StateOverdue else primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            IconButton(onClick = { subtaskDuePicker = sub }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Event, contentDescription = "Đặt hạn cho bước", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { taskDetailViewModel.deleteSubtask(sub) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa bước", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subtaskInput,
                        onValueChange = { subtaskInput = it },
                        label = { Text("Thêm bước con") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        singleLine = true
                    )
                    SmallActionButton("Thêm") {
                        taskDetailViewModel.addSubtask(subtaskInput)
                        subtaskInput = ""
                    }
                }
                Spacer(Modifier.height(8.dp))
                AiChip(
                    label = "✨ Nhờ AI chia nhỏ việc này",
                    working = aiWorking == TaskDetailViewModel.AiJob.BREAKDOWN,
                    onClick = { taskDetailViewModel.requestBreakdown(title, description) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        DateDialog(
            initial = dueDate ?: LocalDate.now(zone),
            onDismiss = { showDatePicker = false },
            onPick = { date -> setDue(date, dueTime); showDatePicker = false }
        )
    }
    if (showTimePicker) {
        TimeDialog(
            initial = dueTime ?: LocalTime.of(9, 0),
            onDismiss = { showTimePicker = false },
            onPick = { time -> setDue(dueDate ?: LocalDate.now(zone), time); showTimePicker = false }
        )
    }
    if (showStartPicker && dueAt != null && dueTime != null) {
        TimeDialog(
            initial = startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: dueTime.minusHours(1),
            onDismiss = { showStartPicker = false },
            onPick = { time ->
                showStartPicker = false
                if (!time.isBefore(dueTime)) {
                    Toast.makeText(context, "Giờ bắt đầu phải trước ${formatTime(dueAt!!)}", Toast.LENGTH_SHORT).show()
                } else {
                    startAt = dueDate!!.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    // Chưa ước tính thời lượng → lấy luôn độ dài khung giờ
                    if (estimatedMinutes == 0) {
                        estimatedMinutes = java.time.Duration.between(time, dueTime).toMinutes().toInt().coerceAtMost(1440)
                    }
                }
            }
        )
    }
    subtaskDuePicker?.let { sub ->
        DateDialog(
            initial = sub.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dueDate ?: LocalDate.now(zone),
            onDismiss = { subtaskDuePicker = null },
            onPick = { date -> taskDetailViewModel.setSubtaskDue(sub, date.toString()); subtaskDuePicker = null }
        )
    }
    breakdown?.let { result ->
        BreakdownDialog(
            steps = result.steps,
            estimatedMinutes = result.estimatedMinutes,
            canSetEstimate = estimatedMinutes == 0,
            onDismiss = { breakdown = null },
            onConfirm = { chosen, useEstimate ->
                taskDetailViewModel.addSubtasks(chosen)
                if (useEstimate) estimatedMinutes = result.estimatedMinutes
                breakdown = null
            }
        )
    }
    if (showUntilPicker) {
        DateDialog(
            initial = recurrenceUntil?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: (dueDate ?: LocalDate.now(zone)).plusMonths(1),
            onDismiss = { showUntilPicker = false },
            onPick = { date -> recurrenceUntil = allDayDue(date, zone); showUntilPicker = false }
        )
    }
}

/** Nút gọi AI (hiện vòng quay khi đang chờ). */
@Composable
private fun AiChip(label: String, working: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = !working, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (working) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

/** Xem trước các bước AI gợi ý: bỏ chọn bước không cần rồi thêm. */
@Composable
private fun BreakdownDialog(
    steps: List<String>,
    estimatedMinutes: Int,
    canSetEstimate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Boolean) -> Unit
) {
    val checked = remember(steps) { mutableStateListOf<Boolean>().apply { addAll(steps.map { true }) } }
    var useEstimate by remember { mutableStateOf(canSetEstimate && estimatedMinutes > 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI gợi ý các bước") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                steps.forEachIndexed { i, step ->
                    Row(
                        Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                        Text(step, fontSize = 14.sp)
                    }
                }
                if (canSetEstimate && estimatedMinutes > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { useEstimate = !useEstimate },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useEstimate, onCheckedChange = { useEstimate = it })
                        Text("Đặt thời lượng ước tính: ${formatDuration(estimatedMinutes)}", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            val count = checked.count { it }
            TextButton(
                enabled = count > 0 || useEstimate,
                onClick = { onConfirm(steps.filterIndexed { i, _ -> checked[i] }, useEstimate) }
            ) { Text(if (count > 0) "Thêm $count bước" else "Áp dụng") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun DetailOverflowMenu(canSkip: Boolean, onSkip: () -> Unit, onDuplicate: () -> Unit, onAddToCalendar: (() -> Unit)?, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Thêm", tint = MaterialTheme.colorScheme.onPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canSkip) {
                DropdownMenuItem(
                    text = { Text("Bỏ qua lần này") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                    onClick = { expanded = false; onSkip() }
                )
            }
            if (onAddToCalendar != null) {
                DropdownMenuItem(
                    text = { Text("Thêm vào lịch của máy") },
                    leadingIcon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                    onClick = { expanded = false; onAddToCalendar() }
                )
            }
            DropdownMenuItem(
                text = { Text("Nhân bản") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = { expanded = false; onDuplicate() }
            )
            DropdownMenuItem(
                text = { Text("Xóa", color = PriorityHighColor) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PriorityHighColor) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

@Composable
private fun PickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (onClear != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Chọn ngày (Material 3). DatePicker làm việc với mốc UTC 00:00 của ngày được chọn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn giờ") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Chọn") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ─── Reusable section wrapper ─────────────────────────────────────────────────

@Composable
private fun DetailSection(
    emoji: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

/**
 * ViewModel chi tiết cho khung bên phải trên màn rộng: không có đích điều hướng riêng nên taskId được truyền
 * qua CreationExtras (DEFAULT_ARGS_KEY) — createSavedStateHandle() của Factory đọc ra như khi mở bằng route.
 * key theo taskId → đổi việc thì có ViewModel mới, quay lại việc cũ dùng lại cái cũ.
 */
@Composable
fun rememberPaneDetailViewModel(taskId: String): TaskDetailViewModel {
    val owner = checkNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current)
    val base = (owner as? androidx.lifecycle.HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: androidx.lifecycle.viewmodel.CreationExtras.Empty
    val extras = androidx.lifecycle.viewmodel.MutableCreationExtras(base).apply {
        set(androidx.lifecycle.DEFAULT_ARGS_KEY, android.os.Bundle().apply { putString("taskId", taskId) })
    }
    return viewModel(key = "pane-$taskId", factory = TaskDetailViewModel.Factory, extras = extras)
}
