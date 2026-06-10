package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkCategory
import java.util.Locale

data class DictionaryMatch<T>(
    val value: T,
    val confidence: Int,
    val reason: String
)

data class DictionarySnapshot(
    val projectId: String,
    val nodes: List<GisNode>,
    val routes: List<GisRoute>,
    val workCategories: List<WorkCategory>
)

class DictionaryResolverCore(private val snapshot: DictionarySnapshot) {
    private val nodeIndex = buildNodeIndex(snapshot.nodes)
    private val routeIndex = buildRouteIndex(snapshot.routes)
    private val categoryIndex = buildCategoryIndex(snapshot.workCategories)
    private val unitIndex = buildUnitIndex(snapshot.workCategories)

    // Precompute normalized aliases
    private val normalizedNodes = snapshot.nodes.map { node ->
        node to listOf(node.code, node.mapNumberLabel, node.contractor)
            .filter { it.isNotBlank() }
            .map { normalize(it) }
    }

    private val normalizedRoutes = snapshot.routes.map { route ->
        route to listOf(route.code, route.startNodeCode, route.endNodeCode, route.contractor)
            .filter { it.isNotBlank() }
            .map { normalize(it) }
    }

    private val normalizedCategories = snapshot.workCategories.map { category ->
        category to listOf(category.name, category.unit)
            .filter { it.isNotBlank() }
            .map { normalize(it) }
    }

    fun resolveNode(raw: String): DictionaryMatch<GisNode>? {
        val key = normalize(raw)
        if (key.isBlank()) return null
        nodeIndex[key]?.let { return DictionaryMatch(it, 100, "exact") }
        snapshot.nodes.firstOrNull { normalize(it.mapNumberLabel) == key }?.let { return DictionaryMatch(it, 90, "map_label") }
        snapshot.nodes.firstOrNull { normalize(it.contractor) == key }?.let { return DictionaryMatch(it, 60, "contractor") }
        snapshot.nodes.firstOrNull { normalize(it.code).contains(key) || key.contains(normalize(it.code)) }?.let {
            return DictionaryMatch(it, 70, "partial_code")
        }
        return null
    }

    fun resolveRoute(raw: String): DictionaryMatch<GisRoute>? {
        val key = normalize(raw)
        if (key.isBlank()) return null
        routeIndex[key]?.let { return DictionaryMatch(it, 100, "exact") }
        snapshot.routes.firstOrNull {
            normalize(it.startNodeCode).contains(key) ||
                normalize(it.endNodeCode).contains(key) ||
                normalize(it.contractor) == key
        }?.let { return DictionaryMatch(it, 65, "route_reference") }
        return null
    }

    fun resolveCategory(raw: String): DictionaryMatch<WorkCategory>? {
        val key = normalize(raw)
        if (key.isBlank()) return null
        categoryIndex[key]?.let { return DictionaryMatch(it, 100, "exact") }
        snapshot.workCategories.firstOrNull { normalize(it.name).contains(key) || key.contains(normalize(it.name)) }?.let {
            return DictionaryMatch(it, 80, "partial_name")
        }
        unitIndex[key]?.let { return DictionaryMatch(it, 75, "unit") }
        return null
    }

    fun buildCanonicalPromptContext(): String = buildString {
        append("project=").append(snapshot.projectId)
        if (snapshot.nodes.isNotEmpty()) append("\nnode_codes=").append(snapshot.nodes.take(30).joinToString(", ") { it.code })
        if (snapshot.routes.isNotEmpty()) append("\nroute_codes=").append(snapshot.routes.take(30).joinToString(", ") { it.code })
        if (snapshot.routes.isNotEmpty()) {
            append("\nroute_aliases=").append(
                snapshot.routes.take(30).joinToString(", ") { route ->
                    listOf(route.code, route.startNodeCode, route.endNodeCode, route.contractor)
                        .filter { it.isNotBlank() }
                        .joinToString(">")
                }
            )
        }
        if (snapshot.workCategories.isNotEmpty()) {
            append("\nwork_categories=").append(snapshot.workCategories.take(30).joinToString(", ") { "${it.name}:${it.unit}" })
        }
        if (snapshot.workCategories.any { it.unit.isNotBlank() }) {
            append("\nunits=").append(snapshot.workCategories.mapNotNull { it.unit.takeIf(String::isNotBlank) }.distinct().take(20).joinToString(", "))
        }
    }

    fun buildInputHints(message: String, selectedNodeCode: String?, selectedRouteCode: String?): String {
        val normalizedMessage = normalize(message)
        val hints = mutableListOf<String>()
        resolveMention(message, selectedNodeCode, normalizedMessage)?.let { hints += "node=${it.value.code}(${it.reason},${it.confidence})" }
        resolveRouteMention(message, selectedRouteCode, normalizedMessage)?.let { hints += "route=${it.value.code}(${it.reason},${it.confidence})" }
        resolveCategoryMention(message, normalizedMessage)?.let { hints += "category=${it.value.name}:${it.value.unit}(${it.reason},${it.confidence})" }
        return if (hints.isEmpty()) "" else hints.joinToString(prefix = "resolved_refs=", separator = ";")
    }

    fun canonicalizeMessage(message: String, selectedNodeCode: String?, selectedRouteCode: String?): String {
        val normalizedMessage = normalize(message)
        val replacements = mutableListOf<Pair<String, String>>()
        val hints = mutableListOf<String>()
        resolveMention(message, selectedNodeCode, normalizedMessage)?.let { match ->
            hints += "node=${match.value.code}"
            listOf(match.value.code, match.value.mapNumberLabel).filter { it.isNotBlank() }.forEach { alias ->
                replacements += alias to match.value.code
            }
        }
        resolveRouteMention(message, selectedRouteCode, normalizedMessage)?.let { match ->
            hints += "route=${match.value.code}"
            replacements += match.value.code to match.value.code
        }
        resolveCategoryMention(message, normalizedMessage)?.let { match ->
            hints += "category=${match.value.name}:${match.value.unit}"
            listOf(match.value.name, match.value.unit).filter { it.isNotBlank() }.forEach { alias ->
                replacements += alias to "${match.value.name}:${match.value.unit}"
            }
        }
        var canonicalMessage = message
        replacements.distinctBy { it.first.lowercase(Locale.US) }.forEach { (alias, canonical) ->
            if (alias.isBlank()) return@forEach
            canonicalMessage = canonicalMessage.replace(Regex("(?i)\\b${Regex.escape(alias)}\\b"), canonical)
        }
        return if (hints.isEmpty()) canonicalMessage else "canonical_refs=${hints.distinct().joinToString(";")}\n$canonicalMessage"
    }

    private fun resolveMention(message: String, selectedNodeCode: String?, normalizedMessage: String = normalize(message)): DictionaryMatch<GisNode>? {
        val direct = selectedNodeCode?.let { resolveNode(it) }
        if (direct != null) return direct
        return normalizedNodes.firstNotNullOfOrNull { (node, aliases) ->
            if (aliases.any { normalizedMessage.contains(it) }) DictionaryMatch(node, 80, "message_alias") else null
        }
    }

    private fun resolveRouteMention(message: String, selectedRouteCode: String?, normalizedMessage: String = normalize(message)): DictionaryMatch<GisRoute>? {
        val direct = selectedRouteCode?.let { resolveRoute(it) }
        if (direct != null) return direct
        return normalizedRoutes.firstNotNullOfOrNull { (route, aliases) ->
            if (aliases.any { normalizedMessage.contains(it) }) DictionaryMatch(route, 75, "message_alias") else null
        }
    }

    private fun resolveCategoryMention(message: String, normalizedMessage: String = normalize(message)): DictionaryMatch<WorkCategory>? {
        return normalizedCategories.firstNotNullOfOrNull { (category, aliases) ->
            if (aliases.any { normalizedMessage.contains(it) }) DictionaryMatch(category, 70, "message_alias") else null
        }
    }

    private fun buildNodeIndex(nodes: List<GisNode>): Map<String, GisNode> = nodes.flatMap { node ->
        listOf(normalize(node.code) to node, normalize(node.mapNumberLabel) to node, normalize(node.contractor) to node)
    }.filter { it.first.isNotBlank() }.toMap()

    private fun buildRouteIndex(routes: List<GisRoute>): Map<String, GisRoute> = routes.flatMap { route ->
        listOf(normalize(route.code) to route, normalize(route.startNodeCode) to route, normalize(route.endNodeCode) to route)
    }.filter { it.first.isNotBlank() }.toMap()

    private fun buildCategoryIndex(categories: List<WorkCategory>): Map<String, WorkCategory> = categories.flatMap { category ->
        listOf(normalize(category.name) to category, normalize("${category.name}:${category.unit}") to category)
    }.filter { it.first.isNotBlank() }.toMap()

    private fun buildUnitIndex(categories: List<WorkCategory>): Map<String, WorkCategory> = categories.mapNotNull { category ->
        category.unit.takeIf { it.isNotBlank() }?.let { normalize(it) to category }
    }.toMap()

    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        return CanonicalTextNormalizer.normalizeKey(value)
    }
}
