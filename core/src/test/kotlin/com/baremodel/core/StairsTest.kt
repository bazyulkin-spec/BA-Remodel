package com.baremodel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ступени: габарит из числа ступеней, куски на проступи и подступёнки, удобство шага. */
class StairsTest {

    private val st = StairsSpec(id = "s1", x = 1.0, y = 1.0, widthM = 1.0, steps = 3)

    @Test
    fun footprintFollowsStepsAndTread() {
        assertEquals(0.9, st.runM, 1e-9) // 3 × 300 мм
        assertEquals(0.48, st.riseM, 1e-9) // 3 × 160 мм
        assertEquals(1.0, st.w, 1e-9)
        assertEquals(0.9, st.h, 1e-9)
        // поворот меняет габарит местами, вылет остаётся тем же
        val turned = st.copy(dirDeg = 90)
        assertEquals(0.9, turned.w, 1e-9)
        assertEquals(1.0, turned.h, 1e-9)
        assertTrue(turned.horizontal)
    }

    @Test
    fun stepEdgesGoFromBaseToTop() {
        val base = st.edge(-1)
        val top = st.edge(st.steps - 1)
        assertEquals(1.0, base.first.y, 1e-9) // нижняя ступень — у края площадки
        assertEquals(1.9, top.first.y, 1e-9) // верхняя — через весь вылет
        // подъём вниз по плану: рёбра идут в обратную сторону
        val down = st.copy(dirDeg = 180)
        assertEquals(1.9, down.edge(-1).first.y, 1e-9)
        assertEquals(1.0, down.edge(down.steps - 1).first.y, 1e-9)
    }

    @Test
    fun piecesAcrossStepAndCut() {
        val p = StairsCalc.plan(st)
        assertEquals(4, p.acrossPieces) // 1000 мм / 300+3
        assertEquals(209, p.cutMm.toInt()) // с крайней плитки срезаем
        assertEquals(12, p.treadPieces) // 3 ступени × 4
        assertEquals(12, p.riserPieces)
        assertEquals(3 * 1.0 * 0.3, p.treadAreaM2, 1e-9)
        assertEquals(3 * 1.0 * 0.16, p.riserAreaM2, 1e-9)
    }

    @Test
    fun risersAreCutAsStripsFromWholeTiles() {
        // подступёнок 160 из плитки 300 — одна полоса, остаток 140 не годится
        assertEquals(1, StairsCalc.plan(st).stripsPerTile)
        assertEquals(12, StairsCalc.plan(st).tilesForRisers)
        // подступёнок 140 — уже две полосы с одной плитки, плиток вдвое меньше
        val low = StairsCalc.plan(st.copy(riserMm = 140.0))
        assertEquals(2, low.stripsPerTile)
        assertEquals(6, low.tilesForRisers)
        // открытые ступени: подступёнков нет вовсе
        val open = StairsCalc.plan(st.copy(risers = false))
        assertEquals(0, open.riserPieces)
        assertEquals(0, open.tilesForRisers)
        assertEquals(0.0, open.riserAreaM2, 1e-9)
    }

    @Test
    fun reserveIsAddedToPurchase() {
        val plain = StairsCalc.plan(st)
        val res = StairsCalc.plan(st, reservePct = 10)
        assertEquals(StairsCalc.cutPlan(st).totalTiles, plain.buyPieces)
        assertTrue(res.buyPieces > plain.buyPieces)
        assertEquals(plain.treadPieces, res.treadPieces) // запас не меняет раскладку
    }

    @Test
    fun cuttingListPacksEdgePieces() {
        // ступень 1 м плиткой 300: срезаем 209, в дело идёт кусок 91 —
        // таких из одной плитки выходит три, значит на три ступени хватит одной
        val c = StairsCalc.cutPlan(st)
        assertEquals(3, c.treadCuts)
        assertEquals(91.0, c.treadCutMm, 1e-9)
        assertEquals(3, c.perTreadTile)
        assertEquals(1, c.treadTiles)
        assertEquals(9, c.wholeTreadTiles) // 3 ступени × 3 целые плитки
        assertEquals(12, c.riserStrips)
        assertEquals(1, c.perRiserTile)
        assertEquals(12, c.riserTiles)
        assertEquals(22, c.totalTiles)
        assertEquals(22, StairsCalc.plan(st).buyPieces)
        // подступёнок 140 — две полосы с плитки, шесть плиток вместо двенадцати
        assertEquals(16, StairsCalc.cutPlan(st.copy(riserMm = 140.0)).totalTiles)
        // открытые ступени: подступёнков нет
        assertEquals(0, StairsCalc.cutPlan(st.copy(risers = false)).riserTiles)
        // ширина ровно под целые плитки — краевых кусков нет вовсе
        val exact = StairsCalc.cutPlan(st.copy(widthM = 1.209))
        assertEquals(0, exact.treadCuts)
        assertEquals(0, exact.treadTiles)
        assertEquals(12, exact.wholeTreadTiles)
    }

    @Test
    fun comfortRulesAreChecked() {
        val ok = StairsCalc.plan(st)
        assertEquals(620.0, ok.formulaMm, 1e-9) // 2 × 160 + 300
        assertTrue(ok.comfy && !ok.treadTooShort && !ok.riserBad && !ok.hasWarnings)
        // 2×220 + 200 = 640 — формула сходится, но ступень всё равно плохая:
        // поэтому проступь и подступёнок проверяются отдельно от формулы
        val steep = StairsCalc.plan(st.copy(treadMm = 200.0, riserMm = 220.0))
        assertTrue(steep.comfy)
        assertTrue(steep.treadTooShort && steep.riserBad && steep.hasWarnings)
        // а вот здесь не сходится и сама формула: 2×160 + 400 = 720
        val flat = StairsCalc.plan(st.copy(treadMm = 400.0))
        assertTrue(!flat.comfy && flat.hasWarnings && !flat.treadTooShort && !flat.riserBad)
    }

    @Test
    fun fitToHeightKeepsStepComfortable() {
        val fit = StairsCalc.fitToHeight(0.85, st)
        assertEquals(5, fit.steps)
        assertEquals(0.85, fit.riseM, 1e-6) // подъём сошёлся ровно
        assertTrue(StairsCalc.plan(fit).comfy)
        // высокий марш на второй этаж
        val floor2 = StairsCalc.fitToHeight(2.8, st)
        assertTrue(floor2.steps in 15..18)
        assertTrue(StairsCalc.plan(floor2).comfy)
        assertTrue(floor2.riserMm in StairsCalc.RISER_MIN..StairsCalc.RISER_MAX)
    }

    @Test
    fun finishesControlWhatIsCounted() {
        // дерево: доска на ступень, плитки к покупке нет
        val wood = StairsCalc.plan(st.copy(treadFinish = StairsFinish.WOOD, riserFinish = StairsFinish.WOOD))
        assertEquals(3, wood.treadPieces)
        assertEquals(3, wood.riserPieces)
        assertEquals(0, wood.buyPieces)
        // «отметка»: марш на плане есть, материала нет, площадь для справки осталась
        val mark = StairsCalc.plan(st.copy(treadFinish = StairsFinish.NONE, riserFinish = StairsFinish.NONE))
        assertEquals(0, mark.treadPieces)
        assertEquals(0, mark.buyPieces)
        assertTrue(mark.areaM2 > 0)
        // микс: проступь плиткой, подступёнок деревом — плитка только на проступи
        val mix = StairsCalc.plan(st.copy(riserFinish = StairsFinish.WOOD))
        assertEquals(12, mix.treadPieces)
        assertEquals(3, mix.riserPieces)
        assertEquals(0, StairsCalc.cutPlan(st.copy(riserFinish = StairsFinish.WOOD)).riserTiles)
        assertEquals(10, mix.buyPieces) // 9 целых + 1 плитка на краевые куски
        // бетонный подступёнок при плиточной проступи: полос из плитки нет
        val conc = StairsCalc.plan(st.copy(riserFinish = StairsFinish.CONCRETE))
        assertEquals(0, conc.tilesForRisers)
        // пол под маршем по умолчанию режется
        assertTrue(st.cutsFloor)
    }

    @Test
    fun degenerateInputGivesEmptyPlan() {
        assertEquals(0, StairsCalc.plan(st.copy(steps = 0)).treadPieces)
        assertEquals(0, StairsCalc.plan(st.copy(widthM = 0.0)).treadPieces)
        assertEquals(0.0, StairsCalc.plan(st.copy(treadMm = 0.0)).areaM2, 1e-9)
        assertEquals(st, StairsCalc.fitToHeight(0.0, st)) // нечего подбирать
    }
}
