package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.WorkCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalAiOptimizationsTest {

    private val sampleNodes = listOf(
        GisNode(id = "1", projectId = "P1", code = "T1-N01", mapNumberLabel = "Trụ T1 Nút 1", contractor = "Song Da", latitude = 10.0, longitude = 106.0),
        GisNode(id = "2", projectId = "P1", code = "T2-N02", mapNumberLabel = "Trụ T2 Nút 2", contractor = "Song Da", latitude = 10.1, longitude = 106.1),
        GisNode(id = "3", projectId = "P1", code = "A-GA01", mapNumberLabel = "Hố ga A01", contractor = "Cienco4", latitude = 10.2, longitude = 106.2)
    )

    private val sampleCategories = listOf(
        WorkCategory(id = "c1", projectId = "P1", name = "Bê tông móng", unit = "m3", createdAtEpochMs = 123456L),
        WorkCategory(id = "c2", projectId = "P1", name = "Kéo cáp quang", unit = "m", createdAtEpochMs = 123456L),
        WorkCategory(id = "c3", projectId = "P1", name = "Đào đất hố móng", unit = "m3", createdAtEpochMs = 123456L)
    )

    @Test
    fun `levenshtein distance calculates correctly`() {
        assertEquals(0, PostProcessorMapping.levenshteinDistance("bê tông", "bê tông"))
        assertEquals(1, PostProcessorMapping.levenshteinDistance("ga A01", "ga A02"))
        assertEquals(2, PostProcessorMapping.levenshteinDistance("ga A01", "ga B02"))
    }

    @Test
    fun `finds closest node with fuzzy matching`() {
        // Exact match
        val node1 = PostProcessorMapping.findClosestNode("T1-N01", sampleNodes)
        assertNotNull(node1)
        assertEquals("T1-N01", node1?.code)

        // Fuzzy match with minor spelling issue
        val node2 = PostProcessorMapping.findClosestNode("T2-N03", sampleNodes) // should match closest T2-N02
        assertNotNull(node2)
        assertEquals("T2-N02", node2?.code)
        
        // Label match
        val node3 = PostProcessorMapping.findClosestNode("Hố ga A01", sampleNodes)
        assertNotNull(node3)
        assertEquals("A-GA01", node3?.code)
    }

    @Test
    fun `finds closest category with fuzzy matching`() {
        // Partial match
        val cat1 = PostProcessorMapping.findClosestCategory("bê tông", sampleCategories)
        assertNotNull(cat1)
        assertEquals("Bê tông móng", cat1?.name)

        // Spelling error match
        val cat2 = PostProcessorMapping.findClosestCategory("kéo cáp", sampleCategories)
        assertNotNull(cat2)
        assertEquals("Kéo cáp quang", cat2?.name)
    }

    @Test
    fun `RAG retrieves only relevant categories`() {
        val result = LocalRAGEngine.retrieveRelevantCategories("Hôm nay đi kéo cáp", sampleCategories)
        assertEquals(1, result.size)
        assertEquals("Kéo cáp quang", result.first().name)
    }

    @Test
    fun `RAG retrieves only relevant nodes`() {
        val result = LocalRAGEngine.retrieveRelevantNodes("Có sự cố tại hố ga A01", sampleNodes)
        assertEquals(1, result.size)
        assertEquals("A-GA01", result.first().code)
    }
}
