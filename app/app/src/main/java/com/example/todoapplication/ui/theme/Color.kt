package com.example.todoapplication.ui.theme

import androidx.compose.ui.graphics.Color

/* ----------------------------------------------------------------------------
 * Bảng màu Pastel — tối giản, trẻ trung. Dùng qua Material3 colorScheme (Theme.kt).
 * -------------------------------------------------------------------------- */

// LIGHT
val LightPrimary = Color(0xFF6C5CE7)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE7E3FF)
val LightOnPrimaryContainer = Color(0xFF221A5E)
val LightSecondary = Color(0xFFFF8A8A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightTertiary = Color(0xFF2FB89B)
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F2F8)
val LightOnBackground = Color(0xFF1B1B2A)
val LightOnSurfaceVariant = Color(0xFF6B7280)
val LightOutline = Color(0xFFE3E5EE)
val LightOutlineVariant = Color(0xFFEEF0F6)
val LightError = Color(0xFFFF5A5A)

// DARK
val DarkPrimary = Color(0xFFA99BFF)
val DarkOnPrimary = Color(0xFF20193F)
val DarkPrimaryContainer = Color(0xFF3A2F73)
val DarkOnPrimaryContainer = Color(0xFFE7E3FF)
val DarkSecondary = Color(0xFFFF9D9D)
val DarkOnSecondary = Color(0xFF3A1A1A)
val DarkTertiary = Color(0xFF5BD9BE)
val DarkBackground = Color(0xFF131320)
val DarkSurface = Color(0xFF1C1C2A)
val DarkSurfaceVariant = Color(0xFF262635)
val DarkOnBackground = Color(0xFFECECF5)
val DarkOnSurfaceVariant = Color(0xFFA0A3B1)
val DarkOutline = Color(0xFF33334A)
val DarkOutlineVariant = Color(0xFF262635)
val DarkError = Color(0xFFFF7B7B)

/* ----------------------------------------------------------------------------
 * Màu accent / priority / state — dùng chung cho cả hai chế độ (mid-tone, nền tint alpha).
 * Đặt là hằng số top-level để dùng được cả trong và ngoài @Composable.
 * -------------------------------------------------------------------------- */

val PriorityHighColor = Color(0xFFFF6B6B)
val PriorityMediumColor = Color(0xFFFFA94D)
val PriorityLowColor = Color(0xFF51CF66)

val StateCompleted = Color(0xFF2FB89B)
val StateInProgress = Color(0xFF7C6CF0)
val StateTodo = Color(0xFF9AA0AE)
val StatePostponed = Color(0xFFFFA94D)
val StateOverdue = Color(0xFFFF6B6B)
val StateCancelled = Color(0xFF9AA0AE)
