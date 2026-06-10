package com.mapsupervision.app.workspace

data class ChatNormalizationContext(
    val projectId: String,
    val selectedNodeCode: String?,
    val selectedRouteCode: String?,
    val nodeCodes: List<String>,
    val routeCodes: List<String>,
    val workCategories: List<String>,
    val knownUnits: List<String>,
    val taskStatuses: List<String> = listOf("TODO", "IN_PROGRESS", "COMPLETED")
)

fun buildChatNormalizationContext(state: WorkspaceState): ChatNormalizationContext {
    return ChatNormalizationContext(
        projectId = state.activeProjectId.orEmpty(),
        selectedNodeCode = state.mapUi.selectedNode?.code,
        selectedRouteCode = state.mapUi.selectedRoute?.code,
        nodeCodes = state.designNodes.take(30).map { it.code },
        routeCodes = state.designRoutes.take(30).map { it.code },
        workCategories = state.workCategories.take(30).map { it.name },
        knownUnits = state.workCategories.mapNotNull { it.unit.takeIf { unit -> unit.isNotBlank() } }.distinct().take(20)
    )
}

fun ChatNormalizationContext.toPromptString(): String = buildString {
    append("project=").append(projectId)
    selectedNodeCode?.takeIf { it.isNotBlank() }?.let { append("\nselected_node=").append(it) }
    selectedRouteCode?.takeIf { it.isNotBlank() }?.let { append("\nselected_route=").append(it) }
    if (nodeCodes.isNotEmpty()) {
        append("\nnode_codes=").append(nodeCodes.joinToString(", "))
    }
    if (routeCodes.isNotEmpty()) {
        append("\nroute_codes=").append(routeCodes.joinToString(", "))
    }
    if (workCategories.isNotEmpty()) {
        append("\nwork_categories=").append(workCategories.joinToString(", "))
    }
    if (knownUnits.isNotEmpty()) {
        append("\nknown_units=").append(knownUnits.joinToString(", "))
    }
    if (taskStatuses.isNotEmpty()) {
        append("\ntask_statuses=").append(taskStatuses.joinToString(", "))
    }
}
