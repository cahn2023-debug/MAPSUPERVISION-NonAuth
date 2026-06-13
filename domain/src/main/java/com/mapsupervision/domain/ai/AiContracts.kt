package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.TaskStatus

enum class AiCapability {
    CHAT_ASSISTANT,
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
    LOCAL_MODEL,
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

enum class GemmaModelFamily {
    QWEN3_0_6B,
    E2B,
    E4B
}

enum class GemmaModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    UNSUPPORTED,
    LOAD_FAILED
}

data class GemmaModelInfo(
    val family: GemmaModelFamily,
    val displayName: String,
    val estimatedSizeMb: Long,
    val recommendedMinAvailableRamMb: Long,
    val recommendedMinFreeStorageMb: Long,
    val downloadFileName: String,
    val expectedBytes: Long,
    val url: String? = null
)

data class GemmaDeviceSnapshot(
    val availableRamMb: Long,
    val freeStorageMb: Long,
    val batteryLevel: Int,
    val thermalStatus: ThermalStatus,
    val isCharging: Boolean
)

data class GemmaModelSelection(
    val supported: Boolean,
    val selected: GemmaModelInfo?,
    val reason: String,
    val candidates: List<GemmaModelInfo> = emptyList()
)

data class ChatAssistantPayload(
    val projectId: String?,
    val currentTab: String,
    val message: String,
    val contextSummary: String = "",
    val normalizationContext: String = "",
    val selectedNodeCode: String? = null,
    val selectedRouteCode: String? = null
) : AiPayload {
    override val capability: AiCapability = AiCapability.CHAT_ASSISTANT
}

enum class ChatActionType {
    UPDATE_CONSTRUCTION_PROGRESS,
    ADD_DAILY_LOG,
    UPDATE_SITE_PHOTO,
    SAVE_REPORT_DRAFT,
    ADD_NOTE,
    ADD_TASK,
    UPDATE_MATERIAL_OR_VOLUME_PROGRESS
}

enum class WriteDisposition {
    AUTO_SAVE,
    REQUIRE_CONFIRMATION,
    REJECT
}

data class ChatConfidenceScore(
    val intentConfidence: Int,
    val locationConfidence: Int,
    val categoryConfidence: Int,
    val overallConfidence: Int,
    val isDataComplete: Boolean
)

data class ConstructionProgressDraft(
    val nodeCode: String,
    val planned: Float,
    val actual: Float
)

data class MaterialOrVolumeProgressDraft(
    val nodeCode: String,
    val materialName: String,
    val actualQty: Float,
    val plannedQty: Float? = null,
    val unit: String = ""
)

data class DailyLogDraft(
    val workItem: String,
    val manpower: Int,
    val note: String,
    val weather: String = "",
    val temperature: Double = 0.0,
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val dateEpochDay: Long = 0L,
    val volume: Double = 0.0,
    val unit: String = "",
    val categoryName: String = ""
)

data class SitePhotoUpdateDraft(
    val photoId: String,
    val tagCodesCsv: String = "",
    val matchedNodeCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class ReportDraftDbSaveDraft(
    val projectId: String,
    val title: String,
    val executiveSummary: String,
    val riskSection: String,
    val recommendedActions: List<String>
)

data class NoteDraft(
    val objectCode: String,
    val content: String
)

data class TaskDraft(
    val objectCode: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO
)

data class ChatPendingAction(
    val type: ChatActionType,
    val title: String,
    val draftJson: String,
    val constructionProgress: ConstructionProgressDraft? = null,
    val dailyLog: DailyLogDraft? = null,
    val sitePhotoUpdate: SitePhotoUpdateDraft? = null,
    val reportDraftSave: ReportDraftDbSaveDraft? = null,
    val noteDraft: NoteDraft? = null,
    val taskDraft: TaskDraft? = null,
    val materialOrVolumeProgress: MaterialOrVolumeProgressDraft? = null
)

data class ChatAssistantResult(
    val answer: String,
    val suggestedAction: String? = null,
    val draftJson: String? = null,
    val pendingAction: ChatPendingAction? = null,
    val confidence: ChatConfidenceScore? = null,
    val missingFields: List<String> = emptyList(),
    val resolvedEntities: Map<String, String> = emptyMap(),
    val writeDisposition: WriteDisposition = WriteDisposition.REJECT
) : AiResult

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
