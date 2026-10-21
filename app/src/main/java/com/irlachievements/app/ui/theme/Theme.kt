package com.irlachievements.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090B12)
val Panel = Color(0xFF121622)
val PanelRaised = Color(0xFF1A2030)
val Acid = Color(0xFFB8FF5A)
val Cyan = Color(0xFF63E6FF)
val Violet = Color(0xFFAD8CFF)
val Muted = Color(0xFF99A2B8)
val Gold = Color(0xFFFFD166)

private val IrlColors = darkColorScheme(
    primary = Acid,
    onPrimary = Ink,
    primaryContainer = Color(0xFF263A18),
    onPrimaryContainer = Color(0xFFD6FF9F),
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Violet,
    background = Ink,
    onBackground = Color(0xFFF4F6FF),
    surface = Panel,
    onSurface = Color(0xFFF4F6FF),
    surfaceVariant = PanelRaised,
    onSurfaceVariant = Muted,
    outline = Color(0xFF343D52),
    error = Color(0xFFFF6B7A),
)

@Composable
fun IrlAchievementsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IrlColors, content = content)
}
