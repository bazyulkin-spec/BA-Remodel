package com.baremodel.core

import kotlin.math.hypot
import kotlin.math.max

/** Отрезок плинтуса вдоль одной стены: между углами и дверными проёмами. */
data class SkirtSegment(
    val wall: Int,
    val startM: Double,
    val lenM: Double,
    val partOfWall: Int,
    val partsOnWall: Int,
)

/** Кусок, отпиленный от хлыста: какой сегмент закрывает. */
data class SkirtCut(
    val segment: Int,
    val lenM: Double,
    val isJointPart: Boolean,
)

/** Один хлыст: распил и остаток. */
data class SkirtBar(val cuts: List<SkirtCut>, val restM: Double)

data class SkirtPlan(
    val segments: List<SkirtSegment>,
    val bars: List<SkirtBar>,
    val totalM: Double,
    val barLenM: Double,
    val joints: Int,
)

/**
 * Плинтус как материал со своим планом распила — та же идея, что подрезка плитки:
 * видно, какие куски нужны, из какого хлыста их пилить и куда уходит каждый остаток.
 */
object SkirtingCalc {

    /** Куски короче этого не пилим и не считаем (щель у самого угла). */
    const val MIN_SEG_M = 0.03

    /**
     * Сегменты плинтуса: каждая стена режется стоящими на полу проёмами
     * (двери, проходы: y < 0.05) на куски; окна плинтус не разрывают.
     */
    fun segments(points: List<Pt>, openings: Map<String, List<Cutout>>): List<SkirtSegment> {
        val res = ArrayList<SkirtSegment>()
        if (points.size < 3) return res
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < MIN_SEG_M) continue
            val doors = (openings["wall-" + (i + 1)] ?: emptyList())
                .filter { it.y < 0.05 }
                .map { max(0.0, it.x) to kotlin.math.min(len, it.x + it.w) }
                .filter { it.second - it.first > 1e-6 }
                .sortedBy { it.first }
            val spans = ArrayList<Pair<Double, Double>>()
            var cur = 0.0
            for ((s0, s1) in doors) {
                if (s0 - cur > MIN_SEG_M) spans.add(cur to s0)
                cur = max(cur, s1)
            }
            if (len - cur > MIN_SEG_M) spans.add(cur to len)
            spans.forEachIndexed { pi, (s0, s1) ->
                res.add(
                    SkirtSegment(
                        i,
                        Math.round(s0 * 1000.0) / 1000.0,
                        Math.round((s1 - s0) * 1000.0) / 1000.0,
                        pi,
                        spans.size,
                    ),
                )
            }
        }
        return res
    }

    /**
     * План распила. Сегменты длиннее хлыста режутся на целые части + добор
     * (стык на стене), затем куски упаковываются в хлысты First-Fit-Decreasing:
     * остаток одного хлыста уходит на короткий сегмент другой стены — это и есть
     * совет «какой кусок куда поставить».
     */
    fun plan(segments: List<SkirtSegment>, barLenM: Double): SkirtPlan {
        val bl = max(0.3, barLenM)

        data class Piece(val segment: Int, val lenM: Double, val joint: Boolean)

        val pieces = ArrayList<Piece>()
        var joints = 0
        segments.forEachIndexed { si, seg ->
            var rest = seg.lenM
            while (rest > bl + 1e-9) {
                pieces.add(Piece(si, bl, true))
                joints++
                rest -= bl
            }
            if (rest > MIN_SEG_M) pieces.add(Piece(si, rest, seg.lenM > bl + 1e-9))
        }
        pieces.sortByDescending { it.lenM }
        val bars = ArrayList<MutableList<SkirtCut>>()
        val rests = ArrayList<Double>()
        for (p in pieces) {
            var placed = false
            for (bi in bars.indices) {
                if (rests[bi] >= p.lenM - 1e-9) {
                    bars[bi].add(SkirtCut(p.segment, p.lenM, p.joint))
                    rests[bi] -= p.lenM
                    placed = true
                    break
                }
            }
            if (!placed) {
                bars.add(mutableListOf(SkirtCut(p.segment, p.lenM, p.joint)))
                rests.add(bl - p.lenM)
            }
        }
        val total = segments.sumOf { it.lenM }
        return SkirtPlan(
            segments,
            bars.mapIndexed { bi, cuts ->
                SkirtBar(cuts, Math.round(rests[bi] * 1000.0) / 1000.0)
            },
            Math.round(total * 100.0) / 100.0,
            bl,
            joints,
        )
    }
}
