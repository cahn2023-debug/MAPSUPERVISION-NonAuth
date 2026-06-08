package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute

interface GisRepository {
    suspend fun upsertNode(node: GisNode): AppResult<Unit>
    suspend fun upsertRoute(route: GisRoute): AppResult<Unit>
    suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit>
    suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit>
    suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>>
    suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>>
    suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?>
}
