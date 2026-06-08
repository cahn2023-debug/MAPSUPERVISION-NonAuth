package com.mapsupervision.domain.ai.engines

import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.ai.AiCapability.DISCREPANCY_CHECK
import com.mapsupervision.domain.ai.AiCapability.IMPORT_MAPPING
import com.mapsupervision.domain.ai.AiCapability.OPS_RECOMMENDATION
import com.mapsupervision.domain.ai.AiCapability.REPORT_DRAFT
import com.mapsupervision.domain.ai.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.domain.ai.AiEngine
import com.mapsupervision.domain.ai.AiEngineInterface
import com.mapsupervision.domain.ai.AiPayload
import com.mapsupervision.domain.ai.AiResult
import com.mapsupervision.domain.repository.AiRepository

/**
 * Cloud Gemini Engine - Google Gemini API for cloud-based AI
 * Handles: All text-based capabilities as fallback
 * Requires: Internet connection, API key
 */
class CloudGeminiEngine(
    private val aiRepository: AiRepository
) : AiEngineInterface {
    override val engineType = AiEngine.CLOUD_GEMINI
    override val priority = 50 // Medium priority, fallback for text tasks
    
    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            TIMELINE_SUMMARY,
            REPORT_DRAFT,
            IMPORT_MAPPING,
            OPS_RECOMMENDATION,
            DISCREPANCY_CHECK
        )
    }
    
    override suspend fun isAvailable(): Boolean = true
    
    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload.capability) {
            IMPORT_MAPPING -> aiRepository.suggestMapping(payload as com.mapsupervision.domain.ai.ImportMappingPayload)
            DISCREPANCY_CHECK -> aiRepository.detectDiscrepancies(payload as com.mapsupervision.domain.ai.DiscrepancyCheckPayload)
            TIMELINE_SUMMARY -> aiRepository.summarizeDaily(payload as com.mapsupervision.domain.ai.TimelineSummaryPayload)
            REPORT_DRAFT -> aiRepository.reportDraft(payload as com.mapsupervision.domain.ai.ReportDraftPayload)
            OPS_RECOMMENDATION -> aiRepository.operationRecommendations(payload as com.mapsupervision.domain.ai.OpsRecommendationPayload)
            else -> throw IllegalArgumentException("Unsupported capability: ${payload.capability}")
        }
    }
    
    override fun getResourceUsageScore(): Int = 40 // Low local resource usage, but requires network
    
}
