package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutNumberingTest {

    /** Комната 0.905×0.95 м, плитка 300×300 без шва: справа полоса 5 мм, снизу 50 мм. */
    private val room = RoomSpec(
        listOf(Pt(0.0, 0.0), Pt(0.905, 0.0), Pt(0.905, 0.95), Pt(0.0, 0.95)),
    )
    private val tile = TileSpec(300.0, 300.0, 0.0)

    @Test
    fun phantomsGetNoNumberAndNumbersAreContiguous() {
        val lay = TilingEngine.build(room, tile, PatternSpec())
        val info = CutNumbering.compute(room, lay)
        val cutTiles = lay.tiles.count { it.cls == TileClass.CUT }
        // плитки без видимого куска (фантомы на границе) номеров не получают
        assertTrue("фантомы должны остаться без номера", info.size < cutTiles)
        // номера идут подряд без дыр: 1..n
        assertEquals((1..info.size).toList(), info.values.map { it.number }.sorted())
    }

    @Test
    fun straightCutReportsRemainderAndCutOffMm() {
        val lay = TilingEngine.build(room, tile, PatternSpec())
        val info = CutNumbering.compute(room, lay)
        // нижняя полоса: остаток 300×50 мм, срезано 250 мм, площадь ~16.7 %
        val bottom = info.values.first { it.hMm in 49.0..51.0 && it.wMm > 299.0 }
        assertNotNull(bottom.cutOffMm)
        assertTrue(bottom.cutOffMm!! in 249.0..251.0)
        assertTrue(bottom.areaPct in 16.0..17.5)
        // тонкая видимая полоса 5 мм номер получает: мастер должен её увидеть
        val thin = info.values.first { it.wMm in 4.0..6.0 && it.hMm > 290.0 }
        assertTrue(thin.cutOffMm!! in 294.0..296.0)
    }

    @Test
    fun labelCentersLieInsideRoom() {
        val lay = TilingEngine.build(room, tile, PatternSpec())
        val info = CutNumbering.compute(room, lay)
        for (ci in info.values) {
            assertTrue(pointInPolygon(Pt(ci.cx, ci.cy), room.points))
        }
        // №1 — в первом ряду узора: порядок предсказуем, ряд за рядом
        val first = info.values.first { it.number == 1 }
        assertTrue(first.cy < 0.35)
    }

    @Test
    fun cutoutOnlyTileKeepsFullSizeButHonestAreaPct() {
        // плитка целиком в комнате, но с вырезом-колонной внутри неё
        val big = RoomSpec(
            listOf(Pt(0.0, 0.0), Pt(0.9, 0.0), Pt(0.9, 0.9), Pt(0.0, 0.9)),
            cutouts = listOf(Cutout(0.35, 0.35, 0.2, 0.2)),
        )
        val lay = TilingEngine.build(big, tile, PatternSpec())
        val info = CutNumbering.compute(big, lay)
        // центральная плитка (0.3..0.6): вырез 200×200 внутри неё
        val mid = info.values.first { it.cx in 0.4..0.5 && it.cy in 0.4..0.5 }
        // по габариту плитка целая, но честная площадь меньше 100 %
        assertTrue(mid.wMm > 299.0 && mid.hMm > 299.0)
        assertTrue("вырез должен уменьшить площадь", mid.areaPct in 50.0..60.0)
    }
}
