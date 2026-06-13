package com.mapsupervision.domain.ai.engines

import android.content.Context
import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.ai.AiEngine
import com.mapsupervision.domain.ai.AiEngineInterface
import com.mapsupervision.domain.ai.AiPayload
import com.mapsupervision.domain.ai.AiResult

/**
 * ML Kit Vision Engine - Google ML Kit for OCR and barcode scanning
 * Handles: OCR, QR/Barcode scanning
 * Requires: Minimal resources, Google Play Services
 */
class MlKitVisionEngine(
    private val context: Context
) : AiEngineInterface {
    override val engineType = AiEngine.MLKIT_VISION
    override val priority = 70 // Medium priority for utility tasks
    
    private var isInitialized = false
    
    // ML Kit handles new capabilities not in the original AiCapability enum
    // These will be added later: OCR_TEXT_EXTRACTION, BARCODE_SCAN
    
    override fun canHandle(capability: AiCapability): Boolean {
        return false
    }
    
    override suspend fun isAvailable(): Boolean {
        // Check if Google Play Services is available
        if (!isInitialized) {
            isInitialized = checkGooglePlayServices()
        }
        return isInitialized
    }
    
    override suspend fun execute(payload: AiPayload): AiResult {
        if (!isInitialized) {
            throw IllegalStateException("ML Kit not initialized")
        }

        throw IllegalStateException("ML Kit Vision engine is not wired to an AiPayload in this build")
    }
    
    override fun getResourceUsageScore(): Int = 30 // Low resource usage
    
    private fun checkGooglePlayServices(): Boolean {
        return true
    }
    
    fun initialize() {
        this.isInitialized = true
    }
}
