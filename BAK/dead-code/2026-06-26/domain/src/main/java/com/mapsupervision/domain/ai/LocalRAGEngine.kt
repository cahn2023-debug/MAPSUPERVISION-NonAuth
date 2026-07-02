package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkCategory

object LocalRAGEngine {

    // Tối ưu 1: Compile Regex một lần duy nhất
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    // Tối ưu 2: Tách logic xử lý câu truy vấn
    private fun extractQueryWords(userMessage: String): List<String> {
        return PostProcessorMapping.normalizeText(userMessage)
            .split(WHITESPACE_REGEX)
            .filter { it.length > 2 }
    }

    // Tối ưu 3: Gom nhóm toàn bộ logic RAG chung vào một hàm Generic an toàn, dùng `asSequence` và trích xuất danh sách trường để tránh nối chuỗi
    private inline fun <T> retrieveRelevantEntities(
        queryWords: List<String>,
        items: List<T>,
        limit: Int,
        crossinline fieldsExtractor: (T) -> List<String?>
    ): List<T> {
        if (queryWords.isEmpty()) return items.take(limit)

        return items.asSequence()
            .map { item ->
                // Trích xuất các trường dạng danh sách, loại bỏ null/empty và chuẩn hóa
                val normalizedFields = fieldsExtractor(item).mapNotNull { field ->
                    if (!field.isNullOrBlank()) {
                        PostProcessorMapping.normalizeText(field)
                    } else {
                        null
                    }
                }
                // Tính điểm dựa trên số từ khoá khớp với bất kỳ trường nào đã chuẩn hoá
                val score = queryWords.count { word ->
                    normalizedFields.any { it.contains(word) }
                }
                item to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
            .ifEmpty { items.take(limit) }
    }

    /* Các hàm truy xuất nội bộ nhận queryWords (Dùng để tránh việc parse lại string nhiều lần) */

    private fun retrieveNodesByWords(queryWords: List<String>, nodes: List<GisNode>, limit: Int): List<GisNode> =
        retrieveRelevantEntities(queryWords, nodes, limit) { listOf(it.code, it.mapNumberLabel, it.contractor) }

    private fun retrieveRoutesByWords(queryWords: List<String>, routes: List<GisRoute>, limit: Int): List<GisRoute> =
        retrieveRelevantEntities(queryWords, routes, limit) { listOf(it.code, it.startNodeCode, it.endNodeCode, it.contractor) }

    private fun retrieveCategoriesByWords(queryWords: List<String>, categories: List<WorkCategory>, limit: Int): List<WorkCategory> =
        retrieveRelevantEntities(queryWords, categories, limit) { listOf(it.name, it.unit) }

    /* Các hàm Public API (Vẫn giữ nguyên chữ ký hàm cho ứng dụng của bạn) */

    /** Retrieve the most relevant nodes based on search keywords from user message. */
    fun retrieveRelevantNodes(userMessage: String, nodes: List<GisNode>, limit: Int = 5): List<GisNode> =
        retrieveNodesByWords(extractQueryWords(userMessage), nodes, limit)

    /** Retrieve the most relevant routes based on search keywords. */
    fun retrieveRelevantRoutes(userMessage: String, routes: List<GisRoute>, limit: Int = 3): List<GisRoute> =
        retrieveRoutesByWords(extractQueryWords(userMessage), routes, limit)

    /** Retrieve the most relevant work categories based on search keywords. */
    fun retrieveRelevantCategories(userMessage: String, categories: List<WorkCategory>, limit: Int = 5): List<WorkCategory> =
        retrieveCategoriesByWords(extractQueryWords(userMessage), categories, limit)

    /** Builds a RAG context string containing only the retrieved entities. */
    fun buildRAGPromptContext(
        userMessage: String,
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        categories: List<WorkCategory>
    ): String = buildString {
        // Tối ưu 4: Parse userMessage đúng MỘT LẦN duy nhất
        val queryWords = extractQueryWords(userMessage)

        val relevantNodes = retrieveNodesByWords(queryWords, nodes, 5)
        val relevantRoutes = retrieveRoutesByWords(queryWords, routes, 3)
        val relevantCategories = retrieveCategoriesByWords(queryWords, categories, 5)

        if (relevantNodes.isNotEmpty()) {
            append("\nrelevant_nodes=").append(relevantNodes.joinToString(", ") { "${it.code}(Label:${it.mapNumberLabel})" })
        }
        if (relevantRoutes.isNotEmpty()) {
            append("\nrelevant_routes=").append(relevantRoutes.joinToString(", ") { it.code })
        }
        if (relevantCategories.isNotEmpty()) {
            append("\nrelevant_categories=").append(relevantCategories.joinToString(", ") { "${it.name}:${it.unit}" })
        }
    }
}
