package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: com.mapsupervision.domain.repository.ActiveProjectRepository
) : NoteRepository {
    override suspend fun add(note: Note): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        dao(note.projectId).insert(NoteEntity.fromDomain(note))
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to add note", it)) }
    ) }

    override suspend fun delete(noteId: String): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        val activeProjectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
        val scopedDao = if (activeProjectId.isNullOrBlank()) dao else dao(activeProjectId)
        scopedDao.deleteById(noteId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to delete note", it)) }
    ) }

    override suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Note>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byObject(projectId, objectCode).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list notes by object", it)) }
    ) }

    override suspend fun byProject(projectId: String): AppResult<List<Note>> = withContext(Dispatchers.IO) { runCatching {
        dao(projectId).byProject(projectId).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to list notes by project", it)) }
    ) }

    private suspend fun dao(projectId: String): NoteDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.noteDao() ?: dao
}
