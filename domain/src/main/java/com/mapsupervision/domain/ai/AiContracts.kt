package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.NodeProgress

enum class AiCapability {
    IMPORT_MAPPING,
    DISCREPANCY_CHECK,
    TIMELINE_SUMMARY,
    PHOTO_QUALITY_CHECK,
    REPORT_DRAFT,
    OPS_RECOMMENDATION,
    NOTE_SUMMARIZATION,
    TASK_RECOMMENDATION
}

enum class AiDecisionSource {
    MODEL,
    FALLBACK,
    DISABLED,
    MEDIAPIPE_LLM,
    TFLITE_VISION,
    MLKIT_VISION,
    RULE_BASED
}

interface AiPayload {
    val capability: AiCapability
}

interface AiResult

data class AiDecision<T : AiResult>(
    val capability: AiCapability,
    val result: T,
    val confidence: Int,
    val source: AiDecisionSource,
    val reason: String,
    val warnings: List<String> = emptyList(),
    val latencyMs: Long = 0L,
    val estimatedTokens: Int? = null,
    val progress: AiProgress? = null
)

/**
 * Progress information for long-running AI tasks
 */
data class AiProgress(
    val currentStep: String,
    val totalSteps: Int,
    val currentStepIndex: Int,
    val percentage: Float,
    val message: String
) {
    companion object {
        fun starting(message: String = "Đang khởi tạo..."): AiProgress {
            return AiProgress(
                currentStep = "initialization",
                totalSteps = 5,
                currentStepIndex = 0,
                percentage = 0f,
                message = message
            )
        }
        
        fun processing(step: String, index: Int, total: Int, message: String): AiProgress {
            return AiProgress(
                currentStep = step,
                totalSteps = total,
                currentStepIndex = index,
                percentage = (index.toFloat() / total) * 100f,
                message = message
            )
        }
        
        fun complete(): AiProgress {
            return AiProgress(
                currentStep = "complete",
                totalSteps = 5,
                currentStepIndex = 5,
                percentage = 100f,
                message = "Hoàn tất"
            )
        }
    }
}

data class ImportMappingPayload(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val fileType: String? = null
) : AiPayload {
    override val capability: AiCapability = AiCapability.IMPORT_MAPPING
}

data class ImportMappingResult(
    val nodeCodeColumn: String,
    val latitudeColumn: String,
    val longitudeColumn: String,
    val contractorColumn: String,
    val itemColumns: List<String>,
    val requiresManualReview: Boolean
) : AiResult

data class DiscrepancyCheckPayload(
    val projectId: String,
    val rows: List<DiscrepancyInputRow>
) : AiPayload {
    override val capability: AiCapability = AiCapability.DISCREPANCY_CHECK
}

data class DiscrepancyInputRow(
    val code: String,
    val incomingContractor: String,
    val existingContractor: String,
    val distanceMeters: Double
)

data class DiscrepancyResult(
    val issues: List<String>,
    val recommendedActions: List<String>
) : AiResult

data class TimelineSummaryPayload(
    val progress: List<NodeProgress>,
    val logs: List<DailyLog>,
    val photoCount: Int
) : AiPayload {
    override val capability: AiCapability = AiCapability.TIMELINE_SUMMARY
}

data class TimelineSummaryResult(
    val summary: String,
    val issueHighlights: List<String>,
    val recommendedActions: List<String>
) : AiResult

/**
 * Dữ liệu yêu cầu đánh giá chất lượng ảnh hiện trường (PhotoQualityPayload).
 * 
 * @property objectCode Mã ký hiệu đối tượng nút hạ tầng tương ứng.
 * @property engineer Tên kỹ sư giám sát đã thực hiện chụp ảnh.
 * @property latitude Vĩ độ GPS lúc thực hiện chụp (nullable).
 * @property longitude Kinh độ GPS lúc thực hiện chụp (nullable).
 * @property filePath Đường dẫn lưu trữ tệp tin ảnh vật lý trên máy di động để chạy kiểm định mờ/chất lượng offline (mặc định = null).
 */
data class PhotoQualityPayload(
    val objectCode: String,
    val engineer: String,
    val latitude: Double?,
    val longitude: Double?,
    val filePath: String? = null
) : AiPayload {
    override val capability: AiCapability = AiCapability.PHOTO_QUALITY_CHECK
}


data class PhotoQualityResult(
    val score: Int,
    val issues: List<String>,
    val recommendation: String,
    val shouldRetake: Boolean
) : AiResult

data class ReportDraftPayload(
    val projectId: String,
    val totalNodes: Int,
    val delayedNodes: Int,
    val avgActualProgress: Float,
    val totalPhotos: Int,
    val nodesSummary: String = "",
    val contractorsSummary: String = "",
    val notesSummary: String = "",
    val inProgressNodes: Int = 0,
    val photoNodesSummary: String = ""
) : AiPayload {
    override val capability: AiCapability = AiCapability.REPORT_DRAFT
}

data class ReportDraftResult(
    val executiveSummary: String,
    val riskSection: String,
    val recommendedActions: List<String>
) : AiResult

data class OpsRecommendationPayload(
    val totalNodes: Int,
    val delayedNodes: Int,
    val completionPercent: Float,
    val importWarnings: Int
) : AiPayload {
    override val capability: AiCapability = AiCapability.OPS_RECOMMENDATION
}

data class OpsRecommendationResult(
    val prioritizedActions: List<String>,
    val priority: Int
) : AiResult

data class NoteSummarizationPayload(
    val objectCode: String,
    val notes: List<String>
) : AiPayload {
    override val capability: AiCapability = AiCapability.NOTE_SUMMARIZATION
}

data class NoteSummarizationResult(
    val summary: String
) : AiResult

data class TaskRecommendationPayload(
    val objectCode: String,
    val notes: List<String>,
    val existingTasks: List<String>
) : AiPayload {
    override val capability: AiCapability = AiCapability.TASK_RECOMMENDATION
}

data class TaskRecommendationResult(
    val suggestedTasks: List<String>
) : AiResult
