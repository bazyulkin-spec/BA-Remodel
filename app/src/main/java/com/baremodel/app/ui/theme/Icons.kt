package com.baremodel.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Иконки нарисованы штрихом 1.7 по сетке 24 — как в утверждённом макете.
 * Собственные векторы вместо эмодзи и без дополнительных зависимостей.
 */
private fun stroked(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object BaIcons {
    val Layers: ImageVector = stroked("layers", "M12 3 3 8l9 5 9-5-9-5Z M3 13.5 12 18l9-4.5")
    val Room: ImageVector = stroked("room", "M3 4h18v10h-8v6H3V4Z")
    val Ruler: ImageVector = stroked("ruler", "M2 8h20v8H2V8Z M7 8v3 M12 8v4 M17 8v3")
    val Scissors: ImageVector = stroked(
        "scissors",
        "M8.4 6a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0Z " +
            "M8.4 18a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0Z M20 4 8.6 16.4 M20 20 8.6 7.6",
    )
    val Fit: ImageVector = stroked("fit", "M4 9V4h5 M20 9V4h-5 M4 15v5h5 M20 15v5h-5")
    val Tile: ImageVector = stroked(
        "tile",
        "M3 3h8v8H3V3Z M13 3h8v8h-8V3Z M3 13h8v8H3v-8Z M13 13h8v8h-8v-8Z",
    )
    val Magic: ImageVector = stroked(
        "magic",
        "m5 19 9-9 M13 4l1 3 3 1-3 1-1 3-1-3-3-1 3-1 1-3Z M19 13l.7 2.1 2.1.7-2.1.7-.7 2.1-.7-2.1-2.1-.7 2.1-.7.7-2.1Z",
    )
    val Doc: ImageVector = stroked("doc", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z M14 3v5h5 M9 13h6 M9 17h4")
    val Share: ImageVector = stroked("share", "M12 15V3 M8 7l4-4 4 4 M4 14v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5")
    val Camera: ImageVector = stroked(
        "camera",
        "M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z " +
            "M15.2 13a3.2 3.2 0 1 1-6.4 0 3.2 3.2 0 0 1 6.4 0Z",
    )
    val Save: ImageVector = stroked("save", "M5 3h11l3 3v15H5V3Z M8 3v6h7V3 M8 21v-6h8v6")
    val Plus: ImageVector = stroked("plus", "M12 5v14 M5 12h14")
    val Close: ImageVector = stroked("close", "M6 6l12 12 M18 6 6 18")
    val Star: ImageVector = stroked("star", "M12 3.5l2.6 5.5 5.9.8-4.3 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6L3.5 9.8l5.9-.8L12 3.5Z")
    val Check: ImageVector = stroked("check", "M4 12.5 9.5 18 20 6.5")
    val Palette: ImageVector = stroked(
        "palette",
        "M12 3a9 9 0 1 0 0 18c1.1 0 1.8-.9 1.8-1.8 0-1.2-1-1.7-1-2.7 0-.8.7-1.5 1.5-1.5H16a5 5 0 0 0 5-5c0-4-4-7-9-7Z",
    )
}
