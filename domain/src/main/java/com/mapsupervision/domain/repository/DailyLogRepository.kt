package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog

interface DailyLogRepository {
    suspend fun add(log: DailyLog): AppResult<Unit>
    suspend fun byProject(projectId: String): AppResult<List<DailyLog>>
}
