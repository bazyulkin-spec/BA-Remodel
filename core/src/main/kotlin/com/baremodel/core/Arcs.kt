package com.baremodel.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Участок контура, лежащий на одной дуге: с какого ребра начинается,
 * сколько рёбер занимает, радиус и длина по дуге.
 * Нужен, чтобы подписывать дугу одной меткой и считать по ней плинтус,
 * а не двадцатью крошечными размерами.
 */
data class ArcRun(
    val startEdge: Int,
    val edges: Int,
    val radiusM: Double,
    val lengthM: Double,
    val chordM: Double,
)

/**
 * Дуги: движок раскладки работает с многоугольником, поэтому дуга живёт в контуре
 * как частая ломаная. Шаг ломаной берётся от размера плитки — под 600-ю плитку
 * дробить контур на сантиметры бессмысленно.
 */
object Arcs {

    /** Шаг полигонизации по умолчанию, мм: глазу — дуга, движку — многоугольник. */
    const val SEG_MM = 150.0

    /** Больше этого числа точек на одну дугу не ставим — иначе тормозит раскладка. */
    const val MAX_SEG = 96

    /** Радиус дуги по хорде и стрелке прогиба. */
    fun radiusOf(chordM: Double, sagittaM: Double): Double {
        val s = abs(sagittaM)
        if (s < 1e-9 || chordM <= 1e-9) return 0.0
        return (chordM * chordM / 4.0 + s * s) / (2.0 * s)
    }

    /** Длина дуги по хорде и стрелке прогиба. */
    fun arcLenOf(chordM: Double, sagittaM: Double): Double {
        val r = radiusOf(chordM, sagittaM)
        if (r <= 0.0) return chordM
        val half = (chordM / (2.0 * r)).coerceIn(-1.0, 1.0)
        // прогиб больше радиуса — дуга больше полукруга
        val alpha = if (abs(sagittaM) <= r) 2.0 * asin(half) else 2.0 * PI - 2.0 * asin(half)
        return r * alpha
    }

    /**
     * Промежуточные точки дуги от [a] до [b] с прогибом [sagittaM].
     * Знак прогиба задаёт сторону: плюс — влево от направления a→b.
     * Концы не возвращаются: они уже есть в контуре.
     */
    fun bend(a: Pt, b: Pt, sagittaM: Double, segMm: Double = SEG_MM): List<Pt> {
        val chord = hypot(b.x - a.x, b.y - a.y)
        val s = sagittaM
        if (chord <= 1e-9 || abs(s) < 0.001) return emptyList()
        val r = radiusOf(chord, s)
        if (r <= 0.0) return emptyList()

        val mx = (a.x + b.x) / 2.0
        val my = (a.y + b.y) / 2.0
        val ux = (b.x - a.x) / chord
        val uy = (b.y - a.y) / chord
        // нормаль влево от a→b
        val nx = -uy
        val ny = ux
        // центр отстоит от середины хорды на (r - |s|) в сторону, ПРОТИВОПОЛОЖНУЮ прогибу;
        // если прогиб больше радиуса, выражение меняет знак само и центр уходит внутрь дуги
        val sgn = sign(s)
        val dSigned = -sgn * (r - abs(s))
        val ox = mx + nx * dSigned
        val oy = my + ny * dSigned

        val a1 = atan2(a.y - oy, a.x - ox)
        val a2 = atan2(b.y - oy, b.x - ox)
        var sweep = a2 - a1
        // меньшая дуга; -PI не переворачиваем, иначе полукруг уходит не в ту сторону
        while (sweep < -PI) sweep += 2 * PI
        while (sweep > PI) sweep -= 2 * PI
        // прогиб больше радиуса — нужна большая дуга
        if (abs(s) > r) sweep = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI

        val len = abs(sweep) * r
        val n = ceil(len * 1000.0 / max(20.0, segMm)).toInt().coerceIn(2, MAX_SEG)
        val out = ArrayList<Pt>(n - 1)
        for (i in 1 until n) {
            val t = a1 + sweep * i / n
            out.add(Pt(ox + r * cos(t), oy + r * sin(t)))
        }
        return out
    }

    /** Круглая комната по диаметру; контур начинается в левой точке. */
    fun circle(diameterM: Double, segMm: Double = SEG_MM): List<Pt> =
        oval(diameterM, diameterM, segMm)

    /** Овальная комната по двум осям (ширина × длина). */
    fun oval(axisAM: Double, axisBM: Double, segMm: Double = SEG_MM): List<Pt> {
        val ra = max(0.05, axisAM) / 2.0
        val rb = max(0.05, axisBM) / 2.0
        // периметр эллипса по Рамануджану — от него берём число сегментов
        val h = (ra - rb) * (ra - rb) / ((ra + rb) * (ra + rb))
        val per = PI * (ra + rb) * (1 + 3 * h / (10 + sqrt(4 - 3 * h)))
        // кратно четырём — тогда точки ложатся ровно на концы обеих осей
        // и габарит комнаты читается как ровные 3.00 м, а не 2.99
        val n0 = ceil(per * 1000.0 / max(20.0, segMm)).toInt().coerceIn(12, MAX_SEG)
        val n = ((n0 + 3) / 4) * 4
        val out = ArrayList<Pt>(n)
        for (i in 0 until n) {
            val t = 2 * PI * i / n
            out.add(Pt(ra + ra * cos(t), rb + rb * sin(t)))
        }
        return out
    }

    /**
     * Находит участки контура, лежащие на одной дуге: подряд идущие рёбра
     * с поворотом одного знака и почти равной величины.
     * [minEdges] — короче этого участок считаем обычным углом, не дугой.
     */
    fun detectArcs(points: List<Pt>, minEdges: Int = 3, tolDeg: Double = 8.0): List<ArcRun> {
        val n = points.size
        if (n < minEdges + 1) return emptyList()

        fun turnAt(i: Int): Double {
            val p = points[(i - 1 + n) % n]
            val c = points[i % n]
            val q = points[(i + 1) % n]
            val a1 = atan2(c.y - p.y, c.x - p.x)
            val a2 = atan2(q.y - c.y, q.x - c.x)
            var t = a2 - a1
            while (t <= -PI) t += 2 * PI
            while (t > PI) t -= 2 * PI
            return t
        }

        // поворот в вершине i соединяет рёбра (i-1) и i
        val turns = DoubleArray(n) { turnAt(it) }
        val tol = tolDeg * PI / 180.0
        val used = BooleanArray(n)
        val runs = ArrayList<ArcRun>()

        for (start in 0 until n) {
            if (used[start]) continue
            val t0 = turns[start]
            if (abs(t0) < 0.5 * PI / 180.0 || abs(t0) > 60 * PI / 180.0) continue
            var k = start
            var count = 0
            while (count < n) {
                val t = turns[(k) % n]
                if (used[(k) % n] || sign(t) != sign(t0) || abs(abs(t) - abs(t0)) > tol) break
                used[(k) % n] = true
                k++
                count++
            }
            if (count + 1 < minEdges) continue
            // рёбра участка: от (start-1) до (start+count-1)
            val firstEdge = (start - 1 + n) % n
            // k поворотов связывают k+1 рёбер, но на замкнутом контуре не больше n
            val edges = min(count + 1, n)
            var len = 0.0
            for (e in 0 until edges) {
                val p1 = points[(firstEdge + e) % n]
                val p2 = points[(firstEdge + e + 1) % n]
                len += hypot(p2.x - p1.x, p2.y - p1.y)
            }
            val pA = points[firstEdge]
            val pB = points[(firstEdge + edges) % n]
            val chord = hypot(pB.x - pA.x, pB.y - pA.y)
            // радиус из среднего поворота: длина ребра / угол
            val avgTurn = (0 until count).sumOf { abs(turns[(start + it) % n]) } / count
            val r = if (avgTurn > 1e-9) (len / edges) / (2 * sin(avgTurn / 2).coerceAtLeast(1e-9)) else 0.0
            runs.add(ArcRun(firstEdge, edges, r, len, chord))
        }
        return runs.sortedBy { it.startEdge }
    }

    /** Ребро [edge] входит в дугу? Нужно, чтобы не подписывать каждый сегмент. */
    fun edgeInArc(runs: List<ArcRun>, edge: Int, total: Int): ArcRun? =
        runs.firstOrNull { run ->
            (0 until run.edges).any { (run.startEdge + it) % total == edge }
        }

    /** Угол между рёбрами в вершине, градусы: 180 — прямая. */
    fun cornerAngleDeg(points: List<Pt>, i: Int): Double {
        val n = points.size
        if (n < 3) return 180.0
        val p = points[(i - 1 + n) % n]
        val c = points[i % n]
        val q = points[(i + 1) % n]
        val l1 = hypot(p.x - c.x, p.y - c.y)
        val l2 = hypot(q.x - c.x, q.y - c.y)
        if (l1 < 1e-9 || l2 < 1e-9) return 180.0
        val cosT = (((p.x - c.x) * (q.x - c.x) + (p.y - c.y) * (q.y - c.y)) / (l1 * l2)).coerceIn(-1.0, 1.0)
        return acos(cosT) * 180.0 / PI
    }

    /** Сколько точек добавит прогиб — чтобы предупредить о тяжёлом контуре. */
    fun bendCost(chordM: Double, sagittaM: Double, segMm: Double = SEG_MM): Int {
        val len = arcLenOf(chordM, sagittaM)
        return ceil(len * 1000.0 / max(20.0, segMm)).toInt().coerceIn(2, MAX_SEG) - 1
    }

    /** Минимальная плитка, которая ещё имеет смысл на дуге такого радиуса. */
    fun maxTileOnArc(radiusM: Double): Double = max(50.0, min(1200.0, radiusM * 1000.0 / 4.0))
}
