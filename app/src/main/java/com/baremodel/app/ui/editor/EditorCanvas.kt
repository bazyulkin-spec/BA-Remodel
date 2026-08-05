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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.baremodel.app.data.UiPrefs
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Good
import com.baremodel.app.ui.theme.Acc2
import androidx.compose.ui.graphics.Brush
import com.baremodel.app.ui.theme.CanvasBg
import com.baremodel.app.ui.theme.GroutC
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.Arcs
import com.baremodel.core.PatternType
import com.baremodel.core.AnchorMode
import com.baremodel.core.LocalRect
import com.baremodel.core.Pt
import com.baremodel.core.ArtRect
import com.baremodel.core.TileClass
import com.baremodel.core.TilingEngine
import com.baremodel.core.pointInPolygon
import java.util.Locale
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Цвет типа проёма — единый на плане, в 3D и в PNG-схеме; дверь и балкон различимы. */
internal fun openingTone(kind: Int): Color = when (kind) {
    OPENING_WINDOW -> Acc
    OPENING_ENTRY -> Good
    OPENING_BALCONY -> Color(0xFF3ED0C3)
    OPENING_PASSAGE -> Sub
    else -> Color(0xFFFF9046)
}

@Composable
fun EditorCanvas(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val unitM = stringResource(R.string.unit_m)
    val kindWords = listOf(
        R.string.kind_window, R.string.kind_door, R.string.kind_balcony,
        R.string.kind_entry, R.string.kind_passage,
    ).map { stringResource(it) }
    val wmText = stringResource(R.string.wm_brand)
    val rulerAcrossTpl = stringResource(R.string.ruler_across)
    val rulerRowsTpl = stringResource(R.string.ruler_rows)
    val firstHintText = stringResource(R.string.first_hint)
    val viewOnlyText = stringResource(R.string.view_only)
    val stairUpText = stringResource(R.string.stairs_up)
    val stairPorchText = stringResource(R.string.stairs_porch)
    val inactiveLayouts = remember(vm.rooms, vm.activeRoom) {
        vm.rooms.mapIndexed { i, r ->
            if (i == vm.activeRoom) null else TilingEngine.build(r.spec, r.tile, r.pattern)
        }
    }
    val roomLabels = vm.rooms.mapIndexed { i, r ->
        r.name.ifBlank { stringResource(R.string.room_n, i + 1) }
    }
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

        // 1. фон с мягким свечением сверху — сцена перестаёт быть плоской заливкой
        drawRect(CanvasBg, size = size)
        drawRect(
            Brush.radialGradient(
                colors = listOf(Acc.copy(alpha = 0.075f), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.18f),
                radius = (size.width * 0.95f).coerceAtLeast(1f),
            ),
            size = size,
        )

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

        // 2b. подложка: фото чертежа под всей работой
        vm.planImage?.takeIf { vm.showPlanImage }?.let { pi ->
            val pw = (pi.width * vm.planMPerPx * s).roundToInt().coerceAtLeast(1)
            val ph = (pi.height * vm.planMPerPx * s).roundToInt().coerceAtLeast(1)
            drawImage(
                pi,
                dstOffset = IntOffset(
                    sx(vm.planOrigin.x).roundToInt(),
                    sy(vm.planOrigin.y).roundToInt(),
                ),
                dstSize = IntSize(pw, ph),
                alpha = vm.planAlpha,
            )
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
        val bevel = vm.layout.tiles.size <= 700
        val img = vm.tileImage
        val decorImg = vm.decorImage
        val decorSet = vm.decorIdx
        clipPath(roomPath) {
            val panelCols = vm.decor.panelCols.coerceAtLeast(1)
            val panelRows = vm.decor.panelRows.coerceAtLeast(1)
            vm.layout.tiles.forEachIndexed { ti, t ->
                val q = t.corners
                val isDecor = ti in decorSet
                val cell = vm.panelCell(t)
                val face = if (isDecor && decorImg != null) decorImg else img
                if (cell != null && decorImg == null) {
                    val p = Path().apply {
                        moveTo(sx(q[0].x), sy(q[0].y))
                        lineTo(sx(q[1].x), sy(q[1].y))
                        lineTo(sx(q[2].x), sy(q[2].y))
                        lineTo(sx(q[3].x), sy(q[3].y))
                        close()
                    }
                    drawPath(p, AccentTile)
                    drawPath(p, Acc2.copy(alpha = 0.8f), style = Stroke(1.6f * d))
                } else if (cell != null && decorImg != null) {
                    // кусок общей картинки: у стены он обрежется вместе с плиткой
                    val sw = (decorImg.width / panelCols).coerceAtLeast(1)
                    val sh = (decorImg.height / panelRows).coerceAtLeast(1)
                    val so = IntOffset(
                        (cell.first * sw).coerceIn(0, decorImg.width - sw),
                        (cell.second * sh).coerceIn(0, decorImg.height - sh),
                    )
                    val deg = vm.pattern.rotationDeg.toFloat()
                    val w = (t.rect.w * s).roundToInt() + 1
                    val h = (t.rect.h * s).roundToInt() + 1
                    if (!t.rect.vertical) {
                        withTransform({
                            translate(sx(q[0].x), sy(q[0].y))
                            rotate(deg, Offset.Zero)
                        }) {
                            drawImage(
                                decorImg,
                                srcOffset = so,
                                srcSize = IntSize(sw, sh),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(w, h),
                            )
                        }
                    } else {
                        withTransform({
                            translate(sx(q[1].x), sy(q[1].y))
                            rotate(deg + 90f, Offset.Zero)
                        }) {
                            drawImage(
                                decorImg,
                                srcOffset = so,
                                srcSize = IntSize(sw, sh),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(h, w),
                            )
                        }
                    }
                } else if (face == null) {
                    val p = Path().apply {
                        moveTo(sx(q[0].x), sy(q[0].y))
                        lineTo(sx(q[1].x), sy(q[1].y))
                        lineTo(sx(q[2].x), sy(q[2].y))
                        lineTo(sx(q[3].x), sy(q[3].y))
                        close()
                    }
                    val own = vm.colorOfTile(t)
                    val base = when {
                        own != null -> Color(own)
                        isDecor -> AccentTile
                        else -> vm.tileColor
                    }
                    drawPath(p, if (vm.variation && !isDecor && own == null) shadeOf(base, t.rect) else base)
                    if (bevel) {
                        drawLine(Color.White.copy(alpha = 0.20f), sp(q[0]), sp(q[1]), strokeWidth = 1f * d)
                        drawLine(Color.White.copy(alpha = 0.12f), sp(q[0]), sp(q[3]), strokeWidth = 1f * d)
                        drawLine(Color.Black.copy(alpha = 0.22f), sp(q[2]), sp(q[3]), strokeWidth = 1f * d)
                        drawLine(Color.Black.copy(alpha = 0.14f), sp(q[1]), sp(q[2]), strokeWidth = 1f * d)
                    }
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
            // 4z. зоны: своя плитка внутри той же комнаты
            vm.zoneLayouts.forEach { (z, zl) ->
                val zc = if (z.colorArgb != -1) Color(z.colorArgb) else AccentTile
                zl.tiles.forEach { t ->
                    val q = t.corners
                    val p = Path().apply {
                        moveTo(sx(q[0].x), sy(q[0].y))
                        lineTo(sx(q[1].x), sy(q[1].y))
                        lineTo(sx(q[2].x), sy(q[2].y))
                        lineTo(sx(q[3].x), sy(q[3].y))
                        close()
                    }
                    drawPath(p, if (z.variation) shadeOf(zc, t.rect) else zc)
                    if (bevel) {
                        drawLine(Color.White.copy(alpha = 0.18f), sp(q[0]), sp(q[1]), strokeWidth = 1f * d)
                        drawLine(Color.Black.copy(alpha = 0.20f), sp(q[2]), sp(q[3]), strokeWidth = 1f * d)
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
                    drawPath(p, Warn.copy(alpha = 0.16f))
                    drawPath(p, Warn.copy(alpha = 0.9f), style = Stroke(1.4f * d))
                    drawLine(Warn.copy(alpha = 0.9f), sp(q[0]), sp(q[2]), strokeWidth = 1.4f * d)
                }
            }
        }

        // 4n. номера подрезанных плиток: сверяй план с нарезанным на объекте.
        // Единая нумерация из ядра: номер стоит в центре видимого куска, а не
        // целой плитки, поэтому не прячется за стеной; крохотные полоски номеров
        // не получают и нумерацию не сдвигают. Все номера включаются одним
        // порогом масштаба — без дыр в последовательности при среднем зуме.
        if (vm.showCuts && vm.layout.tiles.size <= 400) {
            val ppmScr = kotlin.math.abs(sx(1.0) - sx(0.0)).toDouble()
            val minSidePx = kotlin.math.min(vm.tile.widthMm, vm.tile.heightMm) / 1000.0 * ppmScr
            if (minSidePx > 20.0 * d) {
                drawIntoCanvas { canvas ->
                    val np = android.graphics.Paint(labelPaint)
                    np.textSize = 8.5f * d
                    np.color = android.graphics.Color.argb(230, 255, 180, 84)
                    vm.cutInfo.values.forEach { ci ->
                        canvas.nativeCanvas.drawText(
                            ci.number.toString(), sx(ci.cx), sy(ci.cy) + 3f * d, np,
                        )
                    }
                }
            }
        }

        // 4s. выбранная плитка — подсветка; клип по контуру: под стеной плитки нет,
        // поэтому и подсветка за стену не вылезает — видно реальный кусок
        (vm.selection as? Selection.Tile)?.let { st ->
            vm.layout.tiles.getOrNull(st.i)?.let { t ->
                val p = Path().apply {
                    moveTo(sx(t.corners[0].x), sy(t.corners[0].y))
                    lineTo(sx(t.corners[1].x), sy(t.corners[1].y))
                    lineTo(sx(t.corners[2].x), sy(t.corners[2].y))
                    lineTo(sx(t.corners[3].x), sy(t.corners[3].y))
                    close()
                }
                clipPath(roomPath) {
                    drawPath(p, Acc.copy(alpha = 0.18f))
                    drawPath(p, Acc2, style = Stroke(2.4f * d))
                }
            }
        }

        // 4h. подсветка плиток, чей кусок совпадает с выбранной строкой обрезков
        vm.highlightCut?.let { hl ->
            vm.layout.tiles.forEachIndexed { i2, t ->
                if (t.cls != TileClass.CUT) return@forEachIndexed
                val piece = vm.cutPieceOf[i2] ?: return@forEachIndexed
                if (kotlin.math.abs(piece.first - hl.first) < 0.26 &&
                    kotlin.math.abs(piece.second - hl.second) < 0.26
                ) {
                    val p = Path().apply {
                        moveTo(sx(t.corners[0].x), sy(t.corners[0].y))
                        lineTo(sx(t.corners[1].x), sy(t.corners[1].y))
                        lineTo(sx(t.corners[2].x), sy(t.corners[2].y))
                        lineTo(sx(t.corners[3].x), sy(t.corners[3].y))
                        close()
                    }
                    clipPath(roomPath) {
                        drawPath(p, Good.copy(alpha = 0.16f))
                        drawPath(p, Good, style = Stroke(2.6f * d))
                    }
                }
            }
        }

        // 4a. область рисунка: где на каждой плитке будет узор и как его режут стены
        if (vm.showArt && vm.layout.tiles.size <= 700) {
            val artSrc = vm.decor.art
            val a = if (artSrc == ArtRect.FULL) ArtRect(0.2, 0.2, 0.6, 0.6) else artSrc
            val u0 = a.x
            val v0 = a.y
            val u1 = a.x + a.w
            val v1 = a.y + a.h
            clipPath(roomPath) {
                vm.layout.tiles.forEach { t ->
                    val c = t.corners
                    fun bl(u: Double, v: Double): Offset {
                        val topX = c[0].x + (c[1].x - c[0].x) * u
                        val topY = c[0].y + (c[1].y - c[0].y) * u
                        val botX = c[3].x + (c[2].x - c[3].x) * u
                        val botY = c[3].y + (c[2].y - c[3].y) * u
                        return Offset(
                            sx(topX + (botX - topX) * v),
                            sy(topY + (botY - topY) * v),
                        )
                    }
                    val p0 = bl(u0, v0)
                    val p1 = bl(u1, v0)
                    val p2 = bl(u1, v1)
                    val p3 = bl(u0, v1)
                    val ap = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }
                    val cut = t.cls == TileClass.CUT
                    drawPath(ap, (if (cut) Warn else Acc).copy(alpha = if (cut) 0.12f else 0.06f))
                    drawPath(
                        ap,
                        if (cut) Warn.copy(alpha = 0.85f) else Acc2.copy(alpha = 0.7f),
                        style = Stroke((if (cut) 1.7f else 1.2f) * d),
                    )
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

        // 4g. этаж снизу призраком: по нему выставляют стены верхнего этажа
        val ghostDash = PathEffect.dashPathEffect(floatArrayOf(9f * d, 7f * d), 0f)
        vm.ghostRooms.forEach { gr ->
            val pts = gr.spec.points
            if (pts.size < 3) return@forEach
            val gp = Path().apply {
                pts.forEachIndexed { gi, pt ->
                    if (gi == 0) moveTo(sx(pt.x), sy(pt.y)) else lineTo(sx(pt.x), sy(pt.y))
                }
                close()
            }
            drawPath(gp, Color(0x14A0ADC2))
            drawPath(gp, Color(0x8A6E7C93), style = Stroke(1.4f * d, pathEffect = ghostDash))
        }

        // 4c. остальные комнаты квартиры: тускло, с подписью; тап переключает
        vm.rooms.forEachIndexed { ri, r ->
            if (ri == vm.activeRoom) return@forEachIndexed
            val lay = inactiveLayouts.getOrNull(ri) ?: return@forEachIndexed
            val base = if (r.colorArgb != -1) Color(r.colorArgb) else Color(0xFFE8EAF0)
            if (lay.tiles.size <= 900) {
                lay.tiles.forEach { t ->
                    val p = Path().apply {
                        moveTo(sx(t.corners[0].x), sy(t.corners[0].y))
                        lineTo(sx(t.corners[1].x), sy(t.corners[1].y))
                        lineTo(sx(t.corners[2].x), sy(t.corners[2].y))
                        lineTo(sx(t.corners[3].x), sy(t.corners[3].y))
                        close()
                    }
                    drawPath(p, base.copy(alpha = 0.26f))
                    drawPath(p, Color(0x552A3140), style = Stroke(1f * d))
                }
            } else {
                val rp0 = Path().apply {
                    r.spec.points.forEachIndexed { i2, pt ->
                        if (i2 == 0) moveTo(sx(pt.x), sy(pt.y)) else lineTo(sx(pt.x), sy(pt.y))
                    }
                    close()
                }
                drawPath(rp0, base.copy(alpha = 0.16f))
            }
            val rp = Path().apply {
                r.spec.points.forEachIndexed { i2, pt ->
                    if (i2 == 0) moveTo(sx(pt.x), sy(pt.y)) else lineTo(sx(pt.x), sy(pt.y))
                }
                close()
            }
            drawPath(rp, Color(0x998A97AC), style = Stroke(1.6f * d))
            val cxr = r.spec.points.sumOf { it.x } / r.spec.points.size
            val cyr = r.spec.points.sumOf { it.y } / r.spec.points.size
            drawIntoCanvas { canvas ->
                val lp = android.graphics.Paint(labelPaint)
                lp.color = android.graphics.Color.argb(155, 160, 173, 194)
                canvas.nativeCanvas.drawText(
                    roomLabels.getOrElse(ri) { "" },
                    sx(cxr), sy(cyr), lp,
                )
            }
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

        // 6b. мебель: плитка просвечивает, перекрытый рисунок подсвечен
        val selFurn = (vm.selection as? Selection.Furn)?.i
        if (vm.showFurniture) {
            vm.furniture.forEachIndexed { i, f ->
                val tl = Offset(sx(f.x), sy(f.y))
                val br = Offset(sx(f.x + f.w), sy(f.y + f.h))
                val fw = br.x - tl.x
                val fh = br.y - tl.y
                if (fw <= 1f || fh <= 1f) return@forEachIndexed
                val col = if (i == selFurn) Warn else Acc2

                // какая часть декора уходит под объект
                if (decorSet.isNotEmpty()) {
                    clipRect(tl.x, tl.y, br.x, br.y) {
                        for (di in decorSet) {
                            val dt = vm.layout.tiles.getOrNull(di) ?: continue
                            val dp = Path().apply {
                                moveTo(sx(dt.corners[0].x), sy(dt.corners[0].y))
                                lineTo(sx(dt.corners[1].x), sy(dt.corners[1].y))
                                lineTo(sx(dt.corners[2].x), sy(dt.corners[2].y))
                                lineTo(sx(dt.corners[3].x), sy(dt.corners[3].y))
                                close()
                            }
                            drawPath(dp, Warn.copy(alpha = 0.30f))
                        }
                    }
                }

                // тень для объёма
                drawRect(
                    Color(0x4D000000),
                    topLeft = Offset(tl.x + 3f * d, tl.y + 4f * d),
                    size = Size(fw, fh),
                )
                // сам объект — полупрозрачный, плитка под ним читается
                drawRect(Color(0x66070E1A), topLeft = tl, size = Size(fw, fh))
                drawRect(col.copy(alpha = 0.9f), topLeft = tl, size = Size(fw, fh), style = Stroke(2f * d))
                if (!f.coversFinish) {
                    drawLine(col.copy(alpha = 0.55f), tl, br, strokeWidth = 1.2f * d)
                    drawLine(col.copy(alpha = 0.55f), Offset(br.x, tl.y), Offset(tl.x, br.y), strokeWidth = 1.2f * d)
                }

                // силуэт по типу объекта — мебель перестаёт быть одинаковыми коробками
                val ink = col.copy(alpha = 0.75f)
                val cx2 = (tl.x + br.x) / 2f
                val cy2 = (tl.y + br.y) / 2f
                val inset = 5f * d
                when (f.kind) {
                    "bath" -> drawRoundRect(
                        ink,
                        topLeft = Offset(tl.x + inset, tl.y + inset),
                        size = Size(fw - 2 * inset, fh - 2 * inset),
                        cornerRadius = CornerRadius(minOf(fw, fh) * 0.28f),
                        style = Stroke(1.6f * d),
                    )
                    "wc" -> {
                        val rw = minOf(fw, fh) * 0.62f
                        drawOval(
                            ink,
                            topLeft = Offset(cx2 - rw / 2, cy2 - rw * 0.62f),
                            size = Size(rw, rw * 1.24f),
                            style = Stroke(1.6f * d),
                        )
                        drawRect(
                            ink,
                            topLeft = Offset(cx2 - rw * 0.45f, tl.y + inset * 0.6f),
                            size = Size(rw * 0.9f, 4.5f * d),
                        )
                    }
                    "washer" -> drawCircle(
                        ink,
                        radius = minOf(fw, fh) * 0.30f,
                        center = Offset(cx2, cy2),
                        style = Stroke(1.6f * d),
                    )
                    "fridge" -> drawLine(
                        ink,
                        Offset(tl.x + inset, cy2),
                        Offset(br.x - inset, cy2),
                        strokeWidth = 1.6f * d,
                    )
                    "kitchen" -> {
                        drawCircle(
                            ink,
                            radius = minOf(fw, fh) * 0.22f,
                            center = Offset(tl.x + fw * 0.22f, cy2),
                            style = Stroke(1.4f * d),
                        )
                        for (i in 0..1) for (j in 0..1) {
                            drawCircle(
                                ink,
                                radius = 2.2f * d,
                                center = Offset(br.x - fw * 0.20f + (i - 0.5f) * 9f * d, cy2 + (j - 0.5f) * 9f * d),
                            )
                        }
                    }
                    "cabinet" -> drawLine(
                        ink,
                        Offset(cx2, tl.y + inset),
                        Offset(cx2, br.y - inset),
                        strokeWidth = 1.4f * d,
                    )
                    "wardrobe" -> {
                        drawLine(
                            ink,
                            Offset(tl.x + fw / 3f, tl.y + inset),
                            Offset(tl.x + fw / 3f, br.y - inset),
                            strokeWidth = 1.4f * d,
                        )
                        drawLine(
                            ink,
                            Offset(tl.x + fw * 2f / 3f, tl.y + inset),
                            Offset(tl.x + fw * 2f / 3f, br.y - inset),
                            strokeWidth = 1.4f * d,
                        )
                    }
                    "table" -> {
                        drawRect(
                            ink,
                            topLeft = Offset(tl.x + inset, tl.y + inset),
                            size = Size(fw - 2 * inset, fh - 2 * inset),
                            style = Stroke(1.4f * d),
                        )
                        listOf(
                            Offset(tl.x + inset * 1.9f, tl.y + inset * 1.9f),
                            Offset(br.x - inset * 1.9f, tl.y + inset * 1.9f),
                            Offset(tl.x + inset * 1.9f, br.y - inset * 1.9f),
                            Offset(br.x - inset * 1.9f, br.y - inset * 1.9f),
                        ).forEach { drawCircle(ink, radius = 2.2f * d, center = it) }
                    }
                    "chair" -> {
                        drawRoundRect(
                            ink,
                            topLeft = Offset(tl.x + inset, tl.y + inset),
                            size = Size(fw - 2 * inset, fh - 2 * inset),
                            cornerRadius = CornerRadius(3.5f * d),
                            style = Stroke(1.4f * d),
                        )
                        drawLine(
                            ink,
                            Offset(tl.x + inset, br.y - inset * 1.4f),
                            Offset(br.x - inset, br.y - inset * 1.4f),
                            strokeWidth = 2.4f * d,
                        )
                    }
                }

                if (fw > 46f && fh > 26f) {
                    drawIntoCanvas { canvas ->
                        val p = android.graphics.Paint(labelPaint)
                        p.color = android.graphics.Color.rgb(207, 224, 255)
                        canvas.nativeCanvas.drawText(f.name, tl.x + fw / 2, tl.y + fh / 2 + 3f * d, p)
                        if (i == selFurn) {
                            val small = android.graphics.Paint(labelPaint)
                            small.textSize = 9.5f * d
                            small.color = android.graphics.Color.rgb(255, 180, 84)
                            canvas.nativeCanvas.drawText(
                                String.format(Locale.getDefault(), "%.2f × %.2f", f.w, f.h) + " " + unitM,
                                tl.x + fw / 2,
                                tl.y + fh / 2 + 17f * d,
                                small,
                            )
                        }
                    }
                }
                if (vm.roomMode) {
                    drawRect(col, topLeft = Offset(br.x - 6f * d, br.y - 6f * d), size = Size(12f * d, 12f * d))
                }
            }
        }

        // 6st. ступени: рёбра ступеней, стрелка подъёма и подпись, куда ведёт
        val selStair = (vm.selection as? Selection.Stair)?.i
        vm.stairs.forEachIndexed { i, st ->
            val tl = Offset(sx(st.x), sy(st.y))
            val br = Offset(sx(st.x + st.w), sy(st.y + st.h))
            val stw = br.x - tl.x
            val sth = br.y - tl.y
            if (stw <= 1f || sth <= 1f) return@forEachIndexed
            val col = if (i == selStair) Warn else Acc2
            drawRect(Color(0x40070E1A), topLeft = tl, size = Size(stw, sth))
            drawRect(col.copy(alpha = 0.95f), topLeft = tl, size = Size(stw, sth), style = Stroke(2f * d))
            // рёбра ступеней: видно, где кончается одна проступь и начинается другая
            for (k in 0 until st.steps - 1) {
                val e = st.edge(k)
                drawLine(
                    col.copy(alpha = 0.65f),
                    Offset(sx(e.first.x), sy(e.first.y)),
                    Offset(sx(e.second.x), sy(e.second.y)),
                    strokeWidth = 1.4f * d,
                )
            }
            // стрелка подъёма: от нижней ступени к верхней
            val base = st.edge(-1)
            val top = st.edge(st.steps - 1)
            val from = Offset(sx((base.first.x + base.second.x) / 2), sy((base.first.y + base.second.y) / 2))
            val to = Offset(sx((top.first.x + top.second.x) / 2), sy((top.first.y + top.second.y) / 2))
            val vx = to.x - from.x
            val vy = to.y - from.y
            val vlen = kotlin.math.sqrt(vx * vx + vy * vy)
            if (vlen > 6f) {
                val ux = vx / vlen
                val uy = vy / vlen
                val tip = Offset(from.x + ux * (vlen - 3f * d), from.y + uy * (vlen - 3f * d))
                drawLine(col, from, tip, strokeWidth = 2.2f * d)
                val a = 7f * d
                drawLine(col, tip, Offset(tip.x - ux * a - uy * a * 0.6f, tip.y - uy * a + ux * a * 0.6f), strokeWidth = 2.2f * d)
                drawLine(col, tip, Offset(tip.x - ux * a + uy * a * 0.6f, tip.y - uy * a - ux * a * 0.6f), strokeWidth = 2.2f * d)
            }
            // подпись: «Вверх, этаж 2» или «Крыльцо» — постороннему сразу понятно
            drawIntoCanvas { canvas ->
                val sp = android.graphics.Paint(labelPaint)
                sp.textSize = 10.5f * d
                canvas.nativeCanvas.drawText(
                    if (st.toLevel >= 0) stairUpText + " " + (st.toLevel + 1) else stairPorchText,
                    (tl.x + br.x) / 2,
                    tl.y - 5f * d,
                    sp,
                )
            }
        }

        // 6c. стены: у каждой стены своя толщина, полоса рисуется наружу от ребра
        run {
            val others = vm.rooms.filterIndexed { i, _ -> i != vm.activeRoom }
                .filter { it.spec.points.size >= 3 }
                .map { r ->
                    Path().apply {
                        r.spec.points.forEachIndexed { i2, p2 ->
                            if (i2 == 0) moveTo(sx(p2.x), sy(p2.y)) else lineTo(sx(p2.x), sy(p2.y))
                        }
                        close()
                    }
                }
            val wallC = Color(0xFF7E8A9C).copy(alpha = 0.55f)
            fun drawWalls() {
                val ptsW = vm.room.points
                for (i in ptsW.indices) {
                    val a = ptsW[i]
                    val b = ptsW[(i + 1) % ptsW.size]
                    val th = vm.wallThicknessOf("wall-" + (i + 1))
                    if (th <= 0.001) continue
                    val ex = b.x - a.x
                    val ey = b.y - a.y
                    val len = hypot(ex, ey)
                    if (len < 1e-6) continue
                    var nx = ey / len
                    var ny = -ex / len
                    val mid = Pt(a.x + ex / 2, a.y + ey / 2)
                    if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), ptsW)) {
                        nx = -nx
                        ny = -ny
                    }
                    // ребро расширено на толщину соседних стен, чтобы углы сходились
                    val thPrev = vm.wallThicknessOf("wall-" + (if (i == 0) ptsW.size else i))
                    val thNext = vm.wallThicknessOf("wall-" + ((i + 1) % ptsW.size + 1))
                    val ux = ex / len
                    val uy = ey / len
                    val a2 = Pt(a.x - ux * thPrev, a.y - uy * thPrev)
                    val b2 = Pt(b.x + ux * thNext, b.y + uy * thNext)
                    val band = Path().apply {
                        moveTo(sx(a2.x), sy(a2.y))
                        lineTo(sx(b2.x), sy(b2.y))
                        lineTo(sx(b2.x + nx * th), sy(b2.y + ny * th))
                        lineTo(sx(a2.x + nx * th), sy(a2.y + ny * th))
                        close()
                    }
                    drawPath(band, wallC)
                }
            }
            clipPath(roomPath, clipOp = ClipOp.Difference) {
                when (others.size) {
                    0 -> drawWalls()
                    1 -> clipPath(others[0], clipOp = ClipOp.Difference) { drawWalls() }
                    2 -> clipPath(others[0], clipOp = ClipOp.Difference) {
                        clipPath(others[1], clipOp = ClipOp.Difference) { drawWalls() }
                    }
                    else -> clipPath(others[0], clipOp = ClipOp.Difference) {
                        clipPath(others[1], clipOp = ClipOp.Difference) {
                            clipPath(others[2], clipOp = ClipOp.Difference) { drawWalls() }
                        }
                    }
                }
            }
        }

        // 6e. проёмы: дверь — разрыв с дугой, окно — двойная линия в стене
        run {
            val ptsO = vm.room.points
            for (i in ptsO.indices) {
                val a = ptsO[i]
                val b = ptsO[(i + 1) % ptsO.size]
                val list = vm.openingsOf("wall-" + (i + 1))
                if (list.isEmpty()) continue
                val ex = b.x - a.x
                val ey = b.y - a.y
                val len = hypot(ex, ey)
                if (len < 1e-6) continue
                val ux = ex / len
                val uy = ey / len
                var nx = ey / len
                var ny = -ex / len
                val mid = Pt(a.x + ex / 2, a.y + ey / 2)
                if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), ptsO)) {
                    nx = -nx
                    ny = -ny
                }
                val th = vm.wallThicknessOf("wall-" + (i + 1))
                val kinds = vm.openingKindsOf("wall-" + (i + 1))
                list.forEachIndexed { oi, o ->
                    val kind = kinds.getOrNull(oi) ?: OPENING_WINDOW
                    val s0 = o.x
                    val s1 = o.x + o.w
                    val p0 = Pt(a.x + ux * s0, a.y + uy * s0)
                    val p1 = Pt(a.x + ux * s1, a.y + uy * s1)
                    val doorLike = kind == OPENING_DOOR || kind == OPENING_BALCONY ||
                        kind == OPENING_ENTRY
                    val tone = openingTone(kind)
                    // сам проём: светлый разрыв в полосе стены
                    val gap = Path().apply {
                        moveTo(sx(p0.x), sy(p0.y))
                        lineTo(sx(p1.x), sy(p1.y))
                        lineTo(sx(p1.x + nx * th), sy(p1.y + ny * th))
                        lineTo(sx(p0.x + nx * th), sy(p0.y + ny * th))
                        close()
                    }
                    drawPath(gap, CanvasBg)
                    drawPath(gap, tone, style = Stroke(1.4f * d))
                    // остекление: двойная линия поперёк проёма (окно и балконная дверь)
                    if (kind == OPENING_WINDOW || kind == OPENING_BALCONY) {
                        for (f in doubleArrayOf(0.34, 0.66)) {
                            drawLine(
                                tone,
                                Offset(sx(p0.x + nx * th * f), sy(p0.y + ny * th * f)),
                                Offset(sx(p1.x + nx * th * f), sy(p1.y + ny * th * f)),
                                strokeWidth = 1.2f * d,
                            )
                        }
                    }
                    // проход без двери: пунктир по середине полосы стены
                    if (kind == OPENING_PASSAGE) {
                        drawLine(
                            tone,
                            Offset(sx(p0.x + nx * th * 0.5), sy(p0.y + ny * th * 0.5)),
                            Offset(sx(p1.x + nx * th * 0.5), sy(p1.y + ny * th * 0.5)),
                            strokeWidth = 1.3f * d,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * d, 4f * d)),
                        )
                    }
                    if (doorLike) {
                        // дуга открывания внутрь комнаты
                        val r = (s1 - s0)
                        val arc = Path()
                        val steps = 10
                        for (k in 0..steps) {
                            val ang = k / steps.toDouble() * (Math.PI / 2)
                            val px = p0.x + ux * (r * cos(ang)) - nx * (r * sin(ang))
                            val py = p0.y + uy * (r * cos(ang)) - ny * (r * sin(ang))
                            if (k == 0) arc.moveTo(sx(px), sy(py)) else arc.lineTo(sx(px), sy(py))
                        }
                        // входная дверь: закрашенный сектор — вход виден с одного взгляда
                        if (kind == OPENING_ENTRY) {
                            val sector = Path().apply {
                                moveTo(sx(p0.x), sy(p0.y))
                                for (k in 0..steps) {
                                    val ang = k / steps.toDouble() * (Math.PI / 2)
                                    val px = p0.x + ux * (r * cos(ang)) - nx * (r * sin(ang))
                                    val py = p0.y + uy * (r * cos(ang)) - ny * (r * sin(ang))
                                    lineTo(sx(px), sy(py))
                                }
                                close()
                            }
                            drawPath(sector, tone.copy(alpha = 0.12f))
                        }
                        drawPath(
                            arc,
                            tone.copy(alpha = 0.6f),
                            style = Stroke(
                                1.2f * d,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * d, 5f * d)),
                            ),
                        )
                        drawLine(
                            tone.copy(alpha = 0.8f),
                            Offset(sx(p0.x), sy(p0.y)),
                            Offset(sx(p0.x - nx * r), sy(p0.y - ny * r)),
                            strokeWidth = if (kind == OPENING_ENTRY) 2.2f * d else 1.6f * d,
                        )
                    }
                }
            }
        }

        // 6m. метки проёмов: цветной кружок с буквой снаружи стены и указатель
        // на сам проём — «квартирку сверху» видно с одного взгляда при любом зуме
        run {
            val ptsM = vm.room.points
            if (ptsM.size >= 3) {
                for (i in ptsM.indices) {
                    val listO = vm.openingsOf("wall-" + (i + 1))
                    if (listO.isEmpty()) continue
                    val kindsM = vm.openingKindsOf("wall-" + (i + 1))
                    val a = ptsM[i]
                    val b = ptsM[(i + 1) % ptsM.size]
                    val ex = b.x - a.x
                    val ey = b.y - a.y
                    val len = kotlin.math.sqrt(ex * ex + ey * ey)
                    if (len < 1e-6) continue
                    val ux = ex / len
                    val uy = ey / len
                    var nx = ey / len
                    var ny = -ex / len
                    val midW = Pt(a.x + ex / 2, a.y + ey / 2)
                    if (pointInPolygon(Pt(midW.x + nx * 0.03, midW.y + ny * 0.03), ptsM)) {
                        nx = -nx
                        ny = -ny
                    }
                    val th = vm.wallThicknessOf("wall-" + (i + 1))
                    listO.forEachIndexed { oi, o ->
                        val kind = kindsM.getOrNull(oi) ?: OPENING_WINDOW
                        val tone = openingTone(kind)
                        val cxW = a.x + ux * (o.x + o.w / 2)
                        val cyW = a.y + uy * (o.x + o.w / 2)
                        // точка на наружной грани стены и центр кружка на отлёте
                        val gx = sx(cxW + nx * th)
                        val gy = sy(cyW + ny * th)
                        // экранное направление наружу (масштаб по осям одинаковый)
                        val dxs = sx(cxW + nx) - sx(cxW)
                        val dys = sy(cyW + ny) - sy(cyW)
                        val dl = kotlin.math.sqrt(dxs * dxs + dys * dys)
                        if (dl < 1e-3) return@forEachIndexed
                        val ndx = dxs / dl
                        val ndy = dys / dl
                        val bx = gx + ndx * 21f * d
                        val by = gy + ndy * 21f * d
                        drawLine(
                            tone.copy(alpha = 0.85f),
                            Offset(gx, gy),
                            Offset(bx, by),
                            strokeWidth = 1.6f * d,
                        )
                        // слово вместо буквы: «Дверь», «Балкон», «Вход» — понятно
                        // любому, кому скинут схему
                        drawIntoCanvas { canvas ->
                            val tp = android.graphics.Paint(labelPaint)
                            tp.textSize = 10.5f * d
                            tp.color = android.graphics.Color.WHITE
                            tp.isFakeBoldText = true
                            val word = kindWords.getOrElse(kind) { "?" }
                            val tw = tp.measureText(word)
                            val rr = android.graphics.RectF(
                                bx - tw / 2 - 7f * d,
                                by - 9.5f * d,
                                bx + tw / 2 + 7f * d,
                                by + 9.5f * d,
                            )
                            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            fill.color = tone.toArgb()
                            canvas.nativeCanvas.drawRoundRect(rr, 6f * d, 6f * d, fill)
                            val edge = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            edge.color = android.graphics.Color.rgb(11, 18, 32)
                            edge.style = android.graphics.Paint.Style.STROKE
                            edge.strokeWidth = 1.1f * d
                            canvas.nativeCanvas.drawRoundRect(rr, 6f * d, 6f * d, edge)
                            canvas.nativeCanvas.drawText(word, bx, by + 3.7f * d, tp)
                        }
                    }
                }
            }
        }

        // 6t. пороги: плитка проходит через дверной проём межкомнатной стены —
        // полоса видна на плане и входит в расчёт покупки
        vm.thresholdStrips.forEach { st ->
            val q = Path().apply {
                moveTo(sx(st.x0), sy(st.y0))
                lineTo(sx(st.x0 + st.ux * st.w), sy(st.y0 + st.uy * st.w))
                lineTo(sx(st.x0 + st.ux * st.w + st.nx * st.th), sy(st.y0 + st.uy * st.w + st.ny * st.th))
                lineTo(sx(st.x0 + st.nx * st.th), sy(st.y0 + st.ny * st.th))
                close()
            }
            drawPath(q, Acc2.copy(alpha = 0.14f))
            drawPath(
                q,
                Acc2.copy(alpha = 0.75f),
                style = Stroke(
                    1.2f * d,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 4f * d)),
                ),
            )
        }

        // 6d. калибровка подложки: два конца известного размера
        if (vm.calibMode) {
            val a = vm.calibA
            val b = vm.calibB
            listOfNotNull(a, b).forEach { p ->
                drawCircle(Color(0xFF0B1220), radius = 10f * d, center = Offset(sx(p.x), sy(p.y)))
                drawCircle(Acc2, radius = 5.5f * d, center = Offset(sx(p.x), sy(p.y)))
            }
            if (a != null && b != null) {
                drawLine(
                    Acc2,
                    Offset(sx(a.x), sy(a.y)),
                    Offset(sx(b.x), sy(b.y)),
                    strokeWidth = 2f * d,
                )
            }
        }

        // 6g. направляющие-уровень через выбранную вершину
        (vm.selection as? Selection.Vertex)?.let { sv ->
            vm.room.points.getOrNull(sv.i)?.let { p0 ->
                val others = vm.room.points.filterIndexed { i2, _ -> i2 != sv.i }
                val alignedX = others.any { kotlin.math.abs(it.x - p0.x) < 0.01 }
                val alignedY = others.any { kotlin.math.abs(it.y - p0.y) < 0.01 }
                val dash = PathEffect.dashPathEffect(floatArrayOf(9f * d, 7f * d))
                drawLine(
                    if (alignedX) Good else Color(0x668A97AC),
                    Offset(sx(p0.x), 0f),
                    Offset(sx(p0.x), size.height),
                    strokeWidth = (if (alignedX) 1.6f else 1f) * d,
                    pathEffect = dash,
                )
                drawLine(
                    if (alignedY) Good else Color(0x668A97AC),
                    Offset(0f, sy(p0.y)),
                    Offset(size.width, sy(p0.y)),
                    strokeWidth = (if (alignedY) 1.6f else 1f) * d,
                    pathEffect = dash,
                )
            }
        }

        // 7. подписи размеров
        if (vm.showDims) {
            // быстрая прикидка «на вскидку»: сколько плиток влазит по ширине (сверху)
            // и по высоте (слева) — 7 сверху × 5 слева ≈ 35, как считает мастер
            run {
                val rot = ((vm.pattern.rotationDeg % 360.0) + 360.0) % 360.0
                val herring = vm.pattern.type == PatternType.HERRINGBONE
                if (!herring &&
                    (kotlin.math.abs(rot % 90.0) < 0.01 || kotlin.math.abs(rot % 90.0 - 90.0) < 0.01)
                ) {
                    val swap = kotlin.math.abs(rot % 180.0 - 90.0) < 0.01
                    val stepW = (
                        (if (swap) vm.tile.heightMm else vm.tile.widthMm) +
                            kotlin.math.max(0.0, vm.tile.groutMm)
                        ) / 1000.0
                    val stepH = (
                        (if (swap) vm.tile.widthMm else vm.tile.heightMm) +
                            kotlin.math.max(0.0, vm.tile.groutMm)
                        ) / 1000.0
                    if (stepW > 1e-6 && stepH > 1e-6) {
                        val minx = pts.minOf { it.x }
                        val maxx = pts.maxOf { it.x }
                        val miny = pts.minOf { it.y }
                        val maxy = pts.maxOf { it.y }
                        val gM = kotlin.math.max(0.0, vm.tile.groutMm) / 1000.0
                        // целые клетки, попавшие в габарит целиком, с фазой узора;
                        // остаток — всё, что уйдёт в подрезку с обоих концов, в мм
                        fun axis(minC: Double, maxC: Double, off: Double, step: Double): Pair<Int, Int> {
                            val cell = step - gM
                            var k = floor((minC - off) / step).toInt() - 1
                            var full = 0
                            var guard = 0
                            while (off + k * step < maxC && guard < 4000) {
                                val s0 = off + k * step
                                if (s0 >= minC - 1e-6 && s0 + cell <= maxC + 1e-6) full++
                                k++
                                guard++
                            }
                            val rem = (maxC - minC - full * cell - (full - 1).coerceAtLeast(0) * gM) * 1000.0
                            return full to kotlin.math.max(0.0, rem).roundToInt()
                        }
                        val ax = axis(minx, maxx, vm.pattern.offsetX, stepW)
                        val ay = axis(miny, maxy, vm.pattern.offsetY, stepH)
                        fun pill(txt: String, cxp: Float, cyp: Float) {
                            drawIntoCanvas { canvas ->
                                val tp = android.graphics.Paint(labelPaint)
                                tp.textSize = 10f * d
                                tp.color = android.graphics.Color.rgb(255, 196, 92)
                                tp.isFakeBoldText = true
                                val tw = tp.measureText(txt)
                                val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                                bg.color = android.graphics.Color.argb(225, 15, 22, 34)
                                canvas.nativeCanvas.drawRoundRect(
                                    android.graphics.RectF(
                                        cxp - tw / 2 - 6f * d, cyp - 8.5f * d,
                                        cxp + tw / 2 + 6f * d, cyp + 8.5f * d,
                                    ),
                                    7f * d, 7f * d, bg,
                                )
                                canvas.nativeCanvas.drawText(txt, cxp, cyp + 3.4f * d, tp)
                            }
                        }
                        pill(
                            String.format(rulerAcrossTpl, ax.first, ax.second),
                            sx((minx + maxx) / 2),
                            sy(miny) + 16f * d,
                        )
                        pill(
                            String.format(rulerRowsTpl, ay.first, ay.second),
                            sx((minx + maxx) / 2),
                            sy(miny) + 36f * d,
                        )
                    }
                }
            }
            // дуги: одна метка на всю дугу вместо десятков крошечных размеров
            val arcRuns = vm.arcRuns
            if (arcRuns.isNotEmpty()) {
                val cxA = pts.sumOf { it.x } / pts.size
                val cyA = pts.sumOf { it.y } / pts.size
                drawIntoCanvas { canvas ->
                    val ap = android.graphics.Paint(labelPaint)
                    ap.textSize = 11f * d
                    for (run in arcRuns) {
                        val mp = pts[(run.startEdge + run.edges / 2) % pts.size]
                        val vx = mp.x - cxA
                        val vy = mp.y - cyA
                        val vl = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(1e-6)
                        canvas.nativeCanvas.drawText(
                            "R " + String.format(java.util.Locale.US, "%.2f", run.radiusM) + " · " +
                                String.format(java.util.Locale.US, "%.2f", run.lengthM) + " " + unitM,
                            sx(mp.x + vx / vl * 0.16),
                            sy(mp.y + vy / vl * 0.16) + 4f * d,
                            ap,
                        )
                    }
                }
            }
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                if (Arcs.edgeInArc(arcRuns, i, pts.size) != null) continue
                val sa = sp(a)
                val sb = sp(b)
                if ((sb - sa).getDistance() < 46f * d) continue
                val len = hypot(b.x - a.x, b.y - a.y)
                if (len <= 0.0) continue
                var nx = -(b.y - a.y) / len
                var ny = (b.x - a.x) / len
                val midW = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
                if (pointInPolygon(Pt(midW.x + nx * 0.08, midW.y + ny * 0.08), pts)) {
                    nx = -nx; ny = -ny
                }
                val at = Offset(
                    sx(midW.x) + (nx * 30.0 * d).toFloat(),
                    sy(midW.y) + (ny * 30.0 * d).toFloat(),
                )
                val text = String.format(Locale.getDefault(), "%.2f", len) + " " + unitM
                val axis = kotlin.math.abs(a.x - b.x) < 0.005 || kotlin.math.abs(a.y - b.y) < 0.005
                val tw = labelPaint.measureText(text)
                drawRoundRect(
                    Color(0xE0090F1A),
                    topLeft = Offset(at.x - tw / 2f - 6f * d, at.y - 9.5f * d),
                    size = Size(tw + 12f * d, 19f * d),
                    cornerRadius = CornerRadius(6f * d, 6f * d),
                )
                drawIntoCanvas { canvas ->
                    val lp2 = if (axis) {
                        android.graphics.Paint(labelPaint).apply {
                            color = android.graphics.Color.rgb(72, 213, 151)
                        }
                    } else {
                        labelPaint
                    }
                    canvas.nativeCanvas.drawText(text, at.x, at.y + 4f * d, lp2)
                }
            }
        }

        // 7c. редактор длины стены: сама стена и её концы A и B подсвечены,
        // чтобы выбор «какой конец двигать» был очевиден
        if (vm.edgeEditIndex in pts.indices) {
            val ea = pts[vm.edgeEditIndex]
            val eb = pts[(vm.edgeEditIndex + 1) % pts.size]
            drawLine(Acc2, sp(ea), sp(eb), strokeWidth = 3.2f * d)
            drawIntoCanvas { canvas ->
                val tp = android.graphics.Paint(labelPaint)
                tp.textSize = 10f * d
                listOf(ea to "A", eb to "B").forEach { (pp, lbl) ->
                    drawCircle(Color(0xFF0B1220), radius = 11f * d, center = sp(pp))
                    drawCircle(Acc2, radius = 9f * d, center = sp(pp), style = Stroke(2f * d))
                    canvas.nativeCanvas.drawText(lbl, sp(pp).x, sp(pp).y + 3.5f * d, tp)
                }
            }
        }

        // 6f. плитки под мебелью приглушены: скрытая зона — сюда прячь куски
        if (vm.showFurniture && vm.hiddenTiles.isNotEmpty()) {
            clipPath(roomPath) {
                vm.hiddenTiles.forEach { ti ->
                    vm.layout.tiles.getOrNull(ti)?.let { t ->
                        val p = Path()
                        t.corners.forEachIndexed { k, c2 ->
                            if (k == 0) p.moveTo(sx(c2.x), sy(c2.y)) else p.lineTo(sx(c2.x), sy(c2.y))
                        }
                        p.close()
                        drawPath(p, Color(0xFF0B1220).copy(alpha = 0.35f))
                    }
                }
            }
        }

        // 7d. стена с предупреждением о полоске — подсвечена по тапу на плашку
        if (vm.warnEdge in pts.indices) {
            val wa = pts[vm.warnEdge]
            val wb = pts[(vm.warnEdge + 1) % pts.size]
            drawLine(Warn, sp(wa), sp(wb), strokeWidth = 4.5f * d)
        }

        // 7e. магниты узора: направляющая показывает, к чему прилипла раскладка
        if (vm.patternSnapX != 0 || vm.patternSnapY != 0) {
            val minx = pts.minOf { it.x }
            val maxx = pts.maxOf { it.x }
            val miny = pts.minOf { it.y }
            val maxy = pts.maxOf { it.y }
            val dashG = PathEffect.dashPathEffect(floatArrayOf(8f * d, 6f * d))
            val gx = when (vm.patternSnapX) {
                1 -> if (vm.patternHintX.isNaN()) null else vm.patternHintX
                2, 3 -> (minx + maxx) / 2
                4 -> minx
                5 -> maxx
                else -> null
            }
            gx?.let {
                drawLine(
                    if (vm.patternSnapX == 1) Warn else Good,
                    Offset(sx(it), sy(miny)), Offset(sx(it), sy(maxy)),
                    strokeWidth = 1.6f * d, pathEffect = dashG,
                )
            }
            val gy = when (vm.patternSnapY) {
                1 -> if (vm.patternHintY.isNaN()) null else vm.patternHintY
                2, 3 -> (miny + maxy) / 2
                4 -> miny
                5 -> maxy
                else -> null
            }
            gy?.let {
                drawLine(
                    if (vm.patternSnapY == 1) Warn else Good,
                    Offset(sx(minx), sy(it)), Offset(sx(maxx), sy(it)),
                    strokeWidth = 1.6f * d, pathEffect = dashG,
                )
            }
        }

        // 7b. водяной знак: угол, где потом будет логотип мастера
        if (Entitlements.watermark) {
            drawIntoCanvas { canvas ->
                val wp = android.graphics.Paint(labelPaint)
                wp.textAlign = android.graphics.Paint.Align.LEFT
                wp.textSize = 10.5f * d
                wp.color = android.graphics.Color.argb(54, 233, 238, 246)
                canvas.nativeCanvas.drawText(wmText, 14f * d, size.height - 13f * d, wp)
            }
            drawRect(
                Acc.copy(alpha = 0.18f),
                topLeft = Offset(6f * d, size.height - 22f * d),
                size = Size(3f * d, 12f * d),
            )
        }

        // 7c. режим черчения: полилиния, точки, длины сегментов
        if (vm.drawMode && vm.drawPts.isNotEmpty()) {
            val dpts = vm.drawPts
            val lineC = if (vm.drawOverlaps()) Warn else Acc
            if (dpts.size >= 2) {
                val pl = Path().apply {
                    dpts.forEachIndexed { i2, p2 ->
                        if (i2 == 0) moveTo(sx(p2.x), sy(p2.y)) else lineTo(sx(p2.x), sy(p2.y))
                    }
                }
                drawPath(pl, lineC, style = Stroke(2.2f * d))
                // пунктир к первой точке — куда замыкать
                if (dpts.size >= 3) {
                    val last = dpts.last()
                    val first = dpts.first()
                    val dash = Path().apply {
                        moveTo(sx(last.x), sy(last.y))
                        lineTo(sx(first.x), sy(first.y))
                    }
                    drawPath(
                        dash,
                        lineC.copy(alpha = 0.55f),
                        style = Stroke(
                            1.5f * d,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * d, 6f * d)),
                        ),
                    )
                }
                // длины сегментов
                drawIntoCanvas { canvas ->
                    val lp3 = android.graphics.Paint(labelPaint)
                    lp3.textSize = 9.5f * d
                    for (i2 in 0 until dpts.size - 1) {
                        val a2 = dpts[i2]
                        val b2 = dpts[i2 + 1]
                        val ln = hypot(b2.x - a2.x, b2.y - a2.y)
                        if (ln < 0.05) continue
                        canvas.nativeCanvas.drawText(
                            String.format(Locale.getDefault(), "%.2f", ln),
                            (sx(a2.x) + sx(b2.x)) / 2f,
                            (sy(a2.y) + sy(b2.y)) / 2f - 6f * d,
                            lp3,
                        )
                    }
                }
            }
            dpts.forEachIndexed { i2, p2 ->
                val first = i2 == 0
                drawCircle(
                    Color(0xFF0B1220),
                    radius = (if (first) 10f else 7f) * d,
                    center = Offset(sx(p2.x), sy(p2.y)),
                )
                drawCircle(
                    if (first) Good else lineC,
                    radius = (if (first) 6.5f else 4.2f) * d,
                    center = Offset(sx(p2.x), sy(p2.y)),
                )
            }
        }

        // 7z. рамки зон: выбранная — акцентом, у неё ручка размера
        vm.zones.forEachIndexed { i, z ->
            val tl = Offset(sx(z.x), sy(z.y))
            val br = Offset(sx(z.x + z.w), sy(z.y + z.h))
            val on = i == vm.activeZone
            drawRect(
                if (on) Acc2 else Color(0xFF8A97AC),
                topLeft = tl,
                size = Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(
                    (if (on) 2.4f else 1.4f) * d,
                    pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(8f * d, 6f * d)),
                ),
            )
            if (on) {
                drawCircle(Color(0xFF0B1220), radius = 9f * d, center = br)
                drawCircle(Acc2, radius = 5f * d, center = br)
            }
        }

        // 7op. режим «+ Дверь/Окно»: стены подсвечены — видно, куда тапать
        if (vm.placeOpeningKind >= 0 && pts.size >= 2) {
            val dashOp = PathEffect.dashPathEffect(floatArrayOf(12f * d, 8f * d), 0f)
            for (i in pts.indices) {
                val a = sp(pts[i])
                val b = sp(pts[(i + 1) % pts.size])
                drawLine(Acc2.copy(alpha = 0.85f), a, b, strokeWidth = 5f * d, pathEffect = dashOp)
            }
        }

        // 7vo. показ клиенту: метка, чтобы никто не искал, почему план не правится
        if (vm.viewOnly) {
            drawIntoCanvas { canvas ->
                val vp = android.graphics.Paint(labelPaint)
                vp.textSize = 11.5f * d
                vp.color = android.graphics.Color.rgb(0x7A, 0xA7, 0xFF)
                canvas.nativeCanvas.drawText(viewOnlyText, size.width / 2f, size.height - 12f * d, vp)
            }
        }

        // 7fh. до самого первого касания — одна строка, что делать дальше.
        // Не режим и не обучение: первый же жест гасит её навсегда.
        if (UiPrefs.firstTouch) {
            drawIntoCanvas { canvas ->
                val hp = android.graphics.Paint(labelPaint)
                hp.textSize = 12.5f * d
                hp.color = android.graphics.Color.rgb(0xA6, 0xB2, 0xC6)
                val cx = size.width / 2f
                canvas.nativeCanvas.drawText(firstHintText, cx, 26f * d, hp)
            }
        }

        // 8. ручки режима «Комната»
        if (vm.roomMode) {
            for (i in pts.indices) {
                if (vm.edgeOnArc(i)) continue
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val sa = sp(a)
                val sb = sp(b)
                if ((sb - sa).getDistance() < 56f * d) continue
                val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)
                drawCircle(Panel2.copy(alpha = 0.9f), 8f * d, mid)
                drawCircle(Acc2, 8f * d, mid, style = Stroke(1.4f * d))
                drawLine(Acc2, Offset(mid.x - 4f * d, mid.y), Offset(mid.x + 4f * d, mid.y), 1.6f * d)
                drawLine(Acc2, Offset(mid.x, mid.y - 4f * d), Offset(mid.x, mid.y + 4f * d), 1.6f * d)
            }
            val selV = (vm.selection as? Selection.Vertex)?.i
            pts.forEachIndexed { i, p ->
                if (vm.vertexOnArc(i)) return@forEachIndexed // точки дуги заперты — ручек нет
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
