package com.lorian.lunna.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta "noche lunar": la app es siempre oscura.
val Night = Color(0xFF0C0A1D)
val NightTop = Color(0xFF231A50)
val Surface = Color(0xFF16132E)
val SurfaceHi = Color(0xFF221E42)
val SurfaceTop = Color(0xFF2A2550)
val Lavender = Color(0xFFB79CFF)
val Pink = Color(0xFFFF8FC7)
val Sky = Color(0xFF7FD6FF)
val Ink = Color(0xFFEFEBFF)
val InkMuted = Color(0xFFA7A1C8)
val Line = Color(0xFF353058)
val OnAccent = Color(0xFF1B1038)
val ErrorSoft = Color(0xFFFF8A9A)

val NightBrush = Brush.verticalGradient(listOf(NightTop, Night, Night))
val AccentBrush = Brush.linearGradient(listOf(Lavender, Pink))
