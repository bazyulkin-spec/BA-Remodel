import com.baremodel.core.AnchorMode
import com.baremodel.core.Aligner
import com.baremodel.core.ArtRect
import com.baremodel.core.CoverageAnalyzer
import com.baremodel.core.CutAnalyzer
import com.baremodel.core.DecorMode
import com.baremodel.core.DecorPlanner
import com.baremodel.core.DecorSpec
import com.baremodel.core.Finish
import com.baremodel.core.Furniture
import com.baremodel.core.PatternSpec
import com.baremodel.core.Pt
import com.baremodel.core.RoomModel
import com.baremodel.core.RoomSpec
import com.baremodel.core.SurfaceKind
import com.baremodel.core.TileClass
import com.baremodel.core.TileSpec
import com.baremodel.core.TilingEngine
import com.baremodel.core.areaM2
import com.baremodel.core.buildLayout
import kotlin.math.abs
import kotlin.system.exitProcess

/** Проверка второго слоя движка: поверхности, декор, центровка, подрезка, мебель. */

private var failures = 0
private fun check(name: String, ok: Boolean, detail: String = "") {
    println((if (ok) "PASS  " else "FAIL  ") + name + if (detail.isEmpty()) "" else "  [$detail]")
    if (!ok) failures++
}

fun main() {
    val room = RoomSpec(listOf(Pt(0.0, 0.0), Pt(3.4, 0.0), Pt(3.4, 2.6), Pt(0.0, 2.6)))
    val tile = TileSpec(300.0, 300.0, 2.0)
    val center = Pt(1.7, 1.3)
    val art = ArtRect(0.22, 0.18, 0.52, 0.56) // рисунок смещён от центра плитки

    // 1. центровка по рисунку
    run {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        val dec = DecorPlanner.select(lay, tile, DecorSpec(DecorMode.SINGLE, art = art), center)
        val ok = dec.size == 1 &&
            DecorPlanner.artCenterMatches(lay.tiles[dec.first()], tile, art, center)
        check("центр рисунка совпадает с центром комнаты", ok)
    }

    // 2. центровка по плитке даёт симметричную подрезку
    run {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.TILE_CENTER)
        val lay = TilingEngine.build(room, tile, pat)
        val rep = CutAnalyzer.analyze(room, tile, lay)
        check("подрезка симметрична по обеим осям", rep.symmetricX && rep.symmetricY,
            rep.edges.joinToString { "%.1f".format(it.minStripCm) })
        check("рёбер в отчёте: 4", rep.edges.size == 4)
    }

    // 3. от угла — целая плитка у первой стены, асимметрия
    run {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.CORNER)
        val lay = TilingEngine.build(room, tile, pat)
        val rep = CutAnalyzer.analyze(room, tile, lay)
        check("от угла: раскладка несимметрична", !(rep.symmetricX && rep.symmetricY))
        check("от угла: есть предупреждение ASYMMETRIC", rep.warnings.any { it.code == "ASYMMETRIC" })
    }

    // 4. узкая полоса ловится предупреждением
    run {
        val t2 = TileSpec(500.0, 500.0, 2.0)
        val r2 = RoomSpec(listOf(Pt(0.0, 0.0), Pt(3.03, 0.0), Pt(3.03, 2.0), Pt(0.0, 2.0)))
        val pat = Aligner.applyAnchor(PatternSpec(), r2.points, t2, AnchorMode.CORNER)
        val lay = TilingEngine.build(r2, t2, pat)
        val rep = CutAnalyzer.analyze(r2, t2, lay)
        check("узкая подрезка помечена", rep.warnings.any { it.code == "THIN_STRIP" },
            rep.edges.joinToString { "%.1f".format(it.minStripCm) })
    }

    // 5. декор не попадает на подрезку
    run {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        for (mode in listOf(DecorMode.SINGLE, DecorMode.PANEL, DecorMode.EVERY_N)) {
            val dec = DecorPlanner.select(lay, tile, DecorSpec(mode, art = art), center)
            val clean = dec.all { lay.tiles[it].cls == TileClass.FULL }
            check("декор $mode только на целых плитках", clean && dec.isNotEmpty(), "${dec.size} шт")
        }
        val panel = DecorPlanner.select(lay, tile, DecorSpec(DecorMode.PANEL, panelCols = 2, panelRows = 2, art = art), center)
        check("панно 2×2 = 4 плитки", panel.size == 4, "${panel.size}")
    }

    // 6. мебель перекрывает декор
    run {
        val pat = Aligner.applyAnchor(PatternSpec(), room.points, tile, AnchorMode.ART_CENTER, art)
        val lay = TilingEngine.build(room, tile, pat)
        val dec = DecorPlanner.select(lay, tile, DecorSpec(DecorMode.SINGLE, art = art), center)
        val far = Furniture("f1", "Тумба", 0.05, 0.05, 1.2, 0.5)
        val onTop = Furniture("f2", "Тумба", 1.4, 1.0, 1.2, 0.6, coversFinish = false)
        val a = CoverageAnalyzer.analyze(lay, tile, dec, art, listOf(far))
        val b = CoverageAnalyzer.analyze(lay, tile, dec, art, listOf(onTop))
        check("декор открыт, когда мебель в стороне", a.coveredPct == 0, "${a.coveredPct}%")
        check("декор перекрыт, когда мебель сверху", b.decorHidden, "${b.coveredPct}%")
        check("плитки под мебелью посчитаны", b.hiddenTiles > 0 && b.savedTiles == b.hiddenTiles,
            "скрыто ${b.hiddenTiles}, экономия ${b.savedTiles}")
    }

    // 7. поверхности: пол, стены, потолок
    run {
        val model = RoomModel.fromFloor(room.points, heightM = 2.7)
        check("поверхностей: пол + 4 стены + потолок", model.surfaces.size == 6, "${model.surfaces.size}")
        check("площадь пола 8.84 м²", abs(model.floor.areaM2() - 8.84) < 1e-9, "${model.floor.areaM2()}")
        val wall = model.walls.first()
        check("площадь стены = длина × высота", abs(wall.areaM2() - 3.4 * 2.7) < 1e-9, "${wall.areaM2()}")
        check("потолок и стены различаются типом",
            model.ceiling.kind == SurfaceKind.CEILING && wall.kind == SurfaceKind.WALL)
        val wl = wall.buildLayout(TileSpec(300.0, 600.0, 2.0), PatternSpec())
        check("раскладка по стене считается тем же движком", wl.totalCount > 0, "${wl.totalCount} плиток")
        check("у стены по умолчанию покраска", model.walls.all { it.finish == Finish.PAINT })
    }

    println(if (failures == 0) "ALL V2 CHECKS PASSED" else "$failures FAILURES")
    if (failures > 0) exitProcess(1)
}
