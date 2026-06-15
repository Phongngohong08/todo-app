package com.example.todoapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Font family của app. Hiện dùng FontFamily.Default (an toàn, không phụ thuộc mạng/Play Services).
 * Muốn đổi sang Poppins: thả các file poppins_*.ttf vào res/font rồi đổi dòng dưới thành:
 *   val AppFontFamily = FontFamily(
 *     Font(R.font.poppins_regular, FontWeight.Normal),
 *     Font(R.font.poppins_medium, FontWeight.Medium),
 *     Font(R.font.poppins_semibold, FontWeight.SemiBold),
 *     Font(R.font.poppins_bold, FontWeight.Bold),
 *   )
 */
val AppFontFamily = FontFamily.Default

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp
    )
)
