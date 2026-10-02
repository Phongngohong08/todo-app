package com.example.todoapplication.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/*
 * [TẦNG UI · COMPONENT] Kéo-thả giữa các vùng (ngày trên lịch tuần, ô của ma trận Eisenhower).
 *
 * Cách dùng:
 *   DragDropContainer(state) {                      // bọc toàn màn, vẽ "bóng" thẻ đang kéo đè lên trên
 *       DropTarget(state, key = ngày) { ... }       // vùng thả, ghi lại vị trí của nó trên màn hình
 *           Text(Modifier.dragSource(state, payload) { keyThảVào -> ... })  // nhấn giữ để kéo
 *   }
 * Mọi tọa độ tính theo gốc màn hình (root), nên vùng thả và thẻ kéo nằm ở cây layout nào cũng so được.
 */

/** Thứ đang được kéo: id + chữ hiển thị trên "bóng". */
data class DragPayload(val id: String, val label: String)

@Stable
class DragDropState<K : Any> {
    var dragging by mutableStateOf<DragPayload?>(null)
        private set
    /** Vị trí ngón tay, theo gốc màn hình. */
    var pointer by mutableStateOf(Offset.Zero)
        internal set
    internal var containerOrigin by mutableStateOf(Offset.Zero)
    private val targets = mutableStateMapOf<K, Rect>()

    /** Vùng thả đang nằm dưới ngón tay (để tô sáng). */
    val hovered: K?
        get() = if (dragging == null) null else targetAt(pointer)

    fun targetAt(point: Offset): K? = targets.entries.firstOrNull { it.value.contains(point) }?.key

    internal fun register(key: K, bounds: Rect) { targets[key] = bounds }
    internal fun unregister(key: K) { targets.remove(key) }

    internal fun start(payload: DragPayload, at: Offset) {
        dragging = payload
        pointer = at
    }

    internal fun end() { dragging = null }
}

@Composable
fun <K : Any> rememberDragDropState(): DragDropState<K> = remember { DragDropState() }

/** Bọc vùng có kéo-thả; vẽ thẻ "bóng" đi theo ngón tay. */
@Composable
fun <K : Any> DragDropContainer(
    state: DragDropState<K>,
    modifier: Modifier = Modifier,
    ghost: @Composable (DragPayload) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.onGloballyPositioned { state.containerOrigin = it.positionInRoot() }) {
        content()
        state.dragging?.let { payload ->
            Box(
                Modifier
                    // Lệch lên-trái một chút để bóng không bị ngón tay che hết
                    .offset {
                        val p = state.pointer - state.containerOrigin
                        IntOffset(p.x.roundToInt() - GHOST_SHIFT_PX, p.y.roundToInt() - GHOST_SHIFT_PX)
                    }
                    .graphicsLayer { alpha = 0.9f; scaleX = 1.05f; scaleY = 1.05f }
            ) { ghost(payload) }
        }
    }
}

private const val GHOST_SHIFT_PX = 24

/** Vùng thả có khóa [key]. Tự gỡ khỏi danh sách khi rời màn hình (đổi tuần, đổi chế độ xem). */
@Composable
fun <K : Any> DropTarget(
    state: DragDropState<K>,
    key: K,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isHovered: Boolean) -> Unit
) {
    DisposableEffect(key) { onDispose { state.unregister(key) } }
    Box(modifier.onGloballyPositioned { state.register(key, it.boundsInRoot()) }) {
        content(state.hovered == key)
    }
}

/**
 * Nhấn giữ để bắt đầu kéo [payload]; thả trên một [DropTarget] → [onDrop] nhận khóa của vùng đó.
 * Trước khi nhấn giữ đủ lâu, cử chỉ cuộn bình thường vẫn hoạt động.
 */
fun <K : Any> Modifier.dragSource(
    state: DragDropState<K>,
    payload: DragPayload,
    onDrop: (K) -> Unit
): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val currentOnDrop by rememberUpdatedState(onDrop)
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(payload.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(payload, origin + local)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.pointer += amount
                },
                onDragEnd = {
                    state.targetAt(state.pointer)?.let(currentOnDrop)
                    state.end()
                },
                onDragCancel = { state.end() }
            )
        }
}
