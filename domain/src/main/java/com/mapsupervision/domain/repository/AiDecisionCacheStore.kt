package com.mapsupervision.domain.repository

import com.mapsupervision.domain.ai.AiCapability

interface AiDecisionCacheStore {
    suspend fun get(projectId: String, capability: AiCapability, payloadHash: String): String?
    suspend fun put(projectId: String, capability: AiCapability, payloadHash: String, resultJson: String)
}
