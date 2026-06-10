package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.WorkCategory
import java.text.Normalizer
import java.util.Locale

object PostProcessorMapping {

    fun normalizeText(text: String): String {
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .lowercase(Locale.US)
            .trim()
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    /**
     * Finds the closest match for a node code using fuzzy search.
     */
    fun findClosestNode(rawCode: String, nodes: List<GisNode>): GisNode? {
        if (rawCode.isBlank()) return null
        val normalizedRaw = normalizeText(rawCode)
        
        // 1. Direct exact/partial match
        nodes.firstOrNull { normalizeText(it.code) == normalizedRaw }?.let { return it }
        nodes.firstOrNull { normalizeText(it.mapNumberLabel) == normalizedRaw }?.let { return it }

        // 2. Fuzzy search using Levenshtein distance
        var bestMatch: GisNode? = null
        var minDistance = Int.MAX_VALUE
        
        for (node in nodes) {
            val normalizedNodeCode = normalizeText(node.code)
            val dist = levenshteinDistance(normalizedRaw, normalizedNodeCode)
            if (dist < minDistance && dist <= 3) { // Threshold for minor typing errors
                minDistance = dist
                bestMatch = node
            }
        }
        return bestMatch
    }

    /**
     * Finds the closest match for a work category using fuzzy search.
     */
    fun findClosestCategory(rawCategory: String, categories: List<WorkCategory>): WorkCategory? {
        if (rawCategory.isBlank()) return null
        val normalizedRaw = normalizeText(rawCategory)

        // 1. Exact match
        categories.firstOrNull { normalizeText(it.name) == normalizedRaw }?.let { return it }

        // 2. Partial/Contains match
        categories.firstOrNull { normalizeText(it.name).contains(normalizedRaw) || normalizedRaw.contains(normalizeText(it.name)) }?.let { return it }

        // 3. Fuzzy Levenshtein match
        var bestMatch: WorkCategory? = null
        var minDistance = Int.MAX_VALUE
        
        for (category in categories) {
            val normalizedName = normalizeText(category.name)
            val dist = levenshteinDistance(normalizedRaw, normalizedName)
            if (dist < minDistance && dist <= (normalizedName.length / 3).coerceAtLeast(2)) {
                minDistance = dist
                bestMatch = category
            }
        }
        return bestMatch
    }

    /**
     * Cleans up and validates raw JSON output from the AI.
     */
    fun sanitizeJson(rawJson: String): String {
        return rawJson.trim()
            .replace("```json", "")
            .replace("```", "")
            .trim()
    }
}
