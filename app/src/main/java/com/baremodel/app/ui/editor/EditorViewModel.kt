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
import com.baremodel.app.data.ProjectDto
import com.baremodel.app.data.ProjectMeta
import com.baremodel.app.data.ProjectRepository
import com.baremodel.core.Cutout
import com.baremodel.core.LayoutResult
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.PatternSpec
import com.baremodel.core.PatternType
import com.baremodel.core.Pt
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import com.baremodel.core.TilingEngine
import com.baremodel.core.pointInPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Экранное преобразование: метры → пиксели. */
data class ViewTransform(val scale: Float = 110f, val offset: Offset = Offset(40f, 60f))

sealed interface Selection {
    data class Vertex(val i: Int) : Selection
    data class Cut(val i: Int) : Selection
}

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

    var reservePct by mutableStateOf(10)
        private set

    var roomMode by mutableStateOf(false)
        private set

    var showDims by mutableStateOf(true)
        private set

    var showCuts by mutableStateOf(true)
        private set

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

    // ---------- производные ----------

    val layout: LayoutResult by derivedStateOf { TilingEngine.build(room, tile, pattern) }

    val buyCount: Int get() = ceil(layout.totalCount * (1 + reservePct / 100.0)).toInt()

    val buyM2: Double get() = buyCount * tile.widthMm * tile.heightMm / 1e6

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
        if (!didInitialFit && canvasSize.width > 0f && canvasSize.height > 0f) {
            didInitialFit = true
            fit()
        }
    }

    /** Вписать план комнаты в холст. */
    fun fit() {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f || room.points.isEmpty()) return
        val minx = room.points.minOf { it.x }
        val maxx = room.points.maxOf { it.x }
        val miny = room.points.minOf { it.y }
        val maxy = room.points.maxOf { it.y }
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

    private enum class Drag { NONE, PAN, PATTERN, VERTEX, CUT_MOVE, CUT_RESIZE }

    private var drag = Drag.NONE
    private var dragIndex = -1
    private var grabDx = 0.0
    private var grabDy = 0.0

    fun gestureDown(pos: Offset) {
        hintVisible = false
        drag = Drag.NONE
        dragIndex = -1
        val w = toWorld(pos)
        if (!roomMode) {
            drag = if (pointInPolygon(w, room.points)) Drag.PATTERN else Drag.PAN
            return
        }

        // 1. вершина
        room.points.forEachIndexed { i, p ->
            if (drag == Drag.NONE && (toScreen(p) - pos).getDistance() < 22f) {
                drag = Drag.VERTEX
                dragIndex = i
                selection = Selection.Vertex(i)
            }
        }
        if (drag != Drag.NONE) return

        // 2. «+» на середине ребра
        val pts = room.points
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val sa = toScreen(a)
            val sb = toScreen(b)
            if ((sb - sa).getDistance() < 56f) continue
            val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)
            if ((mid - pos).getDistance() < 18f) {
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
                (toScreen(Pt(c.x + c.w, c.y + c.h)) - pos).getDistance() < 22f
            ) {
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
                drag = Drag.CUT_MOVE
                dragIndex = i
                grabDx = w.x - c.x
                grabDy = w.y - c.y
                selection = Selection.Cut(i)
            }
        }
        if (drag != Drag.NONE) return

        // 5. панорамирование
        drag = Drag.PAN
        selection = null
    }

    fun gestureMove(pos: Offset, prev: Offset) {
        val d = pos - prev
        when (drag) {
            Drag.PAN -> view = view.copy(offset = view.offset + d)

            Drag.PATTERN -> pattern = pattern.copy(
                offsetX = pattern.offsetX + d.x / view.scale,
                offsetY = pattern.offsetY + d.y / view.scale,
            )

            Drag.VERTEX -> {
                val pts = room.points.toMutableList()
                if (dragIndex in pts.indices) {
                    pts[dragIndex] = snapVertex(dragIndex, toWorld(pos))
                    room = room.copy(points = pts)
                }
            }

            Drag.CUT_MOVE -> {
                val cs = room.cutouts.toMutableList()
                if (dragIndex in cs.indices) {
                    val w = toWorld(pos)
                    val c = cs[dragIndex]
                    cs[dragIndex] = c.copy(x = round2(w.x - grabDx), y = round2(w.y - grabDy))
                    room = room.copy(cutouts = cs)
                }
            }

            Drag.CUT_RESIZE -> {
                val cs = room.cutouts.toMutableList()
                if (dragIndex in cs.indices) {
                    val w = toWorld(pos)
                    val c = cs[dragIndex]
                    cs[dragIndex] = c.copy(
                        w = max(0.1, round2(w.x - c.x)),
                        h = max(0.1, round2(w.y - c.y)),
                    )
                    room = room.copy(cutouts = cs)
                }
            }

            Drag.NONE -> Unit
        }
    }

    fun gestureEnd() {
        drag = Drag.NONE
        dragIndex = -1
    }

    /** Прервать текущий жест (например, при переходе к двупальцевому зуму). */
    fun cancelGesture() {
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

    fun setTileWidth(mm: Double) { tile = tile.copy(widthMm = mm) }

    fun setTileHeight(mm: Double) { tile = tile.copy(heightMm = mm) }

    fun setGrout(mm: Double) { tile = tile.copy(groutMm = mm) }

    fun setPatternType(t: PatternType) { pattern = pattern.copy(type = t); suggestions = null }

    fun setRotation(deg: Double) { pattern = pattern.copy(rotationDeg = deg) }

    fun resetShift() { pattern = pattern.copy(offsetX = 0.0, offsetY = 0.0) }

    fun setColor(c: Color) { tileColor = c; tileImage = null }

    fun toggleVariation() { variation = !variation }

    fun clearImage() { tileImage = null }

    fun loadTileImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, uri)
                        ) { decoder, _, _ -> decoder.isMutableRequired = false }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }.getOrNull()
            }
            if (bmp != null) tileImage = bmp.asImageBitmap()
        }
    }

    // ---------- комната ----------

    fun applyRect(wM: Double, hM: Double) {
        room = RoomSpec(
            listOf(Pt(0.0, 0.0), Pt(wM, 0.0), Pt(wM, hM), Pt(0.0, hM)),
            room.cutouts.filter { it.x + it.w <= wM && it.y + it.h <= hM },
        )
        selection = null
        suggestions = null
        fit()
    }

    fun applyLShape() {
        room = RoomSpec(
            listOf(
                Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 1.8),
                Pt(2.2, 1.8), Pt(2.2, 3.0), Pt(0.0, 3.0),
            ),
            emptyList(),
        )
        selection = null
        suggestions = null
        fit()
    }

    fun addCutout() {
        val pts = room.points
        val cx = pts.sumOf { it.x } / pts.size
        val cy = pts.sumOf { it.y } / pts.size
        val cs = room.cutouts + Cutout(round2(cx - 0.4), round2(cy - 0.4), 0.8, 0.8)
        room = room.copy(cutouts = cs)
        roomMode = true
        selection = Selection.Cut(cs.lastIndex)
    }

    fun deleteSelectedVertex() {
        val sel = selection as? Selection.Vertex ?: return
        if (room.points.size <= 3) return
        val pts = room.points.toMutableList()
        if (sel.i !in pts.indices) return
        pts.removeAt(sel.i)
        room = room.copy(points = pts)
        selection = null
    }

    fun deleteSelectedCutout() {
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

    // ---------- режимы и слои ----------

    fun setReserve(p: Int) { reservePct = p }

    fun switchRoomMode(b: Boolean) { roomMode = b; if (!b) selection = null }

    fun toggleDims() { showDims = !showDims }

    fun toggleCuts() { showCuts = !showCuts }

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

    fun saveProject() {
        val n = projectName.ifBlank { getApplication<Application>().getString(R.string.default_name) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.save(
                    ProjectDto(
                        name = n,
                        room = room,
                        tile = tile,
                        pattern = pattern,
                        colorArgb = tileColor.toArgb(),
                        variation = variation,
                        reservePct = reservePct,
                        savedAt = System.currentTimeMillis(),
                    )
                )
            }
            projectName = n
            refreshProjects()
            toast(R.string.saved)
        }
    }

    fun loadProject(name: String) {
        viewModelScope.launch {
            val dto = withContext(Dispatchers.IO) { repo.load(name) } ?: return@launch
            room = dto.room
            tile = dto.tile
            pattern = dto.pattern
            tileColor = Color(dto.colorArgb)
            tileImage = null
            variation = dto.variation
            reservePct = dto.reservePct
            projectName = dto.name
            selection = null
            suggestions = null
            fit()
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
}
