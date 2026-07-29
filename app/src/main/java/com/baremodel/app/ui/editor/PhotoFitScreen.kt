package com.baremodel.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.BaIcons
import com.baremodel.app.ui.theme.CanvasBg
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.core.TileClass
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Проекция единичного квадрата (0..1 × 0..1) в произвольный четырёхугольник —
 * классическая гомография «square to quad». Углы квадрата по часовой:
 * (0,0)→q0, (1,0)→q1, (1,1)→q2, (0,1)→q3.
 */
private class Homography(q: List<Offset>) {
    private val a: Float
    private val b: Float
    private val c: Float
    private val d: Float
    private val e: Float
    private val f: Float
    private val g: Float
    private val h: Float

    init {
        val x0 = q[0].x; val y0 = q[0].y
        val x1 = q[1].x; val y1 = q[1].y
        val x2 = q[2].x; val y2 = q[2].y
        val x3 = q[3].x; val y3 = q[3].y
        val sx = x0 - x1 + x2 - x3
        val sy = y0 - y1 + y2 - y3
        if (abs(sx) < 1e-6f && abs(sy) < 1e-6f) {
            a = x1 - x0; b = x2 - x1; c = x0
            d = y1 - y0; e = y2 - y1; f = y0
            g = 0f; h = 0f
        } else {
            val dx1 = x1 - x2; val dx2 = x3 - x2
            val dy1 = y1 - y2; val dy2 = y3 - y2
            val den = dx1 * dy2 - dx2 * dy1
            g = (sx * dy2 - dx2 * sy) / den
            h = (dx1 * sy - sx * dy1) / den
            a = x1 - x0 + g * x1
            b = x3 - x0 + h * x3
            c = x0
            d = y1 - y0 + g * y1
            e = y3 - y0 + h * y3
            f = y0
        }
    }

    fun map(u: Float, v: Float): Offset {
        val w = g * u + h * v + 1f
        return Offset((a * u + b * v + c) / w, (d * u + e * v + f) / w)
    }
}

private const val MAX_FIT_MESH = 420

@Composable
fun PhotoFitScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadFitPhoto(context, uri)
    }
    val hint = stringResource(R.string.fit_hint)
    val unitM = stringResource(R.string.unit_m)
    val wmBrand = stringResource(R.string.wm_brand)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val photo = vm.fitPhoto
            if (photo == null) {
                Column(
                    Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.fit_empty),
                        color = Sub,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    IconChip(BaIcons.Camera, stringResource(R.string.fit_load), selected = true) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .background(CanvasBg)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@awaitEachGesture
                                // ближайший угол в пределах 42 dp
                                var best = -1
                                var bestD = 42f * density
                                vm.fitQuad.forEachIndexed { i, q ->
                                    val p = Offset(q.x * w, q.y * h)
                                    val dd = (p - down.position).getDistance()
                                    if (dd < bestD) { bestD = dd; best = i }
                                }
                                if (best < 0) return@awaitEachGesture
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    vm.moveFitCorner(best, Offset(ch.position.x / w, ch.position.y / h))
                                    ch.consume()
                                    if (ev.changes.none { it.pressed }) break
                                }
                            }
                        },
                ) {
                    val d = density
                    // фото вписывается в кадр целиком
                    val pw = photo.width.toFloat()
                    val ph = photo.height.toFloat()
                    val k = min(size.width / pw, size.height / ph)
                    val dw = pw * k
                    val dh = ph * k
                    val ox = (size.width - dw) / 2f
                    val oy = (size.height - dh) / 2f
                    drawImage(
                        photo,
                        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                    )

                    // углы рамки заданы в долях всего кадра
                    fun toScreen(p: Offset) = Offset(p.x * size.width, p.y * size.height)
                    val quad = vm.fitQuad.map { toScreen(it) }
                    val hom = Homography(quad)

                    // мир → единичный квадрат по габариту комнаты
                    val pts = vm.room.points
                    if (pts.size >= 3) {
                        val minx = pts.minOf { it.x }.toFloat()
                        val maxx = pts.maxOf { it.x }.toFloat()
                        val miny = pts.minOf { it.y }.toFloat()
                        val maxy = pts.maxOf { it.y }.toFloat()
                        val spx = (maxx - minx).coerceAtLeast(1e-4f)
                        val spy = (maxy - miny).coerceAtLeast(1e-4f)
                        fun world(x: Double, y: Double): Offset =
                            hom.map((x.toFloat() - minx) / spx, (y.toFloat() - miny) / spy)

                        val roomPath = Path().apply {
                            pts.forEachIndexed { i, p ->
                                val s = world(p.x, p.y)
                                if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                            }
                            close()
                        }

                        val alpha = vm.fitAlpha
                        val tiles = vm.layout.tiles
                        val decorSet = vm.decorIdx
                        val panelCols = vm.decor.panelCols.coerceAtLeast(1)
                        val panelRows = vm.decor.panelRows.coerceAtLeast(1)
                        val tileBmp = if (tiles.size <= MAX_FIT_MESH) vm.tileImage?.asAndroidBitmap() else null
                        val decorBmp = if (tiles.size <= MAX_FIT_MESH) {
                            (vm.decorImage ?: vm.tileImage)?.asAndroidBitmap()
                        } else null
                        val meshPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                            this.alpha = (alpha * 255).toInt()
                        }
                        val groutC = Color(0xFF2A3140).copy(alpha = alpha * 0.9f)

                        clipPath(roomPath) {
                            val slices = mutableMapOf<Pair<Int, Int>, android.graphics.Bitmap>()
                            tiles.forEachIndexed { ti, t ->
                                val q = t.corners.map { world(it.x, it.y) }
                                val isDecor = ti in decorSet
                                val ownColor = vm.colorOfTile(t)
                                val cell = if (decorBmp != null && ownColor == null) vm.panelCell(t) else null
                                val bmp = if (ownColor != null) {
                                    null
                                } else if (cell != null && decorBmp != null) {
                                    slices.getOrPut(cell) {
                                        val sw = (decorBmp.width / panelCols).coerceAtLeast(1)
                                        val sh = (decorBmp.height / panelRows).coerceAtLeast(1)
                                        android.graphics.Bitmap.createBitmap(
                                            decorBmp,
                                            (cell.first * sw).coerceIn(0, decorBmp.width - sw),
                                            (cell.second * sh).coerceIn(0, decorBmp.height - sh),
                                            sw,
                                            sh,
                                        )
                                    }
                                } else if (isDecor) {
                                    decorBmp
                                } else {
                                    tileBmp
                                }
                                if (bmp != null) {
                                    drawIntoCanvas { canvas ->
                                        val verts = floatArrayOf(
                                            q[0].x, q[0].y,
                                            q[1].x, q[1].y,
                                            q[3].x, q[3].y,
                                            q[2].x, q[2].y,
                                        )
                                        canvas.nativeCanvas.drawBitmapMesh(bmp, 1, 1, verts, 0, null, 0, meshPaint)
                                    }
                                } else {
                                    val hs = abs(sin(t.rect.x * 127.1 + t.rect.y * 311.7) * 43758.5453) % 1.0
                                    val dk = ((hs - 0.5) * 0.10).toFloat()
                                    val base = when {
                                        ownColor != null -> Color(ownColor)
                                        isDecor -> Color(0xFFE8DFD2)
                                        else -> vm.tileColor
                                    }
                                    val col = Color(
                                        (base.red + dk).coerceIn(0f, 1f),
                                        (base.green + dk).coerceIn(0f, 1f),
                                        (base.blue + dk).coerceIn(0f, 1f),
                                        alpha,
                                    )
                                    val p = Path().apply {
                                        moveTo(q[0].x, q[0].y)
                                        lineTo(q[1].x, q[1].y)
                                        lineTo(q[2].x, q[2].y)
                                        lineTo(q[3].x, q[3].y)
                                        close()
                                    }
                                    drawPath(p, col)
                                }
                                val p = Path().apply {
                                    moveTo(q[0].x, q[0].y)
                                    lineTo(q[1].x, q[1].y)
                                    lineTo(q[2].x, q[2].y)
                                    lineTo(q[3].x, q[3].y)
                                    close()
                                }
                                if (t.cls == TileClass.CUT && vm.showCuts) {
                                    drawPath(p, Color(0xFFFFB454).copy(alpha = alpha * 0.6f), style = Stroke(1.2f * d))
                                } else {
                                    drawPath(p, groutC, style = Stroke(1f * d))
                                }
                            }
                        }

                        // контур пола
                        drawPath(roomPath, Acc.copy(alpha = 0.85f), style = Stroke(2f * d))

                        // размеры комнаты на наложении — понятно, что и в каком масштабе лежит
                        drawIntoCanvas { canvas ->
                            val dp2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb(235, 207, 224, 255)
                                textSize = 11.5f * d
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            }
                            val txt = String.format(
                                java.util.Locale.getDefault(),
                                "%.2f × %.2f ",
                                (maxx - minx),
                                (maxy - miny),
                            ) + unitM
                            val topMid = Offset(
                                (quad[0].x + quad[1].x) / 2f,
                                (quad[0].y + quad[1].y) / 2f - 10f * d,
                            )
                            canvas.nativeCanvas.drawText(txt, topMid.x, topMid.y, dp2)
                        }
                    }

                    // ручки углов
                    quad.forEachIndexed { i, p ->
                        drawCircle(Color(0x8C04060A), radius = 13f * d, center = p)
                        drawCircle(if (i == 0) Acc2 else Acc, radius = 7f * d, center = p)
                        drawCircle(Color.White, radius = 2.6f * d, center = p)
                    }

                    // водяной знак бесплатной версии
                    if (Entitlements.watermark) {
                        drawIntoCanvas { canvas ->
                            val wp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb(56, 233, 238, 246)
                                textSize = 10.5f * d
                            }
                            canvas.nativeCanvas.drawText(wmBrand, 14f * d, size.height - 34f * d, wp)
                        }
                    }

                    // подсказка
                    drawIntoCanvas { canvas ->
                        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(150, 138, 151, 172)
                            textSize = 11f * d
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText(hint, size.width / 2f, size.height - 12f * d, tp)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Panel)
                .padding(16.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconChip(BaIcons.Cube, stringResource(R.string.ar_open), selected = true) {
                    vm.openAr(context)
                }
                IconChip(BaIcons.Camera, stringResource(R.string.fit_load), vm.fitPhoto != null) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (vm.fitPhoto != null) {
                    Chip(stringResource(R.string.clear)) { vm.clearFitPhoto() }
                    Chip(stringResource(R.string.fit_reset)) { vm.resetFitQuad() }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.fit_alpha),
                color = Dim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5f, 0.8f, 1f).forEach { a ->
                    Chip("${(a * 100).toInt()}%", abs(vm.fitAlpha - a) < 0.01f) { vm.updateFitAlpha(a) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.fit_note), color = Dim, fontSize = 10.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.ar_disclosure), color = Dim, fontSize = 9.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.credit),
                color = Dim,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
