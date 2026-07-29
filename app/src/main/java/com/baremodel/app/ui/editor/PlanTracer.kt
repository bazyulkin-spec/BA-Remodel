package com.baremodel.app.ui.editor

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Автообводка помещения по фотографии чертежа — без ИИ, обычной обработкой изображения.
 *
 * Порядок: яркость → порог Оцу → заливка светлой области от точки касания →
 * обход границы по Муру → упрощение Рамера—Дугласа—Пекера → выравнивание почти
 * горизонтальных и вертикальных стен. Возвращает контур в пикселях картинки.
 */
object PlanTracer {

    /** Результат: контур в пикселях и доля кадра, которую занял залитый участок. */
    data class Traced(val points: List<DoubleArray>, val fill: Double)

    fun trace(
        pixels: IntArray,
        w: Int,
        h: Int,
        startX: Int,
        startY: Int,
    ): Traced? {
        if (w < 8 || h < 8 || pixels.size < w * h) return null

        val lum = IntArray(w * h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        val th = otsu(lum)
        val light = BooleanArray(w * h) { lum[it] > th }

        val seed = findSeed(light, w, h, startX, startY) ?: return null
        val filled = flood(light, w, h, seed)
        val count = filled.count { it }
        if (count < w * h / 400) return null

        val border = traceBorder(filled, w, h) ?: return null
        val eps = max(2.0, min(w, h) * 0.006)
        val simple = rdp(border, eps)
        if (simple.size < 3) return null
        val snapped = snapAxes(dropShortEdges(simple, max(3.0, min(w, h) * 0.012)))
        if (snapped.size < 3) return null
        return Traced(snapped, count.toDouble() / (w * h))
    }

    // ---------- шаги ----------

    /** Порог Оцу: делит гистограмму на «тёмное» (стены) и «светлое» (комнаты). */
    private fun otsu(lum: IntArray): Int {
        val hist = IntArray(256)
        for (v in lum) hist[v]++
        val total = lum.size
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var thr = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                thr = i
            }
        }
        return thr
    }

    /** Точка касания могла попасть на линию — ищем ближайший светлый пиксель. */
    private fun findSeed(light: BooleanArray, w: Int, h: Int, sx: Int, sy: Int): Int? {
        val x0 = sx.coerceIn(0, w - 1)
        val y0 = sy.coerceIn(0, h - 1)
        if (light[y0 * w + x0]) return y0 * w + x0
        for (r in 1..max(w, h) / 8) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (abs(dx) != r && abs(dy) != r) continue
                    val x = x0 + dx
                    val y = y0 + dy
                    if (x in 0 until w && y in 0 until h && light[y * w + x]) return y * w + x
                }
            }
        }
        return null
    }

    /** Заливка светлой области (4 соседа), ограничена тёмными линиями чертежа. */
    private fun flood(light: BooleanArray, w: Int, h: Int, seed: Int): BooleanArray {
        val out = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        stack[top++] = seed
        out[seed] = true
        while (top > 0) {
            val i = stack[--top]
            val x = i % w
            val y = i / w
            if (x > 0 && light[i - 1] && !out[i - 1]) { out[i - 1] = true; stack[top++] = i - 1 }
            if (x < w - 1 && light[i + 1] && !out[i + 1]) { out[i + 1] = true; stack[top++] = i + 1 }
            if (y > 0 && light[i - w] && !out[i - w]) { out[i - w] = true; stack[top++] = i - w }
            if (y < h - 1 && light[i + w] && !out[i + w]) { out[i + w] = true; stack[top++] = i + w }
        }
        return out
    }

    /** Обход границы залитой области по соседям Мура, по часовой стрелке. */
    private fun traceBorder(mask: BooleanArray, w: Int, h: Int): List<DoubleArray>? {
        var start = -1
        for (i in mask.indices) {
            if (mask[i]) { start = i; break }
        }
        if (start < 0) return null

        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]

        val res = ArrayList<DoubleArray>()
        var cx = start % w
        var cy = start / w
        var dir = 6
        val sx = cx
        val sy = cy
        var guard = 0
        do {
            res.add(doubleArrayOf(cx.toDouble(), cy.toDouble()))
            var found = false
            for (k in 0 until 8) {
                val d = (dir + 6 + k) % 8
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (inside(nx, ny)) {
                    cx = nx
                    cy = ny
                    dir = d
                    found = true
                    break
                }
            }
            if (!found) break
            guard++
        } while ((cx != sx || cy != sy) && guard < w * h * 4)
        return if (res.size >= 8) res else null
    }

    /** Упрощение контура: убирает шум пикселей, оставляет углы. */
    private fun rdp(pts: List<DoubleArray>, eps: Double): List<DoubleArray> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to pts.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b <= a + 1) continue
            var bestI = -1
            var bestD = eps
            for (i in a + 1 until b) {
                val d = distToSeg(pts[i], pts[a], pts[b])
                if (d > bestD) { bestD = d; bestI = i }
            }
            if (bestI > 0) {
                keep[bestI] = true
                stack.addLast(a to bestI)
                stack.addLast(bestI to b)
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    private fun distToSeg(p: DoubleArray, a: DoubleArray, b: DoubleArray): Double {
        val vx = b[0] - a[0]
        val vy = b[1] - a[1]
        val len = hypot(vx, vy)
        if (len < 1e-9) return hypot(p[0] - a[0], p[1] - a[1])
        val t = (((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / (len * len)).coerceIn(0.0, 1.0)
        return hypot(p[0] - (a[0] + vx * t), p[1] - (a[1] + vy * t))
    }

    /** Выбрасывает совсем короткие рёбра — они появляются на неровностях линии. */
    private fun dropShortEdges(pts: List<DoubleArray>, minLen: Double): List<DoubleArray> {
        if (pts.size <= 4) return pts
        val out = ArrayList<DoubleArray>()
        for (p in pts) {
            val last = out.lastOrNull()
            if (last == null || hypot(p[0] - last[0], p[1] - last[1]) >= minLen) out.add(p)
        }
        while (out.size > 4) {
            val a = out.first()
            val b = out.last()
            if (hypot(a[0] - b[0], a[1] - b[1]) < minLen) out.removeAt(out.size - 1) else break
        }
        return out
    }

    /** Стены на чертеже прямые: почти горизонтальные и вертикальные рёбра выравниваются. */
    private fun snapAxes(pts: List<DoubleArray>): List<DoubleArray> {
        val out = pts.map { doubleArrayOf(it[0], it[1]) }
        val n = out.size
        repeat(2) {
            for (i in 0 until n) {
                val a = out[i]
                val b = out[(i + 1) % n]
                val ex = abs(b[0] - a[0])
                val ey = abs(b[1] - a[1])
                if (ex < 1e-9 && ey < 1e-9) continue
                if (ey < ex * 0.14) {
                    val y = (a[1] + b[1]) / 2
                    a[1] = y
                    b[1] = y
                } else if (ex < ey * 0.14) {
                    val x = (a[0] + b[0]) / 2
                    a[0] = x
                    b[0] = x
                }
            }
        }
        return out
    }
}
