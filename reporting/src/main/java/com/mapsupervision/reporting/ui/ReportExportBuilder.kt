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
    projectId: String,
    filterNodeCode: String?,
    photos: List<SitePhoto>,
    progress: List<NodeProgress>,
    materialRowsRaw: List<MaterialProgress>,
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    dailyLogs: List<DailyLog>,
    activeDraft: ReportDraftResult
): ReportExportContent {
    val filteredPhotos = if (filterNodeCode.isNullOrBlank()) photos else photos.filter { photo ->
        photo.objectCode == filterNodeCode ||
            photo.matchedNodeCode == filterNodeCode ||
            photo.tagCodesCsv.split(',').map(String::trim).any { it == filterNodeCode }
    }
    val filteredProgress = if (filterNodeCode.isNullOrBlank()) progress else progress.filter { it.nodeCode == filterNodeCode }
    val filteredMaterialRows = if (filterNodeCode.isNullOrBlank()) materialRowsRaw else materialRowsRaw.filter { it.nodeCode == filterNodeCode }
    val filteredNodes = if (filterNodeCode.isNullOrBlank()) nodes else nodes.filter { it.code == filterNodeCode || it.id == filterNodeCode }
    val filteredRoutes = if (filterNodeCode.isNullOrBlank()) routes else routes.filter { it.startNodeCode == filterNodeCode || it.endNodeCode == filterNodeCode }
    val filteredLogs = if (filterNodeCode.isNullOrBlank()) dailyLogs else dailyLogs.filter { log ->
        log.nodeCode == filterNodeCode || log.appliedNodeCodesCsv.split(',').map(String::trim).any { it == filterNodeCode }
    }

    val materialRows = buildMaterialReportRows(filteredNodes, filteredRoutes, filteredMaterialRows)
    val dailyLogLines = buildDailyLogSummary(filteredLogs)
    val delayed = filteredProgress.count { it.delayed }
    val avg = if (filteredProgress.isEmpty()) 0f else filteredProgress.map { it.actual }.average().toFloat()
    val targetId = if (filterNodeCode.isNullOrBlank()) projectId else "${projectId}_$filterNodeCode"

    val lines = buildList {
        if (!filterNodeCode.isNullOrBlank()) {
            add("BÁO CÁO CHI TIẾT ĐIỂM GIÁM SÁT: $filterNodeCode")
        }
        add("Tổng số điểm giám sát: ${filteredProgress.size}")
        if (filterNodeCode.isNullOrBlank()) {
            add("Số điểm thi công chậm: $delayed")
            add("Tiến độ thi công trung bình: ${"%.2f".format(avg)}%")
        }
        add("Tổng số ảnh thực địa chụp được: ${filteredPhotos.size}")
        add("Tóm tắt AI: ${activeDraft.executiveSummary}")
        add("Đánh giá rủi ro AI: ${activeDraft.riskSection}")
        add("Hành động đề xuất AI: ${activeDraft.recommendedActions.joinToString("; ")}")
    }

    return ReportExportContent(
        targetId = targetId,
        lines = lines,
        materialRows = materialRows,
        photos = filteredPhotos,
        dailyLogLines = dailyLogLines
    )
}

internal fun buildMaterialReportRows(
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    rows: List<MaterialProgress>
): List<MaterialReportRow> {
    val plannedMap = mutableMapOf<String, Float>()
    val nodeCodesByMaterial = mutableMapOf<String, MutableSet<String>>()
    val nodesById = nodes.associateBy { it.id }
    val nodesByCode = nodes.associateBy { it.code }
    nodes.forEach { node ->
        parseMaterialSummary(node.materialSummary).forEach { (name, qty) ->
            plannedMap[name] = (plannedMap[name] ?: 0f) + qty
            val nodeCode = node.code.ifBlank { node.id }
            if (nodeCode.isNotBlank()) {
                nodeCodesByMaterial.getOrPut(name) { mutableSetOf() }.add(nodeCode)
            }
        }
    }

    rows.forEach { row ->
        val materialName = row.materialName.trim()
        if (materialName.isNotBlank() && !materialName.equals("routeLength", ignoreCase = true)) {
            val nodeCode = nodesById[row.nodeCode]?.code
                ?: nodesByCode[row.nodeCode]?.code
                ?: row.nodeCode
            if (nodeCode.isNotBlank()) {
                nodeCodesByMaterial.getOrPut(materialName) { mutableSetOf() }.add(nodeCode)
            }
        }
    }

    val allMaterialNames = (plannedMap.keys + rows.map { it.materialName.trim() })
        .filter { it.isNotBlank() && !it.equals("routeLength", ignoreCase = true) }
        .distinct()
        .sorted()
    if (allMaterialNames.isEmpty()) return emptyList()

    val materialRows = allMaterialNames.map { materialName ->
        val planned = plannedMap[materialName] ?: 0f
        val actual = rows.filter { it.materialName.trim() == materialName }
            .sumOf { it.actualQty.toDouble() }.toFloat()
        val nodeCodes = nodeCodesByMaterial[materialName].orEmpty()
        MaterialReportRow(
            materialName = materialName,
            nodeCount = nodeCodes.size,
            routeCount = countRoutesForNodes(routes, nodeCodes),
            totalPlannedQty = planned,
            totalActualQty = actual,
            completionPercent = if (planned <= 0f) 0f else (actual / planned) * 100f
        )
    }

    val plannedTotal = materialRows.sumOf { it.totalPlannedQty.toDouble() }.toFloat()
    val actualTotal = materialRows.sumOf { it.totalActualQty.toDouble() }.toFloat()
    val totalNodeCodes = nodeCodesByMaterial.values.flatten().toSet()
    val totalRow = MaterialReportRow(
        materialName = "Tổng",
        nodeCount = totalNodeCodes.size,
        routeCount = countRoutesForNodes(routes, totalNodeCodes),
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
