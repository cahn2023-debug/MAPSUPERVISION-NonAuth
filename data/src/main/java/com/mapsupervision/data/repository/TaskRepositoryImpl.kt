package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : TaskRepository {
    override suspend fun upsert(task: Task): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val database = projectScopedDatabaseProvider.databaseFor(task.projectId)
        val resolvedNodeId = task.objectNodeId ?: database?.gisNodeDao()?.byProject(task.projectId)?.find { it.code == task.objectCode }?.id
        val resolvedRouteId = task.objectRouteId ?: if (resolvedNodeId == null) database?.gisRouteDao()?.byProject(task.projectId)?.find { it.code == task.objectCode }?.id else null

        val normalized = task.copy(
            objectNodeId = resolvedNodeId,
            objectRouteId = resolvedRouteId,
            updatedAtEpochMs = if (task.updatedAtEpochMs == 0L) System.currentTimeMillis() else task.updatedAtEpochMs
        )
        dao(task.projectId).upsert(TaskEntity.fromDomain(normalized))
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert task", it)) }
    ) }

    override suspend fun delete(taskId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val scopedDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
        val now = System.currentTimeMillis()
        scopedDao.markDeletedById(taskId, now, now)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete task", it)) }
    ) }

    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        hydrateTasks(projectId, dao(projectId).byObject(projectId, objectCode))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by object", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        hydrateTasks(projectId, dao(projectId).byProject(projectId))
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by project", it)) }
    ) }

    private suspend fun hydrateTasks(projectId: String, entities: List<TaskEntity>): List<Task> {
        if (entities.isEmpty()) return emptyList()
        val database = projectScopedDatabaseProvider.databaseFor(projectId)
        val nodeDao = database?.gisNodeDao()
        val routeDao = database?.gisRouteDao()
        val nodeCodeMap = nodeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        val routeCodeMap = routeDao?.byProject(projectId)?.associate { it.id to it.code }.orEmpty()
        return entities.map { entity ->
            val resolvedCode = entity.objectNodeId?.let { nodeCodeMap[it] }
                ?: entity.objectRouteId?.let { routeCodeMap[it] }
                ?: ""
            entity.toDomain().copy(objectCode = resolvedCode)
        }
    }

    private suspend fun dao(projectId: String): TaskDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.taskDao() ?: dao
}
