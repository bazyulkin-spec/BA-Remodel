package com.baremodel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B1322)
val Panel = Color(0xFF101A2C)
val Panel2 = Color(0xFF0C1526)
val LineC = Color(0xFF1D2A42)
val Txt = Color(0xFFE9EEF6)
val Sub = Color(0xFF8CA0BC)
val Acc = Color(0xFF3D8BFF)
val Acc2 = Color(0xFF7DB4FF)
val Warn = Color(0xFFFFB454)
val CanvasBg = Color(0xFF070E1A)
val GroutC = Color(0xFF4A5462)
val Good = Color(0xFF4ADE80)

private val Scheme = darkColorScheme(
    primary = Acc,
    background = Bg,
    surface = Panel,
    onSurface = Txt,
    surfaceVariant = Panel2,
    onSurfaceVariant = Sub,
    outline = LineC,
    secondary = Acc2,
    error = Warn,
)

@Composable
fun BARemodelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
