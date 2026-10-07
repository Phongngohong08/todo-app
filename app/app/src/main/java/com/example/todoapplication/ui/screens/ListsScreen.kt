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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.list_add), tint = MaterialTheme.colorScheme.onPrimary)
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
                                add(pluralStringResource(R.plurals.count_tasks, item.pending, item.pending))
                                if (item.overdue > 0) add(stringResource(R.string.list_overdue_count, item.overdue))
                                if (info.sections.isNotEmpty()) add(pluralStringResource(R.plurals.list_section_count, info.sections.size, info.sections.size))
                            }
                            Text(parts.joinToString(" · "), fontSize = 12.sp, color = if (item.overdue > 0) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = info }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.list_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (info.name !in CategoryRepository.DEFAULTS) {
                            IconButton(onClick = { confirmDelete = info.name }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.list_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            nameEditable = info.name !in CategoryRepository.DEFAULTS,
            onDismiss = { editing = null },
            onSave = { viewModel.saveList(info, it); editing = null }
        )
    }
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.list_delete_title, categoryLabel(name))) },
            text = { Text(stringResource(R.string.list_delete_text)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(name); confirmDelete = null }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
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
        title = { Text(if (initial.name.isEmpty()) stringResource(R.string.list_new) else listTitle(initial)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nameEditable) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(CategoryRepository.MAX_LENGTH) },
                        label = { Text(stringResource(R.string.list_name)) },
                        singleLine = true
                    )
                }
                Text(stringResource(R.string.list_icon), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                EmojiPickerRow(LIST_EMOJIS, emoji) { emoji = it }
                Text(stringResource(R.string.list_color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                ColorPickerRow(color) { color = if (it == color) "" else it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), color = color, emoji = emoji)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// ─── Một danh sách: Kanban / Danh sách ───────────────────────────────────────

private fun unsectionedLabel(): String = str(R.string.list_unsectioned)

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
                        str(R.string.matrix_moved, e.section.ifEmpty { unsectionedLabel() }), str(R.string.action_undo), duration = SnackbarDuration.Short
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { kanban = !kanban }) {
                        Icon(
                            if (kanban) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewColumn,
                            contentDescription = stringResource(if (kanban) R.string.list_view_list else R.string.list_view_kanban)
                        )
                    }
                    IconButton(onClick = { addingSection = true }) { Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.list_add_section)) }
                    IconButton(onClick = { editingLook = true }) { Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.list_appearance)) }
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
                                SectionHeader(col.section.ifEmpty { unsectionedLabel() }, col.tasks.size)
                            }
                            items(col.tasks, key = { it.id }) { task ->
                                BoardCard(task, onOpen = { openTask(task) }, onComplete = { viewModel.complete(task) })
                            }
                        }
                    }
                    if (state.completedCount > 0) {
                        item(key = "done") {
                            Text(pluralStringResource(R.plurals.list_completed_count, state.completedCount, state.completedCount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (addingSection) {
        TextPromptDialog(stringResource(R.string.list_add_section), stringResource(R.string.list_section_hint), "", onDismiss = { addingSection = false }) {
            viewModel.addSection(it); addingSection = false
        }
    }
    renaming?.let { section ->
        TextPromptDialog(stringResource(R.string.list_rename_section), "", section, onDismiss = { renaming = null }) {
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
                        allSections = listOf("") + sections,
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
                    Text(stringResource(R.string.list_add_section_plus), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
    allSections: List<String>,
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
                    column.section.ifEmpty { unsectionedLabel() },
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
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.list_section_options), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                            if (index > 0) DropdownMenuItem(text = { Text(stringResource(R.string.list_move_left)) }, onClick = { menu = false; onShift(-1) })
                            if (index in 0 until lastIndex) DropdownMenuItem(text = { Text(stringResource(R.string.list_move_right)) }, onClick = { menu = false; onShift(1) })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_delete_section), color = MaterialTheme.colorScheme.error) },
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
                            onComplete = { onComplete(task) },
                            moveTargets = allSections.filter { it != column.section },
                            onMove = { target -> onMove(task.id, target) }
                        )
                    }
                }
                if (column.tasks.isEmpty()) {
                    Text(
                        stringResource(R.string.list_drop_here),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.list_add_task_plus), fontSize = 13.sp) },
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
private fun BoardCard(
    task: Task,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    /** Trợ năng: các cột có thể chuyển tới — thay cho kéo thả (TalkBack không kéo được). */
    moveTargets: List<String> = emptyList(),
    onMove: (String) -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(str(R.string.a11y_complete)) { onComplete(); true }) +
                    moveTargets.map { target ->
                        CustomAccessibilityAction(str(R.string.a11y_move_to, target.ifEmpty { unsectionedLabel() })) { onMove(target); true }
                    }
            }
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
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
