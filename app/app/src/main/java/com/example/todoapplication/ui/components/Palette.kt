package com.example.todoapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bảng màu cho danh sách / thói quen (lưu dạng "#RRGGBB" để đồng bộ). */
val PALETTE = listOf("#4C6EF5", "#FF6B6B", "#FFA94D", "#FCC419", "#51CF66", "#20C997", "#22B8CF", "#845EF7", "#F06595", "#868E96")

/** Emoji gợi ý cho danh sách / thói quen. */
val LIST_EMOJIS = listOf("📥", "🏠", "💼", "📚", "🛒", "💪", "💰", "✈️", "🎯", "🧠", "❤️", "🎨")
val HABIT_EMOJIS = listOf("💧", "🏃", "📖", "🧘", "😴", "🥗", "💊", "✍️", "🎸", "🧹", "🌅", "📵")

/** "#RRGGBB" → Color; sai định dạng / rỗng → [fallback]. */
fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    return clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
}

@Composable
fun ColorPickerRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PALETTE.forEach { hex ->
            val color = parseHexColor(hex, Color.Gray)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (hex.equals(selected, true)) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
fun EmojiPickerRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { e ->
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (e == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(if (e == selected) "" else e) },
                contentAlignment = Alignment.Center
            ) { Text(e, fontSize = 18.sp) }
        }
    }
}
