package com.mapsupervision.domain.ai

import com.mapsupervision.domain.repository.AiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOrchestratorTest {

    @Test
    fun `uses model when repository succeeds`() {
        val orchestrator = AiOrchestrator(FakeAiRepository(shouldFail = false))
        val payload = ImportMappingPayload(headers = listOf("node", "lat", "lon"), sampleRows = emptyList())
        val decision = runBlocking { orchestrator.execute<ImportMappingResult>(payload) }
        assertEquals(AiDecisionSource.MODEL, decision.source)
        assertEquals("node", decision.result.nodeCodeColumn)
    }

    @Test
    fun `uses fallback when repository fails`() {
        val orchestrator = AiOrchestrator(FakeAiRepository(shouldFail = true))
        val payload = ImportMappingPayload(headers = listOf("node_code", "lat", "lon"), sampleRows = emptyList())
        val decision = runBlocking { orchestrator.execute<ImportMappingResult>(payload) }
        assertEquals(AiDecisionSource.RULE_BASED, decision.source)
        assertTrue(decision.result.nodeCodeColumn.isNotBlank())
    }

    @Test
    fun `uses disabled source when feature flag off`() {
        val orchestrator = AiOrchestrator(FakeAiRepository(shouldFail = false))
        orchestrator.setFeatureEnabled(AiCapability.IMPORT_MAPPING, false)
        val payload = ImportMappingPayload(headers = listOf("node_code"), sampleRows = emptyList())
        val decision = runBlocking { orchestrator.execute<ImportMappingResult>(payload) }
        assertEquals(AiDecisionSource.DISABLED, decision.source)
    }

    @Test
    fun `uses cache on subsequent calls with identical payload`() {
        val fakeRepo = FakeAiRepository(shouldFail = false)
        val orchestrator = AiOrchestrator(fakeRepo)
        val payload = ImportMappingPayload(headers = listOf("node", "lat", "lon"), sampleRows = emptyList())
        
        // Cuộc gọi đầu tiên thực thi qua Model bình thường
        val decision1 = runBlocking { orchestrator.execute<ImportMappingResult>(payload) }
        assertEquals(AiDecisionSource.MODEL, decision1.source)
        
        // Cuộc gọi thứ hai với payload giống hệt sẽ chạm cache (cache_hit)
        val decision2 = runBlocking { orchestrator.execute<ImportMappingResult>(payload) }
        assertEquals(AiDecisionSource.RULE_BASED, decision2.source)
        assertEquals("cache_hit", decision2.reason)
    }

    @Test
    fun `ImportMappingHelper suggestions with accented Vietnamese headers`() {
        val headers = listOf("Mã trạm kỹ thuật", "Vĩ độ GPS", "Kinh độ GPS", "Nhà thầu chính", "Cáp quang lắp ráp", "Đào đất móng")
        val result = com.mapsupervision.domain.ai.engines.ImportMappingHelper.suggestMapping(headers)
        
        assertEquals("Mã trạm kỹ thuật", result.nodeCodeColumn)
        assertEquals("Vĩ độ GPS", result.latitudeColumn)
        assertEquals("Kinh độ GPS", result.longitudeColumn)
        assertEquals("Nhà thầu chính", result.contractorColumn)
        assertTrue(result.itemColumns.contains("Cáp quang lắp ráp"))
        assertTrue(result.itemColumns.contains("Đào đất móng"))
        assertEquals(false, result.requiresManualReview)
    }

    @Test
    fun `ImportMappingHelper normalization removes diacritics`() {
        val normalized = com.mapsupervision.domain.ai.engines.ImportMappingHelper.normalize("Nhà Thầu Kéo Cáp vĩ độ")
        assertEquals("nha thau keo cap vi do", normalized)
    }
}


private class FakeAiRepository(private val shouldFail: Boolean) : AiRepository {
    override suspend fun suggestMapping(payload: ImportMappingPayload): ImportMappingResult {
        if (shouldFail) error("boom")
        return ImportMappingResult("node", "lat", "lon", "contractor", listOf("item"), false)
    }

    override suspend fun detectDiscrepancies(payload: DiscrepancyCheckPayload): DiscrepancyResult {
        if (shouldFail) error("boom")
        return DiscrepancyResult(emptyList(), emptyList())
    }

    override suspend fun summarizeDaily(payload: TimelineSummaryPayload): TimelineSummaryResult {
        if (shouldFail) error("boom")
        return TimelineSummaryResult("ok", emptyList(), emptyList())
    }

    override suspend fun photoQualityCheck(payload: PhotoQualityPayload): PhotoQualityResult {
        if (shouldFail) error("boom")
        return PhotoQualityResult(100, emptyList(), "ok", false)
    }

    override suspend fun reportDraft(payload: ReportDraftPayload): ReportDraftResult {
        if (shouldFail) error("boom")
        return ReportDraftResult("ok", "ok", emptyList())
    }

    override suspend fun operationRecommendations(payload: OpsRecommendationPayload): OpsRecommendationResult {
        if (shouldFail) error("boom")
        return OpsRecommendationResult(emptyList(), 1)
    }
}
