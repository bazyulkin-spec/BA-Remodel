package com.baremodel.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** Хранилище проектов: по одному JSON-файлу на проект в filesDir/projects. */
class ProjectRepository(context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun fileFor(name: String): File {
        val safe = name.trim().replace(Regex("[^\\w\\u0400-\\u04FF -]"), "_").take(60)
        return File(dir, "$safe.json")
    }

    fun list(): List<ProjectMeta> = runCatching {
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f ->
                runCatching { json.decodeFromString(ProjectDto.serializer(), f.readText()) }
                    .getOrNull()
                    ?.let { ProjectMeta(it.name, it.savedAt) }
            }
            .sortedByDescending { it.savedAt }
    }.getOrDefault(emptyList())

    fun save(dto: ProjectDto) {
        runCatching { fileFor(dto.name).writeText(json.encodeToString(ProjectDto.serializer(), dto)) }
    }

    fun load(name: String): ProjectDto? = runCatching {
        val f = fileFor(name)
        if (f.exists()) json.decodeFromString(ProjectDto.serializer(), f.readText()) else null
    }.getOrNull()

    fun delete(name: String) {
        runCatching { fileFor(name).delete() }
    }
}
