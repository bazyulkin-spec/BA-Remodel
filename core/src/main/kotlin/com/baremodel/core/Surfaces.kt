package com.baremodel.core

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/**
 * Поверхность, на которую что-то укладывается: пол, стена, потолок.
 * Контур задаётся в собственной плоскости поверхности (метры), поэтому
 * движок раскладки одинаково работает и с полом, и со стеной.
 * Интерфейс ISurface из ТЗ (раздел 5, «Design ISurface and IPlacer interfaces early»).
 */
interface ISurface {
    val id: String
    val kind: SurfaceKind
    val outline: List<Pt>
    val holes: List<Cutout>
    val finish: Finish
}

@Serializable
enum class SurfaceKind { FLOOR, WALL, CEILING }

/** Чем отделывается поверхность. Расчёт материалов зависит от типа. */
@Serializable
enum class Finish { TILE, WALLPAPER, PAINT, NONE }

@Serializable
data class FloorSurface(
    override val id: String = "floor",
    override val outline: List<Pt>,
    override val holes: List<Cutout> = emptyList(),
    override val finish: Finish = Finish.TILE,
) : ISurface {
    override val kind: SurfaceKind get() = SurfaceKind.FLOOR
}

/**
 * Стена: разворачивается в прямоугольник длина × высота.
 * [fromIndex] — индекс вершины пола, с которой начинается стена (для связи с планом).
 * [holes] — окна, двери, ниши в координатах развёртки.
 */
@Serializable
data class WallSurface(
    override val id: String,
    val lengthM: Double,
    val heightM: Double,
    val fromIndex: Int = 0,
    override val holes: List<Cutout> = emptyList(),
    override val finish: Finish = Finish.TILE,
) : ISurface {
    override val kind: SurfaceKind get() = SurfaceKind.WALL
    override val outline: List<Pt>
        get() = listOf(Pt(0.0, 0.0), Pt(lengthM, 0.0), Pt(lengthM, heightM), Pt(0.0, heightM))
}

@Serializable
data class CeilingSurface(
    override val id: String = "ceiling",
    override val outline: List<Pt>,
    override val holes: List<Cutout> = emptyList(),
    override val finish: Finish = Finish.PAINT,
) : ISurface {
    override val kind: SurfaceKind get() = SurfaceKind.CEILING
}

/** Помещение целиком: пол + стены по его периметру + потолок. */
@Serializable
data class RoomModel(
    val floor: FloorSurface,
    val walls: List<WallSurface>,
    val ceiling: CeilingSurface,
    val wallHeightM: Double,
) {
    val surfaces: List<ISurface> get() = listOf(floor) + walls + listOf(ceiling)

    fun surface(id: String): ISurface? = surfaces.firstOrNull { it.id == id }

    companion object {
        /** Собрать комнату из контура пола: стены поднимаются по каждому ребру. */
        fun fromFloor(
            points: List<Pt>,
            heightM: Double = 2.7,
            cutouts: List<Cutout> = emptyList(),
        ): RoomModel {
            val walls = points.indices.map { i ->
                val a = points[i]
                val b = points[(i + 1) % points.size]
                WallSurface(
                    id = "wall-${i + 1}",
                    lengthM = hypot(b.x - a.x, b.y - a.y),
                    heightM = heightM,
                    fromIndex = i,
                    finish = Finish.PAINT,
                )
            }
            return RoomModel(
                floor = FloorSurface(outline = points, holes = cutouts),
                walls = walls,
                ceiling = CeilingSurface(outline = points),
                wallHeightM = heightM,
            )
        }
    }
}

/** Площадь поверхности за вычетом проёмов и вырезов. */
fun ISurface.areaM2(): Double = polygonArea(outline) - holes.sumOf { it.w * it.h }

/** Раскладка для любой поверхности — тот же движок, что и для пола. */
fun ISurface.buildLayout(tile: TileSpec, pattern: PatternSpec): LayoutResult =
    TilingEngine.build(RoomSpec(outline, holes), tile, pattern)
