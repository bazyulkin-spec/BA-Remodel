package com.baremodel.core

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Чем отделана поверхность ступеней. NONE — просто отметить марш, ничего не считать. */
@Serializable
enum class StairsFinish { TILE, WOOD, CONCRETE, NONE }

/**
 * Ступени: крыльцо, подиум или марш на другой этаж.
 *
 * Габарит на плане считается из числа ступеней и проступи, поэтому лестница
 * не может «врать» — вылет всегда равен тому, что реально ляжет.
 * [dirDeg] задаёт, куда идёт подъём: 0 — вверх по плану, дальше по 90°.
 * [toLevel] — этаж, на который ведёт марш, или -1 для крыльца.
 */
@Serializable
data class StairsSpec(
    val id: String,
    val x: Double,
    val y: Double,
    /** Ширина по фронту, метры. */
    val widthM: Double = 1.0,
    val steps: Int = 3,
    /** Проступь — глубина ступени, мм. */
    val treadMm: Double = 300.0,
    /** Подступёнок — высота ступени, мм. */
    val riserMm: Double = 160.0,
    val dirDeg: Int = 0,
    val toLevel: Int = -1,
    /** Материал ступеней свой: крыльцо может быть из другой плитки, чем пол. */
    val tile: TileSpec = TileSpec(300.0, 300.0, 3.0),
    val material: MaterialSpec = MaterialSpec(),
    /** Облицовывать ли подступёнки (у открытых ступеней их нет). */
    val risers: Boolean = true,
    /** Отделка раздельно: проступь плиткой, подступёнок деревом — обычный микс. */
    val treadFinish: StairsFinish = StairsFinish.TILE,
    val riserFinish: StairsFinish = StairsFinish.TILE,
    /** Резать ли пол под маршем: под крыльцом пола нет, под маршем на этаж — бывает. */
    val cutsFloor: Boolean = true,
) {
    /** Общий вылет марша, метры. */
    val runM: Double get() = steps * treadMm / 1000.0

    /** Общий подъём, метры. */
    val riseM: Double get() = steps * riserMm / 1000.0

    val horizontal: Boolean get() = dirDeg == 90 || dirDeg == 270

    val w: Double get() = if (horizontal) runM else widthM
    val h: Double get() = if (horizontal) widthM else runM

    val corners: List<Pt>
        get() = listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h))

    /** Линия стыка ступени [i] — по ней рисуется ребро на плане. */
    fun edge(i: Int): Pair<Pt, Pt> {
        val d = (i + 1) * treadMm / 1000.0
        return when (dirDeg) {
            90 -> Pt(x + d, y) to Pt(x + d, y + h)
            180 -> Pt(x, y + h - d) to Pt(x + w, y + h - d)
            270 -> Pt(x + w - d, y) to Pt(x + w - d, y + h)
            else -> Pt(x, y + d) to Pt(x + w, y + d)
        }
    }
}

/**
 * План материала на ступени. Проступи считаются как полосы во всю ширину,
 * подступёнки нарезаются из целых плиток полосами — как плинтус из плитки.
 */
data class StairsPlan(
    val steps: Int,
    val treadAreaM2: Double,
    val riserAreaM2: Double,
    val areaM2: Double,
    /** Кусков на проступях и на подступёнках. */
    val treadPieces: Int,
    val riserPieces: Int,
    /** Сколько плиток нужно распустить на подступёнки и сколько полос даёт одна. */
    val tilesForRisers: Int,
    val stripsPerTile: Int,
    /** Целых плиток на проступи и плиток под краевые куски. */
    val wholeTreadTiles: Int,
    val treadCutTiles: Int,
    /** Плиток по ширине ступени и что срезаем с крайней, мм. */
    val acrossPieces: Int,
    val cutMm: Double,
    /** Сколько плиток покупать с запасом. */
    val buyPieces: Int,
    /** Формула безопасности 2×подступёнок + проступь, мм. */
    val formulaMm: Double,
    val comfy: Boolean,
    val treadTooShort: Boolean,
    val riserBad: Boolean,
) {
    val piecesTotal: Int get() = treadPieces + riserPieces
    val hasWarnings: Boolean get() = !comfy || treadTooShort || riserBad
}

/**
 * Раскрой ступеней: что именно режем и из скольких плиток.
 * Краевые куски проступей и полосы подступёнков режутся из целых плиток —
 * из одной плитки выходит столько кусков, сколько влезает по её стороне.
 */
data class StairsCut(
    /** Краевые куски проступей: сколько, какой длины и по сколько из плитки. */
    val treadCuts: Int,
    val treadCutMm: Double,
    val perTreadTile: Int,
    val treadTiles: Int,
    val treadLeftMm: Double,
    /** Полосы подступёнков. */
    val riserStrips: Int,
    val riserMm: Double,
    val perRiserTile: Int,
    val riserTiles: Int,
    val riserLeftMm: Double,
    /** Целых плиток на проступи, без подрезки. */
    val wholeTreadTiles: Int,
) {
    /** Всего плиток на марш: целые + под краевые куски + под подступёнки. */
    val totalTiles: Int get() = wholeTreadTiles + treadTiles + riserTiles
}

/** Расчёт ступеней: площади, куски, подрезка и проверка удобства шага. */
object StairsCalc {

    /** Удобный шаг по формуле 2h + b, мм. */
    const val FORMULA_MIN = 600.0
    const val FORMULA_MAX = 650.0

    /** Ниже этого проступь неудобна, мм. */
    const val TREAD_MIN = 250.0

    /** Рабочий диапазон подступёнка, мм. */
    const val RISER_MIN = 140.0
    const val RISER_MAX = 200.0

    /**
     * Раскрой марша. Краевой кусок проступи режется по ширине плитки,
     * полоса подступёнка — по её длине; лишнее остаётся в остатках.
     */
    fun cutPlan(s: StairsSpec): StairsCut {
        val widthMm = s.widthM * 1000.0
        val tw = max(1.0, s.tile.widthMm)
        val th = max(1.0, s.tile.heightMm)
        val g = max(0.0, s.tile.groutMm)
        val empty = StairsCut(0, 0.0, 0, 0, 0.0, 0, s.riserMm, 0, 0, 0.0, 0)
        if (s.steps <= 0 || widthMm <= 1.0 || s.treadMm <= 1.0 || s.riserMm <= 1.0) return empty

        val across = ceil((widthMm + g) / (tw + g)).toInt().coerceAtLeast(1)
        val cut = max(0.0, across * tw + (across - 1) * g - widthMm)
        val rows = ceil((s.treadMm + g) / (th + g)).toInt().coerceAtLeast(1)
        val hasCut = cut > 1.0
        val treadTile = s.treadFinish == StairsFinish.TILE

        val treadCuts = if (treadTile && hasCut) s.steps * rows else 0
        val whole = if (treadTile) s.steps * rows * (across - if (hasCut) 1 else 0) else 0
        val pieceLen = if (hasCut) tw - cut else tw
        val perTreadTile = if (hasCut) floor((tw + g) / (pieceLen + g)).toInt().coerceAtLeast(1) else 0
        val treadTiles = if (treadCuts > 0) ceil(treadCuts.toDouble() / perTreadTile).toInt() else 0
        val treadLeft = if (perTreadTile > 0) max(0.0, tw - perTreadTile * pieceLen - (perTreadTile - 1) * g) else 0.0

        val riserTile = s.risers && s.riserFinish == StairsFinish.TILE
        val strips = if (riserTile) s.steps * across else 0
        val perRiser = if (riserTile) floor((th + g) / (s.riserMm + g)).toInt().coerceAtLeast(1) else 0
        val riserTiles = if (strips > 0) ceil(strips.toDouble() / perRiser).toInt() else 0
        val riserLeft = if (perRiser > 0) max(0.0, th - perRiser * s.riserMm - (perRiser - 1) * g) else 0.0

        return StairsCut(
            treadCuts = treadCuts,
            treadCutMm = pieceLen,
            perTreadTile = perTreadTile,
            treadTiles = treadTiles,
            treadLeftMm = treadLeft,
            riserStrips = strips,
            riserMm = s.riserMm,
            perRiserTile = perRiser,
            riserTiles = riserTiles,
            riserLeftMm = riserLeft,
            wholeTreadTiles = whole,
        )
    }

    fun plan(s: StairsSpec, reservePct: Int = 0): StairsPlan {
        val widthMm = s.widthM * 1000.0
        val tw = max(1.0, s.tile.widthMm)
        val th = max(1.0, s.tile.heightMm)
        val g = max(0.0, s.tile.groutMm)
        val empty = StairsPlan(
            0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0,
            2 * s.riserMm + s.treadMm, false, false, false,
        )
        if (s.steps <= 0 || widthMm <= 1.0 || s.treadMm <= 1.0 || s.riserMm <= 1.0) return empty

        // по ширине ступени: целые плитки + крайняя в подрезку
        val across = ceil((widthMm + g) / (tw + g)).toInt().coerceAtLeast(1)
        val cover = across * tw + (across - 1) * g
        val cut = max(0.0, cover - widthMm)

        // по глубине проступи: обычно один ряд, но у широкой ступени бывает два
        val rows = ceil((s.treadMm + g) / (th + g)).toInt().coerceAtLeast(1)

        // куски считаются по отделке: плитка — раскладкой, дерево — доска на ступень,
        // бетон и «отметка» материала не требуют
        val treadPieces = when (s.treadFinish) {
            StairsFinish.TILE -> s.steps * across * rows
            StairsFinish.WOOD -> s.steps
            else -> 0
        }
        val riserPieces = if (!s.risers) 0 else when (s.riserFinish) {
            StairsFinish.TILE -> s.steps * across
            StairsFinish.WOOD -> s.steps
            else -> 0
        }

        // раскрой: краевые куски и полосы подступёнков режутся из целых плиток
        val cp = cutPlan(s)
        val stripsPerTile = cp.perRiserTile
        val tilesForRisers = cp.riserTiles

        val treadArea = s.steps * s.widthM * s.treadMm / 1000.0
        val riserArea = if (s.risers) s.steps * s.widthM * s.riserMm / 1000.0 else 0.0
        val buy = ceil(cp.totalTiles * (1.0 + max(0, reservePct) / 100.0)).toInt()
        val formula = 2 * s.riserMm + s.treadMm

        return StairsPlan(
            steps = s.steps,
            treadAreaM2 = treadArea,
            riserAreaM2 = riserArea,
            areaM2 = treadArea + riserArea,
            treadPieces = treadPieces,
            riserPieces = riserPieces,
            tilesForRisers = tilesForRisers,
            stripsPerTile = stripsPerTile,
            wholeTreadTiles = cp.wholeTreadTiles,
            treadCutTiles = cp.treadTiles,
            acrossPieces = across,
            cutMm = cut,
            buyPieces = buy,
            formulaMm = formula,
            comfy = formula in FORMULA_MIN..FORMULA_MAX,
            treadTooShort = s.treadMm < TREAD_MIN,
            riserBad = s.riserMm < RISER_MIN || s.riserMm > RISER_MAX,
        )
    }

    /**
     * Подобрать ступени под заданный подъём [heightM]: держит подступёнок
     * в рабочем диапазоне и подгоняет проступь под формулу 2h + b.
     */
    fun fitToHeight(heightM: Double, current: StairsSpec): StairsSpec {
        if (heightM <= 0.0) return current
        val hMm = heightM * 1000.0
        val n = max(1, Math.round(hMm / 170.0).toInt())
        val riser = (hMm / n).coerceIn(RISER_MIN, RISER_MAX)
        val tread = (625.0 - 2 * riser).coerceAtLeast(TREAD_MIN)
        return current.copy(steps = n, riserMm = Math.round(riser).toDouble(), treadMm = Math.round(tread).toDouble())
    }
}
