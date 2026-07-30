package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** Дуги: радиус по хорде и прогибу, полигонизация, круг и овал, распознавание дуг. */
class ArcsTest {

    private val a = Pt(0.0, 0.0)
    private val b = Pt(2.0, 0.0)

    @Test
    fun radiusAndLengthFromChordAndSagitta() {
        // полукруг: хорда 2, прогиб 1 → радиус 1, длина πR
        assertEquals(1.0, Arcs.radiusOf(2.0, 1.0), 1e-9)
        assertEquals(PI, Arcs.arcLenOf(2.0, 1.0), 1e-6)
        // мелкий прогиб: дуга чуть длиннее хорды
        val r = Arcs.radiusOf(2.0, 0.1)
        assertEquals(5.05, r, 1e-9)
        val len = Arcs.arcLenOf(2.0, 0.1)
        assertTrue(len > 2.0 && len < 2.03)
        // без прогиба дуги нет
        assertEquals(0.0, Arcs.radiusOf(2.0, 0.0), 1e-9)
        assertEquals(2.0, Arcs.arcLenOf(2.0, 0.0), 1e-9)
    }

    @Test
    fun bendPointsLieOnTheArc() {
        val pts = Arcs.bend(a, b, 0.4)
        assertTrue(pts.size >= 2)
        val r = Arcs.radiusOf(2.0, 0.4)
        // центр под хордой на r - s, все точки на радиусе r от него
        val oy = -(r - 0.4)
        for (p in pts) {
            assertEquals(r, hypot(p.x - 1.0, p.y - oy), 1e-6)
        }
        // прогиб вверх: середина дуги поднялась на стрелку
        val mid = pts[pts.size / 2]
        assertTrue(mid.y > 0.3 && mid.y <= 0.4 + 1e-6)
        // знак меняет сторону
        val down = Arcs.bend(a, b, -0.4)
        assertTrue(down[down.size / 2].y < -0.3)
        // концы не дублируются
        assertTrue(pts.none { abs(it.x - a.x) < 1e-9 && abs(it.y - a.y) < 1e-9 })
        assertTrue(pts.none { abs(it.x - b.x) < 1e-9 && abs(it.y - b.y) < 1e-9 })
    }

    @Test
    fun segmentStepFollowsTileSize() {
        val fine = Arcs.bend(a, b, 0.4, segMm = 60.0)
        val coarse = Arcs.bend(a, b, 0.4, segMm = 400.0)
        assertTrue(fine.size > coarse.size)
        assertTrue(coarse.size >= 1)
        // предел на число точек соблюдается
        assertTrue(Arcs.bend(a, b, 0.9, segMm = 1.0).size <= Arcs.MAX_SEG)
        assertEquals(Arcs.bend(a, b, 0.4).size, Arcs.bendCost(2.0, 0.4))
    }

    @Test
    fun bendIgnoresDegenerateInput() {
        assertTrue(Arcs.bend(a, a, 0.4).isEmpty())
        assertTrue(Arcs.bend(a, b, 0.0).isEmpty())
        assertTrue(Arcs.bend(a, b, 0.0005).isEmpty()) // прогиб меньше миллиметра
    }

    @Test
    fun circleAndOvalAreClosedAndCorrectSize() {
        val c = Arcs.circle(3.0)
        assertTrue(c.size >= 12)
        val area = polygonArea(c)
        // многоугольник чуть меньше круга, но не сильно
        assertTrue(area > PI * 1.5 * 1.5 * 0.97 && area <= PI * 1.5 * 1.5)
        assertEquals(0.0, c.minOf { it.x }, 1e-9) // точки ровно на концах осей
        assertEquals(0, c.size % 4)
        assertEquals(3.0, c.maxOf { it.x }, 1e-6)
        assertEquals(3.0, c.maxOf { it.y }, 1e-6)
        val o = Arcs.oval(4.0, 2.0)
        assertEquals(4.0, o.maxOf { it.x } - o.minOf { it.x }, 1e-6)
        assertEquals(2.0, o.maxOf { it.y } - o.minOf { it.y }, 1e-6)
        assertTrue(polygonArea(o) > PI * 2.0 * 1.0 * 0.96)
    }

    @Test
    fun arcsAreDetectedAndStraightWallsAreNot() {
        // прямоугольник: дуг нет, повороты по 90° выходят за допуск
        val rect = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0))
        assertTrue(Arcs.detectArcs(rect).isEmpty())
        // круг: одна дуга на весь контур
        val c = Arcs.circle(3.0)
        val runs = Arcs.detectArcs(c)
        assertEquals(1, runs.size)
        assertEquals(1.5, runs[0].radiusM, 0.05)
        assertTrue(runs[0].lengthM > PI * 3.0 * 0.97)
        assertEquals(c.size, runs[0].edges)
    }

    @Test
    fun detectFindsArcInsideStraightOutline() {
        // стена 0→1 выгнута, остальные прямые
        val base = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0))
        val bent = ArrayList<Pt>()
        bent.add(base[0])
        bent.addAll(Arcs.bend(base[0], base[1], -0.5))
        bent.add(base[1])
        bent.add(base[2])
        bent.add(base[3])
        val runs = Arcs.detectArcs(bent)
        assertEquals(1, runs.size)
        val run = runs[0]
        assertEquals(Arcs.radiusOf(4.0, 0.5), run.radiusM, 0.15)
        assertEquals(Arcs.arcLenOf(4.0, 0.5), run.lengthM, 0.05)
        assertEquals(4.0, run.chordM, 1e-6)
        // рёбра дуги найдены, прямые стены — нет
        assertNotNull(Arcs.edgeInArc(runs, run.startEdge, bent.size))
        assertNull(Arcs.edgeInArc(runs, (run.startEdge + run.edges + 1) % bent.size, bent.size))
    }

    @Test
    fun cornerAngleAndTileAdvice() {
        val rect = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0))
        assertEquals(90.0, Arcs.cornerAngleDeg(rect, 1), 1e-6)
        val flat = listOf(Pt(0.0, 0.0), Pt(2.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0))
        assertEquals(180.0, Arcs.cornerAngleDeg(flat, 1), 1e-6)
        // на радиусе 1 м крупная плитка не ляжет: подсказка ограничивает размер
        assertEquals(250.0, Arcs.maxTileOnArc(1.0), 1e-9)
        assertTrue(Arcs.maxTileOnArc(20.0) <= 1200.0)
        assertTrue(Arcs.maxTileOnArc(0.05) >= 50.0)
    }
}
