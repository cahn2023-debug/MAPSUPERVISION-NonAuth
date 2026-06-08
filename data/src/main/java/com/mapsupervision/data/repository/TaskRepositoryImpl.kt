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
        dao(task.projectId).upsert(TaskEntity.fromDomain(task))
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert task", it)) }
    ) }

    override suspend fun delete(taskId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val scopedDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
        scopedDao.deleteById(taskId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete task", it)) }
    ) }

    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byObject(projectId, objectCode).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by object", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<Task>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list tasks by project", it)) }
    ) }

    private suspend fun dao(projectId: String): TaskDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.taskDao() ?: dao
}
