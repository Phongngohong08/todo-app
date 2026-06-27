package com.example.todoapplication.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.recurrenceLabel
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.RECURRENCE_OPTIONS
import com.example.todoapplication.data.repository.CategoryStore
import com.example.todoapplication.ui.viewmodel.TaskDetailEvent
import com.example.todoapplication.ui.viewmodel.TaskDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    navController: NavController,
    taskId: String,
    taskDetailViewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory)
) {
    val context = LocalContext.current
    val isNewTask = taskId == "new"
    val isLoading by taskDetailViewModel.isBusy.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var dueDate by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OTHER") }
    var recurrence by remember { mutableStateOf("NONE") }
    var subtaskInput by remember { mutableStateOf("") }
    val subtasks by taskDetailViewModel.subtasks.collectAsStateWithLifecycle()
    val calendar = remember { Calendar.getInstance() }

    // Lắng nghe sự kiện từ ViewModel: nạp dữ liệu, lưu xong, hoặc lỗi
    LaunchedEffect(Unit) {
        taskDetailViewModel.events.collect { event ->
            when (event) {
                is TaskDetailEvent.Loaded -> {
                    val task = event.task
                    title = task.title
                    description = task.description ?: ""
                    priority = task.priority
                    dueDate = task.dueDate ?: ""
                    category = task.category
                    recurrence = task.recurrence
                    if (dueDate.isNotEmpty()) parseIso8601(dueDate)?.let { calendar.time = it }
                }
                TaskDetailEvent.Saved -> {
                    Toast.makeText(context, "Đã lưu công việc thành công!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is TaskDetailEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(taskId) {
        if (!isNewTask) {
            taskDetailViewModel.loadTask(taskId)
            taskDetailViewModel.loadSubtasks(taskId)
        } else {
            QuickAddDraft.consume()?.let { draft ->
                title = draft.title
                description = draft.description
                priority = draft.priority.ifBlank { "MEDIUM" }
                dueDate = draft.dueDate ?: ""
                category = draft.category.ifBlank { "OTHER" }
                if (dueDate.isNotEmpty()) parseIso8601(dueDate)?.let { calendar.time = it }
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            // Colored header replacing standard TopAppBar
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = if (isNewTask) "Công việc mới ✨" else "Chỉnh sửa công việc",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
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
                            if (recurrence != "NONE" && dueDate.isEmpty()) {
                                Toast.makeText(context, "Vui lòng đặt hạn chót cho công việc lặp lại", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val dateString = if (dueDate.isEmpty()) null else dueDate
                            if (isNewTask) {
                                taskDetailViewModel.create(
                                    CreateTaskInput(title, description, priority, dateString, category, recurrence)
                                )
                            } else {
                                taskDetailViewModel.update(
                                    taskId,
                                    UpdateTaskInput(title, description, priority, dateString, category, recurrence)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (isNewTask) "Tạo công việc" else "Lưu thay đổi",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading && title.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Section 1: Tên & Mô tả ────────────────────────────────
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                }

                // ── Section 2: Độ ưu tiên ────────────────────────────────
                DetailSection(emoji = "🎯", title = "Độ ưu tiên") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                    .background(
                                        if (isSelected) color else color.copy(alpha = 0.12f)
                                    )
                                    .clickable { priority = p }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color.White else color,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // ── Section 3: Hạn chót ───────────────────────────────────
                DetailSection(emoji = "📅", title = "Hạn chót") {
                    // Chọn nhanh hạn chót
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Hôm nay", 1 to "Ngày mai", 3 to "3 ngày sau",
                            daysUntilSunday() to "Cuối tuần", -1 to "Không"
                        ).forEach { (offset, label) ->
                            val newDue = if (offset < 0) "" else dueAtDayOffset(offset)
                            val isSel = dueDate == newDue
                            StatusFilterChip(
                                text = label,
                                selected = isSel,
                                onClick = {
                                    dueDate = newDue
                                    if (dueDate.isNotEmpty()) parseIso8601(dueDate)?.let { calendar.time = it }
                                }
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dueDate.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        calendar.set(Calendar.YEAR, year)
                                        calendar.set(Calendar.MONTH, month)
                                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                calendar.set(Calendar.MINUTE, minute)
                                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                                sdf.timeZone = TimeZone.getTimeZone("UTC")
                                                dueDate = sdf.format(calendar.time)
                                            },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (dueDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                       else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (dueDate.isEmpty()) "Chọn hạn chót..." else formatUtcToLocal(dueDate),
                                color = if (dueDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = if (dueDate.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            if (dueDate.isNotEmpty()) {
                                IconButton(onClick = { dueDate = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Xóa hạn",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Section 4: Danh mục & Lặp lại ────────────────────────
                DetailSection(emoji = "🏷️", title = "Danh mục & Lặp lại") {
                    Text(
                        "Danh mục",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryStore.all().forEach { c ->
                            StatusFilterChip(
                                text = categoryLabel(c),
                                selected = category == c,
                                onClick = { category = c }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var newCategory by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("Thêm danh mục mới") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors(),
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    val added = CategoryStore.add(newCategory)
                                    if (added.isNotEmpty()) category = added
                                    newCategory = ""
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Thêm", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Lặp lại",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RECURRENCE_OPTIONS.forEach { r ->
                            val isSelected = recurrence == r
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { recurrence = r }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    recurrenceLabel(r),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (recurrence != "NONE" && dueDate.isEmpty()) {
                        Text(
                            "⚠ Cần đặt hạn chót để dùng lặp lại.",
                            color = PriorityMediumColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                // ── Section 5: Các bước con (Checklist) ──────────────────
                DetailSection(emoji = "✅", title = "Các bước con") {
                    if (isNewTask) {
                        Text(
                            "Hãy lưu công việc trước, sau đó mở lại để thêm các bước con.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    } else {
                        val doneCount = subtasks.count { it.isDone }
                        if (subtasks.isNotEmpty()) {
                            val fraction = doneCount.toFloat() / subtasks.size.toFloat()
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Brush.horizontalGradient(listOf(primary, tertiary)))
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("$doneCount/${subtasks.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                            }
                        }

                        subtasks.forEach { sub ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.isDone,
                                    onCheckedChange = { taskDetailViewModel.toggleSubtask(sub) },
                                    colors = CheckboxDefaults.colors(checkedColor = primary)
                                )
                                Text(
                                    sub.title,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (sub.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                                IconButton(onClick = { taskDetailViewModel.deleteSubtask(sub) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa bước", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = subtaskInput,
                                onValueChange = { subtaskInput = it },
                                label = { Text("Thêm bước con") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors(),
                                singleLine = true
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        taskDetailViewModel.addSubtask(taskId, subtaskInput)
                                        subtaskInput = ""
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Thêm", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─── Reusable section wrapper ─────────────────────────────────────────────────

@Composable
private fun DetailSection(
    emoji: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
        ) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
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
