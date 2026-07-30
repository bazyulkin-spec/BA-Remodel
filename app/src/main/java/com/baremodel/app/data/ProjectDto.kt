package com.baremodel.app.data

import com.baremodel.core.AnchorMode
import com.baremodel.core.DecorSpec
import com.baremodel.core.PatternSpec
import com.baremodel.core.Pt
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import com.baremodel.core.Cutout
import com.baremodel.core.Finish
import com.baremodel.core.Furniture
import com.baremodel.core.MaterialSpec
import com.baremodel.core.StairsSpec
import kotlinx.serialization.Serializable

/** Зона: участок комнаты со своей плиткой. Прямоугольник в мировых координатах. */
@Serializable
data class ZoneDto(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 1.2,
    val h: Double = 1.2,
    val tile: TileSpec = TileSpec(300.0, 300.0, 3.0),
    val pattern: PatternSpec = PatternSpec(),
    val colorArgb: Int = -1,
    val variation: Boolean = false,
    val material: MaterialSpec = MaterialSpec(),
)

/** Одна комната квартиры: контур и все её настройки плитки. */
@Serializable
data class RoomDto(
    val name: String = "",
    val spec: RoomSpec = RoomSpec(
        listOf(Pt(0.0, 0.0), Pt(3.0, 0.0), Pt(3.0, 2.4), Pt(0.0, 2.4)),
    ),
    val tile: TileSpec = TileSpec(600.0, 600.0, 3.0),
    val pattern: PatternSpec = PatternSpec(),
    val colorArgb: Int = -1,
    val variation: Boolean = false,
    val decor: DecorSpec = DecorSpec(),
    val anchor: AnchorMode = AnchorMode.FREE,
    val finishes: Map<String, Finish> = emptyMap(),
    val openings: Map<String, List<Cutout>> = emptyMap(),
    val openingKinds: Map<String, List<Int>> = emptyMap(),
    val decorOverrides: Map<String, Boolean> = emptyMap(),
    val panelOn: Boolean = false,
    val panelRX: Double = 0.0,
    val panelRY: Double = 0.0,
    val zones: List<ZoneDto> = emptyList(),
    val tileColors: Map<String, Int> = emptyMap(),
    val wallThickness: Map<String, Double> = emptyMap(),
    val material: MaterialSpec = MaterialSpec(),
    val stairs: List<StairsSpec> = emptyList(),
    /** Крыльцо/терраса: на улице нет стен, потолка и плинтуса. */
    val outdoor: Boolean = false,
)

/**
 * Этаж: свой набор комнат, своя мебель и свои статусы работ.
 * Активный этаж дополнительно лежит в [ProjectDto.rooms] — для совместимости
 * со старыми файлами, где этажей не было вовсе.
 */
@Serializable
data class LevelDto(
    val index: Int = 0,
    val name: String = "",
    val rooms: List<RoomDto> = emptyList(),
    val furniture: List<Furniture> = emptyList(),
    val workStatus: Map<String, Int> = emptyMap(),
)

/** Цены мастера: материалы и работа. Нули означают «не считать». */
@Serializable
data class Prices(
    val tileM2: Double = 0.0,
    val tilePc: Double = 0.0,
    val adhesiveKg: Double = 0.0,
    val roll: Double = 0.0,
    val paintL: Double = 0.0,
    val workTileM2: Double = 0.0,
    val workWallM2: Double = 0.0,
    val workPaintM2: Double = 0.0,
    val currency: String = "₪",
)

/** Метаданные сохранённого проекта для списка. */
data class ProjectMeta(val name: String, val savedAt: Long)

/** Снимок проекта для сохранения на диск (JSON). */
@Serializable
data class ProjectDto(
    val name: String,
    val room: RoomSpec,
    val tile: TileSpec,
    val pattern: PatternSpec,
    val colorArgb: Int,
    val variation: Boolean = true,
    val reservePct: Int = 10,
    val decor: DecorSpec = DecorSpec(),
    val anchor: AnchorMode = AnchorMode.FREE,
    val furniture: List<Furniture> = emptyList(),
    val finishes: Map<String, Finish> = emptyMap(),
    val openings: Map<String, List<Cutout>> = emptyMap(),
    val openingKinds: Map<String, List<Int>> = emptyMap(),
    val wallHeightM: Double = 2.7,
    val prices: Prices = Prices(),
    val wallThicknessM: Double = 0.10,
    val skirtMode: Int = 0,
    val skirtBarLenM: Double = 2.5,
    val skirtHeightMm: Double = 80.0,
    val pairCuts: Boolean = true,
    val workStatus: Map<String, Int> = emptyMap(),
    val rooms: List<RoomDto> = emptyList(),
    val activeRoom: Int = 0,
    val material: MaterialSpec = MaterialSpec(),
    val stairs: List<StairsSpec> = emptyList(),
    val outdoor: Boolean = false,
    /** Все этажи, включая активный. Пусто или один элемент — одноэтажный проект. */
    val levels: List<LevelDto> = emptyList(),
    val activeLevel: Int = 0,
    val savedAt: Long = 0L,
)
