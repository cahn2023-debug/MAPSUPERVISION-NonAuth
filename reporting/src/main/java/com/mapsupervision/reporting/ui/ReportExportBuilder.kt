package com.mapsupervision.reporting.ui

import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ReportExportContent(
    val targetId: String,
    val lines: List<String>,
    val materialRows: List<MaterialReportRow>,
    val photos: List<SitePhoto>,
    val dailyLogLines: List<String>
)

internal fun buildReportExportContent(
    snapshot: ReportingSnapshot,
    filterContractor: String? = null,
    activeDraft: ReportDraftResult
): ReportExportContent = buildReportExportContent(
    projectId = snapshot.projectId.orEmpty(),
    filterContractor = filterContractor,
    photos = snapshot.photos,
    progress = snapshot.progress,
    materialRowsRaw = snapshot.materialRowsRaw,
    nodes = snapshot.nodes,
    routes = snapshot.routes,
    dailyLogs = snapshot.dailyLogs,
    activeDraft = activeDraft
)

internal fun buildReportExportContent(
    projectId: String,
    filterContractor: String? = null,
    photos: List<SitePhoto>,
    progress: List<NodeProgress>,
    materialRowsRaw: List<MaterialProgress>,
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    dailyLogs: List<DailyLog>,
    activeDraft: ReportDraftResult
): ReportExportContent {
    val materialRows = buildMaterialReportRows(nodes, routes, materialRowsRaw, filterContractor)
    val dailyLogLines = buildDailyLogSummary(dailyLogs)
    val delayed = progress.count { it.delayed }
    val avg = if (progress.isEmpty()) 0f else progress.map { it.actual }.average().toFloat()
    val targetId = projectId

    val lines = buildList {
        add("BÁO CÁO TỔNG HỢP DỰ ÁN")
        add("Tổng số điểm giám sát: ${progress.size}")
        add("Số điểm thi công chậm: $delayed")
        add("Tiến độ thi công trung bình: ${"%.2f".format(avg)}%")
        add("Tổng số ảnh thực địa chụp được: ${photos.size}")
        add("Tóm tắt AI: ${activeDraft.executiveSummary}")
        add("Đánh giá rủi ro AI: ${activeDraft.riskSection}")
        add("Hành động đề xuất AI: ${activeDraft.recommendedActions.joinToString("; ")}")
    }

    return ReportExportContent(
        targetId = targetId,
        lines = lines,
        materialRows = materialRows,
        photos = photos,
        dailyLogLines = dailyLogLines
    )
}

internal fun buildMaterialReportRows(
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    rows: List<MaterialProgress>,
    filterContractor: String? = null
): List<MaterialReportRow> {
    val normalizedContractor = filterContractor?.trim()?.takeIf { it.isNotBlank() }
    val filteredNodes = if (normalizedContractor == null) {
        nodes
    } else {
        nodes.filter { it.contractor.trim().equals(normalizedContractor, ignoreCase = true) }
    }
    val filteredRoutes = if (normalizedContractor == null) {
        routes
    } else {
        routes.filter { it.contractor.trim().equals(normalizedContractor, ignoreCase = true) }
    }
    val nodesById = filteredNodes.associateBy { it.id }
    val nodesByCode = filteredNodes.associateBy { it.code }

    data class MaterialAccumulator(
        var plannedQty: Float = 0f,
        var actualQty: Float = 0f,
        val nodeCodes: MutableSet<String> = mutableSetOf()
    )

    val totalsByMaterial = mutableMapOf<String, MaterialAccumulator>()

    filteredNodes.forEach { node ->
        val nodeCode = node.code.ifBlank { node.id }.trim()
        if (nodeCode.isBlank()) return@forEach

        val parsedSummary = parseMaterialSummary(node.materialSummary)
        val summaryTotals = parsedSummary.groupingBy { it.first.trim() }.fold(0f) { acc, value -> acc + value.second }
        val summaryMaterialNames = summaryTotals.keys

        val nodeRows = rows.filter { row ->
            val rowMaterialName = row.materialName.trim()
            if (rowMaterialName.isBlank() || rowMaterialName.equals("routeLength", ignoreCase = true)) {
                return@filter false
            }
            val rowNodeCode = nodesById[row.nodeCode]?.code
                ?: nodesByCode[row.nodeCode]?.code
                ?: row.nodeCode.trim()
            rowNodeCode.isNotBlank() && rowNodeCode.equals(nodeCode, ignoreCase = true)
        }

        val rowTotalsByMaterial = nodeRows.groupBy { it.materialName.trim() }
        val materialNames = (summaryMaterialNames + rowTotalsByMaterial.keys)
            .filter { it.isNotBlank() && !it.equals("routeLength", ignoreCase = true) }
            .distinct()

        materialNames.forEach { materialName ->
            val accumulator = totalsByMaterial.getOrPut(materialName) { MaterialAccumulator() }
            val plannedQty = summaryTotals[materialName]
                ?: rowTotalsByMaterial[materialName].orEmpty().sumOf { it.plannedQty.toDouble() }.toFloat()
            val actualQty = rowTotalsByMaterial[materialName].orEmpty().sumOf { it.actualQty.toDouble() }.toFloat()
            accumulator.plannedQty += plannedQty
            accumulator.actualQty += actualQty
            accumulator.nodeCodes.add(nodeCode)
        }
    }

    val allMaterialNames = totalsByMaterial.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    if (allMaterialNames.isEmpty()) return emptyList()

    val materialRows = allMaterialNames.map { materialName ->
        val accumulator = totalsByMaterial[materialName] ?: MaterialAccumulator()
        MaterialReportRow(
            materialName = materialName,
            nodeCount = accumulator.nodeCodes.size,
            routeCount = countRoutesForNodes(filteredRoutes, accumulator.nodeCodes),
            totalPlannedQty = accumulator.plannedQty,
            totalActualQty = accumulator.actualQty,
            completionPercent = if (accumulator.plannedQty <= 0f) 0f else (accumulator.actualQty / accumulator.plannedQty) * 100f
        )
    }

    val plannedTotal = materialRows.sumOf { it.totalPlannedQty.toDouble() }.toFloat()
    val actualTotal = materialRows.sumOf { it.totalActualQty.toDouble() }.toFloat()
    val totalNodeCodes = totalsByMaterial.values.flatMap { it.nodeCodes }.toSet()
    val totalRow = MaterialReportRow(
        materialName = "Tổng",
        nodeCount = totalNodeCodes.size,
        routeCount = countRoutesForNodes(filteredRoutes, totalNodeCodes),
        totalPlannedQty = plannedTotal,
        totalActualQty = actualTotal,
        completionPercent = if (plannedTotal <= 0f) 0f else (actualTotal / plannedTotal) * 100f,
        isTotal = true
    )
    return materialRows + totalRow
}

internal fun buildDailyLogSummary(logs: List<DailyLog>): List<String> {
    return logs.sortedByDescending { it.createdAtEpochMs }.map { log ->
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.createdAtEpochMs))
        val nodeText = log.nodeCode?.let { "node=$it" }.orEmpty()
        val routeText = log.routeCode?.let { " route=$it" }.orEmpty()
        "- [$time] ${log.workItem} $nodeText$routeText ${log.volume} ${log.unit}".trim()
    }
}

private fun parseMaterialSummary(summary: String): List<Pair<String, Float>> {
    return summary.lineSequence().mapNotNull { line ->
        val trimmed = line.trim()
        if (!trimmed.contains(":")) return@mapNotNull null
        val name = trimmed.substringBefore(":").trim()
        val qty = trimmed.substringAfter(":").trim().toFloatOrNull()
        if (qty == null || name.isBlank() || name.equals("routeLength", ignoreCase = true)) {
            null
        } else {
            name to qty
        }
    }.toList()
}

private fun countRoutesForNodes(routes: List<GisRoute>, nodeCodes: Set<String>): Int {
    if (nodeCodes.isEmpty()) return 0
    return routes.count { route ->
        route.startNodeCode in nodeCodes || route.endNodeCode in nodeCodes
    }
}
