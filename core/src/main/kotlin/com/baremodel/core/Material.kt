package com.baremodel.core

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Чем закрывается пол: плитка, ламинат, паркет-ёлочка, террасная доска или ничего.
 * Геометрия у всех одна (движок раскладки видит планку как очень вытянутую плитку),
 * различается расчёт покупки: плитку берут штуками, планку — упаковками,
 * и у планки есть свои правила разбега швов и минимального куска.
 */
@Serializable
enum class MaterialKind { TILE, LAMINATE, PARQUET, DECK, NONE }

/** Планка (ламинат/паркет/доска) считается рядами и упаковками, плитка — штуками. */
val MaterialKind.isPlank: Boolean
    get() = this == MaterialKind.LAMINATE || this == MaterialKind.PARQUET || this == MaterialKind.DECK

/**
 * Правила укладки материала. Габарит планки НЕ дублируется:
 * длина вдоль ряда — [TileSpec.widthMm], ширина ряда — [TileSpec.heightMm],
 * зазор между планками — [TileSpec.groutMm]. Так раскладка, 3D, AR и нумерация
 * подрезки работают без изменений.
 */
@Serializable
data class MaterialSpec(
    val kind: MaterialKind = MaterialKind.TILE,
    /** Технологический зазор у стены, мм: планка не доходит до стены, прячется под плинтус. */
    val expansionMm: Double = 10.0,
    /** Минимальная длина куска, который допустимо ставить в ряд, мм. */
    val minEndMm: Double = 300.0,
    /** Обязательный разбег торцевых швов соседних рядов, мм. */
    val staggerMm: Double = 300.0,
    /** Упаковка: м² в ней и число планок. Нули — материал продаётся штуками. */
    val packM2: Double = 2.13,
    val packPieces: Int = 8,
    /** Начинать ряд остатком предыдущего — так работают на объекте. */
    val reuseOffcuts: Boolean = true,
) {
    companion object {
        /** Типовые параметры под вид материала. */
        fun preset(kind: MaterialKind): MaterialSpec = when (kind) {
            MaterialKind.TILE -> MaterialSpec(MaterialKind.TILE)
            MaterialKind.LAMINATE -> MaterialSpec(
                kind = MaterialKind.LAMINATE,
                expansionMm = 10.0, minEndMm = 300.0, staggerMm = 300.0,
                packM2 = 2.13, packPieces = 8,
            )
            MaterialKind.PARQUET -> MaterialSpec(
                kind = MaterialKind.PARQUET,
                expansionMm = 12.0, minEndMm = 200.0, staggerMm = 200.0,
                packM2 = 1.0, packPieces = 14,
            )
            MaterialKind.DECK -> MaterialSpec(
                kind = MaterialKind.DECK,
                expansionMm = 8.0, minEndMm = 400.0, staggerMm = 400.0,
                packM2 = 0.0, packPieces = 0,
            )
            MaterialKind.NONE -> MaterialSpec(MaterialKind.NONE)
        }

        /** Типовой габарит: длина × ширина планки и зазор, мм. */
        fun presetTile(kind: MaterialKind): TileSpec = when (kind) {
            MaterialKind.LAMINATE -> TileSpec(1200.0, 190.0, 0.0)
            MaterialKind.PARQUET -> TileSpec(600.0, 120.0, 0.0)
            MaterialKind.DECK -> TileSpec(2000.0, 145.0, 5.0)
            else -> TileSpec(600.0, 600.0, 3.0)
        }

        /** Узор по умолчанию: ламинат и доска — разбег 1/3, паркет — ёлочка. */
        fun presetPattern(kind: MaterialKind): PatternType = when (kind) {
            MaterialKind.LAMINATE, MaterialKind.DECK -> PatternType.THIRD
            MaterialKind.PARQUET -> PatternType.HERRINGBONE
            else -> PatternType.GRID
        }
    }
}

/** Один кусок в ряду: длина в мм, целый или с подрезкой. */
data class PlankPiece(val lenMm: Double, val cut: Boolean)

/** Ряд раскладки: номер, отрезок(и) вдоль ряда и что в них уложено. */
data class PlankRow(val index: Int, val spanLenM: Double, val pieces: List<PlankPiece>)

/**
 * План расхода планки. [planksUsed] — сколько планок надо распаковать,
 * [savedPlanks] — сколько сэкономила нарезка нескольких кусков из одной планки.
 */
data class PlankPlan(
    val rowCount: Int,
    val pieces: Int,
    val cutPieces: Int,
    val reusedPieces: Int,
    val planksUsed: Int,
    val savedPlanks: Int,
    val leftoversMm: List<Double>,
    val planksWithReserve: Int,
    val packs: Int,
    val floorM2: Double,
    /** Что реально ляжет на пол (без зазоров между планками), м². */
    val laidM2: Double,
    /** Что придётся распаковать под раскладку, м². */
    val spendM2: Double,
    /** Что купить: упаковками (или штуками, если упаковок нет), м². */
    val boughtM2: Double,
    /** Отход раскладки: распаковано минус уложено, %. */
    val wastePct: Double,
    val shortLastRows: Int,
    val tightJoints: Int,
    val estimated: Boolean,
    val rows: List<PlankRow>,
) {
    /** Ряды, где последний кусок короче минимума, — раскладку надо сдвинуть. */
    val hasWarnings: Boolean get() = shortLastRows > 0 || tightJoints > 0
}

/**
 * Расход планки по рядам — ровно по той сетке, которую рисует [TilingEngine],
 * поэтому числа в отчёте совпадают с картинкой и с номерами подрезки.
 *
 * Ряд идёт вдоль локальной оси X узора, толщина ряда — ширина планки + зазор,
 * сдвиг ряда — 1/2, 1/3 или без сдвига (как в узоре). Для каждого ряда берутся
 * отрезки «внутри помещения» (сканлайн по трём линиям полосы, объединение —
 * планка обязана дотянуться до самой далёкой стены полосы), из длины вычитается
 * технологический зазор у обеих стен, дальше клетки сетки режутся по краям.
 *
 * Целые клетки — целые планки. Подрезки складываются в планки как в контейнеры
 * (из одной планки режем несколько кусков, пока влезают) — это и есть
 * переиспользование остатков; [PlankPlan.savedPlanks] показывает выигрыш.
 *
 * Ёлочка по рядам не считается (куски идут парами под 45°) — для неё расход
 * оценивается по площади с коэффициентом отхода, [PlankPlan.estimated] = true.
 */
object PlankCalc {

    /** Практический отход на ёлочку: 12% сверх площади. */
    const val HERRINGBONE_WASTE = 0.12

    /** Предел числа рядов, чтобы не подвесить UI на огромном плане. */
    private const val ROW_LIMIT = 4000

    fun plan(
        room: RoomSpec,
        tile: TileSpec,
        pattern: PatternSpec,
        spec: MaterialSpec,
        reservePct: Int = 0,
    ): PlankPlan {
        val floorM2 = floorArea(room)
        val plankLen = tile.widthMm
        val plankWid = tile.heightMm
        val empty = PlankPlan(
            0, 0, 0, 0, 0, 0, emptyList(), 0, 0, floorM2, 0.0, 0.0, 0.0, 0.0, 0, 0, false, emptyList(),
        )
        if (plankLen <= 1.0 || plankWid <= 1.0 || room.points.size < 3 || floorM2 <= 0.0) return empty

        val plankM2 = plankLen * plankWid / 1_000_000.0
        val reserve = 1.0 + max(0, reservePct) / 100.0

        // Ёлочка: по рядам не набирается, считаем по площади.
        if (pattern.type == PatternType.HERRINGBONE) {
            val need = floorM2 * (1.0 + HERRINGBONE_WASTE)
            val used = ceil(need / plankM2).toInt()
            val withReserve = ceil(used * reserve).toInt()
            val packs = packsFor(withReserve, withReserve * plankM2, spec)
            val bought = boughtM2(withReserve, plankM2, packs, spec)
            return empty.copy(
                pieces = used, planksUsed = used, planksWithReserve = withReserve, packs = packs,
                laidM2 = floorM2, spendM2 = used * plankM2, boughtM2 = bought,
                wastePct = wasteOf(floorM2, used * plankM2), estimated = true,
            )
        }

        val stepW = (plankLen + max(0.0, tile.groutMm)) / 1000.0
        val stepH = (plankWid + max(0.0, tile.groutMm)) / 1000.0
        val expM = max(0.0, spec.expansionMm) / 1000.0
        val plankM = plankLen / 1000.0
        val minEndM = max(0.0, spec.minEndMm) / 1000.0
        val staggerM = max(0.0, spec.staggerMm) / 1000.0
        // разбег ряда — тот же, что рисует движок: 1/2, 1/3 или без сдвига
        val k = when (pattern.type) {
            PatternType.HALF -> 2
            PatternType.THIRD -> 3
            else -> 1
        }

        val rad = pattern.rotationDeg * PI / 180.0
        val cosA = cos(rad)
        val sinA = sin(rad)
        fun inv(p: Pt) = Pt(
            (p.x - pattern.offsetX) * cosA + (p.y - pattern.offsetY) * sinA,
            -(p.x - pattern.offsetX) * sinA + (p.y - pattern.offsetY) * cosA,
        )

        val poly = room.points.map { inv(it) }
        val holes = room.cutouts.map { c ->
            listOf(
                inv(Pt(c.x, c.y)), inv(Pt(c.x + c.w, c.y)),
                inv(Pt(c.x + c.w, c.y + c.h)), inv(Pt(c.x, c.y + c.h)),
            )
        }
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in poly) {
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val r0 = floor(minY / stepH).toInt()
        val r1 = ceil(maxY / stepH).toInt()
        if (r1 - r0 > ROW_LIMIT) return empty

        val rows = ArrayList<PlankRow>()
        val cutLens = ArrayList<Double>()
        var laidM = 0.0
        var fullCells = 0
        var pieces = 0
        var shortLast = 0
        var tight = 0
        var prevJoints: List<Double> = emptyList()

        for (r in r0..r1) {
            val yTop = r * stepH
            val yBot = yTop + plankWid / 1000.0
            if (yBot < minY || yTop > maxY) continue
            val eps = (yBot - yTop) * 0.02
            val spans = unionSpans(
                listOf(yTop + eps, (yTop + yBot) / 2.0, yBot - eps).map { y ->
                    subtractSpans(scanSpans(poly, y), holes.flatMap { scanSpans(it, y) })
                },
            )
            if (spans.isEmpty()) continue

            val off = ((r % k + k) % k) * (stepW / k)
            val joints = ArrayList<Double>()
            val rowPieces = ArrayList<PlankPiece>()
            var rowLen = 0.0

            for ((sx1, sx2) in spans) {
                val a = sx1 + expM
                val b = sx2 - expM
                if (b - a <= 1e-6) continue
                rowLen += b - a
                val n0 = floor((a - off) / stepW).toInt()
                val n1 = ceil((b - off) / stepW).toInt()
                for (n in n0..n1) {
                    val cx1 = off + n * stepW
                    val l1 = max(cx1, a)
                    val l2 = min(cx1 + plankM, b)
                    val len = l2 - l1
                    if (len <= 1e-6) continue // клетка попала в шов или мимо ряда
                    val cut = len < plankM - 1e-6
                    if (cut) {
                        cutLens.add(len)
                        if (len < minEndM - 1e-9) shortLast++
                    } else {
                        fullCells++
                    }
                    rowPieces.add(PlankPiece(len * 1000.0, cut))
                    val jx = cx1 + plankM
                    if (jx > a + 1e-9 && jx < b - 1e-9) joints.add(jx)
                }
            }
            if (rowPieces.isEmpty()) continue
            pieces += rowPieces.size
            for (j in joints) {
                if (prevJoints.any { abs(it - j) < staggerM - 1e-9 }) tight++
            }
            prevJoints = joints
            laidM += rowLen
            rows.add(PlankRow(r, rowLen, rowPieces))
        }

        // подрезки: из одной планки режем несколько кусков, пока влезают
        val bins = ArrayList<Double>()
        if (spec.reuseOffcuts) {
            for (len in cutLens.sortedDescending()) {
                val i = bins.indices.filter { bins[it] >= len - 1e-9 }.minByOrNull { bins[it] }
                if (i != null) bins[i] = bins[i] - len else bins.add(plankM - len)
            }
        } else {
            for (len in cutLens) bins.add(plankM - len)
        }
        val planksUsed = fullCells + bins.size
        val withReserve = ceil(planksUsed * reserve).toInt()
        val packs = packsFor(withReserve, withReserve * plankM2, spec)
        val bought = boughtM2(withReserve, plankM2, packs, spec)
        return PlankPlan(
            rowCount = rows.size,
            pieces = pieces,
            cutPieces = cutLens.size,
            reusedPieces = max(0, cutLens.size - bins.size),
            planksUsed = planksUsed,
            savedPlanks = max(0, cutLens.size - bins.size),
            leftoversMm = bins.filter { it >= minEndM - 1e-9 }.map { it * 1000.0 }.sortedDescending(),
            planksWithReserve = withReserve,
            packs = packs,
            floorM2 = floorM2,
            laidM2 = laidM * plankWid / 1000.0,
            spendM2 = planksUsed * plankM2,
            boughtM2 = bought,
            wastePct = wasteOf(laidM * plankWid / 1000.0, planksUsed * plankM2),
            shortLastRows = shortLast,
            tightJoints = tight,
            estimated = false,
            rows = rows,
        )
    }

    /** Сколько упаковок покрывает нужное число планок (0 — материал штуками). */
    fun packsFor(planks: Int, needM2: Double, spec: MaterialSpec): Int = when {
        spec.packPieces > 0 -> ceil(planks.toDouble() / spec.packPieces).toInt()
        spec.packM2 > 0.0 -> ceil(needM2 / spec.packM2).toInt()
        else -> 0
    }

    private fun boughtM2(planks: Int, plankM2: Double, packs: Int, spec: MaterialSpec): Double =
        if (packs > 0 && spec.packM2 > 0.0) packs * spec.packM2 else planks * plankM2

    /** Отход: сколько из распакованного не легло на пол. */
    private fun wasteOf(laidM2: Double, spendM2: Double): Double =
        if (spendM2 <= 0.0) 0.0 else max(0.0, (1.0 - laidM2 / spendM2) * 100.0)

    /** Площадь пола за вычетом вырезов (часть выреза за стеной не считается). */
    fun floorArea(room: RoomSpec): Double =
        polygonArea(room.points) - room.cutouts.sumOf { c ->
            polygonArea(clipPolygonByRect(room.points, c.x, c.y, c.x + c.w, c.y + c.h))
        }

    /** Отрезки «внутри контура» на горизонтали y (чётно-нечётное правило). */
    internal fun scanSpans(poly: List<Pt>, y: Double): List<Pair<Double, Double>> {
        if (poly.size < 3) return emptyList()
        val xs = ArrayList<Double>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val y1 = a.y
            val y2 = b.y
            if (y1 == y2) continue
            if (y >= min(y1, y2) && y < max(y1, y2)) {
                xs.add(a.x + (y - y1) * (b.x - a.x) / (y2 - y1))
            }
        }
        if (xs.size < 2) return emptyList()
        xs.sort()
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        while (i + 1 < xs.size) {
            if (xs[i + 1] - xs[i] > 1e-9) out.add(xs[i] to xs[i + 1])
            i += 2
        }
        return out
    }

    /** Объединение наборов отрезков: планка тянется до самой далёкой стены полосы. */
    internal fun unionSpans(sets: List<List<Pair<Double, Double>>>): List<Pair<Double, Double>> {
        val all = sets.flatten().sortedBy { it.first }
        if (all.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        var cur = all[0]
        for (k in 1 until all.size) {
            val s = all[k]
            cur = if (s.first <= cur.second + 1e-9) cur.first to max(cur.second, s.second) else {
                out.add(cur); s
            }
        }
        out.add(cur)
        return out
    }

    /** Вычитание отверстий из отрезков ряда. */
    internal fun subtractSpans(
        spans: List<Pair<Double, Double>>,
        holes: List<Pair<Double, Double>>,
    ): List<Pair<Double, Double>> {
        if (holes.isEmpty()) return spans
        var cur = spans
        for (h in holes) {
            val next = ArrayList<Pair<Double, Double>>()
            for (s in cur) {
                if (h.second <= s.first + 1e-9 || h.first >= s.second - 1e-9) {
                    next.add(s)
                    continue
                }
                if (h.first > s.first + 1e-9) next.add(s.first to h.first)
                if (h.second < s.second - 1e-9) next.add(h.second to s.second)
            }
            cur = next
        }
        return cur
    }
}
