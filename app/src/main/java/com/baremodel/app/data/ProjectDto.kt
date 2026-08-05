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
/**
 * Вариант дизайна одной комнаты: только решения по отделке, БЕЗ геометрии.
 * Стены, проёмы, мебель и ступени общие — меняются плитка, узор, цвет, декор
 * и зоны. Так дизайнер показывает клиенту «А или Б» на одной и той же квартире.
 */
@Serializable
data class DesignVariant(
    val name: String = "",
    val tile: TileSpec = TileSpec(600.0, 600.0, 3.0),
    val pattern: PatternSpec = PatternSpec(),
    val colorArgb: Int = -1,
    val variation: Boolean = false,
    val decor: DecorSpec = DecorSpec(),
    val zones: List<ZoneDto> = emptyList(),
    val tileColors: Map<String, Int> = emptyMap(),
    val material: MaterialSpec = MaterialSpec(),
    val finishes: Map<String, Finish> = emptyMap(),
    val wallTile: TileSpec = TileSpec(300.0, 600.0, 2.0),
    val wallStartFloor: Boolean = true,
    val wallCentered: Boolean = true,
    val panelOn: Boolean = false,
    val panelRX: Double = 0.0,
    val panelRY: Double = 0.0,
)

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
    /** Плитка для стен — своя: в ванной пол и стены почти всегда разные. */
    val wallTile: TileSpec = TileSpec(300.0, 600.0, 2.0),
    val wallStartFloor: Boolean = true,
    val wallCentered: Boolean = true,
    val stairs: List<StairsSpec> = emptyList(),
    /** Крыльцо/терраса: на улице нет стен, потолка и плинтуса. */
    val outdoor: Boolean = false,
    /** Варианты дизайна этой комнаты; текущий — [activeVariant] или −1 (без варианта). */
    val variants: List<DesignVariant> = emptyList(),
    val activeVariant: Int = -1,
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

/**
 * Что печатать в отчёте. Один отчёт всем не годится: клиенту нужны картинка и
 * деньги, мастеру — подрезка и развёртки, в магазин — только количества.
 * Блоки включаются по одному, а пресеты [ReportPreset] расставляют их разом.
 */
@Serializable
data class ReportOptions(
    val plan: Boolean = true,
    val params: Boolean = true,
    val results: Boolean = true,
    val cutMap: Boolean = true,
    val stairs: Boolean = true,
    val apartment: Boolean = true,
    val estimate: Boolean = true,
    val prices: Boolean = true,
    val cutNumbers: Boolean = true,
    val watermark: Boolean = true,
)

/** Упаковка набора блоков в число — чтобы хранить личный выбор в настройках. */
fun ReportOptions.toMask(): Int =
    (if (plan) 1 else 0) or
        (if (params) 2 else 0) or
        (if (results) 4 else 0) or
        (if (cutMap) 8 else 0) or
        (if (stairs) 16 else 0) or
        (if (apartment) 32 else 0) or
        (if (estimate) 64 else 0) or
        (if (prices) 128 else 0) or
        (if (cutNumbers) 256 else 0) or
        (if (watermark) 512 else 0)

fun reportOptionsOf(mask: Int): ReportOptions = ReportOptions(
    plan = mask and 1 != 0,
    params = mask and 2 != 0,
    results = mask and 4 != 0,
    cutMap = mask and 8 != 0,
    stairs = mask and 16 != 0,
    apartment = mask and 32 != 0,
    estimate = mask and 64 != 0,
    prices = mask and 128 != 0,
    cutNumbers = mask and 256 != 0,
    watermark = mask and 512 != 0,
)

/** Ситуация, под которую собран отчёт. */
enum class ReportPreset { CLIENT, MASTER, SHOP, FULL }

/** Готовые наборы блоков под ситуацию: клиенту, мастеру, в магазин, всё. */
fun presetOptions(p: ReportPreset): ReportOptions = when (p) {
    // клиенту: как будет выглядеть и сколько стоит; техника ему не нужна
    ReportPreset.CLIENT -> ReportOptions(
        plan = true, params = false, results = false, cutMap = false,
        stairs = false, apartment = true, estimate = true, prices = true,
        cutNumbers = false, watermark = true,
    )
    // мастеру: чертёж, раскладка, подрезка с номерами; цены в бригаде лишние
    ReportPreset.MASTER -> ReportOptions(
        plan = true, params = true, results = true, cutMap = true,
        stairs = true, apartment = false, estimate = false, prices = false,
        cutNumbers = true, watermark = false,
    )
    // в магазин: только что купить и сколько
    ReportPreset.SHOP -> ReportOptions(
        plan = false, params = true, results = true, cutMap = false,
        stairs = true, apartment = true, estimate = true, prices = true,
        cutNumbers = false, watermark = false,
    )
    ReportPreset.FULL -> ReportOptions()
}

/**
 * Запись журнала проекта: кто, что и когда. Программа работает без интернета
 * и аккаунтов — проекты ходят файлом из рук в руки, поэтому историю носит
 * сам файл: получил проект от напарника и сразу видишь, что он успел.
 */
@Serializable
data class HistoryEntry(
    val who: String = "",
    val what: String = "",
    val at: Long = 0L,
    /** Отмечено работ на момент записи — виден прогресс, а не только факт правки. */
    val done: Int = 0,
    val total: Int = 0,
)

/** Метаданные сохранённого проекта для списка. */
data class ProjectMeta(val name: String, val savedAt: Long, val author: String = "")

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
    /** Показ клиенту: правки на плане выключены, чтобы проект не сбился случайно. */
    val viewOnly: Boolean = false,
    val report: ReportOptions = ReportOptions(),
    /** Кто правил последним и журнал: проект ходит между людьми файлом. */
    val author: String = "",
    val history: List<HistoryEntry> = emptyList(),
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
