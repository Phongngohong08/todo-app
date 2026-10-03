package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.ListInfo
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.ui.components.ColorPickerRow
import com.example.todoapplication.ui.components.DragDropContainer
import com.example.todoapplication.ui.components.DragDropState
import com.example.todoapplication.ui.components.DragPayload
import com.example.todoapplication.ui.components.DropTarget
import com.example.todoapplication.ui.components.EmojiPickerRow
import com.example.todoapplication.ui.components.LIST_EMOJIS
import com.example.todoapplication.ui.components.dragSource
import com.example.todoapplication.ui.components.parseHexColor
import com.example.todoapplication.ui.components.rememberDragDropState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.StateOverdue
import com.example.todoapplication.ui.utils.categoryLabel
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.viewmodel.ListBoardEvent
import com.example.todoapplication.ui.viewmodel.ListBoardViewModel
import com.example.todoapplication.ui.viewmodel.ListsViewModel

/** Nhãn hiển thị của danh sách kèm emoji (nếu có). */
fun listTitle(info: ListInfo): String = (if (info.emoji.isNotBlank()) info.emoji + " " else "") + categoryLabel(info.name)

/** [TẦNG UI · MÀN HÌNH] Các danh sách: số việc đang chờ / quá hạn; bấm để mở dạng Danh sách hoặc Kanban. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    navController: NavController,
    viewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListInfo?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh sách", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Thêm danh sách", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.lists, key = { it.info.name }) { item ->
                val info = item.info
                val color = parseHexColor(info.color, MaterialTheme.colorScheme.primary)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.ListBoard.createRoute(info.name)) }
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (info.emoji.isNotBlank()) Text(info.emoji, fontSize = 18.sp)
                            else Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(categoryLabel(info.name), fontWeight = FontWeight.SemiBold)
                            val parts = buildList {
                                add("${item.pending} việc")
                                if (item.overdue > 0) add("${item.overdue} quá hạn")
                                if (info.sections.isNotEmpty()) add("${info.sections.size} mục")
                            }
                            Text(parts.joinToString(" · "), fontSize = 12.sp, color = if (item.overdue > 0) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = info }) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa danh sách", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (info.name !in CategoryRepository.DEFAULTS) {
                            IconButton(onClick = { confirmDelete = info.name }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa danh sách", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ListAppearanceDialog(
            initial = ListInfo(""),
            nameEditable = true,
            onDismiss = { creating = false },
            onSave = { info -> viewModel.create(info.name, info.color, info.emoji); creating = false }
        )
    }
    editing?.let { info ->
        ListAppearanceDialog(
            initial = info,
            nameEditable = false,
            onDismiss = { editing = null },
            onSave = { viewModel.saveAppearance(it); editing = null }
        )
    }
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xóa danh sách \"$name\"?") },
            text = { Text("Các việc trong danh sách vẫn được giữ, chỉ bỏ danh sách khỏi bộ lọc.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(name); confirmDelete = null }) { Text("Xóa", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Hủy") } }
        )
    }
}

/** Tạo danh sách / đổi màu + emoji. */
@Composable
private fun ListAppearanceDialog(initial: ListInfo, nameEditable: Boolean, onDismiss: () -> Unit, onSave: (ListInfo) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nameEditable) "Danh sách mới" else listTitle(initial)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nameEditable) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(CategoryRepository.MAX_LENGTH) },
                        label = { Text("Tên danh sách") },
                        singleLine = true
                    )
                }
                Text("Biểu tượng", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                EmojiPickerRow(LIST_EMOJIS, emoji) { emoji = it }
                Text("Màu", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                ColorPickerRow(color) { color = if (it == color) "" else it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), color = color, emoji = emoji)) }) {
                Text("Lưu")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ─── Một danh sách: Kanban / Danh sách ───────────────────────────────────────

private const val UNSECTIONED_LABEL = "Chưa phân mục"

/**
 * [TẦNG UI · MÀN HÌNH] Một danh sách. Chế độ **Kanban**: mỗi mục là một cột, nhấn giữ thẻ rồi kéo sang cột khác;
 * cuối mỗi cột có ô thêm nhanh việc. Chế độ **Danh sách**: các mục xếp dọc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListBoardScreen(
    navController: NavController,
    name: String,
    viewModel: ListBoardViewModel = viewModel(factory = ListBoardViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var kanban by rememberSaveable { mutableStateOf(true) }
    var addingSection by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var editingLook by remember { mutableStateOf(false) }
    val openTask: (Task) -> Unit = { navController.navigate(Screen.TaskDetail.createRoute(it.id)) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is ListBoardEvent.Moved -> {
                    val r = snackbar.showSnackbar(
                        "Đã chuyển sang \"${e.section.ifEmpty { UNSECTIONED_LABEL }}\"", "Hoàn tác", duration = SnackbarDuration.Short
                    )
                    if (r == SnackbarResult.ActionPerformed) viewModel.undoMove(e.task)
                }
                is ListBoardEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(listTitle(state.info.copy(name = name)), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { kanban = !kanban }) {
                        Icon(
                            if (kanban) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewColumn,
                            contentDescription = if (kanban) "Xem dạng danh sách" else "Xem dạng Kanban"
                        )
                    }
                    IconButton(onClick = { addingSection = true }) { Icon(Icons.Default.PlaylistAdd, contentDescription = "Thêm mục") }
                    IconButton(onClick = { editingLook = true }) { Icon(Icons.Default.Palette, contentDescription = "Màu & biểu tượng") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (kanban) {
                KanbanBoard(
                    columns = state.columns,
                    accent = parseHexColor(state.info.color, MaterialTheme.colorScheme.primary),
                    sections = state.info.sections,
                    onMove = viewModel::moveTo,
                    onOpen = openTask,
                    onComplete = viewModel::complete,
                    onAdd = viewModel::addTask,
                    onRename = { renaming = it },
                    onDeleteSection = viewModel::deleteSection,
                    onShift = viewModel::shiftSection,
                    onAddSection = { addingSection = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.columns.forEach { col ->
                        if (col.section.isNotEmpty() || col.tasks.isNotEmpty()) {
                            item(key = "h-" + col.section) {
                                SectionHeader(col.section.ifEmpty { UNSECTIONED_LABEL }, col.tasks.size)
                            }
                            items(col.tasks, key = { it.id }) { task ->
                                BoardCard(task, onOpen = { openTask(task) }, onComplete = { viewModel.complete(task) })
                            }
                        }
                    }
                    if (state.completedCount > 0) {
                        item(key = "done") {
                            Text("✓ ${state.completedCount} việc đã xong", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (addingSection) {
        TextPromptDialog("Thêm mục", "VD: Cần làm, Đang làm, Chờ phản hồi", "", onDismiss = { addingSection = false }) {
            viewModel.addSection(it); addingSection = false
        }
    }
    renaming?.let { section ->
        TextPromptDialog("Đổi tên mục", "", section, onDismiss = { renaming = null }) {
            viewModel.renameSection(section, it); renaming = null
        }
    }
    if (editingLook) {
        ListAppearanceDialog(
            initial = state.info,
            nameEditable = false,
            onDismiss = { editingLook = false },
            onSave = { viewModel.saveAppearance(it.color, it.emoji); editingLook = false }
        )
    }
}

@Composable
private fun KanbanBoard(
    columns: List<com.example.todoapplication.ui.viewmodel.BoardColumn>,
    accent: Color,
    sections: List<String>,
    onMove: (String, String) -> Unit,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onAdd: (String, String) -> Unit,
    onRename: (String) -> Unit,
    onDeleteSection: (String) -> Unit,
    onShift: (String, Int) -> Unit,
    onAddSection: () -> Unit
) {
    val dragState = rememberDragDropState<String>()
    DragDropContainer(state = dragState, modifier = Modifier.fillMaxSize(), ghost = { DragGhost(it.label) }) {
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            columns.forEach { col ->
                key(col.section) {
                    KanbanColumn(
                        column = col,
                        accent = accent,
                        dragState = dragState,
                        index = sections.indexOf(col.section),
                        lastIndex = sections.lastIndex,
                        onMove = onMove,
                        onOpen = onOpen,
                        onComplete = onComplete,
                        onAdd = { onAdd(it, col.section) },
                        onRename = { onRename(col.section) },
                        onDelete = { onDeleteSection(col.section) },
                        onShift = { onShift(col.section, it) }
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.width(200.dp).height(56.dp).clickable(onClick = onAddSection)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+ Thêm mục", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun KanbanColumn(
    column: com.example.todoapplication.ui.viewmodel.BoardColumn,
    accent: Color,
    dragState: DragDropState<String>,
    index: Int,
    lastIndex: Int,
    onMove: (String, String) -> Unit,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onAdd: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShift: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    DropTarget(dragState, column.section, Modifier.width(280.dp).fillMaxHeight()) { hovered ->
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(if (hovered) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    column.section.ifEmpty { UNSECTIONED_LABEL },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${column.tasks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (column.section.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn mục", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { menu = false; onRename() })
                            if (index > 0) DropdownMenuItem(text = { Text("Chuyển sang trái") }, onClick = { menu = false; onShift(-1) })
                            if (index in 0 until lastIndex) DropdownMenuItem(text = { Text("Chuyển sang phải") }, onClick = { menu = false; onShift(1) })
                            DropdownMenuItem(
                                text = { Text("Xóa mục (giữ việc)", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                column.tasks.forEach { task ->
                    key(task.id) {
                        BoardCard(
                            task = task,
                            modifier = Modifier.dragSource(dragState, DragPayload(task.id, task.title)) { target -> onMove(task.id, target) },
                            onOpen = { onOpen(task) },
                            onComplete = { onComplete(task) }
                        )
                    }
                }
                if (column.tasks.isEmpty()) {
                    Text(
                        "Kéo việc vào đây",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("+ Thêm việc", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd(input); input = "" }),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

/** Thẻ việc gọn trên bảng Kanban. */
@Composable
private fun BoardCard(task: Task, modifier: Modifier = Modifier, onOpen: () -> Unit, onComplete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(priorityColor(task.priority).copy(alpha = 0.15f))
                    .clickable(onClick = onComplete)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = buildList {
                    task.dueAt?.let { add(formatDueLabel(it, task.dueAllDay)) }
                    if (task.subtaskTotal > 0) add("☑ ${task.subtaskDone}/${task.subtaskTotal}")
                    task.tags.take(3).forEach { add("@$it") }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        fontSize = 11.sp,
                        color = if (task.isOverdue()) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Hộp nhập một dòng chữ (tên mục, tên bộ lọc...). */
@Composable
fun TextPromptDialog(title: String, hint: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(50) },
                placeholder = { if (hint.isNotEmpty()) Text(hint) },
                singleLine = true
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
