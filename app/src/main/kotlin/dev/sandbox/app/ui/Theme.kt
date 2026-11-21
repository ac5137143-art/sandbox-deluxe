package dev.sandbox.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val palette = darkColorScheme(
    primary = Color(0xFF7FD4C1),
    onPrimary = Color(0xFF062420),
    primaryContainer = Color(0xFF18413A),
    onPrimaryContainer = Color(0xFFB7F0E2),
    secondary = Color(0xFFE3C173),
    onSecondary = Color(0xFF2A1F00),
    secondaryContainer = Color(0xFF3D3013),
    tertiary = Color(0xFFFF8A5C),
    background = Color(0xFF070910),
    onBackground = Color(0xFFE8EDF5),
    surface = Color(0xFF0E1420),
    onSurface = Color(0xFFE8EDF5),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFFA9B6C8),
    outline = Color(0xFF3A4658),
    error = Color(0xFFFF6B6B),
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun SandboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, typography = typography, content = content)
}
