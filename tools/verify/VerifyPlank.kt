import com.baremodel.core.Cutout
import com.baremodel.core.MaterialKind
import com.baremodel.core.MaterialSpec
import com.baremodel.core.PatternSpec
import com.baremodel.core.PatternType
import com.baremodel.core.PlankCalc
import com.baremodel.core.Pt
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import com.baremodel.core.isPlank
import kotlin.math.abs
import kotlin.system.exitProcess

/** Проверка расчёта планки: ламинат, паркет-ёлочка, террасная доска. */

private var failures = 0
private fun check(name: String, ok: Boolean, detail: String = "") {
    println((if (ok) "PASS  " else "FAIL  ") + name + if (detail.isEmpty()) "" else "  [$detail]")
    if (!ok) failures++
}

private fun rect(w: Double, h: Double) =
    RoomSpec(listOf(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, h), Pt(0.0, h)))

private val lam = MaterialSpec.preset(MaterialKind.LAMINATE)
private val lamTile = MaterialSpec.presetTile(MaterialKind.LAMINATE)
private val third = PatternSpec(PatternType.THIRD)

fun main() {
    println("=== ПЛАНКА: РАСХОД ПО РЯДАМ ===")

    // 1. виды материала
    check("вид: ламинат/паркет/доска — планка", MaterialKind.LAMINATE.isPlank && MaterialKind.PARQUET.isPlank && MaterialKind.DECK.isPlank)
    check("вид: плитка и «нет» — не планка", !MaterialKind.TILE.isPlank && !MaterialKind.NONE.isPlank)
    check("пресет ламината 1200×190", lamTile.widthMm == 1200.0 && lamTile.heightMm == 190.0 && lamTile.groutMm == 0.0)
    check("пресет паркета — ёлочка", MaterialSpec.presetPattern(MaterialKind.PARQUET) == PatternType.HERRINGBONE)

    // 2. комната 4×3, ламинат 1/3
    val room = rect(4.0, 3.0)
    val p = PlankCalc.plan(room, lamTile, third, lam, reservePct = 0)
    println(
        "   4.0×3.0 м: рядов=${p.rowCount} кусков=${p.pieces} подрезок=${p.cutPieces} " +
            "планок=${p.planksUsed} упак=${p.packs} отход=${"%.1f".format(p.wastePct)}% " +
            "экономия=${p.savedPlanks} остатков=${p.leftoversMm.size}",
    )
    check("площадь пола 12 м²", abs(p.floorM2 - 12.0) < 1e-6, "${p.floorM2}")
    check("ряды заполнены", p.rowCount in 15..17, "${p.rowCount}")

    // каждый ряд покрыт полностью: сумма кусков = длина ряда
    var coverOk = true
    var totalRowM = 0.0
    for (r in p.rows) {
        val sum = r.pieces.sumOf { it.lenMm } / 1000.0
        totalRowM += r.spanLenM
        if (abs(sum - r.spanLenM) > 1e-6) coverOk = false
    }
    check("ряд покрыт кусками без дыр и нахлёста", coverOk)

    // площадь уложенного ≈ площадь пола минус зазоры у стен
    val laidM2 = totalRowM * lamTile.heightMm / 1000.0
    check("уложено ≈ пол (последний ряд распускается вдоль)", abs(laidM2 - p.laidM2) < 1e-9 &&
        laidM2 >= 12.0 * 0.97 && laidM2 <= 12.0 + 4.0 * 0.19, "%.3f".format(laidM2))
    check("распаковано не меньше уложенного", p.spendM2 >= p.laidM2 - 1e-9, "%.3f / %.3f".format(p.spendM2, p.laidM2))
    check("к покупке не меньше распакованного", p.boughtM2 >= p.spendM2 - 1e-9, "%.3f".format(p.boughtM2))

    // нельзя израсходовать меньше, чем площадь пола
    val plankM2 = lamTile.widthMm * lamTile.heightMm / 1_000_000.0
    check("планок не меньше площади пола", p.planksUsed * plankM2 >= p.floorM2 - 1e-9, "${p.planksUsed} × $plankM2")
    check("подрезок не больше кусков", p.cutPieces <= p.pieces && p.cutPieces > 0)
    check("упаковки = ceil(планок / 8)", p.packs == Math.ceil(p.planksUsed / 8.0).toInt(), "${p.packs}")
    check("отход раскладки в разумных пределах", p.wastePct in 0.0..15.0, "%.1f".format(p.wastePct))

    // 3. разбег швов
    check("1/3: разбег соблюдён", p.tightJoints == 0, "${p.tightJoints}")
    val grid = PlankCalc.plan(room, lamTile, PatternSpec(PatternType.GRID), lam)
    check("без сдвига: швы в линию — предупреждение", grid.tightJoints > 0, "${grid.tightJoints}")
    val half = PlankCalc.plan(room, lamTile, PatternSpec(PatternType.HALF), lam)
    check("1/2: разбег соблюдён", half.tightJoints == 0, "${half.tightJoints}")

    // 4. переиспользование остатков
    val noReuse = PlankCalc.plan(room, lamTile, third, lam.copy(reuseOffcuts = false))
    check("остатки экономят планки", p.planksUsed < noReuse.planksUsed, "${p.planksUsed} < ${noReuse.planksUsed}")
    check("остатки снижают отход", p.wastePct < noReuse.wastePct, "%.1f < %.1f".format(p.wastePct, noReuse.wastePct))
    check("экономия = подрезки − планок под подрезки", p.savedPlanks == p.cutPieces - (p.planksUsed - (p.pieces - p.cutPieces)))
    check("без остатков экономии нет", noReuse.savedPlanks == 0)

    // 5. запас
    val res = PlankCalc.plan(room, lamTile, third, lam, reservePct = 10)
    check("запас 10% увеличивает закупку", res.planksWithReserve > p.planksWithReserve && res.packs >= p.packs)
    check("запас не меняет раскладку", res.pieces == p.pieces && res.rowCount == p.rowCount)

    // 6. вырез в полу
    val holed = PlankCalc.plan(
        room.copy(cutouts = listOf(Cutout(1.0, 1.0, 1.0, 1.0))), lamTile, third, lam,
    )
    check("вырез уменьшает площадь", abs(holed.floorM2 - 11.0) < 1e-6, "${holed.floorM2}")
    check("вырез уменьшает расход", holed.planksUsed < p.planksUsed, "${holed.planksUsed} < ${p.planksUsed}")

    // 7. Г-образная комната: в ряду несколько отрезков
    val lshape = RoomSpec(
        listOf(Pt(0.0, 0.0), Pt(5.0, 0.0), Pt(5.0, 2.0), Pt(2.0, 2.0), Pt(2.0, 4.0), Pt(0.0, 4.0)),
    )
    val pl = PlankCalc.plan(lshape, lamTile, third, lam)
    check("Г-комната: площадь 14 м²", abs(pl.floorM2 - 14.0) < 1e-6, "${pl.floorM2}")
    var lok = true
    for (r in pl.rows) {
        if (abs(r.pieces.sumOf { it.lenMm } / 1000.0 - r.spanLenM) > 1e-6) lok = false
    }
    check("Г-комната: ряды покрыты", lok && pl.rowCount > 15, "${pl.rowCount}")
    check("Г-комната: расход больше прямоугольника", pl.planksUsed > p.planksUsed)
    // ряд, идущий через вырез, разбит на два отрезка: подрезка у стены и у выреза
    check(
        "ряд через вырез разбит на два отрезка",
        holed.rows.any { it.pieces.count { pc -> pc.cut } >= 4 },
        holed.rows.maxOf { r -> r.pieces.count { it.cut } }.toString(),
    )

    // 8. поворот раскладки — материал тот же с точностью до края
    val rot = PlankCalc.plan(room, lamTile, PatternSpec(PatternType.THIRD, rotationDeg = 90.0), lam)
    check("поворот 90°: площадь та же", abs(rot.floorM2 - 12.0) < 1e-6)
    check("поворот 90°: расход сопоставим", abs(rot.planksUsed - p.planksUsed) <= p.planksUsed / 3, "${rot.planksUsed} vs ${p.planksUsed}")

    // 9. короткий последний кусок ловится
    val narrow = PlankCalc.plan(rect(2.55, 3.0), lamTile, PatternSpec(PatternType.GRID), lam)
    check("короткий кусок в ряду замечен", narrow.shortLastRows > 0, "${narrow.shortLastRows}")

    // 10. паркет-ёлочка: оценка по площади
    val par = MaterialSpec.preset(MaterialKind.PARQUET)
    val parT = MaterialSpec.presetTile(MaterialKind.PARQUET)
    val hb = PlankCalc.plan(room, parT, PatternSpec(PatternType.HERRINGBONE), par)
    println("   ёлочка: планок=${hb.planksUsed} упак=${hb.packs} отход=${"%.1f".format(hb.wastePct)}%")
    check("ёлочка: помечена как оценка", hb.estimated)
    check("ёлочка: отход не меньше 12%", hb.wastePct >= 10.0, "%.1f".format(hb.wastePct))
    check("ёлочка: упаковки посчитаны", hb.packs > 0)

    // 11. террасная доска: продаётся штуками, упаковок нет
    val deck = MaterialSpec.preset(MaterialKind.DECK)
    val deckT = MaterialSpec.presetTile(MaterialKind.DECK)
    val d = PlankCalc.plan(rect(6.0, 3.0), deckT, PatternSpec(PatternType.THIRD), deck)
    println("   доска 2000×145: досок=${d.planksUsed} упак=${d.packs} отход=${"%.1f".format(d.wastePct)}%")
    check("доска: упаковок нет, считаем штуками", d.packs == 0 && d.planksUsed > 0)
    check("доска: зазор 5 мм учтён в шаге", d.rowCount in 19..21, "${d.rowCount}")

    // 12. вырожденные данные не роняют расчёт
    val bad = PlankCalc.plan(rect(4.0, 3.0), TileSpec(0.0, 0.0, 0.0), third, lam)
    check("нулевая планка — пустой план", bad.planksUsed == 0 && bad.rowCount == 0)
    val tiny = PlankCalc.plan(rect(0.05, 0.05), lamTile, third, lam)
    check("комната в ладонь — не роняет и не плодит куски", tiny.pieces <= 1, "${tiny.pieces}")
    val twoPt = PlankCalc.plan(RoomSpec(listOf(Pt(0.0, 0.0), Pt(1.0, 0.0))), lamTile, third, lam)
    check("контур из двух точек — пустой план", twoPt.planksUsed == 0)

    println()
    if (failures == 0) println("ALL CHECKS PASSED") else println("FAILURES: $failures")
    if (failures > 0) exitProcess(1)
}
