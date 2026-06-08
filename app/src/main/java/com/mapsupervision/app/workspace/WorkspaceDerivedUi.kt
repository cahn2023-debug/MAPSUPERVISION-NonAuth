package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress

data class SelectorOption(
    val key: String,
    val label: String
)

data class PreparedMaterialLine(
    val itemName: String,
    val plannedText: String,
    val plannedQty: Float,
    val actualText: String
)

data class DataHubDisplayItem(
    val isRoute: Boolean,
    val id: String,
    val code: String,
    val contractor: String,
    val node: GisNode? = null,
    val route: GisRoute? = null
)

data class ProgressUiState(
    val nonStructuralNodes: List<GisNode> = emptyList(),
    val nodesByCode: Map<String, GisNode> = emptyMap(),
    val progressByNodeCode: Map<String, NodeProgress> = emptyMap(),
    val allDisplayItems: List<NodeProgress> = emptyList(),
    val activeNodeCodes: Set<String> = emptySet(),
    val criticalNodes: List<NodeProgress> = emptyList(),
    val nodeSelectorOptions: List<SelectorOption> = emptyList(),
    val logEpochDays: Set<Long> = emptySet(),
    val materialOptionsByNodeCode: Map<String, List<SelectorOption>> = emptyMap()
)

data class DataHubUiState(
    val nonStructuralNodes: List<GisNode> = emptyList(),
    val contractorOptions: List<String> = emptyList(),
    val baseDisplayItems: List<DataHubDisplayItem> = emptyList()
)

internal data class WorkspaceIndexes(
    val nodesById: Map<String, GisNode> = emptyMap(),
    val nodesByCode: Map<String, GisNode> = emptyMap(),
    val routesByCode: Map<String, GisRoute> = emptyMap(),
    val routeRepresentativesByCode: Map<String, GisRoute> = emptyMap(),
    val progressByNodeCode: Map<String, NodeProgress> = emptyMap(),
    val materialRowsByNodeKey: Map<String, List<MaterialProgress>> = emptyMap(),
    val parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>> = emptyMap(),
    val normalizedNodeSearch: Map<String, String> = emptyMap(),
    val normalizedRouteSearch: Map<String, String> = emptyMap(),
    val progressUi: ProgressUiState = ProgressUiState(),
    val dataHubUi: DataHubUiState = DataHubUiState()
)

internal fun buildWorkspaceIndexes(state: WorkspaceState): WorkspaceIndexes {
    val routeNodeCodesUpper = HashSet<String>(state.designRoutes.size * 2 + 1)
    for (route in state.designRoutes) {
        routeNodeCodesUpper += route.startNodeCode.trim().uppercase()
        routeNodeCodesUpper += route.endNodeCode.trim().uppercase()
    }

    val nonStructuralNodes = state.designNodes.filter { node ->
        !WorkspaceImportHelper.isStructuralRouteNode(node.code, routeNodeCodesUpper)
    }
    val nodesById = state.designNodes.associateBy { it.id }
    val nodesByCode = state.designNodes.associateBy { it.code }
    val routesByCode = state.designRoutes.associateBy { it.code }
    val progressByNodeCode = state.constructionProgress.associateBy { it.nodeCode }
    val activeNodeCodes = nonStructuralNodes.mapTo(linkedSetOf()) { normalizeObjectCode(it.code) }

    val parsedMaterialsByNodeKey = LinkedHashMap<String, List<PreparedMaterialLine>>(state.designNodes.size * 2 + 1)
    for (node in state.designNodes) {
        val parsed = parseMaterialSummary(node.materialSummary)
        parsedMaterialsByNodeKey[node.id] = parsed
        parsedMaterialsByNodeKey[node.code] = parsed
    }

    val materialRowsByNodeKey = LinkedHashMap<String, MutableList<MaterialProgress>>(state.materialRows.size * 2 + 1)
    for (row in state.materialRows) {
        materialRowsByNodeKey.getOrPut(row.nodeCode) { mutableListOf() } += row
        val aliasNode = nodesById[row.nodeCode] ?: nodesByCode[row.nodeCode]
        if (aliasNode != null) {
            materialRowsByNodeKey.getOrPut(aliasNode.id) { mutableListOf() } += row
            materialRowsByNodeKey.getOrPut(aliasNode.code) { mutableListOf() } += row
        }
    }

    val nodeSelectorOptions = nonStructuralNodes
        .sortedBy { it.code }
        .map { node ->
            SelectorOption(
                key = node.code,
                label = "${nodeDisplayName(node.code, nodesByCode)} (${node.code})"
            )
        }

    val materialOptionsByNodeCode = LinkedHashMap<String, List<SelectorOption>>(nonStructuralNodes.size * 2 + 1)
    for (node in nonStructuralNodes) {
        val options = parsedMaterialsByNodeKey[node.id].orEmpty()
            .map { SelectorOption(it.itemName, it.itemName) }
        materialOptionsByNodeCode[node.code] = options
    }

    val allDisplayItems = nonStructuralNodes.map { node ->
        progressByNodeCode[node.code] ?: NodeProgress(
            id = "",
            projectId = state.activeProjectId.orEmpty(),
            nodeCode = node.code,
            planned = 0f,
            actual = 0f,
            remain = 0f,
            delayed = false,
            updatedAtEpochMs = 0L
        )
    }

    val criticalNodes = state.constructionProgress
        .asSequence()
        .filter { progress -> progress.planned - progress.actual > 0f }
        .filter { progress -> normalizeObjectCode(progress.nodeCode) in activeNodeCodes }
        .sortedByDescending { progress -> progress.planned - progress.actual }
        .take(3)
        .toList()

    val contractorOptions = buildList {
        add("Tất cả")
        addAll(
            ((nonStructuralNodes.map { it.contractor } + state.designRoutes.map { it.contractor }))
                .filter { it.isNotBlank() }
                .distinct()
        )
    }

    val routeRepresentatives = LinkedHashMap<String, GisRoute>(state.designRoutes.size)
    for (route in state.designRoutes) {
        val prefix = routeDisplayKey(route.code)
        if (prefix !in routeRepresentatives) {
            routeRepresentatives[prefix] = route
        }
    }

    val baseDisplayItems = buildList {
        nonStructuralNodes.forEach { node ->
            add(
                DataHubDisplayItem(
                    isRoute = false,
                    id = node.id,
                    code = node.code,
                    contractor = node.contractor,
                    node = node
                )
            )
        }
        routeRepresentatives.forEach { (prefix, representative) ->
            if (prefix.contains("#pm")) {
                val segments = state.designRoutes
                    .asSequence()
                    .filter { routeDisplayKey(it.code) == prefix }
                    .sortedBy { route -> route.code.substringAfterLast("_s").toIntOrNull() ?: 0 }
                    .toList()
                val first = segments.firstOrNull() ?: representative
                val last = segments.lastOrNull() ?: representative
                add(
                    DataHubDisplayItem(
                        isRoute = true,
                        id = prefix,
                        code = prefix,
                        contractor = representative.contractor,
                        route = representative.copy(
                            id = prefix,
                            code = prefix,
                            startNodeCode = first.startNodeCode,
                            endNodeCode = last.endNodeCode
                        )
                    )
                )
            } else {
                add(
                    DataHubDisplayItem(
                        isRoute = true,
                        id = representative.id,
                        code = representative.code,
                        contractor = representative.contractor,
                        route = representative
                    )
                )
            }
        }
    }

    val logEpochDays = state.dailyLogs.mapTo(linkedSetOf()) { log -> resolveLogEpochDay(log) }

    return WorkspaceIndexes(
        nodesById = nodesById,
        nodesByCode = nodesByCode,
        routesByCode = routesByCode,
        routeRepresentativesByCode = routeRepresentatives,
        progressByNodeCode = progressByNodeCode,
        materialRowsByNodeKey = materialRowsByNodeKey,
        parsedMaterialsByNodeKey = parsedMaterialsByNodeKey,
        normalizedNodeSearch = state.designNodes.associate { node -> node.id to buildNodeSearchBlob(node) },
        normalizedRouteSearch = state.designRoutes.associate { route -> route.code to buildRouteSearchBlob(route) },
        progressUi = ProgressUiState(
            nonStructuralNodes = nonStructuralNodes,
            nodesByCode = nodesByCode,
            progressByNodeCode = progressByNodeCode,
            allDisplayItems = allDisplayItems,
            activeNodeCodes = activeNodeCodes,
            criticalNodes = criticalNodes,
            nodeSelectorOptions = nodeSelectorOptions,
            logEpochDays = logEpochDays,
            materialOptionsByNodeCode = materialOptionsByNodeCode
        ),
        dataHubUi = DataHubUiState(
            nonStructuralNodes = nonStructuralNodes,
            contractorOptions = contractorOptions,
            baseDisplayItems = baseDisplayItems
        )
    )
}

internal fun resolveMaterialActualText(
    materialProgress: Map<String, String>,
    node: GisNode,
    itemName: String
): String {
    return materialProgress["${node.id}_$itemName"]
        ?: materialProgress["${node.code}_$itemName"]
        ?: ""
}

private fun parseMaterialSummary(summary: String): List<PreparedMaterialLine> {
    if (summary.isBlank()) return emptyList()
    return summary
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            val itemName = parts.getOrNull(0)?.trim().orEmpty()
            if (itemName.isBlank()) {
                null
            } else {
                val plannedText = parts.getOrNull(1)?.trim().orEmpty()
                PreparedMaterialLine(
                    itemName = itemName,
                    plannedText = plannedText,
                    plannedQty = plannedText.toFloatOrNull() ?: 0f,
                    actualText = ""
                )
            }
        }
        .toList()
}

private fun buildNodeSearchBlob(node: GisNode): String =
    normalizeForSearch(
        listOf(node.code, node.mapNumberLabel, node.contractor).joinToString(" ")
    )

private fun buildRouteSearchBlob(route: GisRoute): String =
    normalizeForSearch(
        listOf(route.code, route.contractor, route.startNodeCode, route.endNodeCode).joinToString(" ")
    )

private fun resolveLogEpochDay(log: DailyLog): Long {
    if (log.dateEpochDay != 0L) return log.dateEpochDay
    return log.createdAtEpochMs / (24 * 60 * 60 * 1000)
}

private fun normalizeObjectCode(value: String): String = value.trim().uppercase()

private fun routeDisplayKey(code: String): String {
    val markerIndex = if (code.contains("#pm")) code.lastIndexOf("_s") else code.lastIndexOf("_R")
    return if (markerIndex >= 0) code.substring(0, markerIndex) else code
}

private fun normalizeForSearch(text: String): String {
    val stripped = java.text.Normalizer
        .normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .replace('đ', 'd')
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
