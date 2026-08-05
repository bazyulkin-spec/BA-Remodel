package com.baremodel.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Развёртка стены: та же раскладка, что и на полу, только контур — прямоугольник
 * «длина × высота», а вырезы — проёмы этой стены (они уже хранятся в координатах
 * развёртки: x вдоль стены от угла, y от пола вверх).
 *
 * Благодаря этому стена получает НАСТОЯЩУЮ раскладку с подрезкой и номерами, а не
 * «площадь ÷ площадь плитки». Логика реза, списка обрезков и нумерации — общая
 * с полом, поэтому чинится и проверяется в одном месте.
 */
data class WallSpec(
    val lengthM: Double,
    val heightM: Double,
    val openings: List<Cutout> = emptyList(),
    val tile: TileSpec = TileSpec(300.0, 600.0, 2.0),
    /** Целая плитка от пола (иначе целая под потолком, подрезка внизу). */
    val startFromFloor: Boolean = true,
    /** Центрировать по длине: равные подрезки в обоих углах вместо целой слева. */
    val centered: Boolean = true,
)

/** Ряд или столбец раскладки: сколько целых и какие куски по краям, мм. */
data class WallAxis(val cells: Int, val firstMm: Double, val lastMm: Double)

/**
 * План стены. [layout] — та же структура, что у пола, поэтому нумерация подрезки,
 * список обрезков и отрисовка работают без единой правки.
 */
data class WallPlan(
    val layout: LayoutResult,
    val areaM2: Double,
    val fullCount: Int,
    val cutCount: Int,
    /** По горизонтали и по вертикали: сколько плиток и что в крайних. */
    val across: WallAxis,
    val up: WallAxis,
    val pattern: PatternSpec,
    val tile: TileSpec,
    val lengthM: Double,
    val heightM: Double,
) {
    val totalCount: Int get() = fullCount + cutCount

    /** Сколько покупать с запасом: каждый кусок — из своей плитки. */
    fun buyCount(reservePct: Int): Int =
        ceil(totalCount * (1.0 + max(0, reservePct) / 100.0)).toInt()

    /** Самая узкая полоска у края, мм — по ней и предупреждаем. */
    val thinnestMm: Double
        get() = listOf(across.firstMm, across.lastMm, up.firstMm, up.lastMm)
            .filter { it > 0.5 }
            .minOrNull() ?: 0.0
}

object WallCalc {

    /** Полоска уже этого — плохой рез: крошится и заметна, мм. */
    const val THIN_MM = 30.0

    fun plan(s: WallSpec): WallPlan {
        val len = max(0.0, s.lengthM)
        val h = max(0.0, s.heightM)
        val tw = s.tile.widthMm / 1000.0
        val th = s.tile.heightMm / 1000.0
        val g = max(0.0, s.tile.groutMm) / 1000.0
        val empty = WallPlan(
            LayoutResult(emptyList(), 0, 0, 0.0, emptyList(), false),
            0.0, 0, 0, WallAxis(0, 0.0, 0.0), WallAxis(0, 0.0, 0.0),
            PatternSpec(PatternType.GRID), s.tile, len, h,
        )
        if (len < 0.01 || h < 0.01 || tw < 0.005 || th < 0.005) return empty

        val stepW = tw + g
        val stepH = th + g

        // фаза по горизонтали: целая от угла или симметрично (равные куски по краям)
        val offX = if (s.centered) {
            val n = floor((len + g) / stepW).toInt().coerceAtLeast(0)
            if (n <= 0) 0.0 else (len - (n * tw + (n - 1) * g)) / 2.0
        } else {
            0.0
        }
        // фаза по вертикали: целая от пола или целая под потолком
        val offY = if (s.startFromFloor) {
            0.0
        } else {
            val r = (h - th) % stepH
            if (r < 0) r + stepH else r
        }

        val rect = listOf(Pt(0.0, 0.0), Pt(len, 0.0), Pt(len, h), Pt(0.0, h))
        val holes = s.openings.mapNotNull { o ->
            val x1 = max(0.0, o.x)
            val y1 = max(0.0, o.y)
            val x2 = min(len, o.x + o.w)
            val y2 = min(h, o.y + o.h)
            if (x2 - x1 > 1e-6 && y2 - y1 > 1e-6) Cutout(x1, y1, x2 - x1, y2 - y1) else null
        }
        val pattern = PatternSpec(PatternType.GRID, offsetX = offX, offsetY = offY)
        val layout = TilingEngine.build(RoomSpec(rect, holes), s.tile, pattern)

        return WallPlan(
            layout = layout,
            areaM2 = layout.areaM2,
            fullCount = layout.fullCount,
            cutCount = layout.cutCount,
            across = axis(len, tw, stepW, offX),
            up = axis(h, th, stepH, offY),
            pattern = pattern,
            tile = s.tile,
            lengthM = len,
            heightM = h,
        )
    }

    /**
     * Что видно вдоль одной оси: сколько плиток попадает в пролёт и какие куски
     * с краёв. Сетка та же, что строит движок, поэтому числа сходятся с картинкой.
     */
    internal fun axis(span: Double, tileM: Double, step: Double, off: Double): WallAxis {
        if (span <= 1e-9 || tileM <= 1e-9 || step <= 1e-9) return WallAxis(0, 0.0, 0.0)
        var k = floor((0.0 - off) / step).toInt() - 1
        var count = 0
        var first = 0.0
        var last = 0.0
        var guard = 0
        while (off + k * step < span && guard < 20_000) {
            val a = off + k * step
            val vis = min(span, a + tileM) - max(0.0, a)
            if (vis > 1e-6) {
                count++
                if (count == 1) first = vis
                last = vis
            }
            k++
            guard++
        }
        return WallAxis(count, first * 1000.0, last * 1000.0)
    }

    /**
     * Сдвиг фазы, при котором крайние куски перестают быть полосками:
     * равные куски по краям — самое частое решение у мастера.
     */
    fun suggestCentered(s: WallSpec): WallSpec = s.copy(centered = true)
}
