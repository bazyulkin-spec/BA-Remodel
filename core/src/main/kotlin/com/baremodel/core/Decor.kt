package com.baremodel.core

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Область рисунка внутри плитки в долях от её размера (0..1).
 * Нужна, чтобы центрировать раскладку по самому рисунку, а не по краю плитки.
 */
@Serializable
data class ArtRect(val x: Double, val y: Double, val w: Double, val h: Double) {
    val cx: Double get() = x + w / 2
    val cy: Double get() = y + h / 2

    companion object {
        /** Рисунок занимает всю плитку. */
        val FULL = ArtRect(0.0, 0.0, 1.0, 1.0)
    }
}

@Serializable
enum class DecorMode { NONE, SINGLE, PANEL, EVERY_N, ALL }

/**
 * Правило размещения декоративной плитки.
 * [everyN] — шаг для EVERY_N (каждая n-я в шахматном порядке).
 * [panelCols]/[panelRows] — размер панно в плитках для PANEL.
 * [art] — где на плитке находится рисунок.
 */
@Serializable
data class DecorSpec(
    val mode: DecorMode = DecorMode.NONE,
    val everyN: Int = 3,
    val panelCols: Int = 2,
    val panelRows: Int = 2,
    val art: ArtRect = ArtRect.FULL,
)

/** Точка отсчёта раскладки. */
@Serializable
enum class AnchorMode { ART_CENTER, TILE_CENTER, CORNER, FREE }

object Aligner {

    /**
     * Смещение узора для выбранной точки отсчёта.
     * ART_CENTER — центр рисунка совпадает с центром поверхности;
     * TILE_CENTER — центр плитки совпадает с центром поверхности (симметричная подрезка);
     * CORNER — целая плитка прижата к левому верхнему углу габарита.
     */
    fun offsetFor(
        outline: List<Pt>,
        tile: TileSpec,
        anchor: AnchorMode,
        art: ArtRect = ArtRect.FULL,
        rotationDeg: Double = 0.0,
    ): Pair<Double, Double> {
        if (outline.isEmpty()) return 0.0 to 0.0
        val minx = outline.minOf { it.x }
        val maxx = outline.maxOf { it.x }
        val miny = outline.minOf { it.y }
        val maxy = outline.maxOf { it.y }
        val cx = (minx + maxx) / 2
        val cy = (miny + maxy) / 2
        val tw = tile.widthMm / 1000.0
        val th = tile.heightMm / 1000.0
        val rad = rotationDeg * PI / 180.0
        val cs = cos(rad)
        val sn = sin(rad)

        // локальная точка внутри плитки, которую нужно совместить с целевой точкой
        val (lx, ly) = when (anchor) {
            AnchorMode.ART_CENTER -> art.cx * tw to art.cy * th
            AnchorMode.TILE_CENTER -> tw / 2 to th / 2
            AnchorMode.CORNER -> 0.0 to 0.0
            AnchorMode.FREE -> return 0.0 to 0.0
        }
        val targetX = if (anchor == AnchorMode.CORNER) minx else cx
        val targetY = if (anchor == AnchorMode.CORNER) miny else cy
        // offset = target − R·(lx, ly)
        return (targetX - (lx * cs - ly * sn)) to (targetY - (lx * sn + ly * cs))
    }

    /** Готовый PatternSpec с нужной точкой отсчёта. */
    fun applyAnchor(
        pattern: PatternSpec,
        outline: List<Pt>,
        tile: TileSpec,
        anchor: AnchorMode,
        art: ArtRect = ArtRect.FULL,
    ): PatternSpec {
        if (anchor == AnchorMode.FREE) return pattern
        val (ox, oy) = offsetFor(outline, tile, anchor, art, pattern.rotationDeg)
        return pattern.copy(offsetX = ox, offsetY = oy)
    }
}

/** Индексы плитки в решётке узора. */
data class GridIndex(val col: Int, val row: Int)

object DecorPlanner {

    /** Позиция плитки в решётке (для правил «каждая n-я» и панно). */
    fun indexOf(t: PlacedTile, tile: TileSpec): GridIndex {
        val stepW = (tile.widthMm + tile.groutMm) / 1000.0
        val stepH = (tile.heightMm + tile.groutMm) / 1000.0
        return GridIndex((t.rect.x / stepW).roundToInt(), (t.rect.y / stepH).roundToInt())
    }

    /**
     * Какие плитки раскладки становятся декоративными.
     * Декор никогда не попадает на подрезку (кроме режима ALL) — резать рисунок нельзя.
     */
    fun select(layout: LayoutResult, tile: TileSpec, spec: DecorSpec, center: Pt): Set<Int> {
        if (spec.mode == DecorMode.NONE || layout.tiles.isEmpty()) return emptySet()
        if (spec.mode == DecorMode.ALL) return layout.tiles.indices.toSet()

        val full = layout.tiles.withIndex().filter { it.value.cls == TileClass.FULL }
        if (full.isEmpty()) return emptySet()

        fun centerOf(t: PlacedTile): Pt {
            val xs = t.corners.sumOf { it.x } / t.corners.size
            val ys = t.corners.sumOf { it.y } / t.corners.size
            return Pt(xs, ys)
        }

        return when (spec.mode) {
            DecorMode.SINGLE -> {
                val best = full.minByOrNull {
                    val c = centerOf(it.value)
                    (c.x - center.x) * (c.x - center.x) + (c.y - center.y) * (c.y - center.y)
                }
                setOfNotNull(best?.index)
            }

            DecorMode.PANEL -> {
                val best = full.minByOrNull {
                    val c = centerOf(it.value)
                    (c.x - center.x) * (c.x - center.x) + (c.y - center.y) * (c.y - center.y)
                } ?: return emptySet()
                val gi = indexOf(best.value, tile)
                val cols = spec.panelCols.coerceAtLeast(1)
                val rows = spec.panelRows.coerceAtLeast(1)
                val c0 = gi.col - (cols - 1) / 2
                val r0 = gi.row - (rows - 1) / 2
                full.filter {
                    val g = indexOf(it.value, tile)
                    g.col in c0 until c0 + cols && g.row in r0 until r0 + rows
                }.map { it.index }.toSet()
            }

            DecorMode.EVERY_N -> {
                val n = spec.everyN.coerceAtLeast(2)
                full.filter {
                    val g = indexOf(it.value, tile)
                    ((g.col + g.row) % n + n) % n == 0
                }.map { it.index }.toSet()
            }

            else -> emptySet()
        }
    }

    /** Габарит рисунка на плане в мировых координатах (для проверки перекрытия мебелью). */
    fun artBounds(t: PlacedTile, tile: TileSpec, art: ArtRect): List<Pt> {
        val tw = tile.widthMm / 1000.0
        val th = tile.heightMm / 1000.0
        val ax = t.rect.x + art.x * (if (t.rect.vertical) th else tw)
        val ay = t.rect.y + art.y * (if (t.rect.vertical) tw else th)
        val aw = art.w * (if (t.rect.vertical) th else tw)
        val ah = art.h * (if (t.rect.vertical) tw else th)
        // угол плитки в мире + доли вдоль её сторон
        val o = t.corners[0]
        val ux = Pt(
            (t.corners[1].x - t.corners[0].x) / (if (t.rect.vertical) th else tw),
            (t.corners[1].y - t.corners[0].y) / (if (t.rect.vertical) th else tw),
        )
        val uy = Pt(
            (t.corners[3].x - t.corners[0].x) / (if (t.rect.vertical) tw else th),
            (t.corners[3].y - t.corners[0].y) / (if (t.rect.vertical) tw else th),
        )
        val lx = ax - t.rect.x
        val ly = ay - t.rect.y
        fun p(dx: Double, dy: Double) = Pt(o.x + ux.x * dx + uy.x * dy, o.y + ux.y * dx + uy.y * dy)
        return listOf(p(lx, ly), p(lx + aw, ly), p(lx + aw, ly + ah), p(lx, ly + ah))
    }

    /** Совпадает ли центр рисунка с точкой (с допуском). */
    fun artCenterMatches(t: PlacedTile, tile: TileSpec, art: ArtRect, target: Pt, tol: Double = 0.003): Boolean {
        val b = artBounds(t, tile, art)
        val cx = b.sumOf { it.x } / b.size
        val cy = b.sumOf { it.y } / b.size
        return abs(cx - target.x) < tol && abs(cy - target.y) < tol
    }
}
