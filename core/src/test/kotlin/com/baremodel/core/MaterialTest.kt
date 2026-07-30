package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Материалы и расход планки: пресеты, покрытие рядов, упаковки, предупреждения. */
class MaterialTest {

    private val room = RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0)))
    private val lam = MaterialSpec.preset(MaterialKind.LAMINATE)
    private val lamTile = MaterialSpec.presetTile(MaterialKind.LAMINATE)
    private val third = PatternSpec(PatternType.THIRD)

    @Test
    fun presetsMatchRealMaterials() {
        assertTrue(MaterialKind.LAMINATE.isPlank && MaterialKind.PARQUET.isPlank && MaterialKind.DECK.isPlank)
        assertTrue(!MaterialKind.TILE.isPlank && !MaterialKind.NONE.isPlank)
        assertEquals(1200.0, lamTile.widthMm, 1e-9)
        assertEquals(190.0, lamTile.heightMm, 1e-9)
        assertEquals(8, lam.packPieces)
        assertEquals(PatternType.HERRINGBONE, MaterialSpec.presetPattern(MaterialKind.PARQUET))
        assertEquals(5.0, MaterialSpec.presetTile(MaterialKind.DECK).groutMm, 1e-9)
    }

    @Test
    fun rowsAreFullyCoveredByPieces() {
        val p = PlankCalc.plan(room, lamTile, third, lam)
        assertEquals(12.0, p.floorM2, 1e-9)
        assertTrue(p.rowCount in 15..17)
        for (r in p.rows) {
            assertEquals(r.spanLenM, r.pieces.sumOf { it.lenMm } / 1000.0, 1e-6)
        }
        // распаковано не меньше уложенного, куплено не меньше распакованного
        assertTrue(p.spendM2 >= p.laidM2 - 1e-9)
        assertTrue(p.boughtM2 >= p.spendM2 - 1e-9)
    }

    @Test
    fun packsAndReserveAreWholeUnits() {
        val p = PlankCalc.plan(room, lamTile, third, lam)
        assertEquals(Math.ceil(p.planksUsed / 8.0).toInt(), p.packs)
        val r = PlankCalc.plan(room, lamTile, third, lam, reservePct = 10)
        assertTrue(r.planksWithReserve > p.planksWithReserve)
        assertEquals(p.pieces, r.pieces) // запас не меняет раскладку
        // террасная доска продаётся штуками — упаковок нет
        val deck = PlankCalc.plan(
            room, MaterialSpec.presetTile(MaterialKind.DECK), third, MaterialSpec.preset(MaterialKind.DECK),
        )
        assertEquals(0, deck.packs)
        assertTrue(deck.planksUsed > 0)
    }

    @Test
    fun offcutsReduceConsumption() {
        val on = PlankCalc.plan(room, lamTile, third, lam)
        val off = PlankCalc.plan(room, lamTile, third, lam.copy(reuseOffcuts = false))
        assertTrue(on.planksUsed < off.planksUsed)
        assertTrue(on.wastePct < off.wastePct)
        assertEquals(0, off.savedPlanks)
    }

    @Test
    fun staggerAndShortPieceWarnings() {
        assertEquals(0, PlankCalc.plan(room, lamTile, third, lam).tightJoints)
        assertEquals(0, PlankCalc.plan(room, lamTile, PatternSpec(PatternType.HALF), lam).tightJoints)
        // без сдвига торцы стоят в линию — так ламинат не кладут
        assertTrue(PlankCalc.plan(room, lamTile, PatternSpec(PatternType.GRID), lam).tightJoints > 0)
        // 2.55 м при планке 1.2 м даёт короткий последний кусок
        val narrow = RoomSpec(listOf(Pt(0.0, 0.0), Pt(2.55, 0.0), Pt(2.55, 3.0), Pt(0.0, 3.0)))
        assertTrue(PlankCalc.plan(narrow, lamTile, PatternSpec(PatternType.GRID), lam).shortLastRows > 0)
    }

    @Test
    fun cutoutsAndComplexOutlines() {
        val holed = PlankCalc.plan(room.copy(cutouts = listOf(Cutout(1.0, 1.0, 1.0, 1.0))), lamTile, third, lam)
        assertEquals(11.0, holed.floorM2, 1e-9)
        assertTrue(holed.planksUsed < PlankCalc.plan(room, lamTile, third, lam).planksUsed)
        // ряд через вырез разбит на два отрезка: подрезка у стены и у выреза
        assertTrue(holed.rows.any { r -> r.pieces.count { it.cut } >= 4 })
        val lshape = RoomSpec(
            listOf(Pt(0.0, 0.0), Pt(5.0, 0.0), Pt(5.0, 2.0), Pt(2.0, 2.0), Pt(2.0, 4.0), Pt(0.0, 4.0)),
        )
        val pl = PlankCalc.plan(lshape, lamTile, third, lam)
        assertEquals(14.0, pl.floorM2, 1e-9)
        for (r in pl.rows) assertEquals(r.spanLenM, r.pieces.sumOf { it.lenMm } / 1000.0, 1e-6)
    }

    @Test
    fun herringboneIsEstimatedFromArea() {
        val hb = PlankCalc.plan(
            room, MaterialSpec.presetTile(MaterialKind.PARQUET),
            PatternSpec(PatternType.HERRINGBONE), MaterialSpec.preset(MaterialKind.PARQUET),
        )
        assertTrue(hb.estimated)
        assertTrue(hb.packs > 0)
        assertTrue(hb.wastePct >= 10.0)
    }

    @Test
    fun degenerateInputDoesNotCrash() {
        assertEquals(0, PlankCalc.plan(room, TileSpec(0.0, 0.0, 0.0), third, lam).planksUsed)
        assertEquals(0, PlankCalc.plan(RoomSpec(listOf(Pt(0.0, 0.0), Pt(1.0, 0.0))), lamTile, third, lam).planksUsed)
        val tiny = RoomSpec(listOf(Pt(0.0, 0.0), Pt(0.05, 0.0), Pt(0.05, 0.05), Pt(0.0, 0.05)))
        assertTrue(PlankCalc.plan(tiny, lamTile, third, lam).pieces <= 1)
        assertTrue(abs(PlankCalc.floorArea(room) - 12.0) < 1e-9)
    }
}
