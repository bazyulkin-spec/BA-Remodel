package com.baremodel.core

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** Объект, который ставится на поверхность и может её перекрывать. Интерфейс IPlacer из ТЗ. */
interface IPlacer {
    val id: String
    val name: String
    /** Габарит на плане, метры. */
    val x: Double
    val y: Double
    val w: Double
    val h: Double
    /** Высота объекта, метры — понадобится для стен и 3D. */
    val heightM: Double
    /** Нужно ли класть покрытие под объектом (под ванной обычно нет). */
    val coversFinish: Boolean
}

@Serializable
data class Furniture(
    override val id: String,
    override val name: String,
    override val x: Double,
    override val y: Double,
    override val w: Double,
    override val h: Double,
    override val heightM: Double = 0.85,
    override val coversFinish: Boolean = true,
) : IPlacer {
    val corners: List<Pt>
        get() = listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h))
}

/** Насколько мебель закрывает декор и сколько плитки экономится под ней. */
data class CoverageReport(
    val decorTiles: Int,
    val decorAreaM2: Double,
    val coveredAreaM2: Double,
    val hiddenTiles: Int,
    val savedTiles: Int,
) {
    val coveredPct: Int get() = if (decorAreaM2 <= 0) 0 else Math.round(coveredAreaM2 / decorAreaM2 * 100).toInt()
    val decorHidden: Boolean get() = coveredPct >= 50
}

object CoverageAnalyzer {

    private fun rectOverlap(a: List<Pt>, f: IPlacer): Double {
        val ax1 = a.minOf { it.x }; val ax2 = a.maxOf { it.x }
        val ay1 = a.minOf { it.y }; val ay2 = a.maxOf { it.y }
        val w = max(0.0, min(ax2, f.x + f.w) - max(ax1, f.x))
        val h = max(0.0, min(ay2, f.y + f.h) - max(ay1, f.y))
        return w * h
    }

    /**
     * [decorIdx] — индексы декоративных плиток из DecorPlanner.select.
     * Считает: какая доля рисунка скрыта мебелью, сколько плиток вообще под мебелью
     * и сколько из них можно не класть (если под объектом покрытие не нужно).
     */
    fun analyze(
        layout: LayoutResult,
        tile: TileSpec,
        decorIdx: Set<Int>,
        art: ArtRect,
        furniture: List<IPlacer>,
    ): CoverageReport {
        var decorArea = 0.0
        var covered = 0.0
        for (i in decorIdx) {
            val t = layout.tiles.getOrNull(i) ?: continue
            val bounds = DecorPlanner.artBounds(t, tile, art)
            val a = polygonArea(bounds)
            decorArea += a
            var c = 0.0
            for (f in furniture) c = max(c, rectOverlap(bounds, f))
            covered += min(a, c)
        }

        var hidden = 0
        var saved = 0
        for (t in layout.tiles) {
            val tileArea = polygonArea(t.corners)
            if (tileArea <= 0) continue
            for (f in furniture) {
                if (rectOverlap(t.corners, f) > tileArea * 0.98) {
                    hidden++
                    if (!f.coversFinish) saved++
                    break
                }
            }
        }
        return CoverageReport(decorIdx.size, decorArea, covered, hidden, saved)
    }
}
