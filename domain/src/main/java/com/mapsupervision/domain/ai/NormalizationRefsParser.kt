package com.mapsupervision.domain.ai

data class NormalizationRefs(
    val nodeCode: String? = null,
    val routeCode: String? = null,
    val categoryName: String? = null,
    val categoryUnit: String? = null,
    val categories: List<NormalizationCategoryRef> = emptyList(),
    val nodeCodes: List<String> = emptyList(),
    val routeCodes: List<String> = emptyList()
)

data class NormalizationCategoryRef(
    val name: String,
    val unit: String
)

object NormalizationRefsParser {
    fun parse(normalizationContext: String): NormalizationRefs {
        var nodeCode: String? = null
        var routeCode: String? = null
        var categoryName: String? = null
        var categoryUnit: String? = null
        val categories = mutableListOf<NormalizationCategoryRef>()
        val nodeCodes = mutableListOf<String>()
        val routeCodes = mutableListOf<String>()

        normalizationContext.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("work_categories=")) {
                trimmed.removePrefix("work_categories=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { item ->
                        val pieces = item.split(':', limit = 2)
                        val name = pieces.getOrNull(0)?.trim().orEmpty()
                        val unit = pieces.getOrNull(1)?.trim().orEmpty()
                        if (name.isNotBlank()) {
                            categories += NormalizationCategoryRef(name, unit)
                        }
                    }
            } else if (trimmed.startsWith("node_codes=")) {
                trimmed.removePrefix("node_codes=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { nodeCodes += it }
            } else if (trimmed.startsWith("route_codes=")) {
                trimmed.removePrefix("route_codes=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { routeCodes += it }
            }
            
            if (trimmed.startsWith("canonical_refs=")) {
                trimmed.removePrefix("canonical_refs=")
                    .split(';')
                    .forEach { part ->
                        when {
                            part.startsWith("node=") -> nodeCode = part.removePrefix("node=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("route=") -> routeCode = part.removePrefix("route=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("category=") -> {
                                val cat = part.removePrefix("category=").substringBefore('(').trim()
                                val pieces = cat.split(':', limit = 2)
                                categoryName = pieces.getOrNull(0)?.trim().orEmpty().ifBlank { null }
                                categoryUnit = pieces.getOrNull(1)?.trim().orEmpty().ifBlank { null }
                            }
                        }
                    }
            } else if (trimmed.startsWith("resolved_refs=")) {
                trimmed.removePrefix("resolved_refs=")
                    .split(';')
                    .forEach { part ->
                        when {
                            part.startsWith("node=") -> nodeCode = part.removePrefix("node=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("route=") -> routeCode = part.removePrefix("route=").substringBefore('(').trim().ifBlank { null }
                            part.startsWith("category=") -> {
                                val cat = part.removePrefix("category=").substringBefore('(').trim()
                                val pieces = cat.split(':', limit = 2)
                                categoryName = pieces.getOrNull(0)?.trim().orEmpty().ifBlank { null }
                                categoryUnit = pieces.getOrNull(1)?.trim().orEmpty().ifBlank { null }
                            }
                        }
                    }
            } else if (trimmed.startsWith("selected_node=")) {
                nodeCode = trimmed.removePrefix("selected_node=").trim().ifBlank { null }
            } else if (trimmed.startsWith("selected_route=")) {
                routeCode = trimmed.removePrefix("selected_route=").trim().ifBlank { null }
            }
        }

        return NormalizationRefs(
            nodeCode = nodeCode,
            routeCode = routeCode,
            categoryName = categoryName,
            categoryUnit = categoryUnit,
            categories = categories,
            nodeCodes = nodeCodes,
            routeCodes = routeCodes
        )
    }
}
