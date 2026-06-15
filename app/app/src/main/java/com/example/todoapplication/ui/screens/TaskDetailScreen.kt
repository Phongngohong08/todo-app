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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.notifications.ReminderScheduler
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatUtcToLocal
import com.example.todoapplication.ui.utils.parseIso8601
import com.example.todoapplication.ui.utils.priorityLabel
import com.example.todoapplication.ui.utils.recurrenceLabel
import com.example.todoapplication.ui.utils.RECURRENCE_OPTIONS
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(navController: NavController, taskId: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    val isNewTask = taskId == "new"

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var duration by remember { mutableStateOf("30") }
    var dueDate by remember { mutableStateOf("") }
    var preferredTimeStart by remember { mutableStateOf("") }
    var preferredTimeEnd by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagInput by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("NONE") }
    var isLoading by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }

    LaunchedEffect(taskId) {
        if (!isNewTask) {
            isLoading = true
            try {
                val response = apiService.getTask(taskId)
                if (response.isSuccessful && response.body() != null) {
                    val task = response.body()!!
                    title = task.title
                    description = task.description ?: ""
                    priority = task.priority
                    duration = task.estimatedDuration.toString()
                    dueDate = task.dueDate ?: ""
                    preferredTimeStart = task.preferredTimeStart ?: ""
                    preferredTimeEnd = task.preferredTimeEnd ?: ""
                    tags = task.tags
                    recurrence = task.recurrence
                    if (dueDate.isNotEmpty()) parseIso8601(dueDate)?.let { calendar.time = it }
                } else {
                    Toast.makeText(context, "Không thể tải chi tiết công việc", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        } else {
            QuickAddDraft.consume()?.let { draft ->
                title = draft.title
                description = draft.description
                priority = draft.priority.ifBlank { "MEDIUM" }
                if (draft.estimatedDuration > 0) duration = draft.estimatedDuration.toString()
                dueDate = draft.dueDate ?: ""
                tags = draft.tags
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
                            val estimatedMinutes = duration.toIntOrNull() ?: 30
                            val dateString = if (dueDate.isEmpty()) null else dueDate
                            val timeStart = if (preferredTimeStart.isEmpty()) null else preferredTimeStart
                            val timeEnd = if (preferredTimeEnd.isEmpty()) null else preferredTimeEnd
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val savedTask = if (isNewTask) {
                                        val r = apiService.createTask(
                                            CreateTaskInput(title, description, priority, dateString, estimatedMinutes, timeStart, timeEnd, tags, recurrence)
                                        )
                                        if (r.isSuccessful) r.body() else null
                                    } else {
                                        val r = apiService.updateTask(
                                            taskId,
                                            UpdateTaskInput(title, description, priority, dateString, estimatedMinutes, timeStart, timeEnd, tags, recurrence)
                                        )
                                        if (r.isSuccessful) r.body() else null
                                    }
                                    if (savedTask != null) {
                                        ReminderScheduler.schedule(context, savedTask)
                                        Toast.makeText(context, "Đã lưu công việc thành công!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } else {
                                        Toast.makeText(context, "Không thể lưu công việc", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
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

                // ── Section 2: Ưu tiên & Thời lượng ──────────────────────
                DetailSection(emoji = "🎯", title = "Ưu tiên & Thời lượng") {
                    Text(
                        "Độ ưu tiên",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Thời lượng ước tính",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60, 90).forEach { preset ->
                            val isSelected = duration == preset.toString()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { duration = preset.toString() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${preset}p",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { input -> duration = input.filter { it.isDigit() } },
                        label = { Text("Tùy chỉnh (phút)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        singleLine = true
                    )
                }

                // ── Section 3: Thời gian ──────────────────────────────────
                DetailSection(emoji = "📅", title = "Thời gian") {
                    Text(
                        "Hạn chót",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Khung giờ ưu tiên (tùy chọn)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimePickerButton(
                            label = if (preferredTimeStart.isEmpty()) "Từ giờ..." else "Từ $preferredTimeStart",
                            modifier = Modifier.weight(1f)
                        ) {
                            val h = preferredTimeStart.split(":").getOrNull(0)?.toIntOrNull() ?: 8
                            val m = preferredTimeStart.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(context, { _, hr, min ->
                                preferredTimeStart = String.format("%02d:%02d", hr, min)
                            }, h, m, true).show()
                        }
                        TimePickerButton(
                            label = if (preferredTimeEnd.isEmpty()) "Đến giờ..." else "Đến $preferredTimeEnd",
                            modifier = Modifier.weight(1f)
                        ) {
                            val h = preferredTimeEnd.split(":").getOrNull(0)?.toIntOrNull() ?: 18
                            val m = preferredTimeEnd.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(context, { _, hr, min ->
                                preferredTimeEnd = String.format("%02d:%02d", hr, min)
                            }, h, m, true).show()
                        }
                        if (preferredTimeStart.isNotEmpty() || preferredTimeEnd.isNotEmpty()) {
                            IconButton(onClick = { preferredTimeStart = ""; preferredTimeEnd = "" }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa khung giờ", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── Section 4: Nhãn & Lặp lại ────────────────────────────
                DetailSection(emoji = "🏷️", title = "Nhãn & Lặp lại") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            label = { Text("Thêm nhãn") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors(),
                            singleLine = true
                        )
                        val addTag = {
                            val t = tagInput.trim().lowercase()
                            if (t.isNotEmpty() && !tags.contains(t)) tags = tags + t
                            tagInput = ""
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { addTag() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Thêm", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { tags = tags - tag },
                                    label = { Text("#$tag", fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Xóa nhãn",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.primary,
                                        trailingIconColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
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
private fun TimePickerButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
        )
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
