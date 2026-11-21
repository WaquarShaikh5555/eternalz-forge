package dev.eternalz.forge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Color(0xFFB2EC8C), onPrimary = Color(0xFF173505), primaryContainer = Color(0xFF273D20),
    secondary = Color(0xFFC7CBBF), background = Color(0xFF101310), surface = Color(0xFF101310),
    surfaceContainer = Color(0xFF1A1E19), surfaceContainerHigh = Color(0xFF242922),
    onBackground = Color(0xFFE9EDE3), onSurface = Color(0xFFE9EDE3),
    onSurfaceVariant = Color(0xFFA6AD9E), outlineVariant = Color(0xFF333A30), error = Color(0xFFFFB4A7),
)
private val Light = lightColorScheme(
    primary = Color(0xFF37661D), onPrimary = Color.White, primaryContainer = Color(0xFFD1F3B8),
    secondary = Color(0xFF52614A), background = Color(0xFFF6F8F1), surface = Color(0xFFF6F8F1),
    surfaceContainer = Color(0xFFEDF0E6), surfaceContainerHigh = Color(0xFFE3E9DB),
    onBackground = Color(0xFF182015), onSurface = Color(0xFF182015), onSurfaceVariant = Color(0xFF52604B),
    outlineVariant = Color(0xFFCFD7C8), error = Color(0xFFA8291F),
)
val Mono = FontFamily.Monospace
@Composable fun ForgeTheme(theme: String = "System", content: @Composable () -> Unit) {
    val dark = when(theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp),
    ), content = content)
}
