package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SkirtingCalcTest {

    private val pts = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0))
    private val opens = mapOf(
        "wall-1" to listOf(Cutout(1.55, 0.0, 0.9, 2.05)), // дверь рвёт плинтус
        "wall-3" to listOf(Cutout(1.3, 0.9, 1.4, 1.4)), // окно — нет
    )

    @Test
    fun doorSplitsWallWindowDoesNot() {
        val segs = SkirtingCalc.segments(pts, opens)
        assertEquals(5, segs.size)
        val w1 = segs.filter { it.wall == 0 }
        assertEquals(2, w1.size)
        assertEquals(1.55, w1[0].lenM, 1e-9)
        assertEquals(4.0, segs.first { it.wall == 2 }.lenM, 1e-9)
        assertEquals(13.1, segs.sumOf { it.lenM }, 1e-9)
    }

    @Test
    fun ffdPacksRestsAndCoversAllSegments() {
        val segs = SkirtingCalc.segments(pts, opens)
        val plan = SkirtingCalc.plan(segs, 2.5)
        // стены длиннее хлыста дают стыки: 4 м и две по 3 м
        assertEquals(3, plan.joints)
        assertEquals(6, plan.bars.size)
        // каждый хлыст не переполнен, каждый сегмент закрыт полностью
        plan.bars.forEach { b -> assertTrue(b.cuts.sumOf { it.lenM } <= 2.5 + 1e-9) }
        val covered = DoubleArray(segs.size)
        plan.bars.forEach { b -> b.cuts.forEach { covered[it.segment] += it.lenM } }
        segs.forEachIndexed { i, sg -> assertTrue(abs(covered[i] - sg.lenM) < 1e-6) }
        // остатки совмещаются: есть хлыст с двумя кусками
        assertTrue(plan.bars.any { it.cuts.size >= 2 })
    }

    @Test
    fun tileStripMode() {
        val segs = SkirtingCalc.segments(pts, opens)
        val plan = SkirtingCalc.plan(segs, 0.6) // полоса = длинная сторона плитки 600
        assertTrue(plan.bars.size >= 22)
    }
}
