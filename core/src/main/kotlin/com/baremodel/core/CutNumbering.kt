package com.baremodel.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Видимый кусок подрезанной плитки.
 *
 * @param index   индекс плитки в layout.tiles
 * @param number  номер подрезки на плане (1..n) — один и тот же в 2D, 3D, PDF и AR
 * @param wMm     остаток вдоль первой стороны плитки (q0→q1), мм
 * @param hMm     остаток вдоль второй стороны плитки (q0→q3), мм
 * @param aCm     габарит куска, см (a >= b) — совпадает со списком обрезков движка
 * @param bCm     габарит куска, см
 * @param areaPct реальная площадь куска от целой плитки, % (не габаритная рамка)
 * @param cutOffMm сколько срезано при прямом резе поперёк плитки, мм;
 *                 null, если рез сложный (угол, диагональ) или плитка целая по габариту
 * @param cx, cy  центр видимого куска в мировых метрах — сюда ставится номер
 */
data class CutPieceInfo(
    val index: Int,
    val number: Int,
    val wMm: Double,
    val hMm: Double,
    val aCm: Double,
    val bCm: Double,
    val areaPct: Double,
    val cutOffMm: Double?,
    val cx: Double,
    val cy: Double,
)

/**
 * Единая нумерация подрезок: номера получают только реально видимые куски
 * крупнее [MIN_PIECE_CM]. Крохотные полоски и «плитки», от которых в комнате
 * ничего не осталось, номеров не получают — раньше они сдвигали нумерацию,
 * и №1 мог оказаться там, где плитку вообще не видно.
 */
object CutNumbering {

    /** Кусок мельче этого габарита (см) не нумеруется — как в списке обрезков. */
    const val MIN_PIECE_CM = 1.0

    /**
     * Считает видимые куски всех подрезанных плиток.
     * Порядок номеров — по рядам узора (rect.y, затем rect.x): номера идут
     * предсказуемо ряд за рядом, а не в порядке генерации движка.
     */
    fun compute(room: RoomSpec, layout: LayoutResult): Map<Int, CutPieceInfo> {
        val poly = room.points
        if (poly.size < 3) return emptyMap()

        data class Raw(
            val index: Int,
            val wMm: Double,
            val hMm: Double,
            val aCm: Double,
            val bCm: Double,
            val areaPct: Double,
            val cutOffMm: Double?,
            val cx: Double,
            val cy: Double,
            val ry: Double,
            val rx: Double,
        )

        val raws = ArrayList<Raw>()
        layout.tiles.forEachIndexed { i, t ->
            if (t.cls != TileClass.CUT) return@forEachIndexed
            val q = t.corners
            val piece = clipPolygonByQuad(poly, q)
            if (piece.size < 3) return@forEachIndexed

            // локальные оси плитки
            val ux = q[1].x - q[0].x
            val uy = q[1].y - q[0].y
            val vx = q[3].x - q[0].x
            val vy = q[3].y - q[0].y
            val ulen = sqrt(ux * ux + uy * uy)
            val vlen = sqrt(vx * vx + vy * vy)
            if (ulen < 1e-9 || vlen < 1e-9) return@forEachIndexed

            var lx1 = Double.MAX_VALUE
            var lx2 = -Double.MAX_VALUE
            var ly1 = Double.MAX_VALUE
            var ly2 = -Double.MAX_VALUE
            for (p in piece) {
                val dx = p.x - q[0].x
                val dy = p.y - q[0].y
                val lx = (dx * ux + dy * uy) / ulen
                val ly = (dx * vx + dy * vy) / vlen
                if (lx < lx1) lx1 = lx
                if (lx > lx2) lx2 = lx
                if (ly < ly1) ly1 = ly
                if (ly > ly2) ly2 = ly
            }
            val pw = max(0.0, min(ulen, lx2) - max(0.0, lx1))
            val ph = max(0.0, min(vlen, ly2) - max(0.0, ly1))
            if (max(pw, ph) * 100.0 < MIN_PIECE_CM) return@forEachIndexed

            // реальная площадь куска: клип по комнате минус попавшие внутрь вырезы
            var area = abs(polygonArea(piece))
            for (c in room.cutouts) {
                val hole = clipPolygonByRect(piece, c.x, c.y, c.x + c.w, c.y + c.h)
                if (hole.size >= 3) area -= abs(polygonArea(hole))
            }
            val tileArea = ulen * vlen
            val pct = if (tileArea > 1e-9) (area / tileArea * 100.0).coerceIn(0.0, 100.0) else 0.0

            // прямой рез поперёк плитки: одна сторона осталась целой
            val fullW = pw >= ulen - 0.002
            val fullH = ph >= vlen - 0.002
            val cutOff = when {
                fullW && fullH -> null
                fullW -> (vlen - ph) * 1000.0
                fullH -> (ulen - pw) * 1000.0
                else -> null
            }

            // центр видимого куска — сюда ставится номер
            var cx = 0.0
            var cy = 0.0
            for (p in piece) {
                cx += p.x
                cy += p.y
            }
            cx /= piece.size
            cy /= piece.size

            // округление до полусантиметра — ровно как в списке обрезков движка
            val aCm = Math.round(max(pw, ph) * 200).toDouble() / 2.0
            val bCm = Math.round(min(pw, ph) * 200).toDouble() / 2.0

            raws.add(
                Raw(
                    i, pw * 1000.0, ph * 1000.0, aCm, bCm, pct, cutOff,
                    cx, cy, t.rect.y, t.rect.x,
                ),
            )
        }

        raws.sortWith(compareBy({ it.ry }, { it.rx }))
        val res = LinkedHashMap<Int, CutPieceInfo>()
        raws.forEachIndexed { n, r ->
            res[r.index] = CutPieceInfo(
                r.index, n + 1, r.wMm, r.hMm, r.aCm, r.bCm, r.areaPct, r.cutOffMm, r.cx, r.cy,
            )
        }
        return res
    }
}
