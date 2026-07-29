package com.baremodel.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Расчёт материалов для поверхностей без плитки: обои и краска. */
object MaterialCalc {

    /** Стандартный рулон обоев: 10.05 × 0.53 м. */
    const val ROLL_LEN_M = 10.05
    const val ROLL_WIDTH_M = 0.53

    /** Средний расход краски: 8 м² на литр за один слой. */
    const val COVERAGE_M2_PER_L = 8.0

    data class Wallpaper(
        val rolls: Int,
        val strips: Int,
        val stripLenM: Double,
        val stripsPerRoll: Int,
    )

    /**
     * Обои: сколько рулонов нужно на стену [widthM] × [heightM].
     * [repeatM] — раппорт рисунка, добавляется к каждой полосе на подгонку.
     */
    fun wallpaper(
        widthM: Double,
        heightM: Double,
        rollLenM: Double = ROLL_LEN_M,
        rollWidthM: Double = ROLL_WIDTH_M,
        repeatM: Double = 0.0,
    ): Wallpaper {
        if (widthM <= 0 || heightM <= 0) return Wallpaper(0, 0, 0.0, 0)
        val stripLen = heightM + max(0.0, repeatM) + 0.05 // запас на подрезку
        val strips = ceil(widthM / rollWidthM).toInt()
        val perRoll = floor(rollLenM / stripLen).toInt().coerceAtLeast(1)
        val rolls = ceil(strips.toDouble() / perRoll).toInt()
        return Wallpaper(rolls, strips, stripLen, perRoll)
    }

    /** Краска: литры на площадь с учётом числа слоёв. */
    fun paintLiters(
        areaM2: Double,
        coats: Int = 2,
        coverageM2PerL: Double = COVERAGE_M2_PER_L,
    ): Double {
        if (areaM2 <= 0 || coats <= 0) return 0.0
        return areaM2 * coats / coverageM2PerL
    }

    /** Клей для плитки, кг: расход зависит от размера плитки (гребёнка). */
    fun tileAdhesiveKg(areaM2: Double, tile: TileSpec): Double {
        val side = max(tile.widthMm, tile.heightMm)
        val perM2 = when {
            side <= 200 -> 3.5
            side <= 400 -> 4.5
            side <= 600 -> 5.5
            else -> 7.0
        }
        return areaM2 * perM2
    }

    /**
     * Затирка, кг: классическая формула поставщиков —
     * (ширина + длина) / (ширина × длина) × шов × толщина плитки × плотность 1.6 × площадь.
     * Размеры плитки в мм, шов в мм, толщина принимается 9 мм для крупного формата.
     */
    fun groutKg(areaM2: Double, tile: TileSpec, thicknessMm: Double = 9.0): Double {
        val w = tile.widthMm
        val h = tile.heightMm
        if (w <= 0 || h <= 0 || tile.groutMm <= 0) return 0.0
        // ×1.1 — практический запас на неровный шов и остатки в вёдрышке
        val perM2 = (w + h) / (w * h) * tile.groutMm * thicknessMm * 1.6 * 1.1
        return areaM2 * perM2
    }

    /** Плинтус: сколько метров и сколько хлыстов заданной длины. */
    fun plinth(perimeterM: Double, doorsM: Double = 0.0, barLenM: Double = 2.5): Pair<Double, Int> {
        val need = (perimeterM - doorsM).coerceAtLeast(0.0) * 1.05
        return need to ceil(need / barLenM).toInt()
    }
}
