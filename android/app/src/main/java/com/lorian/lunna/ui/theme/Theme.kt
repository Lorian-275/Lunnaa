package com.lorian.lunna.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Sin colores dinámicos del sistema: la identidad de la app es la misma en todos los teléfonos.
private val LunnaColors = darkColorScheme(
    primary = Lavender,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF3A2C78),
    onPrimaryContainer = Ink,
    secondary = Pink,
    onSecondary = OnAccent,
    tertiary = Sky,
    background = Night,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHi,
    surfaceContainerHighest = SurfaceTop,
    outline = Line,
    outlineVariant = Line,
    error = ErrorSoft,
    onError = OnAccent,
    inverseSurface = Ink,
    inverseOnSurface = Night,
)

@Composable
fun LunnaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LunnaColors, typography = Typography) {
        // Las pantallas pintan su propio degradado sin Surface: sin esto el texto sin color cae en negro.
        CompositionLocalProvider(LocalContentColor provides Ink, content = content)
    }
}
