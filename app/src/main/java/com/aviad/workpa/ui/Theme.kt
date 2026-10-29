package com.aviad.workpa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Amber = Color(0xFFFFB300)
val AmberDark = Color(0xFF3D2E00)
val Green = Color(0xFF66BB6A)
val GreenBg = Color(0xFF1B3A1E)
val Warn = Color(0xFFFFB74D)
val WarnBg = Color(0xFF3D2A10)
val Bg = Color(0xFF121212)
val Card = Color(0xFF1E1E1E)
val Card2 = Color(0xFF262626)
val Muted = Color(0xFF9E9E9E)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = AmberDark,
    onPrimaryContainer = Amber,
    secondary = Amber,
    background = Bg,
    onBackground = Color(0xFFEDEDED),
    surface = Card,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Card2,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Card2,
    outline = Color(0xFF444444),
    error = Color(0xFFEF5350),
)

@Composable
fun WorkPATheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
