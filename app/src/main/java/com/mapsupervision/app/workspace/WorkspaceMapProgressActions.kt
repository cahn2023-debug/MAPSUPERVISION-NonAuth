package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.ai.AiDecisionSource
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.ImportMappingPayload
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.ai.NoteSummarizationPayload
import com.mapsupervision.domain.ai.NoteSummarizationResult
import com.mapsupervision.domain.ai.TaskRecommendationPayload
import com.mapsupervision.domain.ai.TaskRecommendationResult
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.createStoredSitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.storage.importer.UserFileImportService
import com.mapsupervision.storage.importer.ConfirmedFieldFlags
import com.mapsupervision.storage.importer.ExcelColumnMapping
import com.mapsupervision.storage.importer.ExcelClassificationMode
import com.mapsupervision.storage.importer.NonExcelFieldCandidateSet
import com.mapsupervision.storage.importer.NonExcelImportMapping
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.MapLayerType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.roundToLong

fun WorkspaceViewModel.addConstructionProgress(nodeCode: String, planned: Float, actual: Float) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val remain = (planned - actual).coerceAtLeast(0f)
        val upsertResult = progressRepository.upsert(
            NodeProgress(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                nodeCode = nodeCode,
                planned = planned,
                actual = actual,
                remain = remain,
                delayed = actual < planned,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        if (upsertResult is AppResult.Error) {
            _state.value = _state.value.copy(
                importUi = _state.value.importUi.copy(
                    message = "Lỗi cập nhật thi công: ${upsertResult.throwable.message}"
                )
            )
            return@launch
        }
        val current = _state.value.constructionProgress.toMutableList()
        val index = current.indexOfFirst { it.projectId == projectId && it.nodeCode == nodeCode }
        val newProgress = NodeProgress(
            id = if (index >= 0) current[index].id else UUID.randomUUID().toString(),
            projectId = projectId,
            nodeCode = nodeCode,
            planned = planned,
            actual = actual,
            remain = remain,
            delayed = actual < planned,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        if (index >= 0) {
            current[index] = newProgress
        } else {
            current += newProgress
        }
        markProjectChanged(projectId, "construction_progress_updated")
        _state.value = _state.value.copy(
            constructionProgress = current,
            dashboard = buildDashboard(_state.value.designNodes, _state.value.designRoutes, current, _state.value.materialRows),
            importUi = _state.value.importUi.copy(message = "Đã cập nhật thi công cho node ")
        )
    }
}

fun WorkspaceViewModel.addDailyLog(
    workItem: String,
    manpower: Int,
    note: String,
    weather: String = "",
    temperature: Double = 0.0,
    nodeCode: String? = null,
    dateEpochDay: Long = 0L,
    volume: Double = 0.0,
    unit: String = "",
    categoryName: String = ""
) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch

        if (!nodeCode.isNullOrBlank() && categoryName.isNotBlank() && volume > 0.0) {
            val node = ensureIndexes().nodesByCode[nodeCode]
            val plannedVolume = extractPlannedQty(node, categoryName)
            val existingMaterials = _state.value.materialRows
            val existing = existingMaterials.firstOrNull {
                (it.nodeCode == nodeCode || ensureIndexes().nodesById[it.nodeCode]?.code == nodeCode) &&
                    it.materialName.equals(categoryName, ignoreCase = true)
            }
            val currentActual = (existing?.actualQty ?: 0f) + volume.toFloat()
            val newMaterial = MaterialProgress(
                id = existing?.id ?: UUID.randomUUID().toString(),
                projectId = projectId,
                nodeCode = nodeCode,
                materialName = existing?.materialName ?: categoryName,
                plannedQty = if (existing != null && existing.plannedQty > 0f) existing.plannedQty else plannedVolume,
                actualQty = currentActual,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            materialProgressRepository.upsert(newMaterial)

            val calculatedProgress = if (newMaterial.plannedQty > 0f) {
                (newMaterial.actualQty / newMaterial.plannedQty * 100f).coerceIn(0f, 100f)
            } else {
                100f
            }
            val existingProgress = _state.value.constructionProgress.firstOrNull { it.nodeCode == nodeCode }
            addConstructionProgress(nodeCode, existingProgress?.planned ?: 100f, calculatedProgress)
        }

        val log = com.mapsupervision.domain.model.DailyLog(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            workItem = workItem,
            manpower = manpower,
            note = note,
            createdAtEpochMs = System.currentTimeMillis(),
            weather = weather,
            temperature = temperature,
            nodeCode = nodeCode.takeIf { !it.isNullOrBlank() },
            dateEpochDay = dateEpochDay,
            volume = volume,
            unit = unit,
            categoryName = categoryName
        )
        val result = dailyLogRepository.add(log)
        if (result is AppResult.Error) {
            AppLogger.d("dailylog.add.error project=$projectId msg=${result.throwable.message}")
            return@launch
        }
        markProjectChanged(projectId, "daily_log_added")
        refresh()
    }
}

internal fun WorkspaceViewModel.extractPlannedQty(node: GisNode?, materialName: String): Float {
    if (node == null) return 100f
    val lines = node.materialSummary.split("\n")
    for (line in lines) {
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].trim().equals(materialName, ignoreCase = true)) {
            return parts[1].trim().toFloatOrNull() ?: 100f
        }
    }
    return 100f
}

fun WorkspaceViewModel.addWorkCategory(name: String, unit: String) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        val category = WorkCategory(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name,
            unit = unit,
            createdAtEpochMs = System.currentTimeMillis()
        )
        workCategoryRepository.add(category)
        markProjectChanged(projectId, "work_category_added")
        _state.value = _state.value.copy(workCategories = _state.value.workCategories + category)
    }
}

fun WorkspaceViewModel.fetchWeatherAuto(nodeCode: String?, onResult: (String, Double) -> Unit) {
    viewModelScope.launch {
        val node = ensureIndexes().nodesByCode[nodeCode]
        val coords = node?.let { it.latitude to it.longitude }
            ?: locationProvider.lastKnownLocation().let { snapshot ->
                snapshot.latitude?.let { lat -> snapshot.longitude?.let { lng -> lat to lng } }
            }
            ?: (10.762622 to 106.660172)

        when (val result = weatherService.fetchWeather(coords.first, coords.second)) {
            is AppResult.Success -> onResult(result.data.condition, result.data.temperature)
            is AppResult.Error -> AppLogger.d("weather.fetch.error msg=${result.throwable.message}")
        }
    }
}

fun WorkspaceViewModel.selectMapNode(node: GisNode) {
    val delayed = ensureIndexes().progressByNodeCode[node.code]?.delayed == true
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            selectedNode = node,
            selectedRoute = null,
            status = if (delayed) "Chậm tiến độ" else "Bình thường",
            expectedCompletion = if (delayed) "Quá hạn" else "Đúng tiến độ",
            lastInspection = "Hôm nay",
            message = ""
        )
    )
    GisMapBridgeRegistry.bridge?.centerOnLocation(node.latitude, node.longitude, 20.0)
}

fun WorkspaceViewModel.clearMapNodeSelection() {
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(selectedNode = null))
}

fun WorkspaceViewModel.selectMapRoute(route: GisRoute) {
    val indexes = ensureIndexes()
    val selectedRoute = indexes.routesByCode[route.code] ?: indexes.routeRepresentativesByCode[route.code] ?: route
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            selectedRoute = selectedRoute,
            selectedNode = null,
            message = ""
        )
    )
    val startNode = indexes.nodesByCode[selectedRoute.startNodeCode]
    val endNode = indexes.nodesByCode[selectedRoute.endNodeCode]
    if (startNode != null && endNode != null) {
        val midLat = (startNode.latitude + endNode.latitude) / 2.0
        val midLng = (startNode.longitude + endNode.longitude) / 2.0
        GisMapBridgeRegistry.bridge?.centerOnLocation(midLat, midLng, 20.0)
    }
}

fun WorkspaceViewModel.clearMapRouteSelection() {
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(selectedRoute = null, routeNote = ""))
}

fun WorkspaceViewModel.updateRouteNote(note: String) {
    // Obsolete: kept as no-op or delegated to persistent note if needed, but UI will use addNote
}

fun WorkspaceViewModel.loadNotesAndTasks(objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val notesResult = noteRepository.byObject(projectId, objectCode)
        val tasksResult = taskRepository.byObject(projectId, objectCode)
        val notes = (notesResult as? AppResult.Success)?.data.orEmpty()
        val tasks = (tasksResult as? AppResult.Success)?.data.orEmpty()
        _state.value = _state.value.copy(
            selectedObjectNotes = notes,
            selectedObjectTasks = tasks,
            aiNoteSummary = "",
            aiTaskSuggestions = emptyList()
        )
    }
}

fun WorkspaceViewModel.addNote(objectCode: String, content: String) {
    if (content.isBlank()) return
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        noteRepository.add(
            Note(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                objectCode = objectCode,
                content = content,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        markProjectChanged(projectId, "note_added")
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.deleteNote(noteId: String, objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId
        noteRepository.delete(noteId)
        if (projectId != null) {
            markProjectChanged(projectId, "note_deleted")
        }
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.addTask(objectCode: String, title: String) {
    if (title.isBlank()) return
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        taskRepository.upsert(
            Task(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                objectCode = objectCode,
                title = title,
                description = "",
                status = TaskStatus.TODO,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        markProjectChanged(projectId, "task_added")
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.toggleTaskStatus(taskId: String, objectCode: String, currentStatus: TaskStatus) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId ?: return@launch
        val task = _state.value.selectedObjectTasks.firstOrNull { it.id == taskId } ?: return@launch
        val nextStatus = when (currentStatus) {
            TaskStatus.TODO -> TaskStatus.IN_PROGRESS
            TaskStatus.IN_PROGRESS -> TaskStatus.COMPLETED
            TaskStatus.COMPLETED -> TaskStatus.TODO
        }
        taskRepository.upsert(
            task.copy(
                status = nextStatus,
                completedAtEpochMs = if (nextStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
            )
        )
        markProjectChanged(projectId, "task_status_updated")
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.deleteTask(taskId: String, objectCode: String) {
    viewModelScope.launch {
        val projectId = _state.value.activeProjectId
        taskRepository.delete(taskId)
        if (projectId != null) {
            markProjectChanged(projectId, "task_deleted")
        }
        loadNotesAndTasks(objectCode)
    }
}

fun WorkspaceViewModel.summarizeNotes(objectCode: String) {
    viewModelScope.launch {
        val notes = _state.value.selectedObjectNotes.map { it.content }
        if (notes.isEmpty()) {
            _state.value = _state.value.copy(aiNoteSummary = "Chưa có ghi chú nào để tóm tắt.")
            return@launch
        }
        _state.value = _state.value.copy(isAiLoading = true)
        val aiResult = runCatching {
            aiOrchestrator.execute<NoteSummarizationResult>(
                NoteSummarizationPayload(objectCode = objectCode, notes = notes)
            )
        }.getOrNull()

        _state.value = _state.value.copy(
            aiNoteSummary = aiResult?.result?.summary ?: "Không thể kết nối dịch vụ tóm tắt AI.",
            isAiLoading = false
        )
    }
}

fun WorkspaceViewModel.suggestTasks(objectCode: String) {
    viewModelScope.launch {
        val notes = _state.value.selectedObjectNotes.map { it.content }
        val existingTasks = _state.value.selectedObjectTasks.map { it.title }
        _state.value = _state.value.copy(isAiLoading = true)
        val aiResult = runCatching {
            aiOrchestrator.execute<TaskRecommendationResult>(
                TaskRecommendationPayload(objectCode = objectCode, notes = notes, existingTasks = existingTasks)
            )
        }.getOrNull()

        _state.value = _state.value.copy(
            aiTaskSuggestions = aiResult?.result?.suggestedTasks.orEmpty(),
            isAiLoading = false
        )
    }
}

fun WorkspaceViewModel.onMapZoomIn() {
    GisMapBridgeRegistry.bridge?.zoomIn()
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(message = "Phóng to"))
}

fun WorkspaceViewModel.onMapZoomOut() {
    GisMapBridgeRegistry.bridge?.zoomOut()
    _state.value = _state.value.copy(mapUi = _state.value.mapUi.copy(message = "Thu nhỏ"))
}

fun WorkspaceViewModel.onMapMyLocation() {
    val centered = GisMapBridgeRegistry.bridge?.centerOnMyLocation() == true
    if (!centered) {
        GisMapBridgeRegistry.bridge?.fitToObjects()
    }
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            message = if (centered) "Định vị vị trí của tôi" else "Không có GPS, đã zoom toàn bộ đối tượng"
        )
    )
}

fun WorkspaceViewModel.onMapToggleLayer() {
    val current = _state.value.mapUi
    val newShowNodes = !current.showNodes
    val newShowRoutes = !current.showRoutes
    GisMapBridgeRegistry.bridge?.setLayerVisibility(newShowNodes, newShowRoutes)
    _state.value = _state.value.copy(
        mapUi = current.copy(
            showNodes = newShowNodes,
            showRoutes = newShowRoutes,
            message = "Đã chuyển đổi lớp bản đồ"
        )
    )
}

fun WorkspaceViewModel.onMapBaseMapChanged(type: MapLayerType) {
    GisMapBridgeRegistry.bridge?.setBaseMap(type)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(message = "Đã đổi lớp nền bản đồ")
    )
}

fun WorkspaceViewModel.onFilterContractorChanged(contractor: String?) {
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            filterContractor = contractor?.takeIf { it.isNotBlank() },
            message = ""
        )
    )
}

fun WorkspaceViewModel.onContractorColorChanged(contractor: String, hexColor: String) {
    val updated = _state.value.mapUi.contractorColors.toMutableMap()
    updated[contractor] = hexColor
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(contractorColors = updated)
    )
}

fun WorkspaceViewModel.onSearchQueryChanged(query: String) {
    val trimmed = query.trim()
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(searchQuery = trimmed, message = "")
    )
    if (trimmed.isBlank()) return
    mapSearchJob?.cancel()
    mapSearchJob = viewModelScope.launch {
        delay(180)
        val stateSnapshot = _state.value
        val indexes = ensureIndexes(stateSnapshot)
        val normalizedQuery = normalizeVietnamese(trimmed)
        val matchedNode = stateSnapshot.designNodes.firstOrNull { node ->
            indexes.normalizedNodeSearch[node.id].orEmpty().contains(normalizedQuery)
        }
        if (matchedNode != null) {
            if (stateSnapshot.mapUi.selectedNode?.code != matchedNode.code) {
                GisMapBridgeRegistry.bridge?.centerOnLocation(matchedNode.latitude, matchedNode.longitude)
            }
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Tìm thấy: ${matchedNode.code}")
            )
            return@launch
        }
        val matchedRoute = stateSnapshot.designRoutes.firstOrNull { route ->
            indexes.normalizedRouteSearch[route.code].orEmpty().contains(normalizedQuery)
        }
        if (matchedRoute != null) {
            val start = indexes.nodesByCode[matchedRoute.startNodeCode]
            val end = indexes.nodesByCode[matchedRoute.endNodeCode]
            if (start != null && end != null && stateSnapshot.mapUi.selectedRoute?.code != matchedRoute.code) {
                GisMapBridgeRegistry.bridge?.centerOnLocation(
                    (start.latitude + end.latitude) / 2,
                    (start.longitude + end.longitude) / 2
                )
            }
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Tìm thấy tuyến: ${matchedRoute.code}")
            )
            return@launch
        }
        val geocode = geocodeByNominatim(trimmed)
        if (geocode != null) {
            GisMapBridgeRegistry.bridge?.centerOnLocation(geocode.first, geocode.second)
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Đã tìm vị trí theo địa chỉ")
            )
        } else {
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Không tìm thấy kết quả")
            )
        }
    }
    return

    // Normalize query for Vietnamese no-accent matching
    val normalizedQuery = normalizeVietnamese(trimmed)

    // Check if any node/route matches locally ? if so, zoom to first match
    val matchedNode = _state.value.designNodes.firstOrNull { node ->
        nodeMatchesQuery(node, trimmed, normalizedQuery)
    }
    if (matchedNode != null) {
        GisMapBridgeRegistry.bridge?.centerOnLocation(matchedNode.latitude, matchedNode.longitude)
        _state.value = _state.value.copy(
            mapUi = _state.value.mapUi.copy(message = "Tìm thấy: ${matchedNode.code}")
        )
        return
    }

    val matchedRoute = _state.value.designRoutes.firstOrNull { route ->
        routeMatchesQuery(route, trimmed, normalizedQuery)
    }
    if (matchedRoute != null) {
        // Zoom to midpoint of route
        val nodesByCode = _state.value.designNodes.associateBy { it.code }
        val start = nodesByCode[matchedRoute.startNodeCode]
        val end   = nodesByCode[matchedRoute.endNodeCode]
        if (start != null && end != null) {
            GisMapBridgeRegistry.bridge?.centerOnLocation(
                (start.latitude + end.latitude) / 2,
                (start.longitude + end.longitude) / 2
            )
        }
        _state.value = _state.value.copy(
            mapUi = _state.value.mapUi.copy(message = "Tìm thấy tuyến: ${matchedRoute.code}")
        )
        return
    }

    // Fallback: geocode by address
    viewModelScope.launch {
        val geocode = geocodeByNominatim(trimmed)
        if (geocode != null) {
            GisMapBridgeRegistry.bridge?.centerOnLocation(geocode.first, geocode.second)
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Đã tìm vị trí theo địa chỉ")
            )
        } else {
            _state.value = _state.value.copy(
                mapUi = _state.value.mapUi.copy(message = "Không tìm thấy kết quả")
            )
        }
    }
}

private fun WorkspaceViewModel.normalizeVietnamese(text: String): String {
    val stripped = java.text.Normalizer.normalize(text.lowercase(java.util.Locale.US), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped.replace("d", "d").replace(Regex("[^a-z0-9 ]+"), " ").trim().replace(Regex("\\s+"), " ")
}

private fun WorkspaceViewModel.nodeMatchesQuery(node: GisNode, raw: String, normalized: String): Boolean {
    // Search: mã nút (code) + số thứ tự (mapNumberLabel)
    val fields = listOf(node.code, node.mapNumberLabel)
    return fields.any { field ->
        field.isNotBlank() && (
            field.contains(raw, ignoreCase = true) ||
            normalizeVietnamese(field).contains(normalized, ignoreCase = true)
        )
    }
}

private fun WorkspaceViewModel.routeMatchesQuery(route: GisRoute, raw: String, normalized: String): Boolean {
    // Search: mã tuyến (code) + mã nút đầu/cuối
    val fields = listOf(route.code, route.startNodeCode, route.endNodeCode)
    return fields.any { field ->
        field.isNotBlank() && (
            field.contains(raw, ignoreCase = true) ||
            normalizeVietnamese(field).contains(normalized, ignoreCase = true)
        )
    }
}

fun WorkspaceViewModel.getFilteredDesignNodes(): List<GisNode> {
    val mapUi = _state.value.mapUi
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeVietnamese(mapUi.searchQuery)
    val indexes = ensureIndexes()
    return _state.value.designNodes.filter { node ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            node.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedNodeSearch[node.id].orEmpty().contains(normalizedQuery)
        byContractor && byQuery
    }
}

fun WorkspaceViewModel.getFilteredDesignNodesForMap(): List<GisNode> {
    return buildMapDesignNodes(_state.value, ensureIndexes())
}

fun WorkspaceViewModel.getFilteredDesignRoutes(): List<GisRoute> {
    val mapUi = _state.value.mapUi
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeVietnamese(mapUi.searchQuery)
    val indexes = ensureIndexes()
    return _state.value.designRoutes.filter { route ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            route.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedRouteSearch[route.code].orEmpty().contains(normalizedQuery)
        byContractor && byQuery
    }
}

fun WorkspaceViewModel.getRouteProperties(route: GisRoute): List<Pair<String, String>> {
    val nodesByCode = ensureIndexes().nodesByCode
    val markerIndex = if (route.code.contains("#pm")) route.code.lastIndexOf("_s") else route.code.lastIndexOf("_R")
    val basePrefix = if (markerIndex >= 0) route.code.substring(0, markerIndex) else ""
    val suffixChar = if (route.code.contains("#pm")) "_s" else "_R"

    if (basePrefix.isNotBlank()) {
        val lineSegments = _state.value.designRoutes.filter { it.code.startsWith(basePrefix + suffixChar) }
        val lineNodes = _state.value.designNodes.filter { it.code.startsWith(basePrefix + "_p") }
        val startNode = lineNodes.firstOrNull { it.code.endsWith("_p1") }
        val endNode = lineNodes.maxByOrNull { n -> n.code.substringAfterLast("_p").toIntOrNull() ?: 0 }

        var totalDistM = 0.0
        lineSegments.forEach { seg ->
            val s = nodesByCode[seg.startNodeCode]
            val e = nodesByCode[seg.endNodeCode]
            if (s != null && e != null) {
                totalDistM += com.mapsupervision.domain.util.Haversine.distanceInMeters(
                    s.latitude, s.longitude,
                    e.latitude, e.longitude
                )
            }
        }

        val distanceText = if (totalDistM >= 1000) "${"%.2f".format(totalDistM / 1000)} km"
        else "${totalDistM.toInt()} m"

        val cleanRouteCode = if (route.code.contains("#pm")) basePrefix.substringBefore("#pm") else basePrefix

        return buildList {
            add("Mã tuyến" to cleanRouteCode)
            if (route.contractor.isNotBlank()) add("Nhà thầu" to route.contractor)
            add("Điểm đầu" to (startNode?.code ?: route.startNodeCode))
            add("Điểm cuối" to (endNode?.code ?: route.endNodeCode))
            if (distanceText.isNotBlank()) add("Chiều dài tuyến" to distanceText)
            startNode?.let { n ->
                if (n.contractor.isNotBlank() && n.contractor != route.contractor)
                    add("Nhà thầu điểm đầu" to n.contractor)
                add("Tọa độ điểm đầu" to "${"%.6f".format(n.latitude)}, ${"%.6f".format(n.longitude)}")
            }
            endNode?.let { n ->
                if (n.contractor.isNotBlank() && n.contractor != route.contractor)
                    add("Nhà thầu điểm cuối" to n.contractor)
                add("Tọa độ điểm cuối" to "${"%.6f".format(n.latitude)}, ${"%.6f".format(n.longitude)}")
            }
            val routeLength = extractRouteLength(startNode?.materialSummary, endNode?.materialSummary)
            if (routeLength.isNotBlank()) add("Chiều dài thiết kế" to routeLength)
            collectSummaryProperties(startNode?.materialSummary, endNode?.materialSummary).forEach { (k, v) ->
                add(k to v)
            }
        }
    } else {
        val startNode = nodesByCode[route.startNodeCode]
        val endNode   = nodesByCode[route.endNodeCode]

        val distanceText = if (startNode != null && endNode != null) {
            val distM = com.mapsupervision.domain.util.Haversine.distanceInMeters(
                startNode.latitude, startNode.longitude,
                endNode.latitude, endNode.longitude
            )
            if (distM >= 1000) "${"%.2f".format(distM / 1000)} km"
            else "${distM.toInt()} m"
        } else ""

        return buildList {
            add("Mã tuyến" to route.code)
            if (route.contractor.isNotBlank()) add("Nhà thầu" to route.contractor)
            add("Điểm đầu" to route.startNodeCode)
            add("Điểm cuối" to route.endNodeCode)
            if (distanceText.isNotBlank()) add("Chiều dài tuyến" to distanceText)
            startNode?.let { n ->
                if (n.contractor.isNotBlank() && n.contractor != route.contractor)
                    add("Nhà thầu điểm đầu" to n.contractor)
                add("Tọa độ điểm đầu" to "${"%.6f".format(n.latitude)}, ${"%.6f".format(n.longitude)}")
            }
            endNode?.let { n ->
                if (n.contractor.isNotBlank() && n.contractor != route.contractor)
                    add("Nhà thầu điểm cuối" to n.contractor)
                add("Tọa độ điểm cuối" to "${"%.6f".format(n.latitude)}, ${"%.6f".format(n.longitude)}")
            }
            val routeLength = extractRouteLength(startNode?.materialSummary, endNode?.materialSummary)
            if (routeLength.isNotBlank()) add("Chiều dài thiết kế" to routeLength)
            collectSummaryProperties(startNode?.materialSummary, endNode?.materialSummary).forEach { (k, v) ->
                add(k to v)
            }
        }
    }
}

fun WorkspaceViewModel.onMapToggleMeasure() {
    val enabled = !_state.value.mapUi.measureEnabled
    GisMapBridgeRegistry.bridge?.setMeasureEnabled(enabled)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            measureEnabled = enabled,
            measureDistanceText = "",
            message = if (enabled) "Chạm 2 điểm để đo khoảng cách" else "Tắt đo khoảng cách"
        )
    )
}

fun WorkspaceViewModel.updateMeasureDistance(distanceM: Double) {
    val text = if (distanceM >= 1000)
        "${"%.2f".format(distanceM / 1000)} km"
    else
        "${distanceM.toInt()} m"
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(measureDistanceText = text)
    )
}

fun WorkspaceViewModel.onEnterMapTab() {
    GisMapBridgeRegistry.bridge?.setLayerVisibility(showNodes = true, showRoutes = true)
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            showNodes = true,
            showRoutes = true,
            message = "Đã hiển thị bản đồ"
        )
    )
}

fun WorkspaceViewModel.updateMapLabelField(field: GisLabelField) {
    _state.value = _state.value.copy(
        mapUi = _state.value.mapUi.copy(
            labelField = field,
            message = "Hiển thị theo trường: ${field.displayName}"
        )
    )
}

fun WorkspaceViewModel.focusPhotosBySelectedNode() {
    _state.value = _state.value.copy(photoFilterNodeCode = _state.value.mapUi.selectedNode?.code)
}

fun WorkspaceViewModel.loadPhotosForSelectedNode() {
    val nodeCode = _state.value.mapUi.selectedNode?.code ?: return
    val projectId = _state.value.activeProjectId ?: return
    viewModelScope.launch {
        val photos = (photoRepository.byObjectCode(projectId, nodeCode) as? AppResult.Success)?.data.orEmpty()
        _state.value = _state.value.copy(selectedNodePhotos = photos)
    }
}

fun WorkspaceViewModel.clearSelectedNodePhotos() {
    _state.value = _state.value.copy(selectedNodePhotos = emptyList())
}

fun WorkspaceViewModel.clearPhotoNodeFilter() {
    _state.value = _state.value.copy(photoFilterNodeCode = null)
}

fun WorkspaceViewModel.getProgressUiState(): ProgressUiState = ensureIndexes().progressUi

fun WorkspaceViewModel.getDataHubUiState(): DataHubUiState = ensureIndexes().dataHubUi

fun WorkspaceViewModel.getSelectedNodeMaterialLines(): List<PreparedMaterialLine> {
    val stateSnapshot = _state.value
    val selectedNode = stateSnapshot.mapUi.selectedNode ?: return emptyList()
    val baseLines = ensureIndexes(stateSnapshot).parsedMaterialsByNodeKey[selectedNode.id].orEmpty()
    return baseLines.map { line ->
        line.copy(actualText = resolveMaterialActualText(stateSnapshot.materialProgress, selectedNode, line.itemName))
    }
}

fun WorkspaceViewModel.getPreviewMaterialRows(previewNodeCode: String?): List<com.mapsupervision.reporting.ui.MaterialReportRow> {
    val stateSnapshot = _state.value
    val selectedNode = stateSnapshot.mapUi.selectedNode
    if (previewNodeCode == null || selectedNode == null || selectedNode.code != previewNodeCode) return emptyList()
    val rows = getSelectedNodeMaterialLines()
    if (rows.isEmpty()) return emptyList()
    val materialRows = rows.map { line ->
        val actualQty = line.actualText.toFloatOrNull() ?: 0f
        com.mapsupervision.reporting.ui.MaterialReportRow(
            materialName = line.itemName,
            totalPlannedQty = line.plannedQty,
            totalActualQty = actualQty,
            completionPercent = if (line.plannedQty <= 0f) 0f else (actualQty / line.plannedQty) * 100f
        )
    }
    val plannedSum = materialRows.sumOf { it.totalPlannedQty.toDouble() }.toFloat()
    val actualSum = materialRows.sumOf { it.totalActualQty.toDouble() }.toFloat()
    return materialRows + com.mapsupervision.reporting.ui.MaterialReportRow(
        materialName = "Tổng",
        totalPlannedQty = plannedSum,
        totalActualQty = actualSum,
        completionPercent = if (plannedSum <= 0f) 0f else (actualSum / plannedSum) * 100f,
        isTotal = true
    )
}

fun WorkspaceViewModel.triggerCapture() {
    _state.value = _state.value.copy(
        pendingCaptureNodeCode = _state.value.mapUi.selectedNode?.code ?: ""
    )
}

fun WorkspaceViewModel.clearCaptureRequest() {
    _state.value = _state.value.copy(pendingCaptureNodeCode = null)
}

fun WorkspaceViewModel.savePhoto(file: java.io.File, nodeCode: String) {
    viewModelScope.launch {
        val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
        withContext(Dispatchers.IO) {
            val loc = locationProvider.lastKnownLocation()
            val thumb = photoPipelineService.createThumbnail(projectId, file)
            val photo = createStoredSitePhoto(
                projectId = projectId,
                objectCode = nodeCode,
                file = file,
                thumbnailFile = thumb,
                location = loc,
                engineer = "Field"
            )
            photoRepository.add(photo)
            storageManager.scanFile(file)
        }
        markProjectChanged(projectId, "photo_saved")
        // Increment counter so ReportingScreen knows to refresh
        _state.value = _state.value.copy(photoSaveCount = _state.value.photoSaveCount + 1)
    }
}

internal fun WorkspaceViewModel.ensureIndexes(state: WorkspaceState = _state.value): WorkspaceIndexes {
    if (
        cachedNodesRef !== state.designNodes ||
        cachedRoutesRef !== state.designRoutes ||
        cachedProgressRef !== state.constructionProgress ||
        cachedMaterialRowsRef !== state.materialRows ||
        cachedDailyLogsRef !== state.dailyLogs
    ) {
        cachedIndexes = buildWorkspaceIndexes(state)
        cachedNodesRef = state.designNodes
        cachedRoutesRef = state.designRoutes
        cachedProgressRef = state.constructionProgress
        cachedMaterialRowsRef = state.materialRows
        cachedDailyLogsRef = state.dailyLogs
    }
    return cachedIndexes
}

private suspend fun WorkspaceViewModel.geocodeByNominatim(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MapSupervision/1.0")
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.inputStream.bufferedReader().use { reader ->
            val payload = reader.readText()
            val array = JSONArray(payload)
            if (array.length() == 0) return@withContext null
            val item = array.getJSONObject(0)
            val lat = item.optString("lat").toDoubleOrNull() ?: return@withContext null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@withContext null
            lat to lon
        }
    }.getOrNull()
}

private fun WorkspaceViewModel.extractRouteLength(startSummary: String?, endSummary: String?): String {
    val combined = sequenceOf(startSummary, endSummary)
        .filterNotNull()
        .flatMap { it.split('\n').asSequence() }
        .map { it.trim() }
        .firstOrNull { it.startsWith("routeLength:", ignoreCase = true) }
        ?: return ""
    return combined.substringAfter(':').trim()
}

internal fun retainRouteEndpointNodes(
    filteredNodes: List<GisNode>,
    filteredRoutes: List<GisRoute>,
    allNodes: List<GisNode>
): List<GisNode> {
    if (filteredRoutes.isEmpty() || allNodes.isEmpty()) return filteredNodes

    val nodesByCode = allNodes.associateBy { it.code.trim().uppercase() }
    val keptById = LinkedHashMap<String, GisNode>(filteredNodes.size + filteredRoutes.size * 2)
    filteredNodes.forEach { node ->
        keptById[node.id] = node
    }
    filteredRoutes.forEach { route ->
        nodesByCode[route.startNodeCode.trim().uppercase()]?.let { node ->
            keptById.putIfAbsent(node.id, node)
        }
        nodesByCode[route.endNodeCode.trim().uppercase()]?.let { node ->
            keptById.putIfAbsent(node.id, node)
        }
    }
    return keptById.values.toList()
}

internal fun buildMapDesignNodes(
    state: WorkspaceState,
    indexes: WorkspaceIndexes
): List<GisNode> {
    val mapUi = state.mapUi
    AppLogger.d("buildMapDesignNodes: filterContractor=${mapUi.filterContractor}, searchQuery='${mapUi.searchQuery}', showNodes=${mapUi.showNodes}")
    AppLogger.d("buildMapDesignNodes: total designNodes=${state.designNodes.size}, total designRoutes=${state.designRoutes.size}")
    
    if (mapUi.filterContractor.isNullOrBlank() && mapUi.searchQuery.isBlank()) {
        AppLogger.d("buildMapDesignNodes: No filter, returning all ${state.designNodes.size} nodes")
        return state.designNodes
    }

    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val filteredNodes = state.designNodes.filter { node ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            node.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedNodeSearch[node.id].orEmpty().contains(normalizedQuery)
        byContractor && byQuery
    }

    // When filtering by contractor, use all routes to retain endpoint nodes
    // This ensures nodes that are route endpoints are shown even if their route belongs to a different contractor
    val routesForEndpointRetention = if (mapUi.filterContractor.isNullOrBlank()) {
        state.designRoutes.filter { route ->
            val byQuery = mapUi.searchQuery.isBlank() ||
                indexes.normalizedRouteSearch[route.code].orEmpty().contains(normalizedQuery)
            byQuery
        }
    } else {
        state.designRoutes
    }

    AppLogger.d("buildMapDesignNodes: filteredNodes=${filteredNodes.size}, routesForRetention=${routesForEndpointRetention.size}")
    val result = retainRouteEndpointNodes(
        filteredNodes = filteredNodes,
        filteredRoutes = routesForEndpointRetention,
        allNodes = state.designNodes
    )
    AppLogger.d("buildMapDesignNodes: final result nodes=${result.size}")
    return result
}

private fun normalizeMapSearchText(text: String): String {
    val stripped = java.text.Normalizer
        .normalize(text.lowercase(java.util.Locale.US), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .replace("đ", "d")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun WorkspaceViewModel.collectSummaryProperties(startSummary: String?, endSummary: String?): List<Pair<String, String>> {
    val map = linkedMapOf<String, String>()
    sequenceOf(startSummary, endSummary)
        .filterNotNull()
        .flatMap { it.split('\n').asSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("routeLength:", ignoreCase = true) }
        .forEach { line ->
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map.putIfAbsent(key, value)
            }
        }
    return map.entries.map { it.key to it.value }
}
