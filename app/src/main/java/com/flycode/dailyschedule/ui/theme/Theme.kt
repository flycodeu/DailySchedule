package com.flycode.dailyschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PlanAccentColors = listOf(
    Color(0xFF1B6B5C), // Emerald Teal
    Color(0xFF2E6FD8), // Cobalt Blue
    Color(0xFFD97706), // Warm Amber
    Color(0xFFD94B64), // Coral Rose
    Color(0xFF6D5BD0), // Indigo Violet
    Color(0xFF5D8233)  // Olive Sage
)

fun planAccentColor(index: Int): Color =
    PlanAccentColors[index.mod(PlanAccentColors.size)]

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFE8),
    onPrimaryContainer = Color(0xFF0A332B),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF0D6),
    onSecondaryContainer = Color(0xFF4A2800),
    background = Color(0xFFF5F6F3),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFEAF0EC),
    onSurfaceVariant = Color(0xFF54625D),
    outline = Color(0xFFD2DBD6),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF65D5BA),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF124D41),
    onPrimaryContainer = Color(0xFFD5EFE8),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3E2400),
    secondaryContainer = Color(0xFF5C3A00),
    onSecondaryContainer = Color(0xFFFEF0D6),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFE4EAE7),
    surface = Color(0xFF18201E),
    onSurface = Color(0xFFE4EAE7),
    surfaceVariant = Color(0xFF242F2C),
    onSurfaceVariant = Color(0xFFA8B5B0),
    outline = Color(0xFF364440),
    error = Color(0xFFFFB4AB)
)

@Composable
fun DailyScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
