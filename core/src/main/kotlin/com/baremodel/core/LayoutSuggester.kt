package com.baremodel.core

/** Перебор типовых конфигураций раскладки; лучшие — по расходу плитки, затем по числу подрезок. */
object LayoutSuggester {

    data class Suggestion(
        val type: PatternType,
        val rotationDeg: Double,
        val total: Int,
        val cuts: Int,
    )

    private val configs = listOf(
        PatternType.GRID to 0.0,
        PatternType.HALF to 0.0,
        PatternType.THIRD to 0.0,
        PatternType.GRID to 45.0,
        PatternType.HALF to 45.0,
        PatternType.HERRINGBONE to 0.0,
        PatternType.HERRINGBONE to 45.0,
    )

    fun suggest(room: RoomSpec, tile: TileSpec, current: PatternSpec, top: Int = 3): List<Suggestion> =
        configs
            .filter { (t, r) -> !(t == current.type && r == current.rotationDeg) }
            .map { (t, r) ->
                val res = TilingEngine.build(room, tile, PatternSpec(t, r, current.offsetX, current.offsetY))
                Suggestion(t, r, res.totalCount, res.cutCount)
            }
            .sortedWith(compareBy({ it.total }, { it.cuts }))
            .take(top)
}
