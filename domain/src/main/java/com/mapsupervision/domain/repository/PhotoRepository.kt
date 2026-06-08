package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.SitePhoto

interface PhotoRepository {
    suspend fun add(photo: SitePhoto): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<SitePhoto>>
    suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>>
}
