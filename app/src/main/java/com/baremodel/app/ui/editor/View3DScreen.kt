package com.baremodel.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baremodel.app.R
import com.baremodel.app.ar.renderFloorBitmap
import com.baremodel.app.ui.theme.Acc
import com.baremodel.app.ui.theme.Acc2
import com.baremodel.app.ui.theme.CanvasBg
import com.baremodel.app.ui.theme.Dim
import com.baremodel.app.ui.theme.Good
import com.baremodel.app.ui.theme.LineC
import com.baremodel.app.ui.theme.Panel
import com.baremodel.app.ui.theme.Panel2
import com.baremodel.app.ui.theme.Panel3
import com.baremodel.app.ui.theme.Sub
import com.baremodel.app.ui.theme.Txt
import com.baremodel.app.ui.theme.Warn
import com.baremodel.core.CutNumbering
import com.baremodel.core.Finish
import com.baremodel.core.Pt
import com.baremodel.core.TileClass
import com.baremodel.core.TilingEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Точка в пространстве: x и z — план, y — высота. */
private data class V3(val x: Float, val y: Float, val z: Float)

/** Грань для отрисовки: вершины, цвет и глубина для сортировки. */
private class Face(
    val pts: List<Offset>,
    val color: Color,
    val depth: Float,
    val outline: Color? = null,
    val bmp: android.graphics.Bitmap? = null,
)

private const val MAX_TEXTURED_3D = 420

private const val MAX_TILES_3D = 1400

@Composable
fun View3DScreen(vm: EditorViewModel) {
    var yaw by rememberSaveable { mutableFloatStateOf(0.6f) }
    var pitch by rememberSaveable { mutableFloatStateOf(0.62f) }
    var dist by rememberSaveable { mutableFloatStateOf(1.05f) }
    var lowWalls by rememberSaveable { mutableStateOf(false) }
    var wallsGhost by rememberSaveable { mutableStateOf(false) }
    // выбранная плитка: индекс комнаты (-1 = активная) и индекс плитки
    var pickedRoom by remember { mutableStateOf(-1) }
    var pickedTile by remember { mutableStateOf(-1) }
    // экранные четырёхугольники плиток пола — заполняются при отрисовке, нужны для тапа
    val tileQuads = remember { mutableListOf<Triple<Int, Int, List<Offset>>>() }

    // раскладка плитки на стенах считается один раз на изменение данных, а не каждый кадр
    val wallLayouts = remember(vm.room, vm.tile, vm.pattern, vm.wallHeightM, vm.finishes, vm.openings) {
        vm.model.walls
            .filter { vm.finishOf(it.id) == Finish.TILE }
            .associate { it.id to vm.surfaceLayout(it.id) }
    }

    // пол каждой комнаты рисуется готовой картинкой: клип по контуру уже внутри,
    // поэтому подрезанные плитки больше не вылезают за стены
    val activeFloor = remember(
        vm.room, vm.tile, vm.pattern, vm.tileColor, vm.variation,
        vm.decorIdx, vm.tileImage, vm.decorImage,
        vm.panelOn, vm.panelRX, vm.panelRY, vm.decor, vm.zones, vm.tileColors, vm.showCuts,
    ) {
        renderFloorBitmap(
            points = vm.room.points,
            tiles = vm.layout.tiles,
            decorIdx = vm.decorIdx,
            tileBmp = vm.tileImage?.asAndroidBitmap(),
            decorBmp = (vm.decorImage ?: vm.tileImage)?.asAndroidBitmap(),
            colorArgb = vm.tileColor.toArgb(),
            variation = vm.variation,
            panel = vm.panelInfo(),
            extra = vm.zoneLayers(),
            colorOf = { t -> vm.colorOfTile(t) },
            cutNumbers = vm.showCuts,
            cutInfo = vm.cutInfo,
        )
    }
    val otherLayouts = remember(vm.rooms, vm.activeRoom) {
        vm.rooms.mapIndexed { i, r ->
            if (i == vm.activeRoom) null else TilingEngine.build(r.spec, r.tile, r.pattern)
        }
    }
    // единая нумерация подрезок для неактивных комнат — та же, что на их полу
    val otherCutInfo = remember(vm.rooms, vm.activeRoom) {
        vm.rooms.mapIndexed { i, r ->
            otherLayouts.getOrNull(i)?.let { CutNumbering.compute(r.spec, it) }
        }
    }
    val otherFloors = remember(vm.rooms, vm.activeRoom, vm.showCuts) {
        vm.rooms.mapIndexed { i, r ->
            val lay = otherLayouts.getOrNull(i)
            if (i == vm.activeRoom || lay == null) {
                null
            } else {
                renderFloorBitmap(
                    points = r.spec.points,
                    tiles = lay.tiles,
                    decorIdx = emptySet(),
                    tileBmp = null,
                    decorBmp = null,
                    colorArgb = if (r.colorArgb != -1) r.colorArgb else 0xFFE8EAF0.toInt(),
                    variation = r.variation,
                    cutNumbers = vm.showCuts,
                    cutInfo = otherCutInfo.getOrNull(i),
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(CanvasBg)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var pinching = false
                            var moved = false
                            var d0 = 1f
                            var base = dist
                            while (true) {
                                val ev = awaitPointerEvent()
                                val act = ev.changes.filter { it.pressed }
                                if (act.size >= 2) {
                                    val dd = max(1f, (act[0].position - act[1].position).getDistance())
                                    if (!pinching) { pinching = true; d0 = dd; base = dist }
                                    else dist = (base * d0 / dd).coerceIn(0.55f, 4f)
                                } else if (act.size == 1 && !pinching) {
                                    val c = act[0]
                                    val dx = c.position.x - c.previousPosition.x
                                    val dy = c.position.y - c.previousPosition.y
                                    if (kotlin.math.abs(dx) > 2f || kotlin.math.abs(dy) > 2f) moved = true
                                    yaw -= dx * 0.008f
                                    pitch = (pitch + dy * 0.006f).coerceIn(0.12f, 1.45f)
                                }
                                ev.changes.forEach { it.consume() }
                                if (act.isEmpty()) break
                            }
                            // короткий тап: выбираем плитку под пальцем
                            if (!pinching && !moved) {
                                val p = down.position
                                var foundRoom = -2
                                var found = -1
                                for ((rIdx, idx, quad) in tileQuads.asReversed()) {
                                    if (quad.size == 4 && pointInQuad(p, quad)) {
                                        foundRoom = rIdx
                                        found = idx
                                        break
                                    }
                                }
                                if (found == pickedTile && foundRoom == pickedRoom) {
                                    pickedTile = -1
                                    pickedRoom = -1
                                } else {
                                    pickedTile = found
                                    pickedRoom = if (foundRoom == -2) -1 else foundRoom
                                }
                            }
                        }
                    },
            ) {
                val pts = vm.room.points
                if (pts.size < 3) return@Canvas
                val boundPts = pts + vm.rooms
                    .filterIndexed { i, _ -> i != vm.activeRoom }
                    .flatMap { it.spec.points }

                val minx = boundPts.minOf { it.x }.toFloat()
                val maxx = boundPts.maxOf { it.x }.toFloat()
                val minz = boundPts.minOf { it.y }.toFloat()
                val maxz = boundPts.maxOf { it.y }.toFloat()
                val cx = (minx + maxx) / 2f
                val cz = (minz + maxz) / 2f
                val span = max(maxx - minx, maxz - minz).coerceAtLeast(1f)
                val wallH = vm.wallHeightM.toFloat()
                val wallDrawH = if (lowWalls) wallH * 0.32f else wallH

                // камера подбирается так, чтобы комната заполняла кадр
                val fitSpan = max(span, wallH * 1.5f)
                val radius = fitSpan * 1.32f * dist
                val cyaw = cos(yaw); val syaw = sin(yaw)
                val cp = cos(pitch); val sp = sin(pitch)
                val focal = min(size.width, size.height) * 0.95f
                val ox = size.width / 2f
                val oy = size.height / 2f + size.height * 0.06f

                // положение камеры в мире — нужно для отсечения ближних стен
                val camX = cx + radius * cp * syaw
                val camY = radius * sp
                val camZ = cz + radius * cp * cyaw

                fun project(p: V3): Pair<Offset, Float> {
                    val dx = p.x - cx
                    val dy = p.y
                    val dz = p.z - cz
                    val rx = dx * cyaw - dz * syaw
                    val rz = dx * syaw + dz * cyaw
                    val ry = dy * cp - rz * sp
                    val rz2 = dy * sp + rz * cp
                    val vz = radius - rz2
                    if (vz < 0.05f) return Offset(Float.NaN, Float.NaN) to -1f
                    return Offset(ox + focal * rx / vz, oy - focal * ry / vz) to vz
                }

                // фон: мягкий градиент вместо плоской заливки
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF0C1119),
                        0.55f to Color(0xFF070A10),
                        1f to Color(0xFF05070B),
                    ),
                    size = size,
                )

                val faces = ArrayList<Face>(1400)

                fun addFace(
                    v: List<V3>,
                    color: Color,
                    outline: Color? = null,
                    bmp: android.graphics.Bitmap? = null,
                    forceBack: Boolean = false,
                ) {
                    val proj = v.map { project(it) }
                    if (proj.any { it.second < 0f }) return
                    val depth = if (forceBack) {
                        Float.MAX_VALUE
                    } else {
                        proj.sumOf { it.second.toDouble() }.toFloat() / proj.size
                    }
                    faces.add(Face(proj.map { it.first }, color, depth, outline, bmp))
                }

                fun shade(c: Color, k: Float) = Color(
                    (c.red * k).coerceIn(0f, 1f),
                    (c.green * k).coerceIn(0f, 1f),
                    (c.blue * k).coerceIn(0f, 1f),
                    c.alpha,
                )

                // пол: картинка на перспективной сетке 10×8
                val floorPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                fun drawFloor(fb: Triple<Bitmap, Float, Float>?, pts0: List<Pt>, alpha: Int) {
                    val bmp = fb?.first ?: return
                    if (pts0.size < 3 || bmp.isRecycled) return
                    val fMinX = pts0.minOf { it.x }.toFloat()
                    val fMaxX = pts0.maxOf { it.x }.toFloat()
                    val fMinZ = pts0.minOf { it.y }.toFloat()
                    val fMaxZ = pts0.maxOf { it.y }.toFloat()
                    val basePath = Path()
                    pts0.forEachIndexed { i3, p3 ->
                        val s3 = project(V3(p3.x.toFloat(), 0f, p3.y.toFloat()))
                        if (s3.second < 0f || s3.first.x.isNaN()) return
                        if (i3 == 0) basePath.moveTo(s3.first.x, s3.first.y)
                        else basePath.lineTo(s3.first.x, s3.first.y)
                    }
                    basePath.close()
                    drawPath(basePath, Color(0xFF465065).copy(alpha = alpha / 255f))
                    val gx = 10
                    val gz = 8
                    val verts = FloatArray((gx + 1) * (gz + 1) * 2)
                    var vi = 0
                    for (rz3 in 0..gz) {
                        val z3 = fMinZ + (fMaxZ - fMinZ) * rz3 / gz
                        for (cx3 in 0..gx) {
                            val x3 = fMinX + (fMaxX - fMinX) * cx3 / gx
                            val pr = project(V3(x3, 0.001f, z3))
                            if (pr.second < 0f || pr.first.x.isNaN()) return
                            verts[vi++] = pr.first.x
                            verts[vi++] = pr.first.y
                        }
                    }
                    floorPaint.alpha = alpha
                    drawIntoCanvas { cnv ->
                        cnv.nativeCanvas.drawBitmapMesh(bmp, gx, gz, verts, 0, null, 0, floorPaint)
                    }
                }
                // экранные контуры плиток — для выбора пальцем
                tileQuads.clear()
                if (vm.layout.tiles.size <= 900) {
                    vm.layout.tiles.forEachIndexed { ti, t ->
                        val pr = t.corners.map { project(V3(it.x.toFloat(), 0.002f, it.y.toFloat())) }
                        if (pr.all { it.second > 0f && !it.first.x.isNaN() }) {
                            tileQuads.add(Triple(-1, ti, pr.map { it.first }))
                        }
                    }
                }
                otherLayouts.forEachIndexed { ri3, lay ->
                    if (lay == null || lay.tiles.size > 900) return@forEachIndexed
                    lay.tiles.forEachIndexed { ti, t ->
                        val pr = t.corners.map { project(V3(it.x.toFloat(), 0.002f, it.y.toFloat())) }
                        if (pr.all { it.second > 0f && !it.first.x.isNaN() }) {
                            tileQuads.add(Triple(ri3, ti, pr.map { it.first }))
                        }
                    }
                }

                drawFloor(activeFloor, vm.room.points, 255)
                vm.rooms.forEachIndexed { fi, fr ->
                    if (fi != vm.activeRoom) drawFloor(otherFloors.getOrNull(fi), fr.spec.points, 165)
                }


                // плитки пола
                val tiles = vm.layout.tiles
                val decorSet = vm.decorIdx
                // выбранная плитка: подсветка прямо на полу
                if (pickedTile >= 0) {
                    tileQuads.firstOrNull {
                        it.second == pickedTile && it.first == pickedRoom
                    }?.let { (_, _, q) ->
                        val path = Path().apply {
                            moveTo(q[0].x, q[0].y)
                            lineTo(q[1].x, q[1].y)
                            lineTo(q[2].x, q[2].y)
                            lineTo(q[3].x, q[3].y)
                            close()
                        }
                        drawPath(path, Acc.copy(alpha = 0.22f))
                        drawPath(path, Acc2, style = Stroke(2.4f))
                    }
                }

                // стены: рисуем только дальние, ближние убираем, чтобы видеть комнату внутри
                for (i in pts.indices) {
                    val a = pts[i]
                    val b = pts[(i + 1) % pts.size]
                    val ax = a.x.toFloat(); val az = a.y.toFloat()
                    val bx = b.x.toFloat(); val bz = b.y.toFloat()
                    val len = hypot(bx - ax, bz - az)
                    if (len < 1e-4f) continue
                    // внешняя нормаль
                    var nx = (bz - az) / len
                    var nz = -(bx - ax) / len
                    val mx = (ax + bx) / 2f
                    val mz = (az + bz) / 2f
                    val inside = Pt((mx + nx * 0.05f).toDouble(), (mz + nz * 0.05f).toDouble())
                    if (com.baremodel.core.pointInPolygon(inside, pts)) { nx = -nx; nz = -nz }
                    // стена между камерой и комнатой — пропускаем
                    if (nx * (camX - mx) + nz * (camZ - mz) > 0f) continue
                    // перегородка между комнатами — полупрозрачная, обзор не заслоняет
                    val outP = Pt((mx + nx * 0.07f).toDouble(), (mz + nz * 0.07f).toDouble())
                    val isInternal = vm.rooms.withIndex().any { (ri3, r3) ->
                        ri3 != vm.activeRoom && r3.spec.points.size >= 3 &&
                            com.baremodel.core.pointInPolygon(outP, r3.spec.points)
                    }
                    val tone = 0.42f + 0.16f * abs(nz)
                    val wallId = "wall-" + (i + 1)
                    val fin = vm.finishOf(wallId)
                    val baseCol = when (fin) {
                        Finish.TILE -> Color(0xFF8E99AB)
                        Finish.WALLPAPER -> Color(0xFFB6A894)
                        Finish.PAINT -> Color(0xFFA8B2C2)
                        Finish.NONE -> Color(0xFF6E7889)
                    }
                    addFace(
                        listOf(
                            V3(ax, 0f, az), V3(bx, 0f, bz),
                            V3(bx, wallDrawH, bz), V3(ax, wallDrawH, az),
                        ),
                        shade(baseCol, tone)
                            .copy(alpha = if (isInternal || wallsGhost) 0.40f else 1f),
                        LineC,
                    )

                    // верх стены: полоса толщины — стена выглядит настоящей, а не бумажной
                    val th = vm.wallThicknessOf(wallId).toFloat()
                    if (th > 0.001f) {
                        addFace(
                            listOf(
                                V3(ax, wallDrawH, az),
                                V3(bx, wallDrawH, bz),
                                V3(bx + nx * th, wallDrawH, bz + nz * th),
                                V3(ax + nx * th, wallDrawH, az + nz * th),
                            ),
                            shade(baseCol, tone + 0.22f)
                                .copy(alpha = if (isInternal || wallsGhost) 0.40f else 1f),
                            LineC,
                        )
                    }

                    // плитка на стене
                    val ux = (bx - ax) / len
                    val uz = (bz - az) / len
                    val wl = wallLayouts[wallId]
                    if (!lowWalls && !isInternal && !wallsGhost && wl != null && wl.tiles.size <= 700) {
                        wl.tiles.forEach { t ->
                            val quad = t.corners.map { c ->
                                V3(
                                    ax + ux * c.x.toFloat() + nx * 0.004f,
                                    c.y.toFloat(),
                                    az + uz * c.x.toFloat() + nz * 0.004f,
                                )
                            }
                            val col = if (t.cls == TileClass.CUT) shade(vm.tileColor, 0.9f) else vm.tileColor
                            addFace(quad, shade(col, tone + 0.30f), Color(0xFF2A3140))
                        }
                    }

                    // проёмы: цвет контура по типу; видны и на межкомнатных
                    // (полупрозрачных) стенах — дверь между комнатами больше не исчезает
                    if (!lowWalls && !wallsGhost) {
                        val wallKinds = vm.openingKindsOf(wallId)
                        vm.openingsOf(wallId).forEachIndexed { oi, o ->
                            val oKind = wallKinds.getOrNull(oi) ?: OPENING_WINDOW
                            val ox0 = o.x.toFloat()
                            val ox1 = (o.x + o.w).toFloat()
                            val oy0 = o.y.toFloat()
                            val oy1 = (o.y + o.h).toFloat()
                            val glass = oKind == OPENING_WINDOW || oKind == OPENING_BALCONY
                            addFace(
                                listOf(
                                    V3(ax + ux * ox0 + nx * 0.008f, oy0, az + uz * ox0 + nz * 0.008f),
                                    V3(ax + ux * ox1 + nx * 0.008f, oy0, az + uz * ox1 + nz * 0.008f),
                                    V3(ax + ux * ox1 + nx * 0.008f, oy1, az + uz * ox1 + nz * 0.008f),
                                    V3(ax + ux * ox0 + nx * 0.008f, oy1, az + uz * ox0 + nz * 0.008f),
                                ),
                                if (glass) Color(0xFF13202F) else Color(0xFF0A0E15),
                                when (oKind) {
                                    OPENING_WINDOW -> Acc.copy(alpha = 0.55f)
                                    OPENING_ENTRY -> Good.copy(alpha = 0.65f)
                                    OPENING_PASSAGE -> Sub.copy(alpha = 0.5f)
                                    else -> Acc2.copy(alpha = 0.55f)
                                },
                            )
                        }
                    }
                }

                // стены остальных комнат (пол уже нарисован картинкой)
                vm.rooms.forEachIndexed { ri2, r ->
                    if (ri2 == vm.activeRoom) return@forEachIndexed
                    val rpts = r.spec.points
                    if (rpts.size < 3) return@forEachIndexed
                    for (i2 in rpts.indices) {
                        val a = rpts[i2]
                        val b = rpts[(i2 + 1) % rpts.size]
                        val ax = a.x.toFloat(); val az = a.y.toFloat()
                        val bx = b.x.toFloat(); val bz = b.y.toFloat()
                        val len = hypot(bx - ax, bz - az)
                        if (len < 1e-4f) continue
                        var nx = (bz - az) / len
                        var nz = -(bx - ax) / len
                        val mx = (ax + bx) / 2f
                        val mz = (az + bz) / 2f
                        val inside = Pt((mx + nx * 0.05f).toDouble(), (mz + nz * 0.05f).toDouble())
                        if (com.baremodel.core.pointInPolygon(inside, rpts)) { nx = -nx; nz = -nz }
                        if (nx * (camX - mx) + nz * (camZ - mz) > 0f) continue
                        val outP2 = Pt((mx + nx * 0.07f).toDouble(), (mz + nz * 0.07f).toDouble())
                        val isInternal2 = com.baremodel.core.pointInPolygon(outP2, vm.room.points) ||
                            vm.rooms.withIndex().any { (rj, rr) ->
                                rj != ri2 && rj != vm.activeRoom && rr.spec.points.size >= 3 &&
                                    com.baremodel.core.pointInPolygon(outP2, rr.spec.points)
                            }
                        val tone = 0.40f + 0.14f * abs(nz)
                        addFace(
                            listOf(
                                V3(ax, 0f, az), V3(bx, 0f, bz),
                                V3(bx, wallDrawH, bz), V3(ax, wallDrawH, az),
                            ),
                            shade(Color(0xFF8E99AB), tone)
                                .copy(alpha = if (isInternal2 || wallsGhost) 0.40f else 1f),
                            LineC,
                        )
                        // проёмы этой стены — видны и у неактивных комнат,
                        // цвет по типу, как у активной
                        if (!lowWalls && !wallsGhost) {
                            val oid = "wall-" + (i2 + 1)
                            val wOpen = r.openings[oid] ?: emptyList()
                            if (wOpen.isNotEmpty()) {
                                val kinds2 = r.openingKinds[oid] ?: emptyList()
                                val ux2 = (bx - ax) / len
                                val uz2 = (bz - az) / len
                                wOpen.forEachIndexed { oi2, o ->
                                    val k2 = kinds2.getOrNull(oi2)
                                        ?: if (o.y < 0.05) OPENING_DOOR else OPENING_WINDOW
                                    val ox0 = o.x.toFloat()
                                    val ox1 = (o.x + o.w).toFloat()
                                    val oy0 = o.y.toFloat()
                                    val oy1 = (o.y + o.h).toFloat()
                                    val glass2 = k2 == OPENING_WINDOW || k2 == OPENING_BALCONY
                                    addFace(
                                        listOf(
                                            V3(ax + ux2 * ox0 + nx * 0.008f, oy0, az + uz2 * ox0 + nz * 0.008f),
                                            V3(ax + ux2 * ox1 + nx * 0.008f, oy0, az + uz2 * ox1 + nz * 0.008f),
                                            V3(ax + ux2 * ox1 + nx * 0.008f, oy1, az + uz2 * ox1 + nz * 0.008f),
                                            V3(ax + ux2 * ox0 + nx * 0.008f, oy1, az + uz2 * ox0 + nz * 0.008f),
                                        ),
                                        if (glass2) Color(0xFF13202F) else Color(0xFF0A0E15),
                                        when (k2) {
                                            OPENING_WINDOW -> Acc.copy(alpha = 0.55f)
                                            OPENING_ENTRY -> Good.copy(alpha = 0.65f)
                                            OPENING_PASSAGE -> Sub.copy(alpha = 0.5f)
                                            else -> Acc2.copy(alpha = 0.55f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // мебель: узнаваемые формы, а не одинаковые ящики
                if (vm.showFurniture) {
                    val body = Color(0xFF20293A)

                    fun box(
                        bx0: Float, bz0: Float, bx1: Float, bz1: Float,
                        y0: Float, y1: Float, c: Color, top: Float = 1.35f,
                    ) {
                        addFace(listOf(V3(bx0, y0, bz0), V3(bx1, y0, bz0), V3(bx1, y1, bz0), V3(bx0, y1, bz0)), shade(c, 0.9f), LineC)
                        addFace(listOf(V3(bx1, y0, bz0), V3(bx1, y0, bz1), V3(bx1, y1, bz1), V3(bx1, y1, bz0)), shade(c, 0.78f), LineC)
                        addFace(listOf(V3(bx1, y0, bz1), V3(bx0, y0, bz1), V3(bx0, y1, bz1), V3(bx1, y1, bz1)), shade(c, 1.05f), LineC)
                        addFace(listOf(V3(bx0, y0, bz1), V3(bx0, y0, bz0), V3(bx0, y1, bz0), V3(bx0, y1, bz1)), shade(c, 0.84f), LineC)
                        addFace(listOf(V3(bx0, y1, bz0), V3(bx1, y1, bz0), V3(bx1, y1, bz1), V3(bx0, y1, bz1)), shade(c, top), Acc2.copy(alpha = 0.45f))
                    }

                    /** Тёмные «швы» дверей на всех четырёх боках. */
                    fun seams(bx0: Float, bz0: Float, bx1: Float, bz1: Float, y0: Float, y1: Float, vertical: Boolean) {
                        val sc = Color(0xFF11161F)
                        val e = 0.006f
                        val t = 0.009f
                        if (vertical) {
                            val mx = (bx0 + bx1) / 2f
                            val mz = (bz0 + bz1) / 2f
                            addFace(listOf(V3(mx - t, y0, bz0 - e), V3(mx + t, y0, bz0 - e), V3(mx + t, y1, bz0 - e), V3(mx - t, y1, bz0 - e)), sc)
                            addFace(listOf(V3(mx - t, y0, bz1 + e), V3(mx + t, y0, bz1 + e), V3(mx + t, y1, bz1 + e), V3(mx - t, y1, bz1 + e)), sc)
                            addFace(listOf(V3(bx0 - e, y0, mz - t), V3(bx0 - e, y0, mz + t), V3(bx0 - e, y1, mz + t), V3(bx0 - e, y1, mz - t)), sc)
                            addFace(listOf(V3(bx1 + e, y0, mz - t), V3(bx1 + e, y0, mz + t), V3(bx1 + e, y1, mz + t), V3(bx1 + e, y1, mz - t)), sc)
                        } else {
                            val my = y0 + (y1 - y0) * 0.68f
                            addFace(listOf(V3(bx0, my - t, bz0 - e), V3(bx1, my - t, bz0 - e), V3(bx1, my + t, bz0 - e), V3(bx0, my + t, bz0 - e)), sc)
                            addFace(listOf(V3(bx0, my - t, bz1 + e), V3(bx1, my - t, bz1 + e), V3(bx1, my + t, bz1 + e), V3(bx0, my + t, bz1 + e)), sc)
                            addFace(listOf(V3(bx0 - e, my - t, bz0), V3(bx0 - e, my - t, bz1), V3(bx0 - e, my + t, bz1), V3(bx0 - e, my + t, bz0)), sc)
                            addFace(listOf(V3(bx1 + e, my - t, bz0), V3(bx1 + e, my - t, bz1), V3(bx1 + e, my + t, bz1), V3(bx1 + e, my + t, bz0)), sc)
                        }
                    }

                    vm.furniture.forEach { f ->
                        val x0 = f.x.toFloat(); val z0 = f.y.toFloat()
                        val x1 = (f.x + f.w).toFloat(); val z1 = (f.y + f.h).toFloat()
                        val h = f.heightM.toFloat().coerceIn(0.05f, 3f)
                        addFace(
                            listOf(
                                V3(x0 - 0.03f, 0.004f, z0 - 0.03f),
                                V3(x1 + 0.06f, 0.004f, z0 - 0.03f),
                                V3(x1 + 0.06f, 0.004f, z1 + 0.06f),
                                V3(x0 - 0.03f, 0.004f, z1 + 0.06f),
                            ),
                            Color(0x2E000000),
                        )
                        val wX = x1 - x0
                        val wZ = z1 - z0
                        when (f.kind) {
                            "bath" -> {
                                box(x0, z0, x1, z1, 0f, h, body)
                                val ix = minOf(wX, wZ) * 0.14f
                                addFace(
                                    listOf(
                                        V3(x0 + ix, h + 0.004f, z0 + ix),
                                        V3(x1 - ix, h + 0.004f, z0 + ix),
                                        V3(x1 - ix, h + 0.004f, z1 - ix),
                                        V3(x0 + ix, h + 0.004f, z1 - ix),
                                    ),
                                    shade(body, 0.55f),
                                    LineC,
                                )
                            }

                            "wc" -> if (wZ >= wX) {
                                box(x0, z0 + wZ * 0.3f, x1, z1, 0f, h * 0.55f, body)
                                box(x0, z0, x1, z0 + wZ * 0.3f, 0f, h, body)
                            } else {
                                box(x0 + wX * 0.3f, z0, x1, z1, 0f, h * 0.55f, body)
                                box(x0, z0, x0 + wX * 0.3f, z1, 0f, h, body)
                            }

                            "washer" -> {
                                box(x0, z0, x1, z1, 0f, h, body)
                                val cy = h * 0.52f
                                val r = minOf(wX, wZ, h) * 0.30f
                                val cx2 = (x0 + x1) / 2f
                                val cz2 = (z0 + z1) / 2f
                                val n = 14
                                val sideA = ArrayList<V3>(n)
                                val sideB = ArrayList<V3>(n)
                                for (k in 0 until n) {
                                    val a = (k.toFloat() / n) * (2f * Math.PI.toFloat())
                                    if (wZ >= wX) {
                                        sideA.add(V3(x0 - 0.006f, cy + sin(a) * r, cz2 + cos(a) * r))
                                        sideB.add(V3(x1 + 0.006f, cy + sin(a) * r, cz2 + cos(a) * r))
                                    } else {
                                        sideA.add(V3(cx2 + cos(a) * r, cy + sin(a) * r, z0 - 0.006f))
                                        sideB.add(V3(cx2 + cos(a) * r, cy + sin(a) * r, z1 + 0.006f))
                                    }
                                }
                                addFace(sideA, shade(body, 0.5f), Acc2.copy(alpha = 0.5f))
                                addFace(sideB, shade(body, 0.5f), Acc2.copy(alpha = 0.5f))
                            }

                            "fridge" -> {
                                box(x0, z0, x1, z1, 0f, h, body)
                                seams(x0, z0, x1, z1, 0f, h, vertical = false)
                            }

                            "cabinet", "wardrobe" -> {
                                box(x0, z0, x1, z1, 0f, h, body)
                                seams(x0, z0, x1, z1, 0f, h, vertical = true)
                            }

                            "kitchen" -> {
                                box(x0, z0, x1, z1, 0f, h, body, top = 1.5f)
                                val sw = minOf(wX, wZ) * 0.55f
                                val sx0: Float
                                val sz0: Float
                                if (wX >= wZ) {
                                    sx0 = x0 + wX * 0.12f
                                    sz0 = (z0 + z1) / 2f - sw / 2f
                                } else {
                                    sx0 = (x0 + x1) / 2f - sw / 2f
                                    sz0 = z0 + wZ * 0.12f
                                }
                                addFace(
                                    listOf(
                                        V3(sx0, h + 0.004f, sz0),
                                        V3(sx0 + sw, h + 0.004f, sz0),
                                        V3(sx0 + sw, h + 0.004f, sz0 + sw),
                                        V3(sx0, h + 0.004f, sz0 + sw),
                                    ),
                                    shade(body, 0.5f),
                                    LineC,
                                )
                            }

                            "chair" -> {
                                val seat = h * 0.55f
                                box(x0, z0, x1, z1, 0f, seat, body)
                                if (wZ >= wX) {
                                    box(x0, z1 - wZ * 0.18f, x1, z1, seat, h, body)
                                } else {
                                    box(x1 - wX * 0.18f, z0, x1, z1, seat, h, body)
                                }
                            }

                            "table" -> {
                                val lw = maxOf(0.05f, minOf(0.09f, wX * 0.2f, wZ * 0.2f))
                                box(x0, z0, x0 + lw, z0 + lw, 0f, h - 0.04f, body)
                                box(x1 - lw, z0, x1, z0 + lw, 0f, h - 0.04f, body)
                                box(x0, z1 - lw, x0 + lw, z1, 0f, h - 0.04f, body)
                                box(x1 - lw, z1 - lw, x1, z1, 0f, h - 0.04f, body)
                                box(x0, z0, x1, z1, h - 0.05f, h, shade(body, 1.25f), top = 1.6f)
                            }

                            else -> box(x0, z0, x1, z1, 0f, h, body)
                        }
                    }
                }

                // дальние грани рисуются первыми
                faces.sortByDescending { it.depth }
                faces.forEach { face ->
                    if (face.pts.any { it.x.isNaN() }) return@forEach
                    val path = Path().apply {
                        moveTo(face.pts[0].x, face.pts[0].y)
                        for (k in 1 until face.pts.size) lineTo(face.pts[k].x, face.pts[k].y)
                        close()
                    }
                    val bmp = face.bmp
                    if (bmp != null && face.pts.size == 4) {
                        // фото плитки натягивается на грань: 4 вершины сетки
                        drawIntoCanvas { canvas ->
                            val verts = floatArrayOf(
                                face.pts[0].x, face.pts[0].y,
                                face.pts[1].x, face.pts[1].y,
                                face.pts[3].x, face.pts[3].y,
                                face.pts[2].x, face.pts[2].y,
                            )
                            canvas.nativeCanvas.drawBitmapMesh(bmp, 1, 1, verts, 0, null, 0, null)
                        }
                    } else {
                        drawPath(path, face.color)
                    }
                    face.outline?.let {
                        drawPath(path, it, style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f * density))
                    }
                }
            }

            if (pickedTile >= 0) {
                val srcLayout = if (pickedRoom >= 0) otherLayouts.getOrNull(pickedRoom) else vm.layout
                val srcTile = if (pickedRoom >= 0) {
                    vm.rooms.getOrNull(pickedRoom)?.tile ?: vm.tile
                } else {
                    vm.tile
                }
                val t = srcLayout?.tiles?.getOrNull(pickedTile)
                if (t != null) {
                    // тот же номер и размеры, что на плане: единая нумерация из ядра
                    val ci = if (pickedRoom < 0) {
                        vm.cutInfo[pickedTile]
                    } else {
                        otherCutInfo.getOrNull(pickedRoom)?.get(pickedTile)
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel2.copy(alpha = 0.94f))
                            .border(1.dp, LineC, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            srcTile.widthMm.toInt().toString() + "×" + srcTile.heightMm.toInt() +
                                if (t.cls == TileClass.CUT) {
                                    "  ·  " + stringResource(R.string.cut_tiles) + cutChipSuffix(ci)
                                } else {
                                    ""
                                },
                            color = if (t.cls == TileClass.CUT) Warn else Txt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.view_hint),
                color = Sub,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Panel.copy(alpha = 0.9f))
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }

        var panelOpen by rememberSaveable { mutableStateOf(true) }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Panel),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { panelOpen = !panelOpen }
                    .padding(top = 9.dp, bottom = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (panelOpen) Panel3 else Acc.copy(alpha = 0.7f)),
                )
            }
            if (panelOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
            Text(stringResource(R.string.wall_height), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2.4, 2.7, 3.0).forEach { h ->
                    Chip("$h " + stringResource(R.string.unit_m), abs(vm.wallHeightM - h) < 0.01) {
                        vm.setWallHeight(h)
                    }
                }
                Chip(stringResource(R.string.walls_low), lowWalls) { lowWalls = !lowWalls }
                Chip(stringResource(R.string.walls_ghost), wallsGhost) { wallsGhost = !wallsGhost }
                Chip(stringResource(R.string.reset_view)) {
                    yaw = 0.6f; pitch = 0.62f; dist = 1.05f
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Panel2)
                    .border(1.dp, LineC, RoundedCornerShape(13.dp))
                    .clickable { }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.view_note), color = Sub, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.hint_3d), color = Dim, fontSize = 10.5.sp)
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
    }
}

/** Точка внутри экранного четырёхугольника — для выбора плитки пальцем в 3D. */
private fun pointInQuad(p: Offset, q: List<Offset>): Boolean {
    var inside = false
    var j = q.size - 1
    for (i in q.indices) {
        val a = q[i]
        val b = q[j]
        if ((a.y > p.y) != (b.y > p.y) &&
            p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}
