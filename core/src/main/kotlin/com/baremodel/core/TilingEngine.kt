package com.baremodel.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Движок раскладки: строит плитки выбранного узора, классифицирует их
 * (целая / с подрезкой) и собирает карту подрезки.
 * Размеры комнаты — метры; плитка и шов — миллиметры.
 * Формулы решёток и инварианты — см. PLAN.md, раздел «Математика движка».
 */
object TilingEngine {

    /** Ограничение на число плиток в охватывающем прямоугольнике. */
    const val TILE_LIMIT = 16_000

    fun build(room: RoomSpec, tile: TileSpec, pattern: PatternSpec): LayoutResult {
        val empty = LayoutResult(emptyList(), 0, 0, 0.0, emptyList(), false)
        val twIn = tile.widthMm / 1000.0
        val thIn = tile.heightMm / 1000.0
        val g = max(0.0, tile.groutMm) / 1000.0
        if (twIn <= 0.005 || thIn <= 0.005 || room.points.size < 3) return empty

        // Ёлочка симметрична к перестановке сторон; решётка требует tw <= th.
        val swap = pattern.type == PatternType.HERRINGBONE && twIn > thIn
        val tw = if (swap) thIn else twIn
        val th = if (swap) twIn else thIn

        val stepW = tw + g
        val stepH = th + g
        val rad = pattern.rotationDeg * PI / 180.0
        val cosA = cos(rad)
        val sinA = sin(rad)
        val pts = room.points

        var minx = Double.MAX_VALUE
        var maxx = -Double.MAX_VALUE
        var miny = Double.MAX_VALUE
        var maxy = -Double.MAX_VALUE
        for (p in pts) {
            if (p.x < minx) minx = p.x
            if (p.x > maxx) maxx = p.x
            if (p.y < miny) miny = p.y
            if (p.y > maxy) maxy = p.y
        }
        val rminx = minx
        val rmaxx = maxx
        val rminy = miny
        val rmaxy = maxy
        val mar = 2 * max(stepW, stepH)
        minx -= mar; maxx += mar; miny -= mar; maxy += mar

        val over = (maxx - minx) * (maxy - miny) / (stepW * stepH) > TILE_LIMIT

        // bbox комнаты в системе координат узора (обратный поворот вокруг origin).
        fun inv(x: Double, y: Double) = Pt(
            (x - pattern.offsetX) * cosA + (y - pattern.offsetY) * sinA,
            -(x - pattern.offsetX) * sinA + (y - pattern.offsetY) * cosA,
        )
        val cor = listOf(inv(minx, miny), inv(maxx, miny), inv(maxx, maxy), inv(minx, maxy))
        val pminx = cor.minOf { it.x }
        val pmaxx = cor.maxOf { it.x }
        val pminy = cor.minOf { it.y }
        val pmaxy = cor.maxOf { it.y }

        val rects = ArrayList<LocalRect>()
        if (!over) {
            if (pattern.type == PatternType.HERRINGBONE) {
                // Решётка пар «горизонталь + вертикаль»:
                // база b = m*(W+H, H-W) + n*(H, H); H-плитка в b, V-плитка в b + (W, H-W).
                val a = stepW + stepH
                val b = stepH
                val c = stepH - stepW
                val mMin = floor((pminx - pmaxy) / (2 * stepW)).toInt() - 2
                val mMax = ceil((pmaxx - pminy) / (2 * stepW)).toInt() + 2
                for (m in mMin..mMax) {
                    val n1 = floor(min((pminy - m * c) / b, (pminx - m * a) / b)).toInt() - 2
                    val n2 = ceil(max((pmaxy - m * c) / b, (pmaxx - m * a) / b)).toInt() + 2
                    for (n in n1..n2) {
                        val bx = m * a + n * b
                        val by = m * c + n * b
                        if (bx > pmaxx + a || bx + a < pminx - a ||
                            by > pmaxy + a || by + a < pminy - a
                        ) continue
                        rects.add(LocalRect(bx, by, tw, th, false))
                        rects.add(LocalRect(bx + stepW, by + stepH - stepW, th, tw, true))
                    }
                }
            } else {
                val k = when (pattern.type) {
                    PatternType.HALF -> 2
                    PatternType.THIRD -> 3
                    else -> 1
                }
                val rMin = floor(pminy / stepH).toInt() - 1
                val rMax = ceil(pmaxy / stepH).toInt() + 1
                for (r in rMin..rMax) {
                    val off = ((r % k + k) % k) * (stepW / k)
                    val cMin = floor((pminx - off) / stepW).toInt() - 1
                    val cMax = ceil((pmaxx - off) / stepW).toInt() + 1
                    for (cc in cMin..cMax) rects.add(LocalRect(cc * stepW + off, r * stepH, tw, th, false))
                }
            }
        }

        fun fwd(x: Double, y: Double) = Pt(
            x * cosA - y * sinA + pattern.offsetX,
            x * sinA + y * cosA + pattern.offsetY,
        )

        val verts = ArrayList(pts)
        for (c in room.cutouts) {
            verts.add(Pt(c.x, c.y))
            verts.add(Pt(c.x + c.w, c.y))
            verts.add(Pt(c.x + c.w, c.y + c.h))
            verts.add(Pt(c.x, c.y + c.h))
        }

        fun inRoom(p: Pt): Boolean {
            if (!pointInPolygon(p, pts)) return false
            for (c in room.cutouts) {
                if (p.x > c.x && p.x < c.x + c.w && p.y > c.y && p.y < c.y + c.h) return false
            }
            return true
        }

        val tiles = ArrayList<PlacedTile>()
        var full = 0
        var cut = 0
        val pieceMap = HashMap<Long, Int>()
        val samples = doubleArrayOf(0.0, 0.5, 1.0)

        for (rc in rects) {
            val q = listOf(
                fwd(rc.x, rc.y),
                fwd(rc.x + rc.w, rc.y),
                fwd(rc.x + rc.w, rc.y + rc.h),
                fwd(rc.x, rc.y + rc.h),
            )
            var qminx = Double.MAX_VALUE
            var qmaxx = -Double.MAX_VALUE
            var qminy = Double.MAX_VALUE
            var qmaxy = -Double.MAX_VALUE
            for (p in q) {
                if (p.x < qminx) qminx = p.x
                if (p.x > qmaxx) qmaxx = p.x
                if (p.y < qminy) qminy = p.y
                if (p.y > qmaxy) qmaxy = p.y
            }
            if (qmaxx < rminx || qminx > rmaxx || qmaxy < rminy || qminy > rmaxy) continue

            var inside = 0
            for (u in samples) {
                for (v in samples) {
                    if (inRoom(fwd(rc.x + u * rc.w, rc.y + v * rc.h))) inside++
                }
            }

            val cls = when {
                inside == 9 -> if (verts.any { pointInPolygon(it, q) }) TileClass.CUT else TileClass.FULL
                inside > 0 -> TileClass.CUT
                verts.any { pointInPolygon(it, q) } -> TileClass.CUT
                else -> null
            } ?: continue

            if (cls == TileClass.FULL) {
                full++
            } else {
                cut++
                val piece = clipPolygonByQuad(pts, q)
                if (piece.size >= 3) {
                    var lx1 = Double.MAX_VALUE
                    var lx2 = -Double.MAX_VALUE
                    var ly1 = Double.MAX_VALUE
                    var ly2 = -Double.MAX_VALUE
                    for (p in piece) {
                        val lx = (p.x - pattern.offsetX) * cosA + (p.y - pattern.offsetY) * sinA - rc.x
                        val ly = -(p.x - pattern.offsetX) * sinA + (p.y - pattern.offsetY) * cosA - rc.y
                        if (lx < lx1) lx1 = lx
                        if (lx > lx2) lx2 = lx
                        if (ly < ly1) ly1 = ly
                        if (ly > ly2) ly2 = ly
                    }
                    val pw = max(0.0, min(rc.w, lx2) - max(0.0, lx1))
                    val ph = max(0.0, min(rc.h, ly2) - max(0.0, ly1))
                    val aHalf = Math.round(max(pw, ph) * 200).toInt() // полусантиметры
                    val bHalf = Math.round(min(pw, ph) * 200).toInt()
                    if (aHalf >= 2) {
                        val key = (aHalf.toLong() shl 20) or bHalf.toLong()
                        pieceMap[key] = (pieceMap[key] ?: 0) + 1
                    }
                }
            }
            tiles.add(PlacedTile(q, cls, rc))
        }

        val area = polygonArea(pts) - room.cutouts.sumOf { it.w * it.h }
        val pieces = pieceMap.entries
            .map { (k, n) -> CutPiece((k shr 20).toDouble() / 2.0, (k and 0xFFFFFL).toDouble() / 2.0, n) }
            .sortedWith(compareByDescending<CutPiece> { it.count }.thenByDescending { it.aCm })
        return LayoutResult(tiles, full, cut, area, pieces, over)
    }
}
