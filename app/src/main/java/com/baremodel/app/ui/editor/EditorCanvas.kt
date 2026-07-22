package com.baremodel.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.CanvasBg
import com.baremodel.app.ui.theme.GroutC
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.AnchorMode
import com.baremodel.core.LocalRect
import com.baremodel.core.Pt
import com.baremodel.core.TileClass
import com.baremodel.core.pointInPolygon
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EditorCanvas(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val unitM = stringResource(R.string.unit_m)
    val d = LocalDensity.current.density
    val labelPaint = remember(d) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(233, 238, 246)
            textSize = 11f * d
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
            )
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged {
                vm.canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                vm.maybeInitialFit()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    vm.gestureDown(first.position)
                    var pinching = false
                    var base = vm.view
                    var d0 = 1f
                    var mid0 = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.size >= 2) {
                            val a = active[0].position
                            val b = active[1].position
                            val dist = max(1f, (a - b).getDistance())
                            val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                            if (!pinching) {
                                pinching = true
                                vm.cancelGesture()
                                base = vm.view
                                d0 = dist
                                mid0 = mid
                            } else {
                                vm.pinch(base, d0, mid0, dist, mid)
                            }
                        } else if (active.size == 1 && !pinching) {
                            val c = active[0]
                            vm.gestureMove(c.position, c.previousPosition)
                        }
                        event.changes.forEach { it.consume() }
                        if (active.isEmpty()) break
                    }
                    vm.gestureEnd()
                }
            }
    ) {
        val vt = vm.view
        val s = vt.scale
        fun sx(x: Double) = (x * s + vt.offset.x).toFloat()
        fun sy(y: Double) = (y * s + vt.offset.y).toFloat()
        fun sp(p: Pt) = Offset(sx(p.x), sy(p.y))

        val pts = vm.room.points
        if (pts.size < 3) return@Canvas

        // 1. фон
        drawRect(CanvasBg, size = size)

        // 2. точечная сетка 0.5 м в режиме комнаты
        if (vm.roomMode && 0.5f * s > 16f) {
            val wx0 = (-vt.offset.x / s).toDouble()
            val wy0 = (-vt.offset.y / s).toDouble()
            val wx1 = ((size.width - vt.offset.x) / s).toDouble()
            val wy1 = ((size.height - vt.offset.y) / s).toDouble()
            var gx = floor(wx0 / 0.5) * 0.5
            while (gx <= wx1) {
                var gy = floor(wy0 / 0.5) * 0.5
                while (gy <= wy1) {
                    drawCircle(Color.White.copy(alpha = 0.055f), 1.2f * d, Offset(sx(gx), sy(gy)))
                    gy += 0.5
                }
                gx += 0.5
            }
        }

        // 3. основание комнаты (цвет шва), вырезы вычтены
        val roomPath = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(sx(pts[0].x), sy(pts[0].y))
            for (i in 1 until pts.size) lineTo(sx(pts[i].x), sy(pts[i].y))
            close()
            for (c in vm.room.cutouts) {
                addRect(Rect(sx(c.x), sy(c.y), sx(c.x + c.w), sy(c.y + c.h)))
            }
        }
        drawPath(roomPath, GroutC)

        // 4. плитки
        val img = vm.tileImage
        val decorImg = vm.decorImage
        val decorSet = vm.decorIdx
        clipPath(roomPath) {
            vm.layout.tiles.forEachIndexed { ti, t ->
                val q = t.corners
                val isDecor = ti in decorSet
                val face = if (isDecor && decorImg != null) decorImg else img
                if (face == null) {
                    val p = Path().apply {
                        moveTo(sx(q[0].x), sy(q[0].y))
                        lineTo(sx(q[1].x), sy(q[1].y))
                        lineTo(sx(q[2].x), sy(q[2].y))
                        lineTo(sx(q[3].x), sy(q[3].y))
                        close()
                    }
                    val base = if (isDecor) AccentTile else vm.tileColor
                    drawPath(p, if (vm.variation && !isDecor) shadeOf(base, t.rect) else base)
                } else {
                    val deg = vm.pattern.rotationDeg.toFloat()
                    val w = (t.rect.w * s).roundToInt() + 1
                    val h = (t.rect.h * s).roundToInt() + 1
                    if (!t.rect.vertical) {
                        withTransform({
                            translate(sx(q[0].x), sy(q[0].y))
                            rotate(deg, Offset.Zero)
                        }) {
                            drawImage(face, dstOffset = IntOffset.Zero, dstSize = IntSize(w, h))
                        }
                    } else {
                        withTransform({
                            translate(sx(q[1].x), sy(q[1].y))
                            rotate(deg + 90f, Offset.Zero)
                        }) {
                            drawImage(face, dstOffset = IntOffset.Zero, dstSize = IntSize(h, w))
                        }
                    }
                }
            }
            if (vm.showCuts) {
                for (t in vm.layout.tiles) {
                    if (t.cls != TileClass.CUT) continue
                    val q = t.corners
                    val p = Path().apply {
                        moveTo(sx(q[0].x), sy(q[0].y))
                        lineTo(sx(q[1].x), sy(q[1].y))
                        lineTo(sx(q[2].x), sy(q[2].y))
                        lineTo(sx(q[3].x), sy(q[3].y))
                        close()
                    }
                    drawPath(p, Warn.copy(alpha = 0.9f), style = Stroke(1.4f * d))
                    drawLine(Warn.copy(alpha = 0.9f), sp(q[0]), sp(q[2]), strokeWidth = 1.4f * d)
                }
            }
        }

        // 4b. оси привязки раскладки
        if (vm.anchor != AnchorMode.FREE) {
            val c = vm.roomCenter()
            val dashAx = PathEffect.dashPathEffect(floatArrayOf(4f * d, 5f * d), 0f)
            val axis = Acc.copy(alpha = 0.4f)
            drawLine(axis, Offset(sx(c.x), sy(pts.minOf { it.y })), Offset(sx(c.x), sy(pts.maxOf { it.y })),
                strokeWidth = 1f * d, pathEffect = dashAx)
            drawLine(axis, Offset(sx(pts.minOf { it.x }), sy(c.y)), Offset(sx(pts.maxOf { it.x }), sy(c.y)),
                strokeWidth = 1f * d, pathEffect = dashAx)
        }

        // 5. контур комнаты
        val outline = Path().apply {
            moveTo(sx(pts[0].x), sy(pts[0].y))
            for (i in 1 until pts.size) lineTo(sx(pts[i].x), sy(pts[i].y))
            close()
        }
        drawPath(outline, Acc, style = Stroke(2.5f * d, join = StrokeJoin.Round))

        // 6. вырезы пунктиром
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f * d, 5f * d), 0f)
        for (c in vm.room.cutouts) {
            drawRect(
                Warn,
                topLeft = Offset(sx(c.x), sy(c.y)),
                size = Size(sx(c.x + c.w) - sx(c.x), sy(c.y + c.h) - sy(c.y)),
                style = Stroke(2f * d, pathEffect = dash),
            )
        }

        // 7. подписи размеров
        if (vm.showDims) {
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val sa = sp(a)
                val sb = sp(b)
                if ((sb - sa).getDistance() < 46f) continue
                val len = hypot(b.x - a.x, b.y - a.y)
                if (len <= 0.0) continue
                var nx = -(b.y - a.y) / len
                var ny = (b.x - a.x) / len
                val midW = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
                if (pointInPolygon(Pt(midW.x + nx * 0.08, midW.y + ny * 0.08), pts)) {
                    nx = -nx; ny = -ny
                }
                val at = Offset(
                    sx(midW.x) + (nx * 17.0 * d).toFloat(),
                    sy(midW.y) + (ny * 17.0 * d).toFloat(),
                )
                val text = String.format(Locale.getDefault(), "%.2f", len) + " " + unitM
                val tw = labelPaint.measureText(text)
                drawRoundRect(
                    Color(0xE0090F1A),
                    topLeft = Offset(at.x - tw / 2f - 6f * d, at.y - 9.5f * d),
                    size = Size(tw + 12f * d, 19f * d),
                    cornerRadius = CornerRadius(6f * d, 6f * d),
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(text, at.x, at.y + 4f * d, labelPaint)
                }
            }
        }

        // 8. ручки режима «Комната»
        if (vm.roomMode) {
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val sa = sp(a)
                val sb = sp(b)
                if ((sb - sa).getDistance() < 56f) continue
                val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)
                drawCircle(Panel2.copy(alpha = 0.9f), 8f * d, mid)
                drawCircle(Acc2, 8f * d, mid, style = Stroke(1.4f * d))
                drawLine(Acc2, Offset(mid.x - 4f * d, mid.y), Offset(mid.x + 4f * d, mid.y), 1.6f * d)
                drawLine(Acc2, Offset(mid.x, mid.y - 4f * d), Offset(mid.x, mid.y + 4f * d), 1.6f * d)
            }
            val selV = (vm.selection as? Selection.Vertex)?.i
            pts.forEachIndexed { i, p ->
                val c = sp(p)
                drawCircle(if (i == selV) Warn else Acc, 7f * d, c)
                drawCircle(Color.White, 7f * d, c, style = Stroke(2f * d))
            }
            val selC = (vm.selection as? Selection.Cut)?.i
            vm.room.cutouts.forEachIndexed { i, c ->
                val h = sp(Pt(c.x + c.w, c.y + c.h))
                drawRect(
                    if (i == selC) Warn else Acc2,
                    topLeft = Offset(h.x - 6f * d, h.y - 6f * d),
                    size = Size(12f * d, 12f * d),
                )
            }
        }
    }
}

private val AccentTile = Color(0xFFE8DFD2)

/** Лёгкий разнотон плитки: детерминированный хеш по позиции в узоре. */
private fun shadeOf(base: Color, rect: LocalRect): Color {
    val v = sin(rect.x * 127.1 + rect.y * 311.7) * 43758.5453
    val h = v - floor(v)
    val delta = ((h - 0.5) * 20).toInt()
    fun ch(x: Float) = ((x * 255).roundToInt() + delta).coerceIn(0, 255)
    return Color(ch(base.red), ch(base.green), ch(base.blue))
}
