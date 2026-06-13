package com.mapsupervision.domain.ai.engines

import android.content.Context
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

/**
 * MediaPipe LLM Engine - On-device LLM for text processing
 * Handles: TIMELINE_SUMMARY, REPORT_DRAFT, IMPORT_MAPPING, OPS_RECOMMENDATION
 * Requires: ~2GB RAM, downloaded model files
 */
class MediaPipeLlmEngine(
    private val context: Context
) : AiEngineInterface {
    override val engineType = AiEngine.MEDIAPIPE_LLM
    override val priority = 100 // Highest priority for text tasks
    
    private var isModelLoaded = false
    
    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            TIMELINE_SUMMARY,
            REPORT_DRAFT,
            IMPORT_MAPPING,
            OPS_RECOMMENDATION
        )
    }
    
    override suspend fun isAvailable(): Boolean {
        return false
    }
    
    override suspend fun execute(payload: AiPayload): AiResult {
        throw IllegalStateException("MediaPipe LLM engine is disabled in this build")
    }
    
    override fun getResourceUsageScore(): Int = 90 // High resource usage
}
