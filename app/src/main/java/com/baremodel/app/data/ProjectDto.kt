package com.baremodel.app.data

import com.baremodel.core.PatternSpec
import com.baremodel.core.RoomSpec
import com.baremodel.core.TileSpec
import kotlinx.serialization.Serializable

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
    val savedAt: Long = 0L,
)
