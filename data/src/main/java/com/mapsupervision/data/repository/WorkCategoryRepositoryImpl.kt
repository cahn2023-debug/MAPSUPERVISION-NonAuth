package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.repository.WorkCategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkCategoryRepositoryImpl @Inject constructor(
    private val dao: WorkCategoryDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : WorkCategoryRepository {
    override suspend fun add(category: WorkCategory): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(category.projectId).upsert(category.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add work category", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list work categories", it)) }
    ) }

    private fun WorkCategory.toEntity() = WorkCategoryEntity(id, projectId, name, unit, createdAtEpochMs)
    private fun WorkCategoryEntity.toDomain() = WorkCategory(id, projectId, name, unit, createdAtEpochMs)

    private suspend fun dao(projectId: String): WorkCategoryDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.workCategoryDao() ?: dao
}
