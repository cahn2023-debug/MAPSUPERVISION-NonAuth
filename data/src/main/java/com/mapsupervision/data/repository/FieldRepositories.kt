package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.MaterialProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProgressRepositoryImpl @Inject constructor(
    private val dao: NodeProgressDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(progress.projectId).upsert(progress.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert progress", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list progress", it)) }
    ) }

    private fun NodeProgress.toEntity() = NodeProgressEntity(id, projectId, nodeCode, planned, actual, remain, delayed, updatedAtEpochMs)
    private fun NodeProgressEntity.toDomain() = NodeProgress(id, projectId, nodeCode, planned, actual, remain, delayed, updatedAtEpochMs)

    private suspend fun dao(projectId: String): NodeProgressDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.nodeProgressDao() ?: dao
}

class PhotoRepositoryImpl @Inject constructor(
    private val dao: SitePhotoDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(photo.projectId).upsert(photo.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add photo", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list photos", it)) }
    ) }

    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao(projectId).byObjectCode(projectId, objectCode).map { it.toDomain() }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(DatabaseException("Failed to list photos by object", it)) }
            )
        }

    private suspend fun dao(projectId: String): SitePhotoDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.sitePhotoDao() ?: dao

    private fun SitePhoto.toEntity() = SitePhotoEntity(
        id,
        projectId,
        objectCode,
        filePath,
        thumbnailPath,
        latitude,
        longitude,
        locationAccuracyM,
        isGpsMocked,
        locationStatus,
        engineer,
        capturedAtEpochMs
    )
    private fun SitePhotoEntity.toDomain() = SitePhoto(
        id,
        projectId,
        objectCode,
        filePath,
        thumbnailPath,
        latitude,
        longitude,
        locationAccuracyM,
        isGpsMocked,
        locationStatus,
        engineer,
        capturedAtEpochMs
    )
}

class DailyLogRepositoryImpl @Inject constructor(
    private val dao: DailyLogDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(log.projectId).upsert(log.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add daily log", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list daily logs", it)) }
    ) }

    private fun DailyLog.toEntity() = DailyLogEntity(id, projectId, workItem, manpower, note, createdAtEpochMs, weather, temperature, nodeCode, dateEpochDay, volume, unit, categoryName)
    private fun DailyLogEntity.toDomain() = DailyLog(id, projectId, workItem, manpower, note, createdAtEpochMs, weather, temperature, nodeCode, dateEpochDay, volume, unit, categoryName)

    private suspend fun dao(projectId: String): DailyLogDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.dailyLogDao() ?: dao
}

class MaterialProgressRepositoryImpl @Inject constructor(
    private val dao: MaterialProgressDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : MaterialProgressRepository {
    override suspend fun upsert(progress: MaterialProgress): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val dao = dao(progress.projectId)
        val existing = dao.findByNaturalKey(progress.projectId, progress.nodeCode, progress.materialName)
        // Preserve existing plannedQty if the incoming value is 0 (parse failed) but DB already has a valid value
        val safePlannedQty = if (progress.plannedQty <= 0f && existing != null && existing.plannedQty > 0f)
            existing.plannedQty else progress.plannedQty
        dao.upsert(progress.copy(id = existing?.id ?: progress.id, plannedQty = safePlannedQty).toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert material progress", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<MaterialProgress>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list material progress", it)) }
    ) }

    private fun MaterialProgress.toEntity() = MaterialProgressEntity(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        materialName = materialName,
        plannedQty = plannedQty,
        actualQty = actualQty,
        updatedAtEpochMs = updatedAtEpochMs
    )

    private fun MaterialProgressEntity.toDomain() = MaterialProgress(
        id = id,
        projectId = projectId,
        nodeCode = nodeCode,
        materialName = materialName,
        plannedQty = plannedQty,
        actualQty = actualQty,
        updatedAtEpochMs = updatedAtEpochMs
    )

    private suspend fun dao(projectId: String): MaterialProgressDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.materialProgressDao() ?: dao
}
