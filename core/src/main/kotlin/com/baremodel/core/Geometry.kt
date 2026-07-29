package com.baremodel.core

import kotlin.math.abs
import kotlin.math.hypot

/** Тест «точка в полигоне» (ray casting). Работает и для невыпуклых полигонов. */
fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
    var c = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) &&
            p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
        ) c = !c
        j = i
    }
    return c
}

fun polygonArea(poly: List<Pt>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += a.x * b.y - b.x * a.y
    }
    return abs(s) / 2
}

fun polygonPerimeter(poly: List<Pt>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += hypot(b.x - a.x, b.y - a.y)
    }
    return s
}

/**
 * Пересечение произвольного полигона с выпуклым четырёхугольником
 * (Sutherland–Hodgman). Ориентация quad нормализуется к CCW.
 */
fun clipPolygonByQuad(subject: List<Pt>, quad: List<Pt>): List<Pt> {
    var ar = 0.0
    for (i in 0 until 4) {
        val a = quad[i]
        val b = quad[(i + 1) % 4]
        ar += a.x * b.y - b.x * a.y
    }
    val q = if (ar < 0) quad.reversed() else quad
    var out: MutableList<Pt> = subject.toMutableList()
    for (i in 0 until 4) {
        if (out.isEmpty()) break
        val a = q[i]
        val b = q[(i + 1) % 4]
        fun side(p: Pt) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val inp = out
        out = mutableListOf()
        for (j in inp.indices) {
            val pCur = inp[j]
            val pNext = inp[(j + 1) % inp.size]
            val sp = side(pCur)
            val sq = side(pNext)
            if (sp >= 0) out.add(pCur)
            if ((sp >= 0) != (sq >= 0)) {
                val t = sp / (sp - sq)
                out.add(Pt(pCur.x + (pNext.x - pCur.x) * t, pCur.y + (pNext.y - pCur.y) * t))
            }
        }
    }
    return out
}

/**
 * Отсечение многоугольника прямоугольником (Сазерленд—Ходжман).
 * Прямоугольник выпуклый, поэтому алгоритм корректен для любых контуров;
 * у невыпуклых фигур части соединяются нулевой ширины перемычками —
 * на площадь и попадание точки это не влияет.
 */
fun clipPolygonByRect(poly: List<Pt>, x0: Double, y0: Double, x1: Double, y1: Double): List<Pt> {
    if (poly.size < 3) return emptyList()
    val minX = minOf(x0, x1)
    val maxX = maxOf(x0, x1)
    val minY = minOf(y0, y1)
    val maxY = maxOf(y0, y1)

    fun inside(p: Pt, edge: Int): Boolean = when (edge) {
        0 -> p.x >= minX
        1 -> p.x <= maxX
        2 -> p.y >= minY
        else -> p.y <= maxY
    }

    fun cross(a: Pt, b: Pt, edge: Int): Pt {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return when (edge) {
            0 -> Pt(minX, a.y + dy * (minX - a.x) / dx)
            1 -> Pt(maxX, a.y + dy * (maxX - a.x) / dx)
            2 -> Pt(a.x + dx * (minY - a.y) / dy, minY)
            else -> Pt(a.x + dx * (maxY - a.y) / dy, maxY)
        }
    }

    var out = poly
    for (edge in 0..3) {
        if (out.isEmpty()) return emptyList()
        val input = out
        val res = ArrayList<Pt>(input.size + 4)
        for (i in input.indices) {
            val cur = input[i]
            val prev = input[(i - 1 + input.size) % input.size]
            val curIn = inside(cur, edge)
            val prevIn = inside(prev, edge)
            if (curIn) {
                if (!prevIn) res.add(cross(prev, cur, edge))
                res.add(cur)
            } else if (prevIn) {
                res.add(cross(prev, cur, edge))
            }
        }
        out = res
    }
    return out
}
