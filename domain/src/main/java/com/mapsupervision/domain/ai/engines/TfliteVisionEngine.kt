package com.mapsupervision.domain.ai.engines

import android.content.Context
import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.ai.AiCapability.DISCREPANCY_CHECK
import com.mapsupervision.domain.ai.AiCapability.PHOTO_QUALITY_CHECK
import com.mapsupervision.domain.ai.AiEngine
import com.mapsupervision.domain.ai.AiEngineInterface
import com.mapsupervision.domain.ai.AiPayload
import com.mapsupervision.domain.ai.AiResult
import com.mapsupervision.domain.ai.DiscrepancyCheckPayload
import com.mapsupervision.domain.ai.DiscrepancyResult
import com.mapsupervision.domain.ai.PhotoQualityPayload
import com.mapsupervision.domain.ai.PhotoQualityResult
import com.mapsupervision.domain.repository.TfLiteRepository

/**
 * TensorFlow Lite Vision Engine - Custom vision models
 * Handles: PHOTO_QUALITY_CHECK, DISCREPANCY_CHECK
 * Requires: ~1GB RAM, model assets in app
 */
class TfliteVisionEngine(
    private val context: Context,
    private val tfLiteRepository: TfLiteRepository
) : AiEngineInterface {
    override val engineType = AiEngine.TFLITE_VISION
    override val priority = 80 // High priority for vision tasks
    
    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            PHOTO_QUALITY_CHECK,
            DISCREPANCY_CHECK
        )
    }
    
    override suspend fun isAvailable(): Boolean {
        return tfLiteRepository.isPhotoQualityModelLoaded() || tfLiteRepository.isDiscrepancyModelLoaded()
    }
    
    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload) {
            is PhotoQualityPayload -> {
                val filePath = payload.filePath
                if (!filePath.isNullOrBlank() && tfLiteRepository.isPhotoQualityModelLoaded()) {
                    tfLiteRepository.checkPhotoQuality(filePath)
                } else {
                    // Trình dự phòng cục bộ (Rule-based Fallback) nếu thiếu đường dẫn tệp ảnh hoặc chưa tải model
                    PhotoQualityResult(
                        score = if (payload.latitude != null && payload.longitude != null) 85 else 45,
                        issues = if (payload.latitude == null || payload.longitude == null) listOf("Ảnh chưa đính kèm tọa độ GPS") else emptyList(),
                        recommendation = "Mô hình TFLite ngoại tuyến chưa nạp, đã dùng kiểm định thông tin Exif dự phòng.",
                        shouldRetake = payload.latitude == null || payload.longitude == null
                    )
                }
            }
            is DiscrepancyCheckPayload -> {
                if (tfLiteRepository.isDiscrepancyModelLoaded()) {
                    // Trích xuất các đặc trưng cơ bản đại diện từ các dòng dữ liệu đầu vào
                    val avgDistance = payload.rows.map { it.distanceMeters }.average().toFloat()
                    val contractorMatches = payload.rows.count { it.incomingContractor.equals(it.existingContractor, ignoreCase = true) }
                    val contractorMatchRate = contractorMatches.toFloat() / payload.rows.size.coerceAtLeast(1)
                    
                    val features = FloatArray(10) { 0f }
                    features[0] = avgDistance
                    features[1] = contractorMatchRate
                    
                    tfLiteRepository.checkDiscrepancy(features)
                } else {
                    // Thuật toán so khớp cục bộ dự phòng
                    val issues = payload.rows.mapNotNull { row ->
                        if (row.distanceMeters > 50.0) "Trạm ${row.code} lệch ${row.distanceMeters.toInt()}m." else null
                    }
                    DiscrepancyResult(
                        issues = issues,
                        recommendedActions = listOf("Mô hình TFLite ngoại tuyến chưa nạp, đã dùng so khớp tọa độ dự phòng.")
                    )
                }
            }
            else -> throw IllegalArgumentException("Unsupported payload: ${payload::class.simpleName}")
        }
    }
    
    override fun getResourceUsageScore(): Int = 60 // Medium resource usage
}

