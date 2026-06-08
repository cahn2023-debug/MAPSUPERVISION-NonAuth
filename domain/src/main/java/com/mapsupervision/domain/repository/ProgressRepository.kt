package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.NodeProgress

interface ProgressRepository {
    suspend fun upsert(progress: NodeProgress): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<NodeProgress>>
}
