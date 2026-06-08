package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.app.workspace.WorkspaceImportHelper

object WorkspaceProgressHelper {
    fun buildDashboard(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        progress: List<NodeProgress>,
        materialRows: List<MaterialProgress>
    ): DashboardState {
        val routeNodeCodesUpper = HashSet<String>(routes.size * 2)
        for (r in routes) {
            routeNodeCodesUpper.add(r.startNodeCode.trim().uppercase())
            routeNodeCodesUpper.add(r.endNodeCode.trim().uppercase())
        }
        val activeNodes = nodes.filter { !WorkspaceImportHelper.isStructuralRouteNode(it.code, routeNodeCodesUpper) }
        val totalDesignNodes = activeNodes.size
        val activeNodeCodes = activeNodes.map { it.code.trim().uppercase() }.toSet()

        val totalDesignRoutes = routes.size
        val updatedNodeCodes = HashSet<String>(progress.size)
        var delayed = 0
        var totalActual = 0.0
        var activeProgressCount = 0
        for (item in progress) {
            val codeUpper = item.nodeCode.trim().uppercase()
            if (codeUpper in activeNodeCodes) {
                activeProgressCount++
                updatedNodeCodes += item.nodeCode
                totalActual += item.actual.toDouble()
                if (item.delayed) delayed++
            }
        }
        val updatedNodes = updatedNodeCodes.size
        val completion = if (activeProgressCount == 0) 0f else (totalActual / activeProgressCount).toFloat()

        // Material quantity aggregation
        var totalPlannedQty = 0f
        var totalActualQty = 0f
        val nodesWithMaterial = HashSet<String>()
        for (row in materialRows) {
            val codeUpper = row.nodeCode.trim().uppercase()
            if (codeUpper in activeNodeCodes) {
                totalPlannedQty += row.plannedQty
                totalActualQty += row.actualQty
                if (row.actualQty > 0f) nodesWithMaterial += row.nodeCode
            }
        }
        val materialCompletionPercent = if (totalPlannedQty > 0f) (totalActualQty / totalPlannedQty * 100f) else 0f

        return DashboardState(
            totalDesignNodes = totalDesignNodes,
            totalDesignRoutes = totalDesignRoutes,
            updatedConstructionNodes = updatedNodes,
            completionPercent = completion,
            delayedCount = delayed,
            totalPlannedQty = totalPlannedQty,
            totalActualQty = totalActualQty,
            materialCompletionPercent = materialCompletionPercent,
            nodesWithMaterialEntry = nodesWithMaterial.size
        )
    }
}
