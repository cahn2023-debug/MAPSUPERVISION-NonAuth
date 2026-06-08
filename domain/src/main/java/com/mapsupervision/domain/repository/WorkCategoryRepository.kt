package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.WorkCategory

interface WorkCategoryRepository {
    suspend fun add(category: WorkCategory): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<WorkCategory>>
}
