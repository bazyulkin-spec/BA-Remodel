package com.baremodel.core

import kotlin.math.abs
import kotlin.math.hypot

/** Подрезка вдоль одного ребра помещения. */
data class EdgeCut(
    val edgeIndex: Int,
    val lengthM: Double,
    val minStripM: Double,
    val maxStripM: Double,
    val tiles: Int,
) {
    /** Полоса одинаковая по всему ребру (прямая стена без клина). */
    val uniform: Boolean get() = maxStripM - minStripM < 0.005
    val minStripCm: Double get() = minStripM * 100
    val maxStripCm: Double get() = maxStripM * 100
}

/** Предупреждение раскладки для показа мастеру. */
data class LayoutWarning(val code: String, val edgeIndex: Int = -1, val valueCm: Double = 0.0)

data class CutReport(
    val edges: List<EdgeCut>,
    val symmetricX: Boolean,
    val symmetricY: Boolean,
    val warnings: List<LayoutWarning>,
)

/**
 * Разбор подрезки по каждому ребру помещения: сколько срезать у каждой стены,
 * симметрична ли раскладка и нет ли слишком узких полос.
 */
object CutAnalyzer {

    /** Полосы уже этого значения считаются неудобными для реза. */
    const val THIN_STRIP_M = 0.06

    fun analyze(room: RoomSpec, tile: TileSpec, layout: LayoutResult, samples: Int = 9): CutReport {
        val pts = room.points
        if (pts.size < 3 || layout.tiles.isEmpty()) {
            return CutReport(emptyList(), false, false, emptyList())
        }
        val edges = ArrayList<EdgeCut>()

        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < 1e-6) continue
            // внутренняя нормаль
            var nx = -(b.y - a.y) / len
            var ny = (b.x - a.x) / len
            val mid = Pt((a.x + b.x) / 2 + nx * 0.01, (a.y + b.y) / 2 + ny * 0.01)
            if (!pointInPolygon(mid, pts)) { nx = -nx; ny = -ny }

            var lo = Double.MAX_VALUE
            var hi = 0.0
            val seen = HashSet<Int>()
            for (s in 0 until samples) {
                val t = (s + 0.5) / samples
                val px = a.x + (b.x - a.x) * t + nx * 0.002
                val py = a.y + (b.y - a.y) * t + ny * 0.002
                val p = Pt(px, py)
                val idx = layout.tiles.indexOfFirst { pointInPolygon(p, it.corners) }
                if (idx < 0) continue
                seen.add(idx)
                val piece = clipPolygonByQuad(pts, layout.tiles[idx].corners)
                if (piece.size < 3) continue
                // глубина куска от стены внутрь помещения
                val depth = piece.maxOf { (it.x - a.x) * nx + (it.y - a.y) * ny }
                if (depth < lo) lo = depth
                if (depth > hi) hi = depth
            }
            if (lo == Double.MAX_VALUE) continue
            edges.add(EdgeCut(i, len, lo, hi, seen.size))
        }

        // симметрия: сравниваем противоположные рёбра прямоугольного контура
        var symX = false
        var symY = false
        if (pts.size == 4 && edges.size == 4) {
            symX = abs(edges[1].minStripM - edges[3].minStripM) < 0.005
            symY = abs(edges[0].minStripM - edges[2].minStripM) < 0.005
        }

        val warns = ArrayList<LayoutWarning>()
        for (e in edges) {
            val tw = tile.widthMm / 1000.0
            if (e.minStripM < THIN_STRIP_M && e.minStripM < tw - 1e-6) {
                warns.add(LayoutWarning("THIN_STRIP", e.edgeIndex, e.minStripCm))
            }
            if (!e.uniform && e.maxStripM - e.minStripM > 0.02) {
                warns.add(LayoutWarning("TAPERED_STRIP", e.edgeIndex, (e.maxStripM - e.minStripM) * 100))
            }
        }
        if (pts.size == 4 && edges.size == 4 && !(symX && symY)) {
            warns.add(LayoutWarning("ASYMMETRIC"))
        }
        return CutReport(edges, symX, symY, warns)
    }
}
