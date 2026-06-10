package com.mapsupervision.domain.ai.engines

import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.ai.AiCapability.CHAT_ASSISTANT
import com.mapsupervision.domain.ai.AiCapability.REPORT_DRAFT
import com.mapsupervision.domain.ai.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.domain.ai.AiEngine
import com.mapsupervision.domain.ai.AiEngineInterface
import com.mapsupervision.domain.ai.AiPayload
import com.mapsupervision.domain.ai.AiResult
import com.mapsupervision.domain.ai.ChatAssistantPayload
import com.mapsupervision.domain.ai.ChatAssistantResult
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.ReportDraftPayload
import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.ai.TimelineSummaryPayload
import com.mapsupervision.domain.ai.TimelineSummaryResult
import com.mapsupervision.domain.repository.LocalLlmRepository
import com.mapsupervision.domain.repository.LocalLlmRequest

class LocalLiteRtEngine(
    private val localLlmRepository: LocalLlmRepository
) : AiEngineInterface {
    override val engineType: AiEngine = AiEngine.LOCAL_LITERT
    override val priority: Int = 95

    private val ruleEngine = RuleBasedEngine()

    override fun canHandle(capability: AiCapability): Boolean {
        return capability in listOf(
            CHAT_ASSISTANT,
            TIMELINE_SUMMARY,
            REPORT_DRAFT
        )
    }

    override suspend fun isAvailable(): Boolean = localLlmRepository.isReady()

    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload) {
            is ChatAssistantPayload -> executeChat(payload)
            is TimelineSummaryPayload -> executeTimeline(payload)
            is ReportDraftPayload -> executeReport(payload)
            else -> throw IllegalArgumentException("Unsupported payload: ${payload::class.simpleName}")
        }
    }

    override fun getResourceUsageScore(): Int = 85

    private suspend fun executeChat(payload: ChatAssistantPayload): ChatAssistantResult {
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = payload.message,
                contextSummary = payload.contextSummary,
                normalizationContext = payload.normalizationContext,
                currentTab = payload.currentTab,
                selectedNodeCode = payload.selectedNodeCode,
                selectedRouteCode = payload.selectedRouteCode
            )
        )
        val responseText = response.text.trim()
        val parsed = if (responseText.contains("[ACTION:", ignoreCase = true)) {
            ChatActionParser.parseLlmResponse(responseText, payload.selectedNodeCode, payload.normalizationContext)
        } else {
            ChatActionParser.parse(payload.message, payload.contextSummary, payload.selectedNodeCode, payload.normalizationContext)
        }
        val cleanAnswer = responseText.replace(Regex("""\[ACTION:.*?\]""", RegexOption.IGNORE_CASE), "").trim()
        return parsed.copy(answer = cleanAnswer.ifBlank { parsed.answer })
    }

    private suspend fun executeTimeline(payload: TimelineSummaryPayload): TimelineSummaryResult {
        val base = ruleEngine.execute(payload) as TimelineSummaryResult
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = buildTimelinePrompt(payload, base),
                contextSummary = buildTimelineContext(payload),
                normalizationContext = "",
                currentTab = "progress"
            )
        )
        val summary = response.text.trim().ifBlank { base.summary }
        return base.copy(summary = summary)
    }

    private suspend fun executeReport(payload: ReportDraftPayload): ReportDraftResult {
        val base = ruleEngine.execute(payload) as ReportDraftResult
        val response = localLlmRepository.generate(
            LocalLlmRequest(
                prompt = buildReportPrompt(payload, base),
                contextSummary = buildReportContext(payload),
                normalizationContext = "",
                currentTab = "reports"
            )
        )
        val parsed = parseReportSections(response.text)
        return base.copy(
            executiveSummary = parsed.first.ifBlank { base.executiveSummary },
            riskSection = parsed.second.ifBlank { base.riskSection }
        )
    }

    private fun buildTimelinePrompt(
        payload: TimelineSummaryPayload,
        base: TimelineSummaryResult
    ): String {
        return buildString {
            append("Viet mot tom tat tien do ngan gon, chinh xac, bang tieng Viet, khong suy dien them so lieu.")
            append(" Toi da co issue highlights va recommended actions; ban chi viet lai phan summary.")
            append("\nTong so node: ").append(payload.progress.size)
            append("\nNode cham: ").append(payload.progress.count { it.delayed })
            append("\nSo nhat ky: ").append(payload.logs.size)
            append("\nSo anh hien truong: ").append(payload.photoCount)
            append("\nSummary hien tai: ").append(base.summary)
            if (base.issueHighlights.isNotEmpty()) {
                append("\nIssue highlights: ").append(base.issueHighlights.joinToString(" | "))
            }
        }
    }

    private fun buildTimelineContext(payload: TimelineSummaryPayload): String {
        val delayedNodes = payload.progress
            .filter { it.delayed }
            .sortedByDescending { it.planned - it.actual }
            .take(5)
            .joinToString(separator = ", ") { "${it.nodeCode}:${it.actual.toInt()}%/${it.planned.toInt()}%" }
        val latestLogs = payload.logs
            .sortedByDescending { it.createdAtEpochMs }
            .take(3)
            .joinToString(separator = " | ") { "${it.workItem}:${it.note.take(60)}" }
        return buildString {
            append("delayed_nodes=").append(if (delayedNodes.isBlank()) "none" else delayedNodes)
            if (latestLogs.isNotBlank()) {
                append("\nlatest_logs=").append(latestLogs)
            }
        }
    }

    private fun buildReportPrompt(
        payload: ReportDraftPayload,
        base: ReportDraftResult
    ): String {
        return buildString {
            append("Viet lai hai phan bang tieng Viet ro rang, co cau truc, khong thay doi so lieu.")
            append("\nTra ve dung dinh dang:")
            append("\nEXECUTIVE_SUMMARY: ...")
            append("\nRISK_SECTION: ...")
            append("\nProject: ").append(payload.projectId)
            append("\nTotal nodes: ").append(payload.totalNodes)
            append("\nDelayed nodes: ").append(payload.delayedNodes)
            append("\nAverage actual progress: ").append(payload.avgActualProgress)
            append("\nPhotos: ").append(payload.totalPhotos)
            append("\nCurrent executive summary: ").append(base.executiveSummary)
            append("\nCurrent risk section: ").append(base.riskSection)
        }
    }

    private fun buildReportContext(payload: ReportDraftPayload): String {
        return buildString {
            if (payload.nodesSummary.isNotBlank()) {
                append("nodes_summary=").append(payload.nodesSummary.take(900))
            }
            if (payload.contractorsSummary.isNotBlank()) {
                append("\ncontractors_summary=").append(payload.contractorsSummary.take(900))
            }
            if (payload.notesSummary.isNotBlank()) {
                append("\nnotes_summary=").append(payload.notesSummary.take(900))
            }
        }
    }

    private fun parseReportSections(text: String): Pair<String, String> {
        val executive = text.substringAfter("EXECUTIVE_SUMMARY:", "").substringBefore("RISK_SECTION:").trim()
        val risk = text.substringAfter("RISK_SECTION:", "").trim()
        return executive to risk
    }
}
