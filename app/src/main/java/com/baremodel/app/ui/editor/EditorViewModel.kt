package com.baremodel.app.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baremodel.app.R
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import com.baremodel.app.ar.ArActivity
import com.baremodel.app.ar.ArBridge
import com.baremodel.app.ar.ExtraLayer
import com.baremodel.app.ar.PanelInfo
import com.baremodel.app.ar.renderFloorBitmap
import com.baremodel.app.data.Prices
import com.baremodel.app.data.RoomDto
import com.baremodel.app.data.ZoneDto
import java.io.File
import com.baremodel.app.data.ProjectDto
import com.baremodel.app.data.ProjectMeta
import com.baremodel.app.data.ProjectRepository
import com.baremodel.core.Aligner
import com.baremodel.core.AnchorMode
import com.baremodel.core.ArtRect
import com.baremodel.core.CutAnalyzer
import com.baremodel.core.CutNumbering
import com.baremodel.core.CutPieceInfo
import com.baremodel.core.CutReport
import com.baremodel.core.CoverageAnalyzer
import com.baremodel.core.CoverageReport
import com.baremodel.core.Cutout
import com.baremodel.core.Furniture
import com.baremodel.core.DecorMode
import com.baremodel.core.DecorPlanner
import com.baremodel.core.DecorSpec
import com.baremodel.core.Finish
import com.baremodel.core.RoomModel
import com.baremodel.core.SurfaceKind
import com.baremodel.core.LayoutResult
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.MaterialCalc
import com.baremodel.core.areaM2
import com.baremodel.core.PatternSpec
import com.baremodel.core.PlacedTile
import com.baremodel.core.PatternType
import com.baremodel.core.Pt
import com.baremodel.core.RoomSpec
import com.baremodel.core.SkirtPlan
import com.baremodel.core.SkirtingCalc
import com.baremodel.core.TileClass
import com.baremodel.core.TileSpec
import com.baremodel.core.TilingEngine
import com.baremodel.core.clipPolygonByRect
import com.baremodel.core.pointInPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Экранное преобразование: метры → пиксели. */
data class ViewTransform(val scale: Float = 110f, val offset: Offset = Offset(40f, 60f))

/** Строка сводки по комнате квартиры (для отчёта и PDF). */
data class RoomStat(
    val name: String,
    val areaM2: Double,
    val buy: Int,
    val money: Double,
    val tileLabel: String,
)

/** Типы проёмов: окно, дверь, балконная дверь, входная дверь, проход без двери. */
const val OPENING_WINDOW = 0
const val OPENING_DOOR = 1
const val OPENING_BALCONY = 2
const val OPENING_ENTRY = 3
const val OPENING_PASSAGE = 4

/** Строка чек-листа «Работы»: ключ статуса, комната, заголовок и объём. */
data class WorkRow(val key: String, val room: Int, val title: String, val detail: String)

/** Порог: полоса пола в дверном проёме межкомнатной стены (мировые метры). */
data class ThresholdStrip(
    val wall: Int,
    val x0: Double,
    val y0: Double,
    val ux: Double,
    val uy: Double,
    val nx: Double,
    val ny: Double,
    val w: Double,
    val th: Double,
)

/**
 * Плитка-«бин» парования: номера подрезок из неё, остаток поперёк реза (мм)
 * и длина полосы остатка (мм) — из остатка режется плинтус.
 */
data class CutBin(val nums: List<Int>, val restMm: Double, val stripLenMm: Double)

/** Пары резов: бины (включая одиночные) и экономия в плитках. */
data class CutPairs(val bins: List<CutBin>, val saved: Int)

sealed interface Selection {
    data class Zone(val i: Int) : Selection
    data class Vertex(val i: Int) : Selection
    data class Cut(val i: Int) : Selection
    data class Furn(val i: Int) : Selection
    data class Tile(val i: Int) : Selection
}

private fun crossZ(ox: Double, oy: Double, ax: Double, ay: Double, bx: Double, by: Double): Double =
    (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)

private fun segsCross(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Boolean {
    val d1 = crossZ(p3.x, p3.y, p4.x, p4.y, p1.x, p1.y)
    val d2 = crossZ(p3.x, p3.y, p4.x, p4.y, p2.x, p2.y)
    val d3 = crossZ(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
    val d4 = crossZ(p1.x, p1.y, p2.x, p2.y, p4.x, p4.y)
    return ((d1 > 1e-9 && d2 < -1e-9) || (d1 < -1e-9 && d2 > 1e-9)) &&
        ((d3 > 1e-9 && d4 < -1e-9) || (d3 < -1e-9 && d4 > 1e-9))
}

/**
 * Пересекаются ли контуры по площади. Касание рёбрами и общими углами не считается:
 * вершины проверяются с миллиметровым сдвигом к центру своей комнаты, поэтому комнаты
 * можно ставить вплотную стена к стене.
 */
fun polygonsOverlap(a: List<Pt>, b: List<Pt>): Boolean {
    if (a.size < 3 || b.size < 3) return false
    for (i in a.indices) {
        for (j in b.indices) {
            if (segsCross(a[i], a[(i + 1) % a.size], b[j], b[(j + 1) % b.size])) return true
        }
    }
    val ca = Pt(a.sumOf { it.x } / a.size, a.sumOf { it.y } / a.size)
    if (a.any { pointInPolygon(Pt(it.x + (ca.x - it.x) * 0.001, it.y + (ca.y - it.y) * 0.001), b) }) return true
    val cb = Pt(b.sumOf { it.x } / b.size, b.sumOf { it.y } / b.size)
    if (b.any { pointInPolygon(Pt(it.x + (cb.x - it.x) * 0.001, it.y + (cb.y - it.y) * 0.001), a) }) return true
    return false
}

/** Стоимость поверхности: материалы и работа. */

data class SurfaceCost(
    val id: String,
    val finish: Finish,
    val areaM2: Double,
    val materials: Double,
    val work: Double,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProjectRepository(app)

    // ---------- состояние ----------

    var room by mutableStateOf(
        RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0)))
    )
        private set

    var tile by mutableStateOf(TileSpec(600.0, 600.0, 3.0))
        private set

    var pattern by mutableStateOf(PatternSpec())
        private set

    var tileColor by mutableStateOf(Color(0xFFC7CCD6))
        private set

    var variation by mutableStateOf(true)
        private set

    var tileImage by mutableStateOf<ImageBitmap?>(null)
        private set

    var decor by mutableStateOf(DecorSpec())
        private set

    var anchor by mutableStateOf(AnchorMode.FREE)
        private set

    var decorImage by mutableStateOf<ImageBitmap?>(null)
        private set

    var furniture by mutableStateOf<List<Furniture>>(emptyList())
        private set

    var reservePct by mutableStateOf(10)
        private set

    var roomMode by mutableStateOf(false)
        private set

    var showDims by mutableStateOf(true)
        private set

    var showCuts by mutableStateOf(true)
        private set

    var showFurniture by mutableStateOf(true)
        private set

    /** Показ области рисунка на каждой плитке плана. */
    var showArt by mutableStateOf(false)
        private set

    var wallHeightM by mutableStateOf(2.7)
        private set

    // ---------- квартира: несколько комнат ----------

    /** Все комнаты квартиры; запись активной обновляется при переключении и сохранении. */
    var rooms by mutableStateOf(listOf<RoomDto>())
        private set

    var activeRoom by mutableStateOf(0)
        private set

    /** Снимок текущих полей как комната. */
    private fun snapshotRoom(name: String): RoomDto = RoomDto(
        name = name,
        decorOverrides = decorOverrides,
        panelOn = panelOn,
        panelRX = panelRX,
        panelRY = panelRY,
        zones = zones,
        tileColors = tileColors,
        wallThickness = wallThickness,
        spec = room,
        tile = tile,
        pattern = pattern,
        colorArgb = tileColor.toArgb(),
        variation = variation,
        decor = decor,
        anchor = anchor,
        finishes = finishes,
        openings = openings,
        openingKinds = openingKinds,
    )

    private fun applyRoom(d: RoomDto) {
        decorOverrides = d.decorOverrides
        panelOn = d.panelOn
        panelRX = d.panelRX
        panelRY = d.panelRY
        zones = d.zones
        tileColors = d.tileColors
        wallThickness = d.wallThickness
        activeZone = -1
        room = d.spec
        tile = d.tile
        pattern = d.pattern
        if (d.colorArgb != -1) tileColor = Color(d.colorArgb)
        variation = d.variation
        decor = d.decor
        anchor = d.anchor
        finishes = d.finishes
        openings = d.openings
        openingKinds = d.openingKinds
        selection = null
        suggestions = null
        reanchor()
    }

    /** Обновить запись активной комнаты в списке. */
    fun syncActiveRoom() {
        val list = rooms.toMutableList()
        if (activeRoom in list.indices) {
            val snap = snapshotRoom(list[activeRoom].name)
            if (list[activeRoom] != snap) {
                list[activeRoom] = snap
                rooms = list
            }
        } else if (list.isEmpty()) {
            rooms = listOf(snapshotRoom(""))
            activeRoom = 0
        }
    }

    fun switchRoom(i: Int) {
        if (i == activeRoom || i !in rooms.indices) return
        pushUndo()
        syncActiveRoom()
        activeRoom = i
        applyRoom(rooms[i])
    }

    /** Новая комната справа от габарита квартиры, наследует плитку и узор текущей. */
    fun addRoom() {
        pushUndo()
        syncActiveRoom()
        val all = rooms.flatMap { it.spec.points } + room.points
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val x0 = round2(maxX + 0.4)
        val y0 = round2(minY)
        val spec = RoomSpec(
            listOf(Pt(x0, y0), Pt(x0 + 3.0, y0), Pt(x0 + 3.0, y0 + 2.4), Pt(x0, y0 + 2.4)),
        )
        val d = snapshotRoom("").copy(
            spec = spec,
            decor = DecorSpec(),
            anchor = AnchorMode.FREE,
            finishes = emptyMap(),
            openings = emptyMap(),
            openingKinds = emptyMap(),
        )
        rooms = rooms + d
        activeRoom = rooms.lastIndex
        applyRoom(d)
        fit()
    }

    fun deleteActiveRoom() {
        if (rooms.size <= 1) return
        pushUndo()
        val del = activeRoom
        val list = rooms.toMutableList()
        list.removeAt(activeRoom)
        rooms = list
        // статусы работ: r{del} выбрасываются, последующие сдвигаются на −1
        if (workStatus.isNotEmpty()) {
            val ws = mutableMapOf<String, Int>()
            workStatus.forEach { (k, v) ->
                val dot = k.indexOf('.')
                val idx = if (k.startsWith("r") && dot > 1) {
                    k.substring(1, dot).toIntOrNull()
                } else {
                    null
                }
                when {
                    idx == null || idx < del -> ws[k] = v
                    idx == del -> Unit
                    else -> ws["r" + (idx - 1) + k.substring(dot)] = v
                }
            }
            workStatus = ws
        }
        activeRoom = activeRoom.coerceAtMost(list.lastIndex)
        applyRoom(list[activeRoom])
        fit()
    }

    /**
     * Насколько сдвинуть активную комнату, чтобы она встала вплотную к соседней.
     * Сравниваются габариты: край к краю по X и по Y, порог 12 см.
     */
    private fun snapOffset(): Pt? {
        if (rooms.size < 2 || room.points.isEmpty()) return null
        val ax0 = room.points.minOf { it.x }
        val ax1 = room.points.maxOf { it.x }
        val ay0 = room.points.minOf { it.y }
        val ay1 = room.points.maxOf { it.y }
        var bestX = 0.0
        var bestY = 0.0
        // порог тем больше, чем толще стена: иначе «подвёл вплотную» не дотягивается
        val limit = 0.10 + wallThicknessM
        var dxMin = limit
        var dyMin = limit
        rooms.forEachIndexed { i, r ->
            if (i == activeRoom || r.spec.points.size < 3) return@forEachIndexed
            val bx0 = r.spec.points.minOf { it.x }
            val bx1 = r.spec.points.maxOf { it.x }
            val by0 = r.spec.points.minOf { it.y }
            val by1 = r.spec.points.maxOf { it.y }
            // между комнатами оставляем зазор ровно в толщину стены: тогда полосы стен
            // обеих комнат ложатся в него и выглядят одной общей стеной, а не налезают
            val t = wallThicknessM
            listOf(bx0 - t - ax1, bx1 + t - ax0, bx0 - ax0, bx1 - ax1).forEach { d ->
                if (abs(d) < dxMin) { dxMin = abs(d); bestX = d }
            }
            listOf(by0 - t - ay1, by1 + t - ay0, by0 - ay0, by1 - ay1).forEach { d ->
                if (abs(d) < dyMin) { dyMin = abs(d); bestY = d }
            }
        }
        return if (bestX != 0.0 || bestY != 0.0) Pt(bestX, bestY) else null
    }

    /** Пересекается ли контур-кандидат с другими комнатами квартиры. */
    private fun overlapsOthers(cand: List<Pt>): Boolean =
        rooms.withIndex().any { (i, r) -> i != activeRoom && polygonsOverlap(cand, r.spec.points) }

    /**
     * Прижать активную комнату к ближайшей соседней с нужной стороны.
     * Зазор между контурами равен толщине стены — стены обеих комнат ложатся в него.
     * side: 0 — слева, 1 — справа, 2 — сверху, 3 — снизу от соседки.
     */
    fun dockActiveRoom(side: Int) {
        if (rooms.size < 2 || room.points.isEmpty()) return
        val ax0 = room.points.minOf { it.x }
        val ax1 = room.points.maxOf { it.x }
        val ay0 = room.points.minOf { it.y }
        val ay1 = room.points.maxOf { it.y }
        val cx = (ax0 + ax1) / 2
        val cy = (ay0 + ay1) / 2

        var best = -1
        var bestD = Double.MAX_VALUE
        rooms.forEachIndexed { i, r ->
            if (i == activeRoom || r.spec.points.size < 3) return@forEachIndexed
            val rx = r.spec.points.sumOf { it.x } / r.spec.points.size
            val ry = r.spec.points.sumOf { it.y } / r.spec.points.size
            val d = (rx - cx) * (rx - cx) + (ry - cy) * (ry - cy)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best < 0) return
        val n = rooms[best].spec.points
        val bx0 = n.minOf { it.x }
        val bx1 = n.maxOf { it.x }
        val by0 = n.minOf { it.y }
        val by1 = n.maxOf { it.y }
        val t = wallThicknessM

        val dx: Double
        val dy: Double
        when (side) {
            0 -> { dx = bx0 - t - ax1; dy = by0 - ay0 }
            1 -> { dx = bx1 + t - ax0; dy = by0 - ay0 }
            2 -> { dx = bx0 - ax0; dy = by0 - t - ay1 }
            else -> { dx = bx0 - ax0; dy = by1 + t - ay0 }
        }
        val cand = room.points.map { Pt(round2(it.x + dx), round2(it.y + dy)) }
        if (overlapsOthers(cand)) return
        pushUndo()
        room = RoomSpec(
            cand,
            room.cutouts.map { it.copy(x = round2(it.x + dx), y = round2(it.y + dy)) },
        )
        pattern = pattern.copy(
            offsetX = round2(pattern.offsetX + dx),
            offsetY = round2(pattern.offsetY + dy),
        )
        val fs = furniture.toMutableList()
        fs.indices.forEach { idx ->
            val f = fs[idx]
            if (pointInPolygon(Pt(f.x + f.w / 2, f.y + f.h / 2), room.points)) {
                fs[idx] = f.copy(x = round2(f.x + dx), y = round2(f.y + dy))
            }
        }
        furniture = fs
        zones = zones.map { it.copy(x = round2(it.x + dx), y = round2(it.y + dy)) }
        fit()
    }

    /** Сводный итог по всем комнатам: штук к покупке и деньги при заданной цене. */
    /** Показывать статистику по всей квартире, а не по активной комнате. */
    var statsApartment by mutableStateOf(false)
        private set

    fun toggleStatsScope() { statsApartment = !statsApartment }

    /** Итоги по всей квартире: площадь, целые, подрезка, к покупке. */
    fun apartmentTotals(): FloatArray {
        var area = 0.0
        var full = 0
        var cut = 0
        var buy = 0
        rooms.forEachIndexed { i, r ->
            val lay = if (i == activeRoom) layout else TilingEngine.build(r.spec, r.tile, r.pattern)
            val strips = if (i == activeRoom) {
                thresholdStrips
            } else {
                thresholdStripsFor(r.spec, r.openings, r.openingKinds, r.wallThickness, i)
            }
            val t = if (i == activeRoom) tile else r.tile
            val stepLong = max(t.widthMm, t.heightMm) / 1000.0 + max(0.0, t.groutMm) / 1000.0
            val tp = strips.sumOf { ceil(it.w / stepLong).toInt() }
            area += lay.areaM2 + strips.sumOf { it.w * it.th }
            full += lay.fullCount
            cut += lay.cutCount + tp
            buy += ceil((lay.totalCount + tp) * (1 + reservePct / 100.0)).toInt()
        }
        return floatArrayOf(area.toFloat(), full.toFloat(), cut.toFloat(), buy.toFloat())
    }

    /** Построчная сводка по всем комнатам квартиры: площадь, закупка, деньги. */
    fun apartmentStats(): List<RoomStat> {
        val app = getApplication<Application>()
        return rooms.mapIndexed { i, r ->
            val t = if (i == activeRoom) tile else r.tile
            val lay = if (i == activeRoom) layout else TilingEngine.build(r.spec, r.tile, r.pattern)
            val strips = if (i == activeRoom) {
                thresholdStrips
            } else {
                thresholdStripsFor(r.spec, r.openings, r.openingKinds, r.wallThickness, i)
            }
            val stepLong = max(t.widthMm, t.heightMm) / 1000.0 + max(0.0, t.groutMm) / 1000.0
            val tp = strips.sumOf { ceil(it.w / stepLong).toInt() }
            val cnt = ceil((lay.totalCount + tp) * (1 + reservePct / 100.0)).toInt()
            val tileAreaM2 = t.widthMm * t.heightMm / 1_000_000.0
            val unit = if (prices.tilePc > 0) prices.tilePc else prices.tileM2 * tileAreaM2
            RoomStat(
                name = r.name.ifBlank { app.getString(R.string.room_n, i + 1) },
                areaM2 = lay.areaM2 + strips.sumOf { it.w * it.th },
                buy = cnt,
                money = cnt * unit,
                tileLabel = t.widthMm.toInt().toString() + "×" + t.heightMm.toInt(),
            )
        }
    }

    fun apartmentPieces(): Pair<Int, Double> {
        val st = apartmentStats()
        return st.sumOf { it.buy } to st.sumOf { it.money }
    }

    /** Цены мастера: сохраняются вместе с проектом. */
    var prices by mutableStateOf(Prices())
        private set

    /** Фото пола для примерки (живёт в рамках сеанса). */
    var fitPhoto by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Четыре угла пола на фото, в долях кадра: слева-сверху по часовой. */
    var fitQuad by mutableStateOf(
        listOf(Offset(0.16f, 0.34f), Offset(0.84f, 0.34f), Offset(0.97f, 0.93f), Offset(0.03f, 0.93f)),
    )
        private set

    /** Прозрачность наложенной раскладки. */
    var fitAlpha by mutableStateOf(0.8f)
        private set

    /** Логотип мастера для шапки PDF; хранится в filesDir/logo.png. */
    var masterLogo by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Отделка по поверхностям: floor / wall-N / ceiling. */
    var finishes by mutableStateOf(mapOf("floor" to Finish.TILE, "ceiling" to Finish.PAINT))
        private set

    /** Проёмы (окна, двери) в координатах развёртки стены. */
    var openings by mutableStateOf(mapOf<String, List<Cutout>>())
        private set

    /** Тип каждого проёма (OPENING_*), параллельно списку openings той же стены. */
    var openingKinds by mutableStateOf(mapOf<String, List<Int>>())
        private set

    var activeSurface by mutableStateOf("floor")
        private set

    /** Открытая секция нижней панели; карточки статистики могут переключать её. */
    var panelSection by mutableStateOf(0)
        private set

    fun updatePanelSection(i: Int) { panelSection = i.coerceIn(0, 10) }

    /** Диалог точного ввода длин сторон у выбранной вершины. */
    var edgeDialog by mutableStateOf(false)
        private set

    fun updateEdgeDialog(v: Boolean) { edgeDialog = v }

    /** Редактор длины стены по тапу на метку размера: индекс ребра или -1. */
    var edgeEditIndex by mutableStateOf(-1)
        private set

    fun closeEdgeEdit() { edgeEditIndex = -1 }

    /** Прижать проёмы к своим стенам после изменения контура: дверь не висит за стеной. */
    private fun clampOpenings() {
        if (openings.isEmpty()) return
        var changed = false
        val out = openings.toMutableMap()
        model.walls.forEach { wl ->
            val list = out[wl.id] ?: return@forEach
            var ch = false
            val upd = list.map { o ->
                val w = o.w.coerceIn(0.1, wl.lengthM.coerceAtLeast(0.1))
                val x = o.x.coerceIn(0.0, (wl.lengthM - w).coerceAtLeast(0.0))
                if (w != o.w || x != o.x) {
                    ch = true
                    o.copy(x = round2(x), w = round2(w))
                } else {
                    o
                }
            }
            if (ch) {
                out[wl.id] = upd
                changed = true
            }
        }
        if (changed) openings = out
    }

    /** Экранная позиция метки размера ребра — та же математика, что в отрисовке. */
    private fun edgeLabelAt(i: Int): Offset? {
        val pts = room.points
        if (i !in pts.indices) return null
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        val sa = toScreen(a)
        val sb = toScreen(b)
        if ((sb - sa).getDistance() < 46f * uiScale) return null
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
        if (len <= 0.0) return null
        var nx = -(b.y - a.y) / len
        var ny = (b.x - a.x) / len
        val midW = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
        if (pointInPolygon(Pt(midW.x + nx * 0.08, midW.y + ny * 0.08), pts)) {
            nx = -nx
            ny = -ny
        }
        val ms = toScreen(midW)
        return Offset(
            ms.x + (nx * 30.0 * uiScale).toFloat(),
            ms.y + (ny * 30.0 * uiScale).toFloat(),
        )
    }

    var filletDialog by mutableStateOf(false)
        private set

    fun updateFilletDialog(v: Boolean) { filletDialog = v }

    // ---------- черчение по точкам ----------

    var drawMode by mutableStateOf(false)
        private set

    var drawPts by mutableStateOf(listOf<Pt>())
        private set

    fun startDraw() {
        drawMode = true
        drawPts = emptyList()
        selection = null
        roomMode = true
    }

    fun cancelDraw() {
        drawMode = false
        drawPts = emptyList()
    }

    fun undoDrawPoint() {
        if (drawPts.isNotEmpty()) drawPts = drawPts.dropLast(1)
    }

    /** Добавить точку с прилипанием к горизонтали/вертикали от предыдущей. */
    fun addDrawPoint(w: Pt) {
        var x = round2(w.x)
        var y = round2(w.y)
        drawPts.lastOrNull()?.let { last ->
            val dx = x - last.x
            val dy = y - last.y
            val segLen = sqrt(dx * dx + dy * dy)
            if (segLen > 0.05) {
                // привязка направления к 0/45/90: стены сразу рисуются ровными
                val ang = atan2(dy, dx)
                val stepA = Math.PI / 4
                val snapA = Math.round(ang / stepA) * stepA
                if (abs(ang - snapA) < 0.15) {
                    x = round2(last.x + segLen * cos(snapA))
                    y = round2(last.y + segLen * sin(snapA))
                }
            }
            if (abs(x - last.x) < 0.12) x = last.x
            if (abs(y - last.y) < 0.12) y = last.y
        }
        // уровни: прилипание к X/Y уже поставленных точек (включая первую — для замыкания)
        for (p in drawPts) {
            if (abs(x - p.x) < 0.10) x = p.x
            if (abs(y - p.y) < 0.10) y = p.y
        }
        drawPts = drawPts + Pt(round2(x), round2(y))
    }

    fun drawOverlaps(): Boolean = drawPts.size >= 3 && overlapsOthers(drawPts)

    /** Замкнуть контур: он становится активной комнатой (вырезы очищаются). */
    fun finishDraw(): Boolean {
        if (drawPts.size < 3 || overlapsOthers(drawPts)) return false
        pushUndo()
        room = RoomSpec(drawPts, emptyList())
        drawMode = false
        drawPts = emptyList()
        selection = null
        reanchor()
        fit()
        return true
    }

    /** Скруглить выбранный угол дугой из 6 сегментов. */
    fun roundSelectedCorner(radius: Double) {
        val sel = selection as? Selection.Vertex ?: return
        val pts = room.points
        val n = pts.size
        if (n < 3 || sel.i !in pts.indices) return
        val c = pts[sel.i]
        val p = pts[(sel.i - 1 + n) % n]
        val q = pts[(sel.i + 1) % n]
        val l1 = sqrt((p.x - c.x) * (p.x - c.x) + (p.y - c.y) * (p.y - c.y))
        val l2 = sqrt((q.x - c.x) * (q.x - c.x) + (q.y - c.y) * (q.y - c.y))
        if (l1 < 1e-6 || l2 < 1e-6) return
        val u1x = (p.x - c.x) / l1
        val u1y = (p.y - c.y) / l1
        val u2x = (q.x - c.x) / l2
        val u2y = (q.y - c.y) / l2
        val cosT = (u1x * u2x + u1y * u2y).coerceIn(-1.0, 1.0)
        val theta = kotlin.math.acos(cosT)
        if (theta < 0.05 || theta > Math.PI - 0.05) return
        var r = radius.coerceIn(0.05, 10.0)
        var t = r / kotlin.math.tan(theta / 2)
        val tMax = minOf(l1, l2) * 0.9
        if (t > tMax) {
            t = tMax
            r = t * kotlin.math.tan(theta / 2)
        }
        val t1 = Pt(c.x + u1x * t, c.y + u1y * t)
        val t2 = Pt(c.x + u2x * t, c.y + u2y * t)
        val bl = sqrt((u1x + u2x) * (u1x + u2x) + (u1y + u2y) * (u1y + u2y))
        if (bl < 1e-6) return
        val o = Pt(
            c.x + (u1x + u2x) / bl * (r / kotlin.math.sin(theta / 2)),
            c.y + (u1y + u2y) / bl * (r / kotlin.math.sin(theta / 2)),
        )
        val a1 = kotlin.math.atan2(t1.y - o.y, t1.x - o.x)
        val a2 = kotlin.math.atan2(t2.y - o.y, t2.x - o.x)
        var sweep = a2 - a1
        while (sweep > Math.PI) sweep -= 2 * Math.PI
        while (sweep < -Math.PI) sweep += 2 * Math.PI
        val segs = 6
        val arc = (0..segs).map { k ->
            val ang = a1 + sweep * k / segs
            Pt(round2(o.x + r * kotlin.math.cos(ang)), round2(o.y + r * kotlin.math.sin(ang)))
        }
        val cand = pts.toMutableList()
        cand.removeAt(sel.i)
        cand.addAll(sel.i, arc)
        if (!overlapsOthers(cand)) {
            pushUndo()
            room = room.copy(points = cand)
            remapWallKeys(sel.i, arc.size - 1)
            clampOpenings()
            selection = null
        }
    }

    /** Ниша наружу по центру стены после выбранного угла (0.8 × 0.3 м). */
    fun addNicheAfterSelected(width: Double = 0.8, depth: Double = 0.3) {
        val sel = selection as? Selection.Vertex ?: return
        val pts = room.points
        val n = pts.size
        if (sel.i !in pts.indices) return
        val a = pts[sel.i]
        val b = pts[(sel.i + 1) % n]
        val ex = b.x - a.x
        val ey = b.y - a.y
        val len = sqrt(ex * ex + ey * ey)
        if (len < width + 0.2) return
        val ux = ex / len
        val uy = ey / len
        var nx = ey / len
        var ny = -ex / len
        val mid = Pt(a.x + ux * len / 2, a.y + uy * len / 2)
        if (pointInPolygon(Pt(mid.x + nx * 0.05, mid.y + ny * 0.05), pts)) {
            nx = -nx
            ny = -ny
        }
        val s0 = len / 2 - width / 2
        val s1 = len / 2 + width / 2
        fun at(sv: Double) = Pt(round2(a.x + ux * sv), round2(a.y + uy * sv))
        fun atOut(sv: Double) = Pt(
            round2(a.x + ux * sv + nx * depth),
            round2(a.y + uy * sv + ny * depth),
        )
        val cand = pts.toMutableList()
        cand.addAll(sel.i + 1, listOf(at(s0), atOut(s0), atOut(s1), at(s1)))
        if (!overlapsOthers(cand)) {
            pushUndo()
            room = room.copy(points = cand)
            remapWallKeys(sel.i + 1, 4)
            clampOpenings()
        }
    }

    /** Избранные размеры плитки: (ширина, длина, шов) в мм. */
    var favTiles by mutableStateOf(listOf<Triple<Double, Double, Double>>())
        private set

    private fun favPrefs() =
        getApplication<Application>().getSharedPreferences("ba_fav", Context.MODE_PRIVATE)

    private fun persistFavs() {
        favPrefs().edit().putString(
            "tiles",
            favTiles.joinToString(";") { it.first.toString() + ":" + it.second + ":" + it.third },
        ).apply()
    }

    fun toggleFavTile() {
        val cur = Triple(tile.widthMm, tile.heightMm, tile.groutMm)
        favTiles = if (favTiles.contains(cur)) favTiles - cur else (favTiles + cur).takeLast(8)
        persistFavs()
    }

    fun applyFavTile(t: Triple<Double, Double, Double>) {
        pushUndo()
        tile = TileSpec(t.first, t.second, t.third)
        reanchor()
    }

    /** Точная длина сторон: соседние углы сдвигаются вдоль текущих направлений. */
    /**
     * Задать длину стены edge -> edge+1, двигая конец A (moveEnd=false, вершина edge)
     * или B (moveEnd=true, вершина edge+1) вдоль направления стены. Сдвиг тянет за
     * собой цепочку вершин до первой стены, ПАРАЛЛЕЛЬНОЙ редактируемой: она поглощает
     * изменение своей длиной. Так комната растягивается, а прямые углы сохраняются —
     * вместо прежнего сдвига соседа по лучу, который ломал соседние стены.
     * Если параллельного поглотителя нет (косой контур) — двигается только сам конец.
     */
    fun setEdgeLength(edge: Int, newLen: Double, moveEnd: Boolean) {
        val pts = room.points
        val n = pts.size
        if (n < 3 || edge !in 0 until n || newLen < 0.05) return
        val a = pts[edge]
        val b = pts[(edge + 1) % n]
        val ex = b.x - a.x
        val ey = b.y - a.y
        val len0 = sqrt(ex * ex + ey * ey)
        if (len0 < 1e-6) return
        val ux = ex / len0
        val uy = ey / len0
        val delta = newLen - len0
        if (abs(delta) < 0.005) return
        val dirStep = if (moveEnd) 1 else -1
        val sgn = if (moveEnd) 1.0 else -1.0
        val start = if (moveEnd) (edge + 1) % n else edge
        val fixedIdx = if (moveEnd) edge else (edge + 1) % n
        val movedSet = LinkedHashSet<Int>()
        var j = start
        var found = false
        var guard = 0
        while (guard++ <= n) {
            movedSet.add(j)
            val k = (j + dirStep + n) % n
            if (k == fixedIdx) break
            val wx = pts[k].x - pts[j].x
            val wy = pts[k].y - pts[j].y
            val wl = sqrt(wx * wx + wy * wy)
            val cross = if (wl < 1e-6) 1.0 else abs(wx * uy - wy * ux) / wl
            if (cross < 0.2) {
                found = true
                break
            }
            j = k
        }
        val toMove: Set<Int> = if (found) movedSet else setOf(start)
        val cand = pts.mapIndexed { idx, p ->
            if (idx in toMove) {
                Pt(round2(p.x + ux * delta * sgn), round2(p.y + uy * delta * sgn))
            } else {
                p
            }
        }
        if (!overlapsOthers(cand)) {
            pushUndo()
            room = room.copy(points = cand)
            clampOpenings()
        }
    }

    /** Длины двух стен из выбранного угла: угол стоит на месте, дальние концы тянутся. */
    fun applyEdgeLengths(prevLen: Double, nextLen: Double) {
        val sel = selection as? Selection.Vertex ?: return
        val n = room.points.size
        if (n < 3 || sel.i !in 0 until n) return
        if (prevLen > 0.05) setEdgeLength((sel.i - 1 + n) % n, prevLen, moveEnd = false)
        if (nextLen > 0.05) setEdgeLength(sel.i, nextLen, moveEnd = true)
    }

    /** Плотность экрана: пороги захвата пальцем задаются в dp и умножаются на неё. */
    private var uiScale = 1f

    fun updateUiScale(v: Float) { uiScale = v.coerceIn(0.5f, 5f) }

    var selection by mutableStateOf<Selection?>(null)
        private set

    var view by mutableStateOf(ViewTransform())
        private set

    var hintVisible by mutableStateOf(true)
        private set

    var projectName by mutableStateOf("")

    var projects by mutableStateOf<List<ProjectMeta>>(emptyList())
        private set

    var suggestions by mutableStateOf<List<LayoutSuggester.Suggestion>?>(null)
        private set

    /** Размер холста в пикселях; обычное поле, не состояние (нужно только для fit/жестов). */
    var canvasSize: Size = Size.Zero
    private var lastFitSize: Size = Size.Zero

    // ---------- производные ----------

    // ---------- зоны: участки со своей плиткой внутри комнаты ----------

    var zones by mutableStateOf(listOf<ZoneDto>())
        private set

    /** Выбранная зона: пока она выбрана, настройки плитки применяются к ней. */
    var activeZone by mutableStateOf(-1)
        private set

    fun updateActiveZone(i: Int) { activeZone = if (i in zones.indices) i else -1 }

    /** Резервный способ добавить зону; основной — кисть размера. */
    fun addZone() {
        pushUndo()
        val c = roomCenter()
        val z = ZoneDto(
            x = round2(c.x - 0.6),
            y = round2(c.y - 0.6),
            w = 1.2,
            h = 1.2,
            tile = TileSpec(300.0, 300.0, tile.groutMm),
            pattern = PatternSpec(),
        )
        zones = zones + z
        activeZone = zones.lastIndex
        selection = null
    }

    fun deleteActiveZone() {
        if (activeZone !in zones.indices) return
        pushUndo()
        val list = zones.toMutableList()
        list.removeAt(activeZone)
        zones = list
        activeZone = -1
    }

    private fun updateZone(i: Int, f: (ZoneDto) -> ZoneDto) {
        if (i !in zones.indices) return
        val list = zones.toMutableList()
        list[i] = f(list[i])
        zones = list
    }

    /** Плитка и узор, которые сейчас редактируются: комнаты или выбранной зоны. */
    val uiTile: TileSpec get() = zones.getOrNull(activeZone)?.tile ?: tile
    val uiPattern: PatternSpec get() = zones.getOrNull(activeZone)?.pattern ?: pattern
    val uiColor: Color get() =
        zones.getOrNull(activeZone)?.let { if (it.colorArgb != -1) Color(it.colorArgb) else tileColor } ?: tileColor
    val uiVariation: Boolean get() = zones.getOrNull(activeZone)?.variation ?: variation

    /** Базовая раскладка: зоны вычтены из неё как отверстия, поэтому стык режется честно. */
    val layout: LayoutResult by derivedStateOf {
        val holes = room.cutouts + zones.map { Cutout(it.x, it.y, it.w, it.h) }
        TilingEngine.build(room.copy(cutouts = holes), tile, pattern)
    }

    /** Раскладки зон: контур зоны = комната ∩ прямоугольник зоны. */
    val zoneLayouts: List<Pair<ZoneDto, LayoutResult>> by derivedStateOf {
        zones.mapNotNull { z ->
            val poly = clipPolygonByRect(room.points, z.x, z.y, z.x + z.w, z.y + z.h)
            if (poly.size < 3) {
                null
            } else {
                val inner = room.cutouts.filter {
                    it.x < z.x + z.w && it.x + it.w > z.x && it.y < z.y + z.h && it.y + it.h > z.y
                }
                z to TilingEngine.build(RoomSpec(poly, inner), z.tile, z.pattern)
            }
        }
    }

    /** Сколько плитки покупать по каждому формату: «600×600» → штук с запасом. */
    fun countsByFormat(): List<Pair<String, Int>> {
        val map = LinkedHashMap<String, Int>()
        fun add(t: TileSpec, count: Int) {
            val key = t.widthMm.toInt().toString() + "×" + t.heightMm.toInt()
            map[key] = (map[key] ?: 0) + count
        }
        add(tile, layout.totalCount)
        zoneLayouts.forEach { (z, l) -> add(z.tile, l.totalCount) }
        return map.map { (k, v) -> k to ceil(v * (1 + reservePct / 100.0)).toInt() }
    }

    /** Индексы декоративных плиток текущей раскладки. */
    // ---------- подложка: фото чертежа под рабочей областью ----------

    /** Фото плана (живёт в рамках сеанса — картинку в проект не пишем). */
    var planImage by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Мировые координаты левого верхнего угла подложки. */
    var planOrigin by mutableStateOf(Pt(0.0, 0.0))
        private set

    /** Масштаб подложки: метров на пиксель картинки. */
    var planMPerPx by mutableStateOf(0.01)
        private set

    var planAlpha by mutableStateOf(0.45f)
        private set

    /** Режим перетаскивания подложки. */
    var planMove by mutableStateOf(false)
        private set

    /** Размеры, прочитанные с чертежа: значение в метрах + точка в МИРОВЫХ координатах. */
    var ocrNumbers by mutableStateOf(listOf<Triple<Double, Pt, Boolean>>())
        private set

    var ocrBusy by mutableStateOf(false)
        private set

    /** Читает числа на подложке. Печатные чертежи — уверенно, рукописные — как получится. */
    fun runPlanOcr() {
        val src = planImage ?: return
        if (ocrBusy) return
        ocrBusy = true
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.Default) {
                val full = src.asAndroidBitmap()
                val maxSide = 1600
                val k = minOf(1.0, maxSide.toDouble() / maxOf(full.width, full.height))
                val bmp = if (k < 1.0) {
                    android.graphics.Bitmap.createScaledBitmap(
                        full,
                        (full.width * k).toInt().coerceAtLeast(8),
                        (full.height * k).toInt().coerceAtLeast(8),
                        true,
                    )
                } else {
                    full
                }
                bmp to k
            }
            runCatching {
                PlanOcr.read(prepared.first) { list ->
                    val k = prepared.second
                    ocrNumbers = list.map {
                        Triple(
                            it.meters,
                            Pt(
                                planOrigin.x + it.cx / k * planMPerPx,
                                planOrigin.y + it.cy / k * planMPerPx,
                            ),
                            it.horizontal,
                        )
                    }
                    ocrBusy = false
                    if (ocrNumbers.isEmpty()) toast(R.string.ocr_none)
                }
            }.onFailure {
                ocrBusy = false
                toast(R.string.ocr_none)
            }
        }
    }

    fun clearOcr() { ocrNumbers = emptyList() }

    /** Числа с чертежа рядом с точкой — для подсказок в диалогах длин. */
    fun ocrNear(p: Pt, limit: Int = 6): List<Double> =
        ocrNumbers
            .sortedBy { (it.second.x - p.x) * (it.second.x - p.x) + (it.second.y - p.y) * (it.second.y - p.y) }
            .take(limit)
            .map { it.first }

    /** Режим автообводки: касание внутри помещения на фото строит контур. */
    var traceMode by mutableStateOf(false)
        private set

    fun toggleTraceMode() {
        traceMode = !traceMode
        if (traceMode) {
            calibMode = false
            planMove = false
        }
    }

    /**
     * Обводит помещение по фото: точка касания переводится в пиксели подложки,
     * PlanTracer возвращает контур, он переводится обратно в метры.
     */
    fun autoTrace(world: Pt) {
        val src = planImage ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                val full = src.asAndroidBitmap()
                val maxSide = 700
                val k = minOf(1.0, maxSide.toDouble() / maxOf(full.width, full.height))
                val bw = (full.width * k).toInt().coerceAtLeast(8)
                val bh = (full.height * k).toInt().coerceAtLeast(8)
                val small = if (k < 1.0) {
                    android.graphics.Bitmap.createScaledBitmap(full, bw, bh, true)
                } else {
                    full
                }
                val px = IntArray(bw * bh)
                small.getPixels(px, 0, bw, 0, 0, bw, bh)
                // мир → пиксели уменьшенной картинки
                val sxp = ((world.x - planOrigin.x) / planMPerPx * k).toInt()
                val syp = ((world.y - planOrigin.y) / planMPerPx * k).toInt()
                PlanTracer.trace(px, bw, bh, sxp, syp) to k
            }
            val traced = res.first
            val k = res.second
            if (traced == null || traced.points.size < 3) {
                traceMode = false
                toast(R.string.plan_trace_fail)
                return@launch
            }
            val pts = traced.points.map {
                Pt(
                    round2(planOrigin.x + it[0] / k * planMPerPx),
                    round2(planOrigin.y + it[1] / k * planMPerPx),
                )
            }
            if (overlapsOthers(pts)) {
                traceMode = false
                toast(R.string.plan_trace_fail)
                return@launch
            }
            pushUndo()
            room = RoomSpec(pts, emptyList())
            selection = null
            suggestions = null
            traceMode = false
            reanchor()
            fit()
        }
    }

    /** Режим калибровки: два касания задают отрезок известной длины. */
    var calibMode by mutableStateOf(false)
        private set

    var calibA by mutableStateOf<Pt?>(null)
        private set

    var calibB by mutableStateOf<Pt?>(null)
        private set

    var calibDialog by mutableStateOf(false)
        private set

    fun loadPlanImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri) } ?: return@launch
            planImage = bmp
            // по умолчанию подложка шириной ~8 м, вписана рядом с началом координат
            planMPerPx = 8.0 / bmp.width.coerceAtLeast(1)
            planOrigin = Pt(0.0, 0.0)
            planMove = false
            calibMode = false
            calibA = null
            calibB = null
        }
    }

    fun clearPlanImage() {
        planImage = null
        ocrNumbers = emptyList()
        planMove = false
        calibMode = false
        calibA = null
        calibB = null
    }

    fun updatePlanAlpha(a: Float) { planAlpha = a.coerceIn(0.15f, 1f) }

    fun togglePlanMove() {
        planMove = !planMove
        if (planMove) calibMode = false
    }

    fun startCalibration() {
        calibMode = true
        planMove = false
        calibA = null
        calibB = null
    }

    fun addCalibPoint(p: Pt) {
        if (calibA == null) {
            calibA = p
        } else if (calibB == null) {
            calibB = p
            calibDialog = true
        }
    }

    fun updateCalibDialog(v: Boolean) {
        calibDialog = v
        if (!v) {
            calibMode = false
            calibA = null
            calibB = null
        }
    }

    /** Приводит масштаб подложки так, чтобы отрезок калибровки стал заданной длины. */
    fun applyCalibration(realM: Double) {
        val a = calibA
        val b = calibB
        if (a == null || b == null || realM <= 0.01) return
        val d0 = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
        if (d0 < 1e-6) return
        val k = realM / d0
        planMPerPx *= k
        // масштабируем вокруг первой точки, чтобы она осталась на месте
        planOrigin = Pt(a.x + (planOrigin.x - a.x) * k, a.y + (planOrigin.y - a.y) * k)
        calibMode = false
        calibA = null
        calibB = null
        calibDialog = false
    }

    /** Толщина стен: показывается на плане и даёт стенам объём в 3D. */
    var wallThicknessM by mutableStateOf(0.10)
        private set

    fun updateWallThickness(m: Double) { wallThicknessM = m.coerceIn(0.02, 0.6) }

    /** Толщина отдельных стен: «wall-3» → метры. Остальные берут общую. */
    var wallThickness by mutableStateOf(mapOf<String, Double>())
        private set

    fun wallThicknessOf(id: String): Double = wallThickness[id] ?: wallThicknessM

    /**
     * Пересчёт ключей wall-N после изменения числа вершин контура. Вставка вершин
     * внутрь ребра сдвигает номера всех ПОСЛЕДУЮЩИХ стен на shift; удаление вершины
     * (shift < 0) отбрасывает записи схлопнувшейся стены. Без этого толщина, проёмы
     * и отделка «переезжали» на чужие стены после ниши, скругления или удаления угла —
     * то самое «толщина как-то неправильно работает».
     */
    private fun remapWallKeys(fromEdge: Int, shift: Int) {
        if (shift == 0) return
        fun <T> remap(src: Map<String, T>): Map<String, T> {
            if (src.isEmpty()) return src
            val out = mutableMapOf<String, T>()
            src.forEach { (k, v) ->
                if (!k.startsWith("wall-")) {
                    out[k] = v
                    return@forEach
                }
                val num = k.removePrefix("wall-").toIntOrNull()
                if (num == null) {
                    out[k] = v
                    return@forEach
                }
                val idx = num - 1
                when {
                    idx < fromEdge -> out[k] = v
                    shift < 0 && idx < fromEdge - shift -> Unit
                    else -> out["wall-" + (idx + shift + 1)] = v
                }
            }
            return out
        }
        wallThickness = remap(wallThickness)
        openings = remap(openings)
        openingKinds = remap(openingKinds)
        finishes = remap(finishes)
    }

    /**
     * Пороги комнаты: дверные проёмы (не окна) на стенах, за которыми лежит другая
     * комната. Комнаты — отдельные контуры, зазор толщины стены между ними никому
     * не принадлежал, и плитка в проёме не считалась вовсе. Порог приписывается той
     * комнате, на чьей стене создан проём — ставь дверь на одну из двух стен, не на обе.
     */
    private fun thresholdStripsFor(
        spec: RoomSpec,
        opens: Map<String, List<Cutout>>,
        kindsMap: Map<String, List<Int>>,
        thick: Map<String, Double>,
        selfIdx: Int,
    ): List<ThresholdStrip> {
        val res = ArrayList<ThresholdStrip>()
        val ptsR = spec.points
        if (ptsR.size < 3) return res
        for (i in ptsR.indices) {
            val id = "wall-" + (i + 1)
            val listO = opens[id] ?: continue
            if (listO.isEmpty()) continue
            val a = ptsR[i]
            val b = ptsR[(i + 1) % ptsR.size]
            val ex = b.x - a.x
            val ey = b.y - a.y
            val len = sqrt(ex * ex + ey * ey)
            if (len < 1e-6) continue
            val ux = ex / len
            val uy = ey / len
            var nx = ey / len
            var ny = -ex / len
            val mid = Pt(a.x + ex / 2, a.y + ey / 2)
            if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), ptsR)) {
                nx = -nx
                ny = -ny
            }
            val th = thick[id] ?: wallThicknessM
            val kinds = kindsMap[id] ?: emptyList()
            listO.forEachIndexed { oi, o ->
                val kind = kinds.getOrNull(oi)
                    ?: if (o.y < 0.05) OPENING_DOOR else OPENING_WINDOW
                if (kind == OPENING_WINDOW) return@forEachIndexed
                val cx = a.x + ux * (o.x + o.w / 2) + nx * (th + 0.02)
                val cy = a.y + uy * (o.x + o.w / 2) + ny * (th + 0.02)
                val interior = rooms.indices.any { ri ->
                    ri != selfIdx && rooms[ri].spec.points.size >= 3 &&
                        pointInPolygon(Pt(cx, cy), rooms[ri].spec.points)
                }
                if (interior) {
                    res.add(
                        ThresholdStrip(
                            i, round2(a.x + ux * o.x), round2(a.y + uy * o.x),
                            ux, uy, nx, ny, o.w, th,
                        ),
                    )
                }
            }
        }
        return res
    }

    /** Пороги активной комнаты — для плана, карточек и покупки. */
    val thresholdStrips: List<ThresholdStrip> by derivedStateOf {
        thresholdStripsFor(room, openings, openingKinds, wallThickness, activeRoom)
    }

    val thresholdAreaM2: Double get() = thresholdStrips.sumOf { it.w * it.th }

    /** Сколько подрезок уйдёт на пороги: раскладка по длинной стороне плитки. */
    val thresholdPieces: Int get() {
        if (thresholdStrips.isEmpty()) return 0
        val stepLong = max(tile.widthMm, tile.heightMm) / 1000.0 +
            max(0.0, tile.groutMm) / 1000.0
        return thresholdStrips.sumOf { ceil(it.w / stepLong).toInt() }
    }

    // ---------- плинтус ----------

    /** Режим плинтуса: 0 — хлысты, 1 — полосы из плитки. */
    var skirtMode by mutableStateOf(0)
        private set

    var skirtBarLenM by mutableStateOf(2.5)
        private set

    var skirtHeightMm by mutableStateOf(80.0)
        private set

    fun switchSkirtMode(m: Int) { skirtMode = m.coerceIn(0, 1) }

    fun setSkirtBarLen(v: Double) { skirtBarLenM = v.coerceIn(0.5, 6.0) }

    fun setSkirtHeight(v: Double) { skirtHeightMm = v.coerceIn(30.0, 200.0) }

    /**
     * План плинтуса активной комнаты: сегменты по стенам (двери рвут), распил по
     * хлыстам с остатками. В режиме «из плитки» хлыст = полоса длиной в длинную
     * сторону плитки.
     */
    val skirtPlan: SkirtPlan by derivedStateOf {
        val segs = SkirtingCalc.segments(room.points, openings)
        val bar = if (skirtMode == 1) {
            max(tile.widthMm, tile.heightMm) / 1000.0
        } else {
            skirtBarLenM
        }
        SkirtingCalc.plan(segs, bar)
    }

    /** Полос заданной высоты из одной целой плитки. */
    val skirtStripsPerTile: Int get() =
        (min(tile.widthMm, tile.heightMm) / skirtHeightMm).toInt().coerceAtLeast(1)

    /**
     * Совет: полосы плинтуса из обрезков раскладки. Прямые резы оставляют кусок
     * «срезано × целая сторона» — из него режутся полосы высоты плинтуса.
     * Возвращает (полос, метров).
     */
    val skirtFromOffcuts: Pair<Int, Double> get() {
        var count = 0
        var meters = 0.0
        if (pairCuts && cutPairs.bins.isNotEmpty()) {
            // парование включено: полосы режутся из того, что РЕАЛЬНО остаётся
            // от каждой плитки-«бина» — иначе один материал считался бы дважды
            cutPairs.bins.forEach { b ->
                val strips = (b.restMm / skirtHeightMm).toInt()
                if (strips > 0) {
                    count += strips
                    meters += strips * b.stripLenMm / 1000.0
                }
            }
        } else {
            cutInfo.values.forEach { ci ->
                val off = ci.cutOffMm ?: return@forEach
                val strips = (off / skirtHeightMm).toInt()
                if (strips <= 0) return@forEach
                count += strips
                meters += strips * max(ci.wMm, ci.hMm) / 1000.0
            }
        }
        return count to Math.round(meters * 10.0) / 10.0
    }

    fun updateWallThicknessOf(id: String, m: Double?) {
        pushUndo()
        val map = wallThickness.toMutableMap()
        if (m == null) map.remove(id) else map[id] = m.coerceIn(0.02, 0.6)
        wallThickness = map
    }

    // ---------- панно: одна картинка на несколько плиток ----------

    var panelOn by mutableStateOf(false)
        private set

    /** Левый верхний угол панно в координатах раскладки — панно едет вместе с узором. */
    var panelRX by mutableStateOf(0.0)
        private set

    var panelRY by mutableStateOf(0.0)
        private set

    /** Ставит панно так, чтобы выбранная плитка стала его левым верхним углом. */
    fun placePanelAtSelected() {
        val sel = selection as? Selection.Tile ?: return
        val t = layout.tiles.getOrNull(sel.i) ?: return
        pushUndo()
        panelRX = t.rect.x
        panelRY = t.rect.y
        panelOn = true
    }

    fun clearPanel() {
        pushUndo()
        panelOn = false
    }

    /** Клетка панно для плитки: (столбец, строка) или null, если плитка вне панно. */
    fun panelCell(t: PlacedTile): Pair<Int, Int>? {
        if (!panelOn) return null
        val stepW = (tile.widthMm + tile.groutMm) / 1000.0
        val stepH = (tile.heightMm + tile.groutMm) / 1000.0
        if (stepW <= 1e-6 || stepH <= 1e-6) return null
        val col = ((t.rect.x - panelRX) / stepW).roundToInt()
        val row = ((t.rect.y - panelRY) / stepH).roundToInt()
        if (col < 0 || row < 0 || col >= decor.panelCols || row >= decor.panelRows) return null
        // плитка должна стоять в узле решётки, а не просто рядом
        if (abs(t.rect.x - (panelRX + col * stepW)) > stepW * 0.3) return null
        if (abs(t.rect.y - (panelRY + row * stepH)) > stepH * 0.3) return null
        return col to row
    }

    /** Слои зон для 3D, AR и PDF. */
    fun zoneLayers(): List<ExtraLayer> = zoneLayouts.map { (z, l) ->
        ExtraLayer(
            tiles = l.tiles,
            colorArgb = if (z.colorArgb != -1) z.colorArgb else Color(0xFFE8DFD2).toArgb(),
            variation = z.variation,
        )
    }

    /** Описание панно для отрисовки в 3D, AR и PDF. */
    fun panelInfo(): PanelInfo? =
        if (panelOn && decorImage != null) {
            PanelInfo(decor.panelCols, decor.panelRows) { t -> panelCell(t) }
        } else {
            null
        }

    /** Цвет отдельных плиток: ключ «см:см» в координатах раскладки — едет вместе с узором. */
    var tileColors by mutableStateOf(mapOf<String, Int>())
        private set

    /** Кисть размера: проводишь по полу — участок становится зоной с этим форматом. */
    var formatBrush by mutableStateOf(false)
        private set

    var brushTile by mutableStateOf(TileSpec(300.0, 300.0, 3.0))
        private set

    private var brushMinX = 0.0
    private var brushMinY = 0.0
    private var brushMaxX = 0.0
    private var brushMaxY = 0.0
    private var brushHit = false

    fun toggleFormatBrush() {
        formatBrush = !formatBrush
        if (formatBrush) paintMode = false
    }

    fun updateBrushTile(t: TileSpec) {
        brushTile = t
        formatBrush = true
        paintMode = false
    }

    private fun brushTouch(w: Pt) {
        val t = layout.tiles.firstOrNull { pointInPolygon(w, it.corners) } ?: return
        val xs = t.corners.map { it.x }
        val ys = t.corners.map { it.y }
        if (!brushHit) {
            brushHit = true
            brushMinX = xs.min()
            brushMaxX = xs.max()
            brushMinY = ys.min()
            brushMaxY = ys.max()
        } else {
            brushMinX = minOf(brushMinX, xs.min())
            brushMaxX = maxOf(brushMaxX, xs.max())
            brushMinY = minOf(brushMinY, ys.min())
            brushMaxY = maxOf(brushMaxY, ys.max())
        }
    }

    /** Заканчивает мазок: из охваченных плиток получается зона со своим форматом. */
    private fun brushFinish() {
        if (!brushHit) return
        brushHit = false
        val w = round2(brushMaxX - brushMinX)
        val h = round2(brushMaxY - brushMinY)
        if (w < 0.05 || h < 0.05) return
        pushOnce()
        zones = zones + ZoneDto(
            x = round2(brushMinX),
            y = round2(brushMinY),
            w = w,
            h = h,
            tile = brushTile,
            pattern = PatternSpec(type = pattern.type),
        )
        activeZone = zones.lastIndex
        formatBrush = false
    }

    /** Режим кисти: проводишь пальцем — плитки красятся выбранным цветом. */
    var paintMode by mutableStateOf(false)
        private set

    var paintColor by mutableStateOf(Color(0xFF8FB8E8).toArgb())
        private set

    fun togglePaintMode() { paintMode = !paintMode }

    fun updatePaintColor(c: Int) {
        paintColor = c
        paintMode = true
    }

    /** Красит плитку под пальцем; повторное касание уже покрашенной снимает цвет. */
    fun paintTileAt(w: Pt, erase: Boolean = false) {
        val idx = layout.tiles.indexOfFirst { pointInPolygon(w, it.corners) }
        if (idx < 0) return
        val key = tileKey(layout.tiles[idx])
        val m = tileColors.toMutableMap()
        if (erase) {
            m.remove(key)
        } else if (m[key] == paintColor) {
            m.remove(key)
        } else {
            m[key] = paintColor
        }
        tileColors = m
    }

    fun colorOfTile(t: PlacedTile): Int? = tileColors[tileKey(t)]

    fun clearTileColors() {
        pushUndo()
        tileColors = emptyMap()
    }

    /** Ручной декор: ключ «см:см» начала плитки → true (декор) / false (снять авто). */
    var decorOverrides by mutableStateOf(mapOf<String, Boolean>())
        private set

    private fun tileKey(t: PlacedTile): String =
        (t.rect.x * 100).roundToInt().toString() + ":" + (t.rect.y * 100).roundToInt()

    fun toggleTileDecor() {
        val sel = selection as? Selection.Tile ?: return
        val t = layout.tiles.getOrNull(sel.i) ?: return
        pushUndo()
        val m = decorOverrides.toMutableMap()
        m[tileKey(t)] = sel.i !in decorIdx
        decorOverrides = m
    }

    /**
     * Видимые куски подрезанных плиток: единый номер (тот же в 2D, 3D, PDF, AR),
     * остаток в мм, реальная доля площади и центр видимой части.
     */
    val cutInfo: Map<Int, CutPieceInfo> by derivedStateOf {
        CutNumbering.compute(room, layout)
    }

    /** Габариты куска (a >= b, см) — для сверки со списком обрезков. */
    val cutPieceOf: Map<Int, Pair<Double, Double>> by derivedStateOf {
        cutInfo.mapValues { (_, v) -> v.aCm to v.bCm }
    }

    /** Подсветка плиток, чей остаток совпадает с выбранной строкой обрезков. */
    var highlightCut by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    fun clearHighlightCut() { highlightCut = null }

    fun toggleHighlightCut(a: Double, b: Double) {
        val v = a to b
        highlightCut = if (highlightCut == v) null else v
    }

    val decorIdx: Set<Int> by derivedStateOf {
        val auto = DecorPlanner.select(layout, tile, decor, roomCenter())
        if (decorOverrides.isEmpty()) {
            auto
        } else {
            layout.tiles.indices.filter { i ->
                when (decorOverrides[tileKey(layout.tiles[i])]) {
                    true -> true
                    false -> false
                    null -> i in auto
                }
            }.toSet()
        }
    }

    /** Перекрытие декора мебелью и плитки под ней. */
    val coverage: CoverageReport by derivedStateOf {
        CoverageAnalyzer.analyze(layout, tile, decorIdx, decor.art, furniture)
    }

    /** Подрезка по каждой стене + предупреждения. */
    val cutReport: CutReport by derivedStateOf { CutAnalyzer.analyze(room, tile, layout) }

    /**
     * Активный магнит раскладки при перетаскивании узора:
     * 0 — нет, 2 — шов по центру, 3 — плитка по центру, 4/5 — полная плитка у стены.
     */
    var patternSnapX by mutableStateOf(0)
        private set

    var patternSnapY by mutableStateOf(0)
        private set

    /** Стена, подсвеченная предупреждением о полоске; −1 — нет. */
    var warnEdge by mutableStateOf(-1)
        private set

    fun toggleWarnEdge(i: Int) { warnEdge = if (warnEdge == i) -1 else i }

    /** Взведённый тип проёма для постановки тапом по стене; −1 — выключено. */
    var placeOpeningKind by mutableStateOf(-1)
        private set

    fun armPlaceOpening(kind: Int) {
        placeOpeningKind = if (placeOpeningKind == kind) -1 else kind
    }

    /** Поставить проём выбранного типа на стену wallIdx с центром в точке sM. */
    private fun addOpeningAt(wallIdx: Int, kind: Int, sM: Double) {
        val id = "wall-" + (wallIdx + 1)
        val wall = model.walls.firstOrNull { it.id == id } ?: return
        // реальные размеры по типам — те же, что в панели «Поверхности»
        val (wM, hM, sill) = when (kind) {
            OPENING_DOOR -> Triple(0.9, 2.05, 0.0)
            OPENING_BALCONY -> Triple(0.8, 2.1, 0.0)
            OPENING_ENTRY -> Triple(1.0, 2.05, 0.0)
            OPENING_PASSAGE -> Triple(1.2, 2.1, 0.0)
            else -> Triple(1.4, 1.4, 0.9)
        }
        pushUndo()
        val x = (sM - wM / 2).coerceIn(0.0, (wall.lengthM - wM).coerceAtLeast(0.0))
        val y = sill.coerceIn(0.0, (wallHeightM - hM).coerceAtLeast(0.0))
        val kinds = openingKindsOf(id) + kind
        openings = openings + (id to (openingsOf(id) + Cutout(round2(x), round2(y), wM, hM)))
        openingKinds = openingKinds + (id to kinds)
        placeOpeningKind = -1
    }

    /** Раскрытые блоки панели (ключ → открыт); живут в рамках сеанса. */
    var foldStates by mutableStateOf(mapOf<String, Boolean>())
        private set

    fun foldOpen(key: String, default: Boolean): Boolean = foldStates[key] ?: default

    fun toggleFold(key: String, default: Boolean) {
        val cur = foldStates[key] ?: default
        foldStates = foldStates + (key to !cur)
    }

    /**
     * Исправить узкую полоску у стены одним нажатием. Пробуются три кандидата
     * сдвига по оси нормали — «полплитки у этой стены», «шов по центру»,
     * «плитка по центру» — каждый оценивается движком, и применяется тот, где
     * САМАЯ УЗКАЯ полоска по всем стенам максимальна. Так фикс не создаёт новую
     * полоску у противоположной стены. Работает при повороте узора кратном 90°.
     */
    fun fixThinEdge(edgeIndex: Int) {
        val rotN = ((pattern.rotationDeg % 360.0) + 360.0) % 360.0
        if (abs(rotN % 90.0) >= 0.01 && abs(rotN % 90.0 - 90.0) >= 0.01) return
        val e = cutReport.edges.firstOrNull { it.edgeIndex == edgeIndex } ?: return
        val pts = room.points
        if (edgeIndex !in pts.indices) return
        val a = pts[edgeIndex]
        val b = pts[(edgeIndex + 1) % pts.size]
        val ex = b.x - a.x
        val ey = b.y - a.y
        val len = sqrt(ex * ex + ey * ey)
        if (len < 1e-6) return
        var nx = -ey / len
        var ny = ex / len
        val mid = Pt((a.x + b.x) / 2 + nx * 0.01, (a.y + b.y) / 2 + ny * 0.01)
        if (!pointInPolygon(mid, pts)) {
            nx = -nx
            ny = -ny
        }
        val swap = abs(rotN % 180.0 - 90.0) < 0.01
        val horiz = abs(nx) > abs(ny)
        val step = (
            (if (horiz != swap) tile.widthMm else tile.heightMm) + max(0.0, tile.groutMm)
            ) / 1000.0
        if (step < 1e-6) return
        val minx = pts.minOf { it.x }
        val maxx = pts.maxOf { it.x }
        val miny = pts.minOf { it.y }
        val maxy = pts.maxOf { it.y }
        val axisV = if (horiz) pattern.offsetX else pattern.offsetY
        val center = if (horiz) (minx + maxx) / 2 else (miny + maxy) / 2
        fun cong(target: Double): Double {
            var df = (target - axisV) % step
            if (df > step / 2) df -= step
            if (df < -step / 2) df += step
            return df
        }
        val sgnN = if ((if (horiz) nx else ny) >= 0) 1.0 else -1.0
        val half = step / 2 - e.minStripM
        val cands = ArrayList<Double>()
        if (half > 0.001) cands.add(half * sgnN)
        cands.add(cong(center))
        cands.add(cong(center - step / 2))
        var bestD = 0.0
        var bestScore = -1.0
        for (dv in cands) {
            if (abs(dv) < 0.0005) continue
            val cand = if (horiz) {
                pattern.copy(offsetX = pattern.offsetX + dv)
            } else {
                pattern.copy(offsetY = pattern.offsetY + dv)
            }
            val rep2 = CutAnalyzer.analyze(room, tile, TilingEngine.build(room, tile, cand))
            val score = rep2.warnings.filter { it.code == "THIN_STRIP" }
                .minOfOrNull { it.valueCm } ?: 999.0
            if (score > bestScore + 1e-9 ||
                (abs(score - bestScore) < 1e-9 && abs(dv) < abs(bestD))
            ) {
                bestScore = score
                bestD = dv
            }
        }
        if (abs(bestD) < 0.0005) return
        pushUndo()
        anchor = AnchorMode.FREE
        pattern = if (horiz) {
            pattern.copy(offsetX = round2(pattern.offsetX + bestD))
        } else {
            pattern.copy(offsetY = round2(pattern.offsetY + bestD))
        }
        warnEdge = -1
    }

    /**
     * Парование резов: прямые полосы пакуются попарно в одну плитку (с пропилом
     * 4 мм). Ёлочка не паруется. bins — номера подрезок из одной плитки.
     */
    val cutPairs: CutPairs by derivedStateOf {
        if (pattern.type == PatternType.HERRINGBONE) return@derivedStateOf CutPairs(emptyList(), 0)
        val kerf = 4.0
        data class P(val num: Int, val width: Double, val poolH: Boolean)
        val ps = ArrayList<P>()
        cutInfo.values.forEach { ci ->
            if (ci.cutOffMm == null) return@forEach
            val fullW = ci.wMm >= tile.widthMm - 2.0
            val fullH = ci.hMm >= tile.heightMm - 2.0
            when {
                fullW && !fullH -> ps.add(P(ci.number, ci.hMm, true))
                fullH && !fullW -> ps.add(P(ci.number, ci.wMm, false))
            }
        }
        fun pack(items: List<P>, capMm: Double, stripLenMm: Double): List<CutBin> {
            val sorted = items.sortedByDescending { it.width }
            val bins = ArrayList<MutableList<Int>>()
            val rest = ArrayList<Double>()
            for (pc in sorted) {
                var put = false
                for (i in bins.indices) {
                    if (rest[i] >= pc.width + kerf) {
                        bins[i].add(pc.num)
                        rest[i] -= pc.width + kerf
                        put = true
                        break
                    }
                }
                if (!put) {
                    bins.add(mutableListOf(pc.num))
                    rest.add(capMm - pc.width)
                }
            }
            return bins.mapIndexed { i, nums ->
                CutBin(nums.toList(), rest[i].coerceAtLeast(0.0), stripLenMm)
            }
        }
        val binsH = pack(ps.filter { it.poolH }, tile.heightMm, tile.widthMm)
        val binsW = pack(ps.filter { !it.poolH }, tile.widthMm, tile.heightMm)
        val saved = (binsH.sumOf { it.nums.size } - binsH.size) +
            (binsW.sumOf { it.nums.size } - binsW.size)
        CutPairs(binsH + binsW, saved)
    }

    // ---------- работы по квартире ----------

    /** Статусы работ: ключ → 0 план · 1 в работе · 2 готово. */
    var workStatus by mutableStateOf(mapOf<String, Int>())
        private set

    fun workStatusOf(key: String): Int = workStatus[key] ?: 0

    fun cycleWorkStatus(key: String) {
        workStatus = workStatus + (key to (workStatusOf(key) + 1) % 3)
    }

    /** Площадь стены комнаты за вычетом проёмов (для чек-листа работ). */
    private fun wallAreaOf(spec: RoomSpec, opens: Map<String, List<Cutout>>, i: Int): Double {
        val pts = spec.points
        if (i !in pts.indices) return 0.0
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
        var area = len * wallHeightM
        (opens["wall-" + (i + 1)] ?: emptyList()).forEach { o ->
            val hw = min(o.w, len)
            val hh = min(o.h, (wallHeightM - o.y).coerceAtLeast(0.0))
            area -= (hw * hh).coerceAtLeast(0.0)
        }
        return area.coerceAtLeast(0.0)
    }

    /**
     * Чек-лист «Работы» по всей квартире: пол-плитка, плинтус, отделка каждой
     * стены (краска/обои/плитка) и потолок — с объёмами и материалами. Первый шаг
     * курса «программа закрывает весь дизайн и работы в квартире».
     */
    fun worksList(): List<WorkRow> {
        val app = getApplication<Application>()
        val out = ArrayList<WorkRow>()
        val m2 = app.getString(R.string.unit_m2)
        val pcs = app.getString(R.string.pcs)
        val mU = app.getString(R.string.unit_m)
        rooms.forEachIndexed { i, r ->
            val roomName = r.name.ifBlank { app.getString(R.string.room_n, i + 1) }
            val t = if (i == activeRoom) tile else r.tile
            val lay = if (i == activeRoom) layout else TilingEngine.build(r.spec, r.tile, r.pattern)
            val spec = if (i == activeRoom) room else r.spec
            val fins = if (i == activeRoom) finishes else r.finishes
            val opens = if (i == activeRoom) openings else r.openings
            // пол — плитка
            if ((fins["floor"] ?: Finish.TILE) == Finish.TILE) {
                val strips = if (i == activeRoom) {
                    thresholdStrips
                } else {
                    thresholdStripsFor(r.spec, r.openings, r.openingKinds, r.wallThickness, i)
                }
                val stepLong = max(t.widthMm, t.heightMm) / 1000.0 + max(0.0, t.groutMm) / 1000.0
                val tp = strips.sumOf { ceil(it.w / stepLong).toInt() }
                val cnt = ceil((lay.totalCount + tp) * (1 + reservePct / 100.0)).toInt()
                out.add(
                    WorkRow(
                        "r$i.floor", i,
                        roomName + " · " + app.getString(R.string.work_floor),
                        String.format(
                            Locale.getDefault(), "%.1f", lay.areaM2 + strips.sumOf { it.w * it.th },
                        ) + " " + m2 + " · " + cnt + " " + pcs + " " +
                            t.widthMm.toInt() + "×" + t.heightMm.toInt(),
                    ),
                )
            }
            // плинтус
            val segs = SkirtingCalc.segments(spec.points, opens)
            if (segs.isNotEmpty()) {
                val bar = if (skirtMode == 1) max(t.widthMm, t.heightMm) / 1000.0 else skirtBarLenM
                val plan = SkirtingCalc.plan(segs, bar)
                val detail = if (skirtMode == 1) {
                    val perTile = (min(t.widthMm, t.heightMm) / skirtHeightMm).toInt().coerceAtLeast(1)
                    val tilesN = ceil(plan.bars.size.toDouble() / perTile).toInt()
                    String.format(Locale.getDefault(), "%.1f", plan.totalM) + " " + mU +
                        " · " + tilesN + " " + app.getString(R.string.skirt_tiles_cnt)
                } else {
                    String.format(Locale.getDefault(), "%.1f", plan.totalM) + " " + mU +
                        " · " + plan.bars.size + " × " +
                        String.format(Locale.getDefault(), "%.1f", plan.barLenM) + " " + mU
                }
                out.add(
                    WorkRow(
                        "r$i.skirt", i,
                        roomName + " · " + app.getString(R.string.need_plinth),
                        detail,
                    ),
                )
            }
            // стены: краска / обои / плитка
            for (w in spec.points.indices) {
                val id = "wall-" + (w + 1)
                val fin = fins[id] ?: Finish.NONE
                if (fin == Finish.NONE) continue
                val a = wallAreaOf(spec, opens, w)
                if (a < 0.05) continue
                val pts = spec.points
                val bpt = pts[(w + 1) % pts.size]
                val wallLen = sqrt(
                    (bpt.x - pts[w].x) * (bpt.x - pts[w].x) +
                        (bpt.y - pts[w].y) * (bpt.y - pts[w].y),
                )
                val finName = app.getString(
                    when (fin) {
                        Finish.TILE -> R.string.finish_tile
                        Finish.WALLPAPER -> R.string.finish_wallpaper
                        else -> R.string.finish_paint
                    },
                )
                val matDetail = when (fin) {
                    Finish.PAINT -> String.format(
                        Locale.getDefault(), "%.1f", MaterialCalc.paintLiters(a, 2),
                    ) + " " + app.getString(R.string.liters_short)
                    Finish.WALLPAPER ->
                        MaterialCalc.wallpaper(wallLen, wallHeightM).rolls.toString() +
                            " " + app.getString(R.string.rolls_short)
                    else -> ceil(
                        a / (t.widthMm * t.heightMm / 1e6) * (1 + reservePct / 100.0),
                    ).toInt().toString() + " " + pcs
                }
                out.add(
                    WorkRow(
                        "r$i.$id", i,
                        roomName + " · " + app.getString(R.string.wall) + " " + (w + 1) +
                            " — " + finName,
                        String.format(Locale.getDefault(), "%.1f", a) + " " + m2 +
                            " · " + matDetail,
                    ),
                )
            }
            // потолок
            val cf = fins["ceiling"] ?: Finish.NONE
            if (cf == Finish.PAINT) {
                out.add(
                    WorkRow(
                        "r$i.ceiling", i,
                        roomName + " · " + app.getString(R.string.work_ceiling),
                        String.format(Locale.getDefault(), "%.1f", lay.areaM2) + " " + m2 +
                            " · " + String.format(
                            Locale.getDefault(), "%.1f", MaterialCalc.paintLiters(lay.areaM2, 2),
                        ) + " " + app.getString(R.string.liters_short),
                    ),
                )
            }
        }
        return out
    }

    /** Учитывать парование в покупке. */
    var pairCuts by mutableStateOf(true)
        private set

    fun togglePairCuts() { pairCuts = !pairCuts }

    /** Плиток на подрезки с учётом парования. */
    val pairedCutTiles: Int get() =
        (layout.cutCount - if (pairCuts) cutPairs.saved else 0).coerceAtLeast(0)

    fun roomCenter(): Pt {
        val pts = room.points
        if (pts.isEmpty()) return Pt(0.0, 0.0)
        return Pt(
            (pts.minOf { it.x } + pts.maxOf { it.x }) / 2,
            (pts.minOf { it.y } + pts.maxOf { it.y }) / 2,
        )
    }

    val buyCount: Int get() =
        ceil((layout.fullCount + pairedCutTiles + thresholdPieces) * (1 + reservePct / 100.0)).toInt()

    val buyM2: Double get() = buyCount * tile.widthMm * tile.heightMm / 1e6

    // ---------- история изменений ----------

    private data class Snap(
        val room: RoomSpec,
        val tile: TileSpec,
        val pattern: PatternSpec,
        val tileColor: Color,
        val variation: Boolean,
        val reservePct: Int,
        val decor: DecorSpec,
        val anchor: AnchorMode,
        val furniture: List<Furniture>,
        val finishes: Map<String, Finish>,
        val openings: Map<String, List<Cutout>>,
        val openingKinds: Map<String, List<Int>>,
        val wallHeightM: Double,
        val wallThicknessM: Double,
        val rooms: List<RoomDto>,
        val activeRoom: Int,
        val decorOverrides: Map<String, Boolean>,
        val panelOn: Boolean,
        val panelRX: Double,
        val panelRY: Double,
        val zones: List<ZoneDto>,
        val tileColors: Map<String, Int>,
        val wallThickness: Map<String, Double>,
    )

    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private fun snap() = Snap(
        room, tile, pattern, tileColor, variation, reservePct,
        decor, anchor, furniture, finishes, openings, openingKinds, wallHeightM, wallThicknessM,
        rooms, activeRoom, decorOverrides, panelOn, panelRX, panelRY, zones, tileColors, wallThickness,
    )

    /** Запомнить состояние перед изменением. */
    fun pushUndo() {
        undoStack.addLast(snap())
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    private fun restore(s: Snap) {
        room = s.room
        tile = s.tile
        pattern = s.pattern
        tileColor = s.tileColor
        variation = s.variation
        reservePct = s.reservePct
        decor = s.decor
        anchor = s.anchor
        furniture = s.furniture
        finishes = s.finishes
        openings = s.openings
        openingKinds = s.openingKinds
        wallHeightM = s.wallHeightM
        wallThicknessM = s.wallThicknessM
        rooms = s.rooms
        activeRoom = s.activeRoom
        decorOverrides = s.decorOverrides
        panelOn = s.panelOn
        panelRX = s.panelRX
        panelRY = s.panelRY
        zones = s.zones
        tileColors = s.tileColors
        wallThickness = s.wallThickness
        selection = null
        suggestions = null
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snap())
        restore(s)
        canUndo = undoStack.isNotEmpty()
        canRedo = true
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snap())
        restore(s)
        canRedo = redoStack.isNotEmpty()
        canUndo = true
    }

    // ---------- преобразования координат ----------

    fun toWorld(o: Offset): Pt = Pt(
        ((o.x - view.offset.x) / view.scale).toDouble(),
        ((o.y - view.offset.y) / view.scale).toDouble(),
    )

    fun toScreen(p: Pt): Offset = Offset(
        (p.x * view.scale + view.offset.x).toFloat(),
        (p.y * view.scale + view.offset.y).toFloat(),
    )

    private var didInitialFit = false

    fun maybeInitialFit() {
        // смена раскладки (телефон ↔ планшет) сильно меняет холст — вписываем заново
        if (didInitialFit && canvasSize.width > 0f && lastFitSize.width > 0f &&
            (kotlin.math.abs(canvasSize.width - lastFitSize.width) > lastFitSize.width * 0.25f ||
                kotlin.math.abs(canvasSize.height - lastFitSize.height) > lastFitSize.height * 0.25f)
        ) {
            lastFitSize = canvasSize
            fit()
        }
        if (!didInitialFit && canvasSize.width > 0f && canvasSize.height > 0f) {
            didInitialFit = true
            fit()
        }
    }

    /** Вписать план комнаты в холст. */
    fun fit() {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f || room.points.isEmpty()) return
        lastFitSize = canvasSize
        val allPts = room.points +
            rooms.filterIndexed { i, _ -> i != activeRoom }.flatMap { it.spec.points }
        val minx = allPts.minOf { it.x }
        val maxx = allPts.maxOf { it.x }
        val miny = allPts.minOf { it.y }
        val maxy = allPts.maxOf { it.y }
        val bw = max(0.5, maxx - minx)
        val bh = max(0.5, maxy - miny)
        val s = max(
            12f,
            min(
                ((canvasSize.width - 76f) / bw).toFloat(),
                ((canvasSize.height - 96f) / bh).toFloat(),
            ),
        )
        val ox = canvasSize.width / 2f - ((minx + maxx) / 2 * s).toFloat()
        val oy = canvasSize.height / 2f - ((miny + maxy) / 2 * s).toFloat()
        view = ViewTransform(s, Offset(ox, oy))
    }

    // ---------- жесты ----------

    private enum class Drag { NONE, PAN, PATTERN, VERTEX, CUT_MOVE, CUT_RESIZE, FURN_MOVE, FURN_RESIZE, ROOM_MOVE, PLAN_MOVE, ZONE_MOVE, ZONE_RESIZE, PAINT, FORMAT, OPENING_MOVE }

    private var drag = Drag.NONE
    private var dragIndex = -1
    private var dragWall = -1
    private var grabDx = 0.0
    private var grabDy = 0.0
    private var movedFurn: List<Int> = emptyList()
    private var pendingSwitch = -1
    private var panMoved = false
    private var tappedTile = -1
    private var patternMoved = false

    private var editSnapPushed = false

    /** Снимок для отмены — один раз на жест, сколько бы веток ни сработало. */
    private fun pushOnce() {
        if (!editSnapPushed) {
            pushUndo()
            editSnapPushed = true
        }
    }

    fun gestureDown(pos: Offset) {
        hintVisible = false
        drag = Drag.NONE
        dragIndex = -1
        editSnapPushed = false
        val w = toWorld(pos)
        if (formatBrush && !roomMode) {
            brushHit = false
            brushTouch(Pt(w.x, w.y))
            drag = Drag.FORMAT
            return
        }
        if (paintMode && !roomMode) {
            pushOnce()
            paintTileAt(Pt(w.x, w.y))
            drag = Drag.PAINT
            return
        }
        if (traceMode && planImage != null) {
            autoTrace(Pt(w.x, w.y))
            drag = Drag.NONE
            return
        }
        if (calibMode && planImage != null) {
            addCalibPoint(Pt(w.x, w.y))
            drag = Drag.NONE
            return
        }
        if (planMove && planImage != null) {
            drag = Drag.PLAN_MOVE
            grabDx = w.x - planOrigin.x
            grabDy = w.y - planOrigin.y
            return
        }
        if (drawMode) {
            if (drawPts.size >= 3 &&
                (toScreen(drawPts.first()) - pos).getDistance() < 26f * uiScale
            ) {
                finishDraw()
            } else {
                addDrawPoint(Pt(w.x, w.y))
            }
            drag = Drag.NONE
            return
        }
        // взведён проём: тап по стене ставит его в место касания
        if (roomMode && placeOpeningKind >= 0) {
            val ptsR = room.points
            var bestI = -1
            var bestS = 0.0
            var bestD = 20.0 * uiScale / view.scale
            for (i in ptsR.indices) {
                val a = ptsR[i]
                val b = ptsR[(i + 1) % ptsR.size]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val len = sqrt(ex * ex + ey * ey)
                if (len < 1e-6) continue
                val t = (((w.x - a.x) * ex + (w.y - a.y) * ey) / (len * len)).coerceIn(0.0, 1.0)
                val px = a.x + ex * t
                val py = a.y + ey * t
                val dist = sqrt((w.x - px) * (w.x - px) + (w.y - py) * (w.y - py))
                if (dist < bestD) {
                    bestD = dist
                    bestI = i
                    bestS = len * t
                }
            }
            if (bestI >= 0) {
                addOpeningAt(bestI, placeOpeningKind, bestS)
            }
            drag = Drag.NONE
            return
        }
        // тап по метке размера стены: редактор длины с выбором конца
        if (showDims) {
            for (i in room.points.indices) {
                val at = edgeLabelAt(i) ?: continue
                if ((pos - at).getDistance() < 22f * uiScale) {
                    edgeEditIndex = i
                    drag = Drag.NONE
                    return
                }
            }
        }
        // проём на стене: хватаем и тащим вдоль стены пальцем (в любом режиме)
        run {
            val ptsR = room.points
            if (ptsR.any { (toScreen(it) - pos).getDistance() < 26f * uiScale }) return@run
            for (i in ptsR.indices) {
                val listO = openingsOf("wall-" + (i + 1))
                if (listO.isEmpty()) continue
                val a = ptsR[i]
                val b = ptsR[(i + 1) % ptsR.size]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val len = sqrt(ex * ex + ey * ey)
                if (len < 1e-6) continue
                val ux = ex / len
                val uy = ey / len
                var nx = ey / len
                var ny = -ex / len
                val mid = Pt(a.x + ex / 2, a.y + ey / 2)
                if (pointInPolygon(Pt(mid.x + nx * 0.03, mid.y + ny * 0.03), ptsR)) {
                    nx = -nx
                    ny = -ny
                }
                val th = wallThicknessOf("wall-" + (i + 1))
                val sAxis = (w.x - a.x) * ux + (w.y - a.y) * uy
                val tAxis = (w.x - a.x) * nx + (w.y - a.y) * ny
                val padS = 8.0 * uiScale / view.scale
                val padT = 6.0 * uiScale / view.scale
                if (tAxis < -padT || tAxis > th + padT) continue
                listO.forEachIndexed { oi, o ->
                    if (drag == Drag.NONE && sAxis >= o.x - padS && sAxis <= o.x + o.w + padS) {
                        drag = Drag.OPENING_MOVE
                        dragWall = i
                        dragIndex = oi
                        grabDx = sAxis - o.x
                    }
                }
                if (drag == Drag.OPENING_MOVE) return
            }
        }
        // касание по ДРУГОЙ комнате: сразу делаем её активной и тащим — одним жестом,
        // и ручки активной комнаты при этом не перехватывают касание (иначе ставилась точка)
        pendingSwitch = -1
        panMoved = false
        // вершину активной комнаты на общей стене не отдаём соседке
        val nearActiveVertex = room.points.any { (toScreen(it) - pos).getDistance() < 26f * uiScale }
        if (!nearActiveVertex && !pointInPolygon(w, room.points)) {
            var other = -1
            rooms.forEachIndexed { i, r ->
                if (other < 0 && i != activeRoom && pointInPolygon(w, r.spec.points)) other = i
            }
            if (other >= 0) {
                switchRoom(other)
                if (roomMode) {
                    drag = Drag.ROOM_MOVE
                    grabDx = w.x
                    grabDy = w.y
                    movedFurn = furniture.indices.filter { idx ->
                        val f = furniture[idx]
                        pointInPolygon(Pt(f.x + f.w / 2, f.y + f.h / 2), room.points)
                    }
                    selection = null
                } else {
                    drag = Drag.PAN
                }
                return
            }
        }
        if (!roomMode) {
            patternMoved = false
            tappedTile = -1
            if (pointInPolygon(w, room.points)) {
                pushOnce()
                drag = Drag.PATTERN
                tappedTile = layout.tiles.indexOfFirst { pointInPolygon(w, it.corners) }
            } else {
                drag = Drag.PAN
            }
            return
        }

        // 1. вершина
        room.points.forEachIndexed { i, p ->
            if (drag == Drag.NONE && (toScreen(p) - pos).getDistance() < 26f * uiScale) {
                pushOnce()
                drag = Drag.VERTEX
                dragIndex = i
                selection = Selection.Vertex(i)
            }
        }
        if (drag != Drag.NONE) return

        // 2. «+» на середине ребра (только если палец не над другой комнатой)
        val overOther = rooms.withIndex().any { (i, r) ->
            i != activeRoom && r.spec.points.size >= 3 && pointInPolygon(w, r.spec.points)
        }
        if (overOther) {
            drag = Drag.PAN
            selection = null
            return
        }
        // 2. «+» на середине ребра
        val pts = room.points
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val sa = toScreen(a)
            val sb = toScreen(b)
            if ((sb - sa).getDistance() < 56f * uiScale) continue
            val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)
            if ((mid - pos).getDistance() < 22f * uiScale) {
                val np = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
                val list = pts.toMutableList()
                list.add(i + 1, np)
                room = room.copy(points = list)
                drag = Drag.VERTEX
                dragIndex = i + 1
                selection = Selection.Vertex(i + 1)
                return
            }
        }

        // 3. ручка выреза (правый нижний угол)
        room.cutouts.forEachIndexed { i, c ->
            if (drag == Drag.NONE &&
                (toScreen(Pt(c.x + c.w, c.y + c.h)) - pos).getDistance() < 26f * uiScale
            ) {
                pushOnce()
                pushOnce()
            drag = Drag.CUT_RESIZE
                dragIndex = i
                selection = Selection.Cut(i)
            }
        }
        if (drag != Drag.NONE) return

        // 4. тело выреза
        room.cutouts.forEachIndexed { i, c ->
            if (drag == Drag.NONE &&
                w.x > c.x && w.x < c.x + c.w && w.y > c.y && w.y < c.y + c.h
            ) {
                pushOnce()
                pushOnce()
            drag = Drag.CUT_MOVE
                dragIndex = i
                grabDx = w.x - c.x
                grabDy = w.y - c.y
                selection = Selection.Cut(i)
            }
        }
        if (drag != Drag.NONE) return

        // 5. мебель: угловая ручка и перенос
        furniture.forEachIndexed { i, f ->
            if (drag == Drag.NONE &&
                (toScreen(Pt(f.x + f.w, f.y + f.h)) - pos).getDistance() < 26f * uiScale
            ) {
                pushOnce()
                pushOnce()
            drag = Drag.FURN_RESIZE
                dragIndex = i
                selection = Selection.Furn(i)
            }
        }
        if (drag != Drag.NONE) return
        furniture.forEachIndexed { i, f ->
            if (drag == Drag.NONE && w.x > f.x && w.x < f.x + f.w && w.y > f.y && w.y < f.y + f.h) {
                pushOnce()
                pushOnce()
            drag = Drag.FURN_MOVE
                dragIndex = i
                grabDx = w.x - f.x
                grabDy = w.y - f.y
                selection = Selection.Furn(i)
            }
        }
        if (drag != Drag.NONE) return

        // 5b2. зоны: угол — размер, внутри — перенос
        zones.forEachIndexed { i, z ->
            if (drag != Drag.NONE) return@forEachIndexed
            val corner = toScreen(Pt(z.x + z.w, z.y + z.h))
            if ((corner - pos).getDistance() < 26f * uiScale) {
                pushOnce()
                pushOnce()
            drag = Drag.ZONE_RESIZE
                dragIndex = i
                activeZone = i
                selection = Selection.Zone(i)
                return
            }
        }
        zones.forEachIndexed { i, z ->
            if (drag != Drag.NONE) return@forEachIndexed
            if (w.x > z.x && w.x < z.x + z.w && w.y > z.y && w.y < z.y + z.h) {
                pushOnce()
                pushOnce()
            drag = Drag.ZONE_MOVE
                dragIndex = i
                activeZone = i
                selection = Selection.Zone(i)
                grabDx = w.x - z.x
                grabDy = w.y - z.y
                return
            }
        }

        // 5c. перенос всей комнаты: захват изнутри активного контура
        if (pointInPolygon(w, room.points)) {
            pushOnce()
            drag = Drag.ROOM_MOVE
            grabDx = w.x
            grabDy = w.y
            movedFurn = furniture.indices.filter { idx ->
                val f = furniture[idx]
                pointInPolygon(Pt(f.x + f.w / 2, f.y + f.h / 2), room.points)
            }
            selection = null
            return
        }

        // 6. панорамирование
        drag = Drag.PAN
        selection = null
    }

    private var gestureSnapped = false

    fun gestureMove(pos: Offset, prev: Offset) {
        if (!gestureSnapped && drag != Drag.NONE && drag != Drag.PAN) {
            gestureSnapped = true
            pushUndo()
        }
        val d = pos - prev
        when (drag) {
            Drag.PAN -> {
                if (d.getDistance() > 3f) panMoved = true
                view = view.copy(offset = view.offset + d)
            }

            Drag.OPENING_MOVE -> {
                val i = dragWall
                val ptsR = room.points
                if (i in ptsR.indices) {
                    val a = ptsR[i]
                    val b = ptsR[(i + 1) % ptsR.size]
                    val ex = b.x - a.x
                    val ey = b.y - a.y
                    val len = sqrt(ex * ex + ey * ey)
                    if (len > 1e-6) {
                        val ux = ex / len
                        val uy = ey / len
                        val wp = toWorld(pos)
                        val sAxis = (wp.x - a.x) * ux + (wp.y - a.y) * uy
                        val id = "wall-" + (i + 1)
                        val listO = openingsOf(id).toMutableList()
                        val o = listO.getOrNull(dragIndex)
                        if (o != null) {
                            var x = (sAxis - grabDx).coerceIn(0.0, (len - o.w).coerceAtLeast(0.0))
                            // магниты: края стены и центр — как везде в приложении
                            val tol = 8.0 * uiScale / view.scale
                            for (tgt in doubleArrayOf(0.0, (len - o.w) / 2, len - o.w)) {
                                if (abs(x - tgt) < tol) x = tgt
                            }
                            val bad = listO.withIndex().any { (j, o2) ->
                                j != dragIndex && x < o2.x + o2.w && o2.x < x + o.w
                            }
                            if (!bad) {
                                listO[dragIndex] = o.copy(x = round2(x))
                                openings = openings + (id to listO)
                            }
                        }
                    }
                }
            }

            Drag.PATTERN -> {
                if (d.getDistance() > 3f) patternMoved = true
                anchor = AnchorMode.FREE
                var ox = pattern.offsetX + d.x / view.scale
                var oy = pattern.offsetY + d.y / view.scale
                patternSnapX = 0
                patternSnapY = 0
                // магниты раскладки, как уровни у стен: шов/плитка по центру,
                // полная плитка у стены; работают при неповёрнутом узоре
                val rotSnap = ((pattern.rotationDeg % 360.0) + 360.0) % 360.0
                val rotSnapOk = abs(rotSnap % 90.0) < 0.01 || abs(rotSnap % 90.0 - 90.0) < 0.01
                if (rotSnapOk && room.points.size >= 3) {
                    val swapSnap = abs(rotSnap % 180.0 - 90.0) < 0.01
                    val stepW = (
                        (if (swapSnap) tile.heightMm else tile.widthMm) + max(0.0, tile.groutMm)
                        ) / 1000.0
                    val stepH = (
                        (if (swapSnap) tile.widthMm else tile.heightMm) + max(0.0, tile.groutMm)
                        ) / 1000.0
                    val minx = room.points.minOf { it.x }
                    val maxx = room.points.maxOf { it.x }
                    val miny = room.points.minOf { it.y }
                    val maxy = room.points.maxOf { it.y }
                    val tol = 7.0 * uiScale / view.scale
                    fun snapAxis(v: Double, step: Double, lo: Double, hi: Double, set: (Int) -> Unit): Double {
                        if (step < 1e-6) return v
                        val cands = listOf(
                            2 to (lo + hi) / 2,
                            3 to (lo + hi) / 2 - step / 2,
                            4 to lo,
                            5 to hi,
                        )
                        var bestKind = 0
                        var bestDf = tol
                        for ((kind, target) in cands) {
                            var df = (target - v) % step
                            if (df > step / 2) df -= step
                            if (df < -step / 2) df += step
                            if (abs(df) < abs(bestDf)) {
                                bestKind = kind
                                bestDf = df
                            }
                        }
                        if (bestKind != 0) {
                            set(bestKind)
                            return v + bestDf
                        }
                        return v
                    }
                    ox = snapAxis(ox, stepW, minx, maxx) { patternSnapX = it }
                    oy = snapAxis(oy, stepH, miny, maxy) { patternSnapY = it }
                }
                pattern = pattern.copy(offsetX = ox, offsetY = oy)
            }

            Drag.VERTEX -> {
                val pts = room.points.toMutableList()
                if (dragIndex in pts.indices) {
                    pts[dragIndex] = snapVertex(dragIndex, toWorld(pos))
                    if (!overlapsOthers(pts)) {
                        room = room.copy(points = pts)
                    }
                }
            }

            Drag.CUT_MOVE -> {
                val cs = room.cutouts.toMutableList()
                if (dragIndex in cs.indices) {
                    val w = toWorld(pos)
                    val c = cs[dragIndex]
                    val nx2 = round2(w.x - grabDx)
                    val ny2 = round2(w.y - grabDy)
                    fun tryPut(px: Double, py: Double): Boolean {
                        cs[dragIndex] = c.copy(x = px, y = py)
                        if (cutsOverlap(cs, dragIndex)) return false
                        // центр выреза не должен выходить за комнату — иначе он «улетает» наружу
                        val cc = Pt(px + c.w / 2, py + c.h / 2)
                        return pointInPolygon(cc, room.points)
                    }
                    if (!tryPut(nx2, ny2) && !tryPut(nx2, c.y) && !tryPut(c.x, ny2)) {
                        cs[dragIndex] = c
                    }
                    room = room.copy(cutouts = cs)
                }
            }

            Drag.CUT_RESIZE -> {
                val cs = room.cutouts.toMutableList()
                if (dragIndex in cs.indices) {
                    val w = toWorld(pos)
                    val c = cs[dragIndex]
                    val nw = max(0.1, round2(w.x - c.x))
                    val nh = max(0.1, round2(w.y - c.y))
                    fun trySize(pw: Double, ph: Double): Boolean {
                        cs[dragIndex] = c.copy(w = pw, h = ph)
                        return !cutsOverlap(cs, dragIndex)
                    }
                    if (!trySize(nw, nh) && !trySize(nw, c.h) && !trySize(c.w, nh)) {
                        cs[dragIndex] = c
                    }
                    room = room.copy(cutouts = cs)
                }
            }

            Drag.ZONE_MOVE -> {
                val wpt = toWorld(pos)
                updateZone(dragIndex) {
                    it.copy(x = round2(wpt.x - grabDx), y = round2(wpt.y - grabDy))
                }
            }

            Drag.ZONE_RESIZE -> {
                val wpt = toWorld(pos)
                updateZone(dragIndex) {
                    it.copy(
                        w = max(0.2, round2(wpt.x - it.x)),
                        h = max(0.2, round2(wpt.y - it.y)),
                    )
                }
            }

            Drag.FORMAT -> {
                brushTouch(toWorld(pos))
            }

            Drag.PAINT -> {
                val wpt = toWorld(pos)
                val idx = layout.tiles.indexOfFirst { pointInPolygon(wpt, it.corners) }
                if (idx >= 0) {
                    val key = tileKey(layout.tiles[idx])
                    if (tileColors[key] != paintColor) {
                        val m = tileColors.toMutableMap()
                        m[key] = paintColor
                        tileColors = m
                    }
                }
            }

            Drag.PLAN_MOVE -> {
                val wpt = toWorld(pos)
                planOrigin = Pt(wpt.x - grabDx, wpt.y - grabDy)
            }

            Drag.ROOM_MOVE -> {
                val wpt = toWorld(pos)
                val dx = wpt.x - grabDx
                val dy = wpt.y - grabDy
                grabDx = wpt.x
                grabDy = wpt.y

                fun shift(ddx: Double, ddy: Double): Boolean {
                    if (ddx == 0.0 && ddy == 0.0) return false
                    val cand = room.points.map { Pt(it.x + ddx, it.y + ddy) }
                    if (overlapsOthers(cand)) return false
                    room = RoomSpec(
                        cand,
                        room.cutouts.map { it.copy(x = it.x + ddx, y = it.y + ddy) },
                    )
                    pattern = pattern.copy(
                        offsetX = pattern.offsetX + ddx,
                        offsetY = pattern.offsetY + ddy,
                    )
                    if (movedFurn.isNotEmpty()) {
                        val fs = furniture.toMutableList()
                        movedFurn.forEach { idx ->
                            if (idx in fs.indices) {
                                val f = fs[idx]
                                fs[idx] = f.copy(x = f.x + ddx, y = f.y + ddy)
                            }
                        }
                        furniture = fs
                    }
                    return true
                }

                // упор в соседнюю комнату со скольжением вдоль её стены
                if (!shift(dx, dy)) {
                    shift(dx, 0.0)
                    shift(0.0, dy)
                }
                // магнит: подвели ближе 12 см к чужой стене — встаём вплотную, без щели
                val snap = snapOffset()
                if (snap != null) shift(snap.x, snap.y)
            }

            Drag.FURN_MOVE -> {
                val fs = furniture.toMutableList()
                if (dragIndex in fs.indices) {
                    val w = toWorld(pos)
                    val f = fs[dragIndex]
                    fs[dragIndex] = f.copy(x = round2(w.x - grabDx), y = round2(w.y - grabDy))
                    furniture = fs
                }
            }

            Drag.FURN_RESIZE -> {
                val fs = furniture.toMutableList()
                if (dragIndex in fs.indices) {
                    val w = toWorld(pos)
                    val f = fs[dragIndex]
                    fs[dragIndex] = f.copy(
                        w = max(0.2, round2(w.x - f.x)),
                        h = max(0.2, round2(w.y - f.y)),
                    )
                    furniture = fs
                }
            }

            Drag.NONE -> Unit
        }
    }

    fun gestureEnd() {
        patternSnapX = 0
        patternSnapY = 0
        if (drag == Drag.VERTEX) clampOpenings()
        if (drag == Drag.FORMAT) brushFinish()
        if (drag == Drag.PATTERN && !patternMoved) {
            selection = if (tappedTile >= 0) {
                if ((selection as? Selection.Tile)?.i == tappedTile) null else Selection.Tile(tappedTile)
            } else {
                null
            }
        }
        tappedTile = -1
        if (drag == Drag.PAN && !panMoved && pendingSwitch >= 0) {
            switchRoom(pendingSwitch)
        }
        pendingSwitch = -1
        if (drag == Drag.ROOM_MOVE) {
            // финальная привязка к сантиметрам, чтобы размеры оставались круглыми
            room = RoomSpec(
                room.points.map { Pt(round2(it.x), round2(it.y)) },
                room.cutouts.map { it.copy(x = round2(it.x), y = round2(it.y)) },
            )
            pattern = pattern.copy(
                offsetX = round2(pattern.offsetX),
                offsetY = round2(pattern.offsetY),
            )
        }
        gestureSnapped = false
        drag = Drag.NONE
        dragIndex = -1
    }

    /** Прервать текущий жест (например, при переходе к двупальцевому зуму). */
    fun cancelGesture() {
        patternSnapX = 0
        patternSnapY = 0
        gestureSnapped = false
        drag = Drag.NONE
        dragIndex = -1
    }

    /** Зум двумя пальцами: мировая точка под mid0 остаётся под mid. */
    fun pinch(base: ViewTransform, d0: Float, mid0: Offset, d: Float, mid: Offset) {
        if (d0 <= 0f) return
        val s = (base.scale * (d / d0)).coerceIn(12f, 2400f)
        val wx = (mid0.x - base.offset.x) / base.scale
        val wy = (mid0.y - base.offset.y) / base.scale
        view = ViewTransform(s, Offset(mid.x - wx * s, mid.y - wy * s))
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0

    /** Округление до сантиметра + прилипание к координатам соседних вершин. */
    private fun snapVertex(i: Int, w: Pt): Pt {
        val pts = room.points
        var x = round2(w.x)
        var y = round2(w.y)
        val tol = 10.0 / view.scale
        val neighbours = listOf(pts[(i - 1 + pts.size) % pts.size], pts[(i + 1) % pts.size])
        for (n in neighbours) {
            if (abs(x - n.x) < tol) x = n.x
            if (abs(y - n.y) < tol) y = n.y
        }
        return Pt(x, y)
    }

    // ---------- плитка и узор ----------

    fun setTileWidth(mm: Double) {
        if (activeZone in zones.indices) {
            updateZone(activeZone) { it.copy(tile = it.tile.copy(widthMm = mm)) }
        } else {
            tile = tile.copy(widthMm = mm)
            reanchor()
        }
    }

    fun setTileHeight(mm: Double) {
        if (activeZone in zones.indices) {
            updateZone(activeZone) { it.copy(tile = it.tile.copy(heightMm = mm)) }
        } else {
            tile = tile.copy(heightMm = mm)
            reanchor()
        }
    }

    fun setGrout(mm: Double) {
        if (activeZone in zones.indices) {
            updateZone(activeZone) { it.copy(tile = it.tile.copy(groutMm = mm)) }
        } else {
            tile = tile.copy(groutMm = mm)
            reanchor()
        }
    }

    fun setPatternType(t: PatternType) {
        pushUndo()
        if (activeZone in zones.indices) {
            updateZone(activeZone) { it.copy(pattern = it.pattern.copy(type = t)) }
            return
        }
        pattern = pattern.copy(type = t)
        suggestions = null
        reanchor()
    }

    fun setRotation(deg: Double) { pattern = pattern.copy(rotationDeg = deg); reanchor() }

    // ---------- декор и точка отсчёта ----------

    /** Пересчитать смещение узора под выбранную точку отсчёта. */
    fun reanchor() {
        if (anchor != AnchorMode.FREE) {
            pattern = Aligner.applyAnchor(pattern, room.points, tile, anchor, decor.art)
        }
    }

    fun switchAnchor(a: AnchorMode) { anchor = a; reanchor() }

    fun setDecorMode(m: DecorMode) {
        pushUndo()
        decor = decor.copy(mode = m)
        if (m != DecorMode.NONE && anchor == AnchorMode.FREE) anchor = AnchorMode.ART_CENTER
        reanchor()
    }

    fun setArt(a: ArtRect) {
        decor = decor.copy(
            art = ArtRect(
                a.x.coerceIn(0.0, 0.9),
                a.y.coerceIn(0.0, 0.9),
                a.w.coerceIn(0.1, 1.0 - a.x.coerceIn(0.0, 0.9)),
                a.h.coerceIn(0.1, 1.0 - a.y.coerceIn(0.0, 0.9)),
            )
        )
        reanchor()
    }

    fun setPanel(cols: Int, rows: Int) { decor = decor.copy(panelCols = cols, panelRows = rows) }

    fun setEveryN(n: Int) { decor = decor.copy(everyN = n.coerceIn(2, 9)) }

    fun loadDecorImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bmp != null) {
                decorImage = bmp
                // старая рамка не должна применяться к новой картинке
                decor = decor.copy(art = ArtRect.FULL)
            }
        }
    }

    fun clearDecorImage() {
        decorImage = null
        decor = decor.copy(art = ArtRect.FULL)
    }

    fun resetShift() { pattern = pattern.copy(offsetX = 0.0, offsetY = 0.0) }

    fun setColor(c: Color) { tileColor = c; tileImage = null }

    fun toggleVariation() { variation = !variation }

    fun clearImage() { tileImage = null }

    private fun decodeBitmap(context: Context, uri: Uri): ImageBitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ -> decoder.isMutableRequired = false }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()?.asImageBitmap()

    fun loadTileImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bmp != null) tileImage = bmp
        }
    }

    // ---------- комната ----------

    fun applyRect(wM: Double, hM: Double) {
        pushUndo()
        room = RoomSpec(
            listOf(Pt(0.0, 0.0), Pt(wM, 0.0), Pt(wM, hM), Pt(0.0, hM)),
            room.cutouts.filter { it.x + it.w <= wM && it.y + it.h <= hM },
        )
        selection = null
        suggestions = null
        reanchor()
        fit()
    }

    fun applyLShape() {
        pushUndo()
        room = RoomSpec(
            listOf(
                Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 1.8),
                Pt(2.2, 1.8), Pt(2.2, 3.0), Pt(0.0, 3.0),
            ),
            emptyList(),
        )
        selection = null
        suggestions = null
        reanchor()
        fit()
    }

    /** Пересекается ли вырез idx с остальными (касание разрешено). */
    private fun cutsOverlap(cs: List<Cutout>, idx: Int): Boolean {
        val a = cs[idx]
        cs.forEachIndexed { j, b ->
            if (j != idx &&
                a.x < b.x + b.w - 0.001 && a.x + a.w > b.x + 0.001 &&
                a.y < b.y + b.h - 0.001 && a.y + a.h > b.y + 0.001
            ) {
                return true
            }
        }
        return false
    }

    fun addCutout() {
        pushUndo()
        val pts = room.points
        val cx = pts.sumOf { it.x } / pts.size
        val cy = pts.sumOf { it.y } / pts.size
        var nx3 = round2(cx - 0.4)
        val ny3 = round2(cy - 0.4)
        var tries = 0
        while (tries < 8) {
            val cand = room.cutouts + Cutout(nx3, ny3, 0.8, 0.8)
            if (!cutsOverlap(cand, cand.lastIndex)) break
            nx3 = round2(nx3 + 0.9)
            tries++
        }
        val cs = room.cutouts + Cutout(nx3, ny3, 0.8, 0.8)
        room = room.copy(cutouts = cs)
        roomMode = true
        selection = Selection.Cut(cs.lastIndex)
    }

    fun deleteSelectedVertex() {
        pushUndo()
        val sel = selection as? Selection.Vertex ?: return
        if (room.points.size <= 3) return
        val pts = room.points.toMutableList()
        if (sel.i !in pts.indices) return
        pts.removeAt(sel.i)
        room = room.copy(points = pts)
        remapWallKeys(sel.i, -1)
        clampOpenings()
        selection = null
    }

    fun deleteSelectedCutout() {
        pushUndo()
        val sel = selection as? Selection.Cut ?: return
        val cs = room.cutouts.toMutableList()
        if (sel.i !in cs.indices) return
        cs.removeAt(sel.i)
        room = room.copy(cutouts = cs)
        selection = null
    }

    fun setSelectedCutW(m: Double) = updateSelectedCut { it.copy(w = max(0.1, m)) }

    fun setSelectedCutH(m: Double) = updateSelectedCut { it.copy(h = max(0.1, m)) }

    private fun updateSelectedCut(f: (Cutout) -> Cutout) {
        val sel = selection as? Selection.Cut ?: return
        val cs = room.cutouts.toMutableList()
        if (sel.i !in cs.indices) return
        cs[sel.i] = f(cs[sel.i])
        room = room.copy(cutouts = cs)
    }

    // ---------- цены и логотип ----------

    fun updatePrices(transform: (Prices) -> Prices) { prices = transform(prices) }

    private val logoFile: File
        get() = File(getApplication<Application>().filesDir, "logo.png")

    private fun decodeLogoFile(): ImageBitmap? = runCatching {
        android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)?.asImageBitmap()
    }.getOrNull()

    fun loadMasterLogo(context: Context, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        logoFile.outputStream().use { out -> input.copyTo(out) }
                    }
                }
            }
            masterLogo = withContext(Dispatchers.IO) { decodeLogoFile() }
        }
    }

    fun loadFitPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bmp != null) fitPhoto = bmp
        }
    }

    fun clearFitPhoto() { fitPhoto = null }

    fun moveFitCorner(i: Int, pos: Offset) {
        if (i !in 0..3) return
        val q = fitQuad.toMutableList()
        q[i] = Offset(pos.x.coerceIn(0f, 1f), pos.y.coerceIn(0f, 1f))
        fitQuad = q
    }

    fun resetFitQuad() {
        fitQuad = listOf(Offset(0.16f, 0.34f), Offset(0.84f, 0.34f), Offset(0.97f, 0.93f), Offset(0.03f, 0.93f))
    }

    fun updateFitAlpha(a: Float) { fitAlpha = a.coerceIn(0.3f, 1f) }

    /** Живой AR: раскладка рисуется в картинку и передаётся ARCore-экрану. */
    fun openAr(context: Context) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                renderFloorBitmap(
                    points = room.points,
                    tiles = layout.tiles,
                    decorIdx = decorIdx,
                    tileBmp = tileImage?.asAndroidBitmap(),
                    decorBmp = (decorImage ?: tileImage)?.asAndroidBitmap(),
                    colorArgb = tileColor.toArgb(),
                    variation = variation,
                    panel = panelInfo(),
                    extra = zoneLayers(),
                    colorOf = { t -> colorOfTile(t) },
                    cutNumbers = showCuts,
                    cutInfo = cutInfo,
                )
            }
            ArBridge.floorBitmap = result.first
            ArBridge.widthM = result.second
            ArBridge.heightM = result.third
            // варианты для показа клиенту: текущий + избранные форматы
            val label = tile.widthMm.toInt().toString() + "×" + tile.heightMm.toInt()
            val list = mutableListOf(label to result.first)
            withContext(Dispatchers.Default) {
                favTiles.filter { it.first != tile.widthMm || it.second != tile.heightMm }
                    .take(3)
                    .forEach { fav ->
                        val t2 = TileSpec(fav.first, fav.second, fav.third)
                        val lay = TilingEngine.build(room, t2, pattern)
                        val r2 = renderFloorBitmap(
                            points = room.points,
                            tiles = lay.tiles,
                            decorIdx = emptySet(),
                            tileBmp = tileImage?.asAndroidBitmap(),
                            decorBmp = null,
                            colorArgb = tileColor.toArgb(),
                            variation = variation,
                        )
                        list.add(
                            (fav.first.toInt().toString() + "×" + fav.second.toInt()) to r2.first,
                        )
                    }
            }
            ArBridge.variants = list
            val app2 = getApplication<Application>()
            ArBridge.info = String.format(Locale.getDefault(), "%.2f", layout.areaM2) + " " +
                app2.getString(R.string.unit_m2) + "  ·  " +
                app2.getString(R.string.buy) + " " + buyCount + " " + app2.getString(R.string.pcs) +
                "  ·  " + app2.getString(R.string.cut_tiles) + " " + layout.cutCount
            context.startActivity(Intent(context, ArActivity::class.java))
        }
    }

    fun clearMasterLogo() {
        masterLogo = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { logoFile.delete() } }
    }

    /** Стоимость каждой отделанной поверхности: материалы и работа по текущим ценам. */
    fun surfaceCosts(): List<SurfaceCost> {
        val p = prices
        val tileAreaM2 = tile.widthMm * tile.heightMm / 1_000_000.0
        return model.surfaces.mapNotNull { su ->
            val fin = finishOf(su.id)
            if (fin == Finish.NONE) return@mapNotNull null
            val area = surfaceAreaM2(su.id)
            if (area <= 0.0) return@mapNotNull null
            var mat = 0.0
            var work = 0.0
            when (fin) {
                Finish.TILE -> {
                    val lay = surfaceLayout(su.id)
                    val pieces = ceil(lay.totalCount * (1 + reservePct / 100.0)).toInt()
                    val tileCost = if (p.tilePc > 0) pieces * p.tilePc else pieces * tileAreaM2 * p.tileM2
                    mat = tileCost + MaterialCalc.tileAdhesiveKg(area, tile) * p.adhesiveKg
                    work = area * p.workTileM2
                }

                Finish.WALLPAPER -> {
                    val wall = model.walls.firstOrNull { it.id == su.id }
                    val w = wall?.lengthM ?: sqrt(area)
                    val h = if (wall != null) wallHeightM else sqrt(area)
                    mat = MaterialCalc.wallpaper(w, h).rolls * p.roll
                    work = area * p.workWallM2
                }

                Finish.PAINT -> {
                    mat = MaterialCalc.paintLiters(area, 2) * p.paintL
                    work = area * p.workPaintM2
                }

                else -> Unit
            }
            SurfaceCost(su.id, fin, area, mat, work)
        }
    }

    // ---------- мебель ----------

    fun addFurniture(name: String, wM: Double, hM: Double, heightM: Double = 0.85, kind: String = "box") {
        pushUndo()
        val c = roomCenter()
        val f = Furniture(
            id = "f" + System.currentTimeMillis(),
            name = name,
            heightM = heightM,
            kind = kind,
            x = round2(c.x - wM / 2),
            y = round2(c.y - hM / 2),
            w = wM,
            h = hM,
        )
        furniture = furniture + f
        roomMode = true
        selection = Selection.Furn(furniture.lastIndex)
    }

    fun deleteSelectedFurniture() {
        pushUndo()
        val sel = selection as? Selection.Furn ?: return
        val fs = furniture.toMutableList()
        if (sel.i !in fs.indices) return
        fs.removeAt(sel.i)
        furniture = fs
        selection = null
    }

    fun setSelectedFurnW(m: Double) = updateSelectedFurn { it.copy(w = max(0.2, m)) }

    fun setSelectedFurnH(m: Double) = updateSelectedFurn { it.copy(h = max(0.2, m)) }

    fun toggleSelectedFurnCover() = updateSelectedFurn { it.copy(coversFinish = !it.coversFinish) }

    /** Повернуть объект на 90°: меняем ширину и глубину местами. */
    fun rotateSelectedFurn() = updateSelectedFurn { it.copy(w = it.h, h = it.w) }

    private fun updateSelectedFurn(f: (Furniture) -> Furniture) {
        val sel = selection as? Selection.Furn ?: return
        val fs = furniture.toMutableList()
        if (sel.i !in fs.indices) return
        fs[sel.i] = f(fs[sel.i])
        furniture = fs
    }

    /** Повернуть плитку на 90°: меняем ширину и длину местами. */
    fun rotateTile() {
        pushUndo()
        tile = tile.copy(widthMm = tile.heightMm, heightMm = tile.widthMm)
        reanchor()
    }

    // ---------- режимы и слои ----------

    fun setReserve(p: Int) { reservePct = p }

    fun switchRoomMode(b: Boolean) {
        roomMode = b
        if (!b) {
            selection = null
            placeOpeningKind = -1
        }
    }

    fun toggleDims() { showDims = !showDims }

    fun toggleCuts() { showCuts = !showCuts }

    fun toggleFurniture() { showFurniture = !showFurniture }

    fun toggleArt() { showArt = !showArt }

    fun setWallHeight(m: Double) { wallHeightM = m.coerceIn(1.8, 4.0) }

    // ---------- поверхности ----------

    /** Модель комнаты: пол, стены по контуру, потолок. */
    val model: RoomModel by derivedStateOf {
        RoomModel.fromFloor(room.points, wallHeightM, room.cutouts)
    }

    fun finishOf(id: String): Finish = finishes[id]
        ?: if (id == "floor") Finish.TILE else Finish.PAINT

    fun setFinish(id: String, f: Finish) {
        pushUndo()
        finishes = finishes + (id to f)
    }

    fun selectSurface(id: String) { activeSurface = id }

    fun openingsOf(id: String): List<Cutout> = openings[id] ?: emptyList()

    /**
     * Типы проёмов стены, выровненные по списку openings. Старые сохранения без
     * типов получают вывод по высоте: стоит на полу — дверь, поднят — окно.
     */
    fun openingKindsOf(id: String): List<Int> {
        val list = openingsOf(id)
        val kinds = openingKinds[id] ?: emptyList()
        return list.mapIndexed { i, o ->
            kinds.getOrNull(i) ?: if (o.y < 0.05) OPENING_DOOR else OPENING_WINDOW
        }
    }

    /** Добавить проём по центру стены: окно поднято над полом, остальные стоят на полу. */
    fun addOpening(id: String, wM: Double, hM: Double, fromFloorM: Double, kind: Int = -1) {
        pushUndo()
        val wall = model.walls.firstOrNull { it.id == id } ?: return
        val x = ((wall.lengthM - wM) / 2).coerceAtLeast(0.0)
        val y = fromFloorM.coerceIn(0.0, (wallHeightM - hM).coerceAtLeast(0.0))
        val k = if (kind >= 0) kind else if (y < 0.05) OPENING_DOOR else OPENING_WINDOW
        val kinds = openingKindsOf(id) + k
        openings = openings + (id to (openingsOf(id) + Cutout(round2(x), round2(y), wM, hM)))
        openingKinds = openingKinds + (id to kinds)
    }

    /** Изменить проём: отступ от начала стены, ширину, высоту, подоконник. */
    fun updateOpening(
        id: String,
        index: Int,
        x: Double? = null,
        w: Double? = null,
        h: Double? = null,
        sill: Double? = null,
    ) {
        val wall = model.walls.firstOrNull { it.id == id } ?: return
        val list = openingsOf(id).toMutableList()
        val o = list.getOrNull(index) ?: return
        pushUndo()
        val kind = openingKindsOf(id).getOrNull(index) ?: OPENING_WINDOW
        val nw = (w ?: o.w).coerceIn(0.1, wall.lengthM)
        val nh = (h ?: o.h).coerceIn(0.1, wallHeightM)
        // всё, кроме окна, стоит на полу — подоконник только у окна
        val ny = if (kind == OPENING_WINDOW) {
            (sill ?: o.y).coerceIn(0.0, (wallHeightM - nh).coerceAtLeast(0.0))
        } else {
            0.0
        }
        val nx = (x ?: o.x).coerceIn(0.0, (wall.lengthM - nw).coerceAtLeast(0.0))
        list[index] = Cutout(round2(nx), round2(ny), round2(nw), round2(nh))
        openings = openings + (id to list)
    }

    /** Сменить тип проёма; двери и проходы опускаются на пол, окно поднимается. */
    fun setOpeningKind(id: String, index: Int, kind: Int) {
        val list = openingsOf(id).toMutableList()
        val o = list.getOrNull(index) ?: return
        pushUndo()
        val kinds = openingKindsOf(id).toMutableList()
        while (kinds.size <= index) kinds.add(OPENING_WINDOW)
        kinds[index] = kind
        openingKinds = openingKinds + (id to kinds)
        if (kind != OPENING_WINDOW && o.y > 0.0) {
            list[index] = o.copy(y = 0.0)
            openings = openings + (id to list)
        } else if (kind == OPENING_WINDOW && o.y < 0.05) {
            val sill = (wallHeightM - o.h).coerceAtLeast(0.0).coerceAtMost(0.9)
            list[index] = o.copy(y = round2(sill))
            openings = openings + (id to list)
        }
    }

    fun deleteOpening(id: String, index: Int) {
        pushUndo()
        val list = openingsOf(id).toMutableList()
        if (index !in list.indices) return
        val kinds = openingKindsOf(id).toMutableList()
        list.removeAt(index)
        if (index in kinds.indices) kinds.removeAt(index)
        openings = openings + (id to list)
        openingKinds = openingKinds + (id to kinds)
    }

    /** Площадь поверхности за вычетом проёмов. */
    fun surfaceAreaM2(id: String): Double {
        val s = model.surfaces.firstOrNull { it.id == id } ?: return 0.0
        val holes = if (s.kind == SurfaceKind.WALL) openingsOf(id).sumOf { it.w * it.h } else 0.0
        return (s.areaM2() - holes).coerceAtLeast(0.0)
    }

    /** Раскладка плитки для стены или потолка (с учётом проёмов). */
    fun surfaceLayout(id: String): LayoutResult {
        val s = model.surfaces.firstOrNull { it.id == id } ?: return TilingEngine.build(room, tile, pattern)
        val spec = RoomSpec(s.outline, if (s.kind == SurfaceKind.WALL) openingsOf(id) else s.holes)
        return TilingEngine.build(spec, tile, pattern)
    }

    // ---------- советы ----------

    fun runSuggest() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) {
                LayoutSuggester.suggest(room, tile, pattern)
            }
            suggestions = s
        }
    }

    fun applySuggestion(s: LayoutSuggester.Suggestion) {
        pattern = pattern.copy(type = s.type, rotationDeg = s.rotationDeg)
        suggestions = null
    }

    // ---------- проекты ----------

    private fun toast(resId: Int) {
        Toast.makeText(getApplication(), getApplication<Application>().getString(resId), Toast.LENGTH_SHORT).show()
    }

    fun refreshProjects() {
        viewModelScope.launch {
            projects = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    private fun currentDto(): ProjectDto {
        syncActiveRoom()
        val n = projectName.ifBlank { getApplication<Application>().getString(R.string.default_name) }
        return ProjectDto(
            name = n,
            room = room,
            tile = tile,
            pattern = pattern,
            colorArgb = tileColor.toArgb(),
            variation = variation,
            reservePct = reservePct,
            decor = decor,
            anchor = anchor,
            furniture = furniture,
            finishes = finishes,
            openings = openings,
            openingKinds = openingKinds,
            wallHeightM = wallHeightM,
            wallThicknessM = wallThicknessM,
            skirtMode = skirtMode,
            skirtBarLenM = skirtBarLenM,
            skirtHeightMm = skirtHeightMm,
            pairCuts = pairCuts,
            workStatus = workStatus,
            prices = prices,
            rooms = rooms,
            activeRoom = activeRoom,
            savedAt = System.currentTimeMillis(),
        )
    }

    fun saveProject() {
        val dto = currentDto()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.save(dto) }
            projectName = dto.name
            refreshProjects()
            toast(R.string.saved)
        }
    }

    private fun applyDto(dto: ProjectDto) {
        room = dto.room
        tile = dto.tile
        pattern = dto.pattern
        tileColor = Color(dto.colorArgb)
        tileImage = null
        variation = dto.variation
        reservePct = dto.reservePct
        decor = dto.decor
        anchor = dto.anchor
        furniture = dto.furniture
        finishes = dto.finishes
        openings = dto.openings
        openingKinds = dto.openingKinds
        wallHeightM = dto.wallHeightM
        wallThicknessM = dto.wallThicknessM
        skirtMode = dto.skirtMode
        skirtBarLenM = dto.skirtBarLenM
        skirtHeightMm = dto.skirtHeightMm
        pairCuts = dto.pairCuts
        workStatus = dto.workStatus
        prices = dto.prices
        if (dto.rooms.isNotEmpty()) {
            rooms = dto.rooms
            activeRoom = dto.activeRoom.coerceIn(0, dto.rooms.lastIndex)
            applyRoom(rooms[activeRoom])
        } else {
            rooms = listOf(snapshotRoom(""))
            activeRoom = 0
        }
        projectName = dto.name
        selection = null
        suggestions = null
        fit()
    }

    /**
     * Упрощение контура: сливает точки ближе 4 см и убирает те, что лежат
     * почти на прямой (отклонение меньше 2 см). Форма остаётся, каша уходит.
     */
    fun simplifyRoom() {
        val pts = room.points
        if (pts.size < 4) return

        data class PI(val p: Pt, val orig: Int)

        val merged = ArrayList<PI>(pts.size)
        pts.forEachIndexed { idx, p ->
            val last = merged.lastOrNull()?.p
            if (last == null ||
                sqrt((p.x - last.x) * (p.x - last.x) + (p.y - last.y) * (p.y - last.y)) > 0.04
            ) {
                merged.add(PI(p, idx))
            }
        }
        while (merged.size > 3) {
            val a = merged.first().p
            val b = merged.last().p
            if (sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)) < 0.04) {
                merged.removeAt(merged.size - 1)
            } else {
                break
            }
        }
        val out = ArrayList<PI>(merged.size)
        for (i in merged.indices) {
            val prev = merged[(i - 1 + merged.size) % merged.size].p
            val cur = merged[i]
            val next = merged[(i + 1) % merged.size].p
            val vx = next.x - prev.x
            val vy = next.y - prev.y
            val len = sqrt(vx * vx + vy * vy)
            val dist = if (len < 1e-9) {
                0.0
            } else {
                abs((cur.p.x - prev.x) * vy - (cur.p.y - prev.y) * vx) / len
            }
            if (dist > 0.02) out.add(cur)
        }
        if (out.size < 3) return
        pushUndo()
        // свойства стен переезжают вслед за выжившими вершинами: новая стена j
        // наследует записи исходной стены, начинавшейся в этой вершине;
        // записи схлопнувшихся стен отбрасываются
        fun <T> rebuilt(src: Map<String, T>): Map<String, T> {
            if (src.isEmpty()) return src
            val res = mutableMapOf<String, T>()
            src.forEach { (k, v) -> if (!k.startsWith("wall-")) res[k] = v }
            out.forEachIndexed { j, pi ->
                src["wall-" + (pi.orig + 1)]?.let { res["wall-" + (j + 1)] = it }
            }
            return res
        }
        wallThickness = rebuilt(wallThickness)
        openings = rebuilt(openings)
        openingKinds = rebuilt(openingKinds)
        finishes = rebuilt(finishes)
        room = room.copy(points = out.map { Pt(round2(it.p.x), round2(it.p.y)) })
        clampOpenings()
        selection = null
        reanchor()
    }

    /** Общий сброс оформления: убирает всё «наставленное», геометрию не трогает. */
    fun resetPlacements() {
        pushUndo()
        tileColors = emptyMap()
        decorOverrides = emptyMap()
        panelOn = false
        zones = emptyList()
        activeZone = -1
        furniture = emptyList()
        decor = DecorSpec()
        paintMode = false
        highlightCut = null
        selection = null
    }

    /** Чистый лист для нового объекта; расценки и логотип мастера остаются. */
    fun newProject() {
        pushUndo()
        projectName = ""
        room = RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0)))
        tile = TileSpec(600.0, 600.0, 3.0)
        pattern = PatternSpec()
        tileColor = Color(0xFFC7CCD6)
        tileImage = null
        decorImage = null
        variation = true
        reservePct = 10
        decor = DecorSpec()
        anchor = AnchorMode.FREE
        furniture = emptyList()
        finishes = mapOf("floor" to Finish.TILE, "ceiling" to Finish.PAINT)
        openings = emptyMap()
        openingKinds = emptyMap()
        decorOverrides = emptyMap()
        panelOn = false
        zones = emptyList()
        tileColors = emptyMap()
        wallThickness = emptyMap()
        activeZone = -1
        wallHeightM = 2.7
        wallThicknessM = 0.10
        rooms = emptyList()
        activeRoom = 0
        rooms = listOf(snapshotRoom(""))
        selection = null
        suggestions = null
        reanchor()
        fit()
    }

    fun loadProject(name: String) {
        viewModelScope.launch {
            val dto = withContext(Dispatchers.IO) { repo.load(name) } ?: return@launch
            applyDto(dto)
            toast(R.string.loaded)
        }
    }

    fun deleteProject(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(name) }
            if (projectName == name) projectName = ""
            refreshProjects()
            toast(R.string.deleted)
        }
    }

    init {
        favTiles = (favPrefs().getString("tiles", "") ?: "")
            .split(";")
            .mapNotNull { part ->
                val p3 = part.split(":")
                if (p3.size == 3) {
                    val a = p3[0].toDoubleOrNull()
                    val b = p3[1].toDoubleOrNull()
                    val c = p3[2].toDoubleOrNull()
                    if (a != null && b != null && c != null) Triple(a, b, c) else null
                } else null
            }
        // Все init-блоки живут в самом конце класса: Kotlin выполняет инициализацию
        // строго сверху вниз, поэтому отсюда все свойства выше гарантированно созданы.
        repo.loadAutosave()?.let { applyDto(it) }
        if (rooms.isEmpty()) rooms = listOf(snapshotRoom(""))
        viewModelScope.launch {
            masterLogo = withContext(Dispatchers.IO) { decodeLogoFile() }
        }
        // автосохранение: через 1.3 с после любого изменения пишем черновик
        viewModelScope.launch {
            snapshotFlow {
                listOf(
                    rooms, activeRoom, room, tile, pattern, tileColor, variation, reservePct,
                    decor, anchor, furniture, finishes, openings, openingKinds, wallHeightM, prices, projectName,
                    skirtMode, skirtBarLenM, skirtHeightMm, pairCuts, workStatus,
                )
            }.collectLatest {
                delay(1300)
                val dto = currentDto()
                withContext(Dispatchers.IO) { repo.saveAutosave(dto) }
            }
        }
    }
}
