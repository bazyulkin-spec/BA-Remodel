import com.baremodel.core.Cutout
import com.baremodel.core.LayoutResult
import com.baremodel.core.LayoutSuggester
import com.baremodel.core.PatternSpec
import com.baremodel.core.PatternType
import com.baremodel.core.Pt
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import com.baremodel.core.TilingEngine
import com.baremodel.core.clipPolygonByQuad
import com.baremodel.core.pointInPolygon
import com.baremodel.core.polygonArea
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Автономная проверка движка без JUnit (как запускать — PLAN.md, раздел 9).
 * Инварианты: при шве 0 плитки узора разбивают плоскость — суммарная площадь
 * пересечений с комнатой равна площади комнаты, и каждая случайная точка
 * накрыта ровно одной плиткой; при шве > 0 перекрытий нет.
 */

private var failures = 0

private fun check(name: String, ok: Boolean, detail: String = "") {
    println((if (ok) "PASS  " else "FAIL  ") + name + if (detail.isEmpty()) "" else "  [$detail]")
    if (!ok) failures++
}

private fun coverage(room: RoomSpec, res: LayoutResult): Double =
    res.tiles.sumOf { polygonArea(clipPolygonByQuad(room.points, it.corners)) }

private fun partitionOk(room: RoomSpec, res: LayoutResult, exact: Boolean): Boolean {
    val rnd = java.util.Random(42)
    val xs = room.points.map { it.x }
    val ys = room.points.map { it.y }
    val minx = xs.min(); val maxx = xs.max()
    val miny = ys.min(); val maxy = ys.max()
    var tested = 0
    var attempts = 0
    while (tested < 4000 && attempts < 40000) {
        attempts++
        val p = Pt(minx + rnd.nextDouble() * (maxx - minx), miny + rnd.nextDouble() * (maxy - miny))
        if (!pointInPolygon(p, room.points)) continue
        tested++
        val cnt = res.tiles.count { pointInPolygon(p, it.corners) }
        if (exact) { if (cnt != 1) return false } else { if (cnt > 1) return false }
    }
    return tested > 1000
}

fun main() {
    val rect = RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0)))
    val lRoom = RoomSpec(listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 1.8), Pt(2.2, 1.8), Pt(2.2, 3.0), Pt(0.0, 3.0)))
    val lArea = polygonArea(lRoom.points)

    run {
        val r = TilingEngine.build(rect, TileSpec(500.0, 500.0, 0.0), PatternSpec(PatternType.GRID, 0.0, 0.013, 0.007))
        check("grid 500, coverage = 12", abs(coverage(rect, r) - 12.0) < 1e-7, "cov=" + coverage(rect, r))
        check("grid 500, partition", partitionOk(rect, r, true))
    }
    run {
        val r = TilingEngine.build(rect, TileSpec(500.0, 500.0, 0.0), PatternSpec(PatternType.GRID, 45.0, 0.2, -0.1))
        check("grid 45deg, coverage = 12", abs(coverage(rect, r) - 12.0) < 1e-7, "cov=" + coverage(rect, r))
    }
    run {
        val r = TilingEngine.build(rect, TileSpec(300.0, 600.0, 0.0), PatternSpec(PatternType.THIRD, 30.0, 0.11, 0.05))
        check("third 30deg, coverage = 12", abs(coverage(rect, r) - 12.0) < 1e-7, "cov=" + coverage(rect, r))
        check("third 30deg, partition", partitionOk(rect, r, true))
    }
    for ((w, h) in listOf(100.0 to 300.0, 200.0 to 600.0, 300.0 to 300.0, 120.0 to 490.0)) {
        val r = TilingEngine.build(rect, TileSpec(w, h, 0.0), PatternSpec(PatternType.HERRINGBONE, 33.0, 0.07, 0.31))
        check("herring ${w.toInt()}x${h.toInt()}, coverage = 12", abs(coverage(rect, r) - 12.0) < 1e-6, "cov=" + coverage(rect, r))
        check("herring ${w.toInt()}x${h.toInt()}, partition", partitionOk(rect, r, true))
    }
    run {
        val a = TilingEngine.build(rect, TileSpec(600.0, 200.0, 0.0), PatternSpec(PatternType.HERRINGBONE, 0.0, 0.02, 0.02))
        val b = TilingEngine.build(rect, TileSpec(200.0, 600.0, 0.0), PatternSpec(PatternType.HERRINGBONE, 0.0, 0.02, 0.02))
        check("herring swap sides, same total", a.totalCount == b.totalCount, "${a.totalCount} vs ${b.totalCount}")
    }
    run {
        val r = TilingEngine.build(lRoom, TileSpec(150.0, 600.0, 0.0), PatternSpec(PatternType.HERRINGBONE, 45.0, 0.13, -0.07))
        check("L-room herring 45deg, coverage = area", abs(coverage(lRoom, r) - lArea) < 1e-6, "cov=" + coverage(lRoom, r) + " vs " + lArea)
        check("L-room herring 45deg, partition", partitionOk(lRoom, r, true))
    }
    run {
        val cutRoom = RoomSpec(rect.points, listOf(Cutout(1.0, 1.0, 0.8, 0.6)))
        val r = TilingEngine.build(cutRoom, TileSpec(300.0, 600.0, 3.0), PatternSpec(PatternType.HALF, 0.0, 0.05, 0.05))
        check("cutout: areaM2 = 12 - 0.48", abs(r.areaM2 - (12.0 - 0.48)) < 1e-9, "area=" + r.areaM2)
        check("grout: no overlaps", partitionOk(cutRoom, r, false))
        check("cut pieces present", r.cutPieces.isNotEmpty() && r.cutCount > 0)
    }
    run {
        val big = RoomSpec(listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 100.0), Pt(0.0, 100.0)))
        val r = TilingEngine.build(big, TileSpec(100.0, 100.0, 2.0), PatternSpec())
        check("tile limit guard", r.overLimit && r.tiles.isEmpty())
    }
    run {
        val s = LayoutSuggester.suggest(rect, TileSpec(600.0, 600.0, 3.0), PatternSpec(PatternType.GRID, 0.0, 0.0, 0.0))
        check(
            "suggester: 3 options, current excluded",
            s.size == 3 && s.none { it.type == PatternType.GRID && it.rotationDeg == 0.0 },
        )
    }

    println(if (failures == 0) "ALL CHECKS PASSED" else "$failures FAILURES")
    if (failures > 0) exitProcess(1)
}
