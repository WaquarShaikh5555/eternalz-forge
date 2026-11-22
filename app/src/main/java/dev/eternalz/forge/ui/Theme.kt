package dev.eternalz.forge.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val Dark = darkColorScheme(
    primary = Color(0xFF8CD9BB), onPrimary = Color(0xFF003828), primaryContainer = Color(0xFF173E32), onPrimaryContainer = Color(0xFFB0EDD6),
    secondary = Color(0xFFB3C8BE), onSecondary = Color(0xFF20352D), secondaryContainer = Color(0xFF2C4037), onSecondaryContainer = Color(0xFFD5E9DE),
    tertiary = Color(0xFFEAC091), background = Color(0xFF171B1A), surface = Color(0xFF1C211F),
    surfaceContainerLowest = Color(0xFF141816), surfaceContainerLow = Color(0xFF202623),
    surfaceContainer = Color(0xFF252C28), surfaceContainerHigh = Color(0xFF2C3430), surfaceContainerHighest = Color(0xFF36403A),
    onBackground = Color(0xFFF0F4F1), onSurface = Color(0xFFF0F4F1), onSurfaceVariant = Color(0xFFB3C0B8),
    outline = Color(0xFF7D8D83), outlineVariant = Color(0xFF3D4A42), error = Color(0xFFFFB4AB),
)
private val Light = lightColorScheme(
    primary = Color(0xFF146B50), onPrimary = Color.White, primaryContainer = Color(0xFFE2F3EB), onPrimaryContainer = Color(0xFF124D3B),
    secondary = Color(0xFF56675F), onSecondary = Color.White, secondaryContainer = Color(0xFFECF1ED), onSecondaryContainer = Color(0xFF2B4035),
    tertiary = Color(0xFF985D29), background = Color(0xFFF5F6F3), surface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FAF7),
    surfaceContainer = Color(0xFFEEF1ED), surfaceContainerHigh = Color(0xFFE7ECE7), surfaceContainerHighest = Color(0xFFDDE5DE),
    onBackground = Color(0xFF1B2721), onSurface = Color(0xFF1B2721), onSurfaceVariant = Color(0xFF56665D),
    outline = Color(0xFF7A8A7F), outlineVariant = Color(0xFFDCE3DC), error = Color(0xFFAE302A),
)
val Mono = FontFamily.Monospace
@Composable fun ForgeTheme(theme: String = "Light", content: @Composable () -> Unit) {
    val dark = when(theme) { "Dark" -> true; "System" -> isSystemInDarkTheme(); else -> false }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp),
    ), shapes = Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    )) {
        // Every route, including sign-in and sheets, receives a real themed background.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
    }
}
