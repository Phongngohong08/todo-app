package com.example.todoapplication.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.todoapplication.R

/**
 * Logo TaskFlow AI — giống hệt icon app. Compose không vẽ trực tiếp adaptive icon (mipmap XML), nên ghép lại
 * hai lớp: khung bo góc cùng gradient với ic_launcher_background + vector ic_launcher_foreground.
 * Lớp foreground rộng 108 đơn vị nhưng phần hiển thị của icon chỉ là 72 ở giữa → phóng 1.5 lần rồi cắt theo khung.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF8C7DF8), Color(0xFF6C5CE7), Color(0xFF4A3BCC))))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "TaskFlow AI",
            modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}
