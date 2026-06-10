package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkCategory

object LocalRAGEngine {

    /**
     * Retrieve the most relevant nodes based on search keywords from user message.
     */
    fun retrieveRelevantNodes(
        userMessage: String,
        nodes: List<GisNode>,
        limit: Int = 5
    ): List<GisNode> {
        val queryWords = PostProcessorMapping.normalizeText(userMessage).split("\\s+".toRegex()).filter { it.length > 2 }
        if (queryWords.isEmpty()) return nodes.take(limit)

        return nodes.map { node ->
            val nodeText = PostProcessorMapping.normalizeText("${node.code} ${node.mapNumberLabel} ${node.contractor}")
            val score = queryWords.count { word -> nodeText.contains(word) }
            node to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .ifEmpty { nodes.take(limit) }
    }

    /**
     * Retrieve the most relevant routes based on search keywords.
     */
    fun retrieveRelevantRoutes(
        userMessage: String,
        routes: List<GisRoute>,
        limit: Int = 3
    ): List<GisRoute> {
        val queryWords = PostProcessorMapping.normalizeText(userMessage).split("\\s+".toRegex()).filter { it.length > 2 }
        if (queryWords.isEmpty()) return routes.take(limit)

        return routes.map { route ->
            val routeText = PostProcessorMapping.normalizeText("${route.code} ${route.startNodeCode} ${route.endNodeCode} ${route.contractor}")
            val score = queryWords.count { word -> routeText.contains(word) }
            route to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .ifEmpty { routes.take(limit) }
    }

    /**
     * Retrieve the most relevant work categories based on search keywords.
     */
    fun retrieveRelevantCategories(
        userMessage: String,
        categories: List<WorkCategory>,
        limit: Int = 5
    ): List<WorkCategory> {
        val queryWords = PostProcessorMapping.normalizeText(userMessage).split("\\s+".toRegex()).filter { it.length > 2 }
        if (queryWords.isEmpty()) return categories.take(limit)

        return categories.map { category ->
            val categoryText = PostProcessorMapping.normalizeText("${category.name} ${category.unit}")
            val score = queryWords.count { word -> categoryText.contains(word) }
            category to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .ifEmpty { categories.take(limit) }
    }

    /**
     * Builds a RAG context string containing only the retrieved entities.
     */
    fun buildRAGPromptContext(
        userMessage: String,
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        categories: List<WorkCategory>
    ): String = buildString {
        val relevantNodes = retrieveRelevantNodes(userMessage, nodes)
        val relevantRoutes = retrieveRelevantRoutes(userMessage, routes)
        val relevantCategories = retrieveRelevantCategories(userMessage, categories)

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
