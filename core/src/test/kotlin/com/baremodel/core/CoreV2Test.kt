package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreV2Test {

    private val room = RoomSpec(listOf(Pt(0.0, 0.0), Pt(3.4, 0.0), Pt(3.4, 2.6), Pt(0.0, 2.6)))
    private val tile = TileSpec(300.0, 300.0, 2.0)
    private val center = Pt(1.7, 1.3)
    private val art = ArtRect(0.22, 0.18, 0.52, 0.56)

    @Test
    fun artCenterAnchorPutsArtworkInRoomCenter() {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        val dec = DecorPlanner.select(lay, tile, DecorSpec(DecorMode.SINGLE, art = art), center)
        assertEquals(1, dec.size)
        assertTrue(DecorPlanner.artCenterMatches(lay.tiles[dec.first()], tile, art, center))
    }

    @Test
    fun tileCenterAnchorGivesSymmetricCuts() {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.TILE_CENTER)
        val rep = CutAnalyzer.analyze(room, tile, TilingEngine.build(room, tile, pat))
        assertEquals(4, rep.edges.size)
        assertTrue(rep.symmetricX && rep.symmetricY)
    }

    @Test
    fun cornerAnchorIsReportedAsAsymmetric() {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.CORNER)
        val rep = CutAnalyzer.analyze(room, tile, TilingEngine.build(room, tile, pat))
        assertTrue(rep.warnings.any { it.code == "ASYMMETRIC" })
    }

    @Test
    fun thinStripIsFlagged() {
        val t = TileSpec(500.0, 500.0, 2.0)
        val r = RoomSpec(listOf(Pt(0.0, 0.0), Pt(3.03, 0.0), Pt(3.03, 2.0), Pt(0.0, 2.0)))
        val pat = Aligner.applyAnchor(PatternSpec(), r.points, t, AnchorMode.CORNER)
        val rep = CutAnalyzer.analyze(r, t, TilingEngine.build(r, t, pat))
        assertTrue(rep.warnings.any { it.code == "THIN_STRIP" })
    }

    @Test
    fun decorNeverLandsOnCutTiles() {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        for (mode in listOf(DecorMode.SINGLE, DecorMode.PANEL, DecorMode.EVERY_N)) {
            val dec = DecorPlanner.select(lay, tile, DecorSpec(mode, art = art), center)
            assertTrue(dec.isNotEmpty())
            assertTrue(dec.all { lay.tiles[it].cls == TileClass.FULL })
        }
    }

    @Test
    fun furnitureCoverageIsDetected() {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        val dec = DecorPlanner.select(lay, tile, DecorSpec(DecorMode.SINGLE, art = art), center)
        val away = CoverageAnalyzer.analyze(lay, tile, dec, art, listOf(Furniture("a", "Тумба", 0.05, 0.05, 1.2, 0.5)))
        val over = CoverageAnalyzer.analyze(
            lay, tile, dec, art,
            listOf(Furniture("b", "Тумба", 1.4, 1.0, 1.2, 0.6, coversFinish = false))
        )
        assertEquals(0, away.coveredPct)
        assertTrue(over.decorHidden)
        assertTrue(over.hiddenTiles > 0 && over.savedTiles == over.hiddenTiles)
    }

    @Test
    fun roomModelBuildsFloorWallsCeiling() {
        val m = RoomModel.fromFloor(room.points, heightM = 2.7)
        assertEquals(6, m.surfaces.size)
        assertEquals(8.84, m.floor.areaM2(), 1e-9)
        assertEquals(3.4 * 2.7, m.walls.first().areaM2(), 1e-9)
        assertTrue(m.walls.first().buildLayout(TileSpec(300.0, 600.0, 2.0), PatternSpec()).totalCount > 0)
    }
}
