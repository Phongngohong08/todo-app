package com.example.todoapplication.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * [TẦNG UI · HIỆU ỨNG] Shared element transition: bấm một thẻ việc → thẻ "nở" thành màn chi tiết (container
 * transform), quay lại thì thu về đúng chỗ. MainActivity bọc NavHost bằng SharedTransitionLayout; mỗi màn tham gia
 * nhận AnimatedVisibilityScope của chính đích điều hướng qua [WithNavScope]. Không có scope (vd khung chi tiết
 * bên phải trên tablet) thì modifier không làm gì.
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Gắn AnimatedVisibilityScope của một đích NavHost cho nội dung bên trong. */
@Composable
fun WithNavScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides scope, content = content)
}

/** Thẻ việc ↔ màn chi tiết của cùng việc [taskId] dùng chung "khung". */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTaskBounds(taskId: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedTaskBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "task-$taskId"),
            animatedVisibilityScope = animated,
            resizeMode = RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
        )
    }
}
