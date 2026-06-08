package com.mapsupervision.data.repository

import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity
import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.repository.AiDecisionCacheStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiDecisionCacheStoreImpl @Inject constructor(
    private val sharedDao: AiDecisionCacheDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : AiDecisionCacheStore {
    override suspend fun get(projectId: String, capability: AiCapability, payloadHash: String): String? =
        withContext(Dispatchers.IO) {
            activeDao(projectId).find(projectId, capability.name, payloadHash)?.resultJson
        }

    override suspend fun put(projectId: String, capability: AiCapability, payloadHash: String, resultJson: String) {
        withContext(Dispatchers.IO) {
            activeDao(projectId).upsert(
                AiDecisionCacheEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    capability = capability.name,
                    payloadHash = payloadHash,
                    resultJson = resultJson,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun activeDao(projectId: String): AiDecisionCacheDao {
        return projectScopedDatabaseProvider.databaseFor(projectId)?.aiDecisionCacheDao() ?: sharedDao
    }
}
