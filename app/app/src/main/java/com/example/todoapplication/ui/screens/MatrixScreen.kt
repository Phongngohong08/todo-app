package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.domain.Eisenhower
import com.example.todoapplication.domain.isOverdue
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.ui.components.DragDropContainer
import com.example.todoapplication.ui.components.DragPayload
import com.example.todoapplication.ui.components.DropTarget
import com.example.todoapplication.ui.components.dragSource
import com.example.todoapplication.ui.components.rememberDragDropState
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.theme.*
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.viewmodel.MatrixEvent
import com.example.todoapplication.ui.viewmodel.MatrixViewModel

/**
 * [TẦNG UI · MÀN HÌNH] Ma trận Eisenhower: 4 ô Quan trọng × Gấp. Nhấn giữ một việc rồi kéo sang ô khác —
 * app tự đổi ưu tiên / hạn cho khớp ô mới (có Hoàn tác).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    navController: NavController,
    matrixViewModel: MatrixViewModel = viewModel(factory = MatrixViewModel.Factory)
) {
    val state by matrixViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dragState = rememberDragDropState<Eisenhower.Quadrant>()

    LaunchedEffect(Unit) {
        matrixViewModel.events.collect { event ->
            when (event) {
                is MatrixEvent.Moved -> {
                    val result = snackbarHostState.showSnackbar(
                        "Đã chuyển sang \"${event.target.title}\"", actionLabel = "Hoàn tác", duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) matrixViewModel.undo(event.task)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ma trận ưu tiên", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        DragDropContainer(
            state = dragState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            ghost = { payload -> DragGhost(payload.label) }
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Text(
                    "Nhấn giữ một việc rồi kéo sang ô khác. Quan trọng = ưu tiên Cao · Gấp = hạn tới hết ngày mai.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                val rows = listOf(
                    Eisenhower.Quadrant.DO_FIRST to Eisenhower.Quadrant.SCHEDULE,
                    Eisenhower.Quadrant.QUICK to Eisenhower.Quadrant.LATER
                )
                rows.forEach { (left, right) ->
                    Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(left, right).forEach { quadrant ->
                            QuadrantCell(
                                quadrant = quadrant,
                                tasks = state.quadrants[quadrant].orEmpty(),
                                dragState = dragState,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onOpen = { navController.navigate(Screen.TaskDetail.createRoute(it.id)) },
                                onComplete = matrixViewModel::complete,
                                onDrop = { task, target -> if (target != quadrant) matrixViewModel.move(task.id, target) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun quadrantColor(q: Eisenhower.Quadrant): Color = when (q) {
    Eisenhower.Quadrant.DO_FIRST -> PriorityHighColor
    Eisenhower.Quadrant.SCHEDULE -> Color(0xFF4C6EF5)
    Eisenhower.Quadrant.QUICK -> PriorityMediumColor
    Eisenhower.Quadrant.LATER -> PriorityLowColor
}

@Composable
private fun QuadrantCell(
    quadrant: Eisenhower.Quadrant,
    tasks: List<Task>,
    dragState: com.example.todoapplication.ui.components.DragDropState<Eisenhower.Quadrant>,
    modifier: Modifier,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onDrop: (Task, Eisenhower.Quadrant) -> Unit
) {
    val color = quadrantColor(quadrant)
    DropTarget(dragState, quadrant, modifier) { hovered ->
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = if (hovered) 0.22f else 0.08f))
                .border(if (hovered) 2.dp else 1.dp, color.copy(alpha = if (hovered) 0.9f else 0.3f), RoundedCornerShape(18.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quadrant.title, fontWeight = FontWeight.Bold, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("${tasks.size}", fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
            }
            Text(quadrant.hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Trống", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tasks.forEach { task ->
                        key(task.id) {
                            MatrixTaskRow(
                                task = task,
                                color = color,
                                modifier = Modifier.dragSource(dragState, DragPayload(task.id, task.title)) { target -> onDrop(task, target) },
                                onOpen = { onOpen(task) },
                                onComplete = { onComplete(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixTaskRow(task: Task, color: Color, modifier: Modifier, onOpen: () -> Unit, onComplete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, color, CircleShape)
                    .clickable(onClick = onComplete)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                task.dueAt?.let {
                    Text(
                        formatDueLabel(it, task.dueAllDay),
                        fontSize = 10.sp,
                        color = if (task.isOverdue()) StateOverdue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Thẻ "bóng" đi theo ngón tay khi kéo (dùng chung cho Ma trận và Lịch tuần). */
@Composable
fun DragGhost(label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 220.dp)
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
