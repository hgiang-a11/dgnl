package com.dgnl.taskflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TaskFlowColors = darkColorScheme(
    primary = AccentColors[0],
    onPrimary = Color(0xFF04070F),
    primaryContainer = Surface3,
    onPrimaryContainer = TextHi,
    secondary = AccentColors[1],
    onSecondary = Color.White,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextHi,
    tertiary = Good,
    onTertiary = Color(0xFF04120C),
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceTint = Color.Transparent,
    inverseSurface = TextHi,
    inverseOnSurface = Ink,
    outline = Line,
    outlineVariant = LineSoft,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFF3A1220),
    onErrorContainer = Danger,
    scrim = Color(0xCC000000)
)

private val TaskFlowTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

@Composable
fun TaskFlowTheme(content: @Composable () -> Unit) {
    // App co dinh mot giao dien toi, khong phu thuoc cai dat he thong.
    MaterialTheme(
        colorScheme = TaskFlowColors,
        typography = TaskFlowTypography,
        content = content
    )
}
