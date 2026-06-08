package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.MaterialProgress

interface MaterialProgressRepository {
    suspend fun upsert(progress: MaterialProgress): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<MaterialProgress>>
}
