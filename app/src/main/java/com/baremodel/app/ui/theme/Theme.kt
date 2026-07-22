package com.baremodel.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Палитра из утверждённого макета BA-Remodel-Design.html
val Bg = Color(0xFF07090D)
val Panel = Color(0xFF0E1117)
val Panel2 = Color(0xFF141922)
val Panel3 = Color(0xFF1B212C)
val LineC = Color(0xFF171C25)
val Line2 = Color(0xFF232A36)
val Txt = Color(0xFFEEF2F8)
val Sub = Color(0xFF8A97AC)
val Dim = Color(0xFF5C6980)
val Acc = Color(0xFF5B92FF)
val Acc2 = Color(0xFF8FB4FF)
val AccDeep = Color(0xFF3059C9)
val Warn = Color(0xFFFFB454)
val Good = Color(0xFF48D597)
val Bad = Color(0xFFFF6B6B)
val CanvasBg = Color(0xFF0A0E15)
val GroutC = Color(0xFF39414E)

val AccSoft = Color(0x245B92FF)
val WarmSoft = Color(0x21FFB454)

private val Scheme = darkColorScheme(
    primary = Acc,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Txt,
    surface = Panel,
    onSurface = Txt,
    surfaceVariant = Panel2,
    onSurfaceVariant = Sub,
    outline = LineC,
    secondary = Acc2,
    tertiary = Good,
    error = Warn,
)

private val BaShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private val BaType = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun BARemodelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = BaShapes, typography = BaType, content = content)
}
