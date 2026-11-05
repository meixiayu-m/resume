package com.resumeagent.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF172033)
val Moss = Color(0xFF1F6B5A)
val MossDark = Color(0xFF154A3F)
val Paper = Color(0xFFF6F4EE)
val WarmWhite = Color(0xFFFFFDF8)
val Line = Color(0xFFE5E0D5)
val Coral = Color(0xFFD9694C)
val Amber = Color(0xFFD69A35)
val Muted = Color(0xFF66716D)

private val Colors = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBE5),
    onPrimaryContainer = MossDark,
    secondary = Coral,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = WarmWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE9E0),
    onSurfaceVariant = Muted,
    outline = Line,
    error = Color(0xFFB23C32)
)

@Composable
fun ResumeAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
