package com.resumeagent.app.data

import android.content.Context
import com.resumeagent.app.domain.ResumeProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "projects")
    private val saveLock = Any()
    private val preferences = context.getSharedPreferences("project_state", Context.MODE_PRIVATE)

    fun lastSelectedProjectId(): String? = preferences.getString("last_selected", null)

    fun setLastSelectedProjectId(projectId: String?) {
        preferences.edit().apply {
            if (projectId.isNullOrBlank()) remove("last_selected") else putString("last_selected", projectId)
        }.apply()
    }

    suspend fun list(): List<ResumeProject> = withContext(Dispatchers.IO) {
        root.mkdirs()
        root.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { file -> runCatching { ProjectJson.decode(file.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun save(project: ResumeProject) = withContext(Dispatchers.IO) {
        synchronized(saveLock) {
            root.mkdirs()
            val target = File(root, "${project.id}.json")
            val temporary = File(root, "${project.id}.json.tmp")
            temporary.writeText(ProjectJson.encode(project))
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }

    suspend fun delete(projectId: String) = withContext(Dispatchers.IO) {
        File(root, "$projectId.json").delete()
        File(context.filesDir, "project_files/$projectId").deleteRecursively()
        File(context.filesDir, "exports")
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.endsWith("_$projectId") }
            .forEach { it.deleteRecursively() }
        if (lastSelectedProjectId() == projectId) setLastSelectedProjectId(null)
    }

    fun projectDirectory(projectId: String): File =
        File(context.filesDir, "project_files/$projectId").apply { mkdirs() }

    fun exportDirectory(): File = File(context.filesDir, "exports").apply { mkdirs() }
}
