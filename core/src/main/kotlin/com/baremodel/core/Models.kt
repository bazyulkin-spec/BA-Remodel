package com.baremodel.core

import kotlinx.serialization.Serializable

@Serializable
data class Pt(val x: Double, val y: Double)

@Serializable
data class Cutout(val x: Double, val y: Double, val w: Double, val h: Double)

/** Помещение: полигон в метрах (обход по кругу) и прямоугольные вырезы. */
@Serializable
data class RoomSpec(
    val points: List<Pt>,
    val cutouts: List<Cutout> = emptyList(),
)

/** Плитка: ширина/длина/шов в миллиметрах. */
@Serializable
data class TileSpec(
    val widthMm: Double,
    val heightMm: Double,
    val groutMm: Double,
)

@Serializable
enum class PatternType { GRID, HALF, THIRD, HERRINGBONE }

/** Узор: тип, поворот в градусах, сдвиг начала (метры, мировые координаты). */
@Serializable
data class PatternSpec(
    val type: PatternType = PatternType.GRID,
    val rotationDeg: Double = 0.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)

enum class TileClass { FULL, CUT }

/** Прямоугольник плитки в системе координат узора (до поворота и сдвига). */
data class LocalRect(val x: Double, val y: Double, val w: Double, val h: Double, val vertical: Boolean)

/** Плитка на плане: 4 угла в мировых координатах (по кругу), класс, исходный прямоугольник. */
data class PlacedTile(val corners: List<Pt>, val cls: TileClass, val rect: LocalRect)

/** Группа кусков подрезки: габарит a × b см (a >= b), количество. */
data class CutPiece(val aCm: Double, val bCm: Double, val count: Int)

data class LayoutResult(
    val tiles: List<PlacedTile>,
    val fullCount: Int,
    val cutCount: Int,
    val areaM2: Double,
    val cutPieces: List<CutPiece>,
    val overLimit: Boolean,
) {
    val totalCount: Int get() = fullCount + cutCount
}
