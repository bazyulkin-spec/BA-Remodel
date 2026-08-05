package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Развёртка стены: сетка, крайние куски, проёмы, старт от пола или от потолка. */
class WallsTest {

    private val tile = TileSpec(300.0, 600.0, 2.0)
    private val wall = WallSpec(lengthM = 3.0, heightM = 2.5, tile = tile)

    @Test
    fun gridCoversWallAndCountsMatchGeometry() {
        val p = WallCalc.plan(wall)
        assertEquals(7.5, p.areaM2, 1e-9) // 3.0 × 2.5 без проёмов
        // по длине 3000 при шаге 302: 9 целых по 300 = 2716, остаток 284 пополам;
        // в куске 140 мм плитки, ещё 2 мм — шов, поэтому кусок 140, а не 142
        assertEquals(11, p.across.cells)
        assertEquals(140.0, p.across.firstMm, 0.5)
        assertEquals(140.0, p.across.lastMm, 0.5)
        // по высоте 2500 при шаге 602: 4 целых от пола (2408), сверху кусок 92
        assertEquals(5, p.up.cells)
        assertEquals(600.0, p.up.firstMm, 1e-6) // целая от пола
        assertEquals(92.0, p.up.lastMm, 1.0)
        assertTrue(p.totalCount > 0 && p.cutCount > 0)
        assertEquals(p.fullCount + p.cutCount, p.layout.totalCount)
    }

    @Test
    fun startFromCeilingMovesTheCutRowDown() {
        val floorFirst = WallCalc.plan(wall)
        val ceilFirst = WallCalc.plan(wall.copy(startFromFloor = false))
        assertEquals(600.0, floorFirst.up.firstMm, 1e-6)
        assertEquals(600.0, ceilFirst.up.lastMm, 1.0) // целая под потолком
        assertTrue(ceilFirst.up.firstMm < 600.0) // подрезка ушла вниз
        // площадь и число плиток не изменились от смены фазы больше чем на ряд
        assertEquals(floorFirst.areaM2, ceilFirst.areaM2, 1e-9)
        assertTrue(abs(floorFirst.totalCount - ceilFirst.totalCount) <= floorFirst.across.cells)
    }

    @Test
    fun centeringGivesEqualEdgesAndCornerStartDoesNot() {
        val centered = WallCalc.plan(wall)
        assertEquals(centered.across.firstMm, centered.across.lastMm, 0.5)
        val corner = WallCalc.plan(wall.copy(centered = false))
        assertEquals(300.0, corner.across.firstMm, 1e-6) // целая от угла
        assertTrue(corner.across.lastMm < 300.0)
    }

    @Test
    fun openingsAreCutOutOfTheWall() {
        // окно 1.4 × 1.4 на высоте 0.9
        val withWin = wall.copy(openings = listOf(Cutout(0.8, 0.9, 1.4, 1.4)))
        val p = WallCalc.plan(withWin)
        assertEquals(7.5 - 1.96, p.areaM2, 1e-9)
        val plain = WallCalc.plan(wall)
        assertTrue(p.totalCount < plain.totalCount) // под окном плитки нет
        assertTrue(p.cutCount > 0) // вокруг окна режется
        // дверь до пола: тоже вырезается
        val withDoor = WallCalc.plan(wall.copy(openings = listOf(Cutout(0.5, 0.0, 0.9, 2.05))))
        assertEquals(7.5 - 0.9 * 2.05, withDoor.areaM2, 1e-9)
        // проём шире стены обрезается по стене, а не уводит площадь в минус
        val huge = WallCalc.plan(wall.copy(openings = listOf(Cutout(2.5, 0.0, 5.0, 9.0))))
        assertEquals(7.5 - 0.5 * 2.5, huge.areaM2, 1e-9)
    }

    @Test
    fun thinEdgeIsReported() {
        // 2.766 м при плитке 300+2: 9 целых (2716), остаток 50 пополам → полоски 23 мм
        val thin = WallCalc.plan(WallSpec(2.766, 2.5, tile = tile))
        assertEquals(23.0, thin.thinnestMm, 1.0)
        assertTrue(thin.thinnestMm < WallCalc.THIN_MM)
        // а обычная стена полосок не даёт
        assertTrue(WallCalc.plan(wall).thinnestMm > WallCalc.THIN_MM)
    }

    @Test
    fun purchaseGrowsWithReserve() {
        val p = WallCalc.plan(wall)
        assertEquals(p.totalCount, p.buyCount(0))
        assertTrue(p.buyCount(10) > p.buyCount(0))
    }

    @Test
    fun degenerateWallsAreEmptyNotCrashing() {
        assertEquals(0, WallCalc.plan(WallSpec(0.0, 2.5, tile = tile)).totalCount)
        assertEquals(0, WallCalc.plan(WallSpec(3.0, 0.0, tile = tile)).totalCount)
        assertEquals(0, WallCalc.plan(WallSpec(3.0, 2.5, tile = TileSpec(0.0, 0.0, 0.0))).totalCount)
    }

    @Test
    fun axisHelperMatchesTheGrid() {
        // пролёт 1000, плитка 300, шов 0: 3 целых + кусок 100
        val a = WallCalc.axis(1.0, 0.3, 0.3, 0.0)
        assertEquals(4, a.cells)
        assertEquals(300.0, a.firstMm, 1e-6)
        assertEquals(100.0, a.lastMm, 1e-6)
        // ровно три плитки — без подрезки
        val b = WallCalc.axis(0.9, 0.3, 0.3, 0.0)
        assertEquals(3, b.cells)
        assertEquals(300.0, b.lastMm, 1e-6)
    }
}
