package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.repository.GisRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GisRepositoryImpl @Inject constructor(
    private val nodeDao: GisNodeDao,
    private val routeDao: GisRouteDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
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

    private fun GisNode.toEntity() = GisNodeEntity(id, projectId, code, contractor, latitude, longitude, mapNumberLabel, materialSummary, importedFileId)
    private fun GisRoute.toEntity() = GisRouteEntity(id, projectId, code, contractor, startNodeCode, endNodeCode, importedFileId)
    private fun GisNodeEntity.toDomain() = GisNode(id, projectId, code, contractor, latitude, longitude, mapNumberLabel, materialSummary, importedFileId)
    private fun GisRouteEntity.toDomain() = GisRoute(id, projectId, code, contractor, startNodeCode, endNodeCode, importedFileId)

    private suspend fun nodeDao(projectId: String): GisNodeDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.gisNodeDao() ?: nodeDao

    private suspend fun routeDao(projectId: String): GisRouteDao =
        projectScopedDatabaseProvider.databaseFor(projectId)?.gisRouteDao() ?: routeDao
}
