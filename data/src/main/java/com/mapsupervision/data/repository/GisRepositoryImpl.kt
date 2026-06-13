package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import javax.inject.Inject
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class GisRepositoryImpl @Inject constructor(
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val sharedDatabase: MapSupervisionDatabase,
    private val activeProjectRepository: ActiveProjectRepository
) : GisRepository {
    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        nodeDao(node.projectId).upsert(node.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert node", it)) }
    ) }

    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        routeDao(route.projectId).upsert(route.toEntity())
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to upsert route", it)) }
    ) }

    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        if (nodes.isNotEmpty()) nodeDao(nodes.first().projectId).upsertAll(nodes.map { it.toEntity() })
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to bulk upsert nodes", it)) }
    ) }

    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = withContext(Dispatchers.IO) { runCatching {
        if (routes.isNotEmpty()) routeDao(routes.first().projectId).upsertAll(routes.map { it.toEntity() })
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to bulk upsert routes", it)) }
    ) }

    override suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit> =
        withContext(Dispatchers.IO) { runCatching {
            val projectId = nodes.firstOrNull()?.projectId
                ?: routes.firstOrNull()?.projectId
                ?: (activeProjectRepository.getActive() as? AppResult.Success)?.data
                ?: throw IllegalStateException("Active project is required to replace imported geometry")

            val normalizedNodes = nodes.map { it.copy(projectId = projectId, importedFileId = importedFileId) }
            val normalizedRoutes = routes.map { it.copy(projectId = projectId, importedFileId = importedFileId) }
            val db = databaseFor(projectId)
            db.withTransaction {
                db.gisNodeDao().deleteByImportedFileId(projectId, importedFileId)
                db.gisRouteDao().deleteByImportedFileId(projectId, importedFileId)
                if (normalizedNodes.isNotEmpty()) db.gisNodeDao().upsertAll(normalizedNodes.map { it.toEntity() })
                if (normalizedRoutes.isNotEmpty()) db.gisRouteDao().upsertAll(normalizedRoutes.map { it.toEntity() })
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(DatabaseException("Failed to replace imported geometry", it)) }
        ) }

    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = withContext(Dispatchers.IO) { runCatching {
        val dao = nodeDao(projectId)
        val rows = if (query.isBlank()) dao.byProject(projectId) else dao.search(projectId, query)
        rows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to search nodes", it)) }
    ) }

    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = withContext(Dispatchers.IO) { runCatching {
        val dao = routeDao(projectId)
        val rows = if (query.isBlank()) dao.byProject(projectId) else dao.search(projectId, query)
        rows.map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to search routes", it)) }
    ) }

    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = withContext(Dispatchers.IO) { runCatching {
        val entity = nodeDao(projectId).findByCode(projectId, code)
        entity?.toDomain()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to find node by code", it)) }
    ) }

    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = flow {
        val dao = nodeDao(projectId)
        val source = if (query.isBlank()) dao.observeByProject(projectId) else dao.observeSearch(projectId, query)
        emitAll(source.map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = flow {
        val dao = routeDao(projectId)
        val source = if (query.isBlank()) dao.observeByProject(projectId) else dao.observeSearch(projectId, query)
        emitAll(source.map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged())
    }

    private fun GisNode.toEntity() = GisNodeEntity(id, projectId, code, contractor, latitude, longitude, mapNumberLabel, materialSummary, importedFileId)
    private fun GisRoute.toEntity() = GisRouteEntity(id, projectId, code, contractor, startNodeCode, endNodeCode, points, importedFileId, designLength)
    private fun GisNodeEntity.toDomain() = GisNode(id, projectId, code, contractor, latitude, longitude, mapNumberLabel, materialSummary, importedFileId)
    private fun GisRouteEntity.toDomain() = GisRoute(id, projectId, code, contractor, startNodeCode, endNodeCode, points, importedFileId, designLength)

    private suspend fun databaseFor(projectId: String): MapSupervisionDatabase =
        projectScopedDatabaseProvider.databaseFor(projectId) ?: sharedDatabase

    private suspend fun nodeDao(projectId: String): GisNodeDao =
        databaseFor(projectId).gisNodeDao()

    private suspend fun routeDao(projectId: String): GisRouteDao =
        databaseFor(projectId).gisRouteDao()
}
