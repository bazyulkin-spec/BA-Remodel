package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilingEngineTest {

    private val rect = RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0)))
    private val lRoom = RoomSpec(
        listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 1.8), Pt(2.2, 1.8), Pt(2.2, 3.0), Pt(0.0, 3.0))
    )

    private fun coverage(room: RoomSpec, res: LayoutResult): Double =
        res.tiles.sumOf { polygonArea(clipPolygonByQuad(room.points, it.corners)) }

    private fun assertPartition(room: RoomSpec, res: LayoutResult) {
        val rnd = java.util.Random(42)
        val xs = room.points.map { it.x }
        val ys = room.points.map { it.y }
        var tested = 0
        var attempts = 0
        while (tested < 2000 && attempts < 20000) {
            attempts++
            val p = Pt(
                xs.min() + rnd.nextDouble() * (xs.max() - xs.min()),
                ys.min() + rnd.nextDouble() * (ys.max() - ys.min()),
            )
            if (!pointInPolygon(p, room.points)) continue
            tested++
            assertEquals(1, res.tiles.count { pointInPolygon(p, it.corners) })
        }
        assertTrue(tested > 500)
    }

    @Test
    fun gridCoversRoomExactly() {
        val r = TilingEngine.build(rect, TileSpec(500.0, 500.0, 0.0), PatternSpec(PatternType.GRID, 0.0, 0.013, 0.007))
        assertEquals(12.0, coverage(rect, r), 1e-7)
        assertPartition(rect, r)
    }

    @Test
    fun rotatedOffsetGridCoversRoomExactly() {
        val r = TilingEngine.build(rect, TileSpec(300.0, 600.0, 0.0), PatternSpec(PatternType.THIRD, 30.0, 0.11, 0.05))
        assertEquals(12.0, coverage(rect, r), 1e-7)
        assertPartition(rect, r)
    }

    @Test
    fun herringboneCoversRoomForAllRatios() {
        for ((w, h) in listOf(100.0 to 300.0, 200.0 to 600.0, 300.0 to 300.0, 120.0 to 490.0)) {
            val r = TilingEngine.build(rect, TileSpec(w, h, 0.0), PatternSpec(PatternType.HERRINGBONE, 33.0, 0.07, 0.31))
            assertEquals(12.0, coverage(rect, r), 1e-6)
            assertPartition(rect, r)
        }
    }

    @Test
    fun herringboneOnConcaveRoom() {
        val r = TilingEngine.build(lRoom, TileSpec(150.0, 600.0, 0.0), PatternSpec(PatternType.HERRINGBONE, 45.0, 0.13, -0.07))
        assertEquals(polygonArea(lRoom.points), coverage(lRoom, r), 1e-6)
        assertPartition(lRoom, r)
    }

    @Test
    fun cutoutsAndGrout() {
        val room = RoomSpec(rect.points, listOf(Cutout(1.0, 1.0, 0.8, 0.6)))
        val r = TilingEngine.build(room, TileSpec(300.0, 600.0, 3.0), PatternSpec(PatternType.HALF, 0.0, 0.05, 0.05))
        assertEquals(12.0 - 0.48, r.areaM2, 1e-9)
        assertTrue(r.cutCount > 0 && r.cutPieces.isNotEmpty())
    }

    @Test
    fun tileLimitGuard() {
        val big = RoomSpec(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 100.0), Pt(0.0, 100.0)))
        val r = TilingEngine.build(big, TileSpec(100.0, 100.0, 2.0), PatternSpec())
        assertTrue(r.overLimit)
        assertTrue(r.tiles.isEmpty())
    }

    @Test
    fun suggesterExcludesCurrentAndReturnsTop3() {
        val s = LayoutSuggester.suggest(rect, TileSpec(600.0, 600.0, 3.0), PatternSpec(PatternType.GRID, 0.0, 0.0, 0.0))
        assertEquals(3, s.size)
        assertTrue(s.none { it.type == PatternType.GRID && it.rotationDeg == 0.0 })
    }
}
