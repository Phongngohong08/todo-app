package com.example.todoapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.todoapplication.ui.theme.PriorityHighColor
import com.example.todoapplication.ui.theme.PriorityLowColor
import com.example.todoapplication.ui.theme.PriorityMediumColor
import com.example.todoapplication.ui.theme.StateCancelled
import com.example.todoapplication.ui.theme.StateOverdue
import com.example.todoapplication.ui.utils.priorityLabel

/** Pill nhỏ dạng nhãn màu (nền tint + viền + chữ cùng tông). */
@Composable
private fun TintPill(text: String, color: Color) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PriorityPill(priority: String) {
    val color = when (priority) {
        "HIGH" -> PriorityHighColor
        "MEDIUM" -> PriorityMediumColor
        else -> PriorityLowColor
    }
    TintPill(priorityLabel(priority), color)
}

@Composable
fun OverduePill() = TintPill("QUÁ HẠN", StateOverdue)

@Composable
fun CancelledPill() = TintPill("ĐÃ HỦY", StateCancelled)

/** Chip nhãn (#tag) dùng màu primary. */
@Composable
fun TagChip(text: String) {
    val color = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = "#$text",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
