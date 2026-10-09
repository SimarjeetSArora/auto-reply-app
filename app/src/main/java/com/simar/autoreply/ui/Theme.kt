package com.simar.autoreply.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Accent colours a mode can pick; [com.simar.autoreply.data.Mode.color] indexes into this. */
val ModePalette = listOf(
    Color(0xFF3B82F6), // blue
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFF97316), // orange
    Color(0xFFF59E0B), // amber
    Color(0xFF14B8A6), // teal
    Color(0xFF22C55E), // green
)

fun modeColor(index: Int) = ModePalette[index.mod(ModePalette.size)]

/** A slightly deeper shade, for gradients. */
fun Color.deeper() = lerp(this, Color.Black, 0.28f)

val Success = Color(0xFF16A34A)
val Warn = Color(0xFFD97706)
val Rose = Color(0xFFF43F5E)

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0EA5E9),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECEEF5),
    surfaceContainerHighest = Color(0xFFE4E7F0),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF0E1016),
    surfaceContainerLow = Color(0xFF12141B),
    surfaceContainer = Color(0xFF161922),
    surfaceContainerHigh = Color(0xFF1F2330),
    surfaceContainerHighest = Color(0xFF282C3A),
    outlineVariant = Color(0xFF262A36),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFECACA),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 1.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun AutoReplyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
