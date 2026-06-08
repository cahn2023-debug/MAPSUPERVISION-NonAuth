package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.CURRENT_METADATA_VERSION
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.util.UUID
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val storageManager: ProjectStorageManager
) : ProjectRepository {
    override suspend fun create(name: String): AppResult<Project> = runCatching {
        val entity = newProject(name)
        projectDao.upsert(entity)
        entity.toDomain()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to create project", it)) }
    )

    override suspend fun list(includeArchived: Boolean): AppResult<List<Project>> = runCatching {
        projectDao.list(includeArchived).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to load projects", it)) }
    )

    override suspend fun clone(projectId: String, newName: String): AppResult<Project> = runCatching {
        val source = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        val cloned = newProject(newName).copy(isArchived = false)
        projectDao.upsert(cloned)
        cloned.toDomain().copy(createdAtEpochMs = System.currentTimeMillis(), isArchived = false, name = cloned.name, slug = cloned.slug)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to clone project", it)) }
    )

    override suspend fun archive(projectId: String): AppResult<Unit> = runCatching {
        projectDao.archive(projectId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to archive project", it)) }
    )

    override suspend fun importProject(project: Project): AppResult<Unit> = runCatching {
        val resolved = if (project.storageMode == ProjectStorageMode.PROJECT_DB) {
            project.copy(projectDbPath = resolveProjectDbPath(project.slug))
        } else if (project.storageMode == ProjectStorageMode.LEGACY_SHARED && project.projectDbPath.isBlank()) {
            project.copy(storageMode = ProjectStorageMode.PROJECT_DB, projectDbPath = resolveProjectDbPath(project.slug))
        } else {
            project
        }
        val entity = ProjectEntity(
            id = resolved.id,
            name = resolved.name,
            slug = resolved.slug,
            isArchived = resolved.isArchived,
            createdAtEpochMs = resolved.createdAtEpochMs,
            metadataVersion = resolved.metadataVersion,
            updatedAtEpochMs = resolved.updatedAtEpochMs,
            storageMode = resolved.storageMode,
            projectDbPath = resolved.projectDbPath
        )
        projectDao.upsert(entity)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to import project", it)) }
    )

    override suspend fun clearProject(projectId: String): AppResult<Unit> = runCatching {
        val project = projectDao.get(projectId)
        if (project?.storageMode == ProjectStorageMode.PROJECT_DB && project.projectDbPath.isNotBlank()) {
            runCatching { java.io.File(project.projectDbPath).delete() }
        }
        projectDao.clearProjectData(projectId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to clear project data", it)) }
    )

    override suspend fun touch(projectId: String): AppResult<Unit> = runCatching {
        projectDao.touch(
            projectId = projectId,
            metadataVersion = CURRENT_METADATA_VERSION,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to update project metadata", it)) }
    )

    private fun newProject(name: String): ProjectEntity {
        val id = UUID.randomUUID().toString()
        return ProjectEntity(
            id = id,
            name = name,
            slug = slugify(name),
            isArchived = false,
            createdAtEpochMs = System.currentTimeMillis(),
            metadataVersion = CURRENT_METADATA_VERSION,
            updatedAtEpochMs = System.currentTimeMillis(),
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = resolveProjectDbPath(slugify(name))
        )
    }

    private fun resolveProjectDbPath(slug: String): String =
        storageManager.projectRoot(slug).resolve("db/project.sqlite").absolutePath

    private fun slugify(name: String): String =
        name.lowercase().trim().replace(Regex("\\s+"), "-").replace(Regex("[^a-z0-9-]"), "")

    private fun ProjectEntity.toDomain() = Project(
        id = id,
        name = name,
        slug = slug,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        metadataVersion = metadataVersion,
        updatedAtEpochMs = updatedAtEpochMs,
        storageMode = storageMode,
        projectDbPath = projectDbPath
    )
}
