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

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    internal val activeProjectRepository: ActiveProjectRepository,
    internal val importedFileRepository: ImportedFileRepository,
    internal val progressRepository: ProgressRepository,
    internal val materialProgressRepository: MaterialProgressRepository,
    internal val projectRepository: ProjectRepository,
    internal val projectSyncRepository: ProjectSyncRepository,
    internal val gisRepository: GisRepository,
    internal val importService: UserFileImportService,
    internal val aiOrchestrator: AiOrchestrator,
    internal val photoRepository: PhotoRepository,
    internal val photoPipelineService: IPhotoPipelineService,
    internal val locationProvider: IPhotoLocationProvider,
    internal val storageManager: ProjectStorageManager,
    internal val dailyLogRepository: DailyLogRepository,
    internal val noteRepository: NoteRepository,
    internal val taskRepository: TaskRepository,
    internal val workCategoryRepository: WorkCategoryRepository,
    internal val weatherService: WeatherService
) : ViewModel() {

    internal val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    internal var mapSearchJob: Job? = null
    internal val materialProgressPersistJobs = mutableMapOf<String, Job>()
    internal var cachedIndexes = WorkspaceIndexes()
    internal var cachedNodesRef: List<GisNode> = emptyList()
    internal var cachedRoutesRef: List<GisRoute> = emptyList()
    internal var cachedProgressRef: List<NodeProgress> = emptyList()
    internal var cachedMaterialRowsRef: List<MaterialProgress> = emptyList()
    internal var cachedDailyLogsRef: List<DailyLog> = emptyList()

    init {
        observeActiveProject()
        observeProjectSync()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refresh()
            }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = _state.value.activeProjectId
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshJob?.cancelAndJoin()
            refreshJob = launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val startedAtMs = System.currentTimeMillis()
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
            if (projectId == null) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    activeProjectId = null,
                    importUi = ImportUiState(
                        status = ImportStatus.IDLE,
                        message = "Chưa chọn dự án active"
                    )
                )
                return@launch
            }

            val snapshot = loadRefreshSnapshot(projectId)

            val loadedMaterialProgress = snapshot.materialRows.associate { row ->
                "${row.nodeCode}_${row.materialName}" to row.actualQty.toInt().toString()
            }

            if (snapshot.dailyLogs.isEmpty()) {
                AppLogger.d("dailylog.load.empty project=$projectId")
            }

            val dashboard = buildDashboard(
                snapshot.nodes,
                snapshot.routes,
                snapshot.progress,
                snapshot.materialRows
            )
            val coordSummary = summarizeNodeCoordinates(snapshot.nodes)
            AppLogger.d("map.refresh nodes=${snapshot.nodes.size} routes=${snapshot.routes.size} project=$projectId")
            AppLogger.d(
                "map.refresh.coords project=$projectId nodesValid=${coordSummary.validCount} invalidNodes=${coordSummary.invalidCount} " +
                    "latRange=${coordSummary.latRangeText} lonRange=${coordSummary.lonRangeText}"
            )
            _state.value = _state.value.copy(
                activeProjectId = projectId,
                importedFiles = snapshot.imports,
                designNodes = snapshot.nodes,
                designRoutes = snapshot.routes,
                constructionProgress = snapshot.progress,
                dashboard = dashboard,
                mapUi = keepMapSelection(_state.value.mapUi, snapshot.nodes, snapshot.routes),
                materialRows = snapshot.materialRows,
                materialProgress = loadedMaterialProgress,
                dailyLogs = snapshot.dailyLogs,
                workCategories = snapshot.workCategories,
                isRefreshing = false,
                lastRefreshedAtEpochMs = startedAtMs,
                aiOpsActions = emptyList(),
                aiOpsPriority = 0
            )

            launch(Dispatchers.Default) {
                val aiOps = runCatching {
                    aiOrchestrator.execute<com.mapsupervision.domain.ai.OpsRecommendationResult>(
                        OpsRecommendationPayload(
                            totalNodes = dashboard.totalDesignNodes,
                            delayedNodes = dashboard.delayedCount,
                            completionPercent = dashboard.completionPercent,
                            importWarnings = _state.value.importUi.warnings.size
                        )
                    )
                }.getOrNull()

                if (aiOps != null) {
                    _state.value = _state.value.copy(
                        mapUi = _state.value.mapUi.copy(
                            message = aiOps.result.prioritizedActions.firstOrNull().orEmpty()
                        ),
                        aiOpsActions = aiOps.result.prioritizedActions,
                        aiOpsPriority = aiOps.result.priority
                    )
                }
            }
            }
        }
    }

    private suspend fun loadRefreshSnapshot(projectId: String): WorkspaceRefreshSnapshot = coroutineScope {
        val importsDeferred = async { (importedFileRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty() }
        val nodesDeferred = async { (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty() }
        val routesDeferred = async { (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty() }
        val progressDeferred = async { (progressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty() }
        val materialDeferred = async { (materialProgressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty() }
        val dailyLogsDeferred = async { (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty() }
        val workCategoriesDeferred = async { (workCategoryRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty() }
        WorkspaceRefreshSnapshot(
            imports = importsDeferred.await(),
            nodes = nodesDeferred.await(),
            routes = routesDeferred.await(),
            progress = progressDeferred.await(),
            materialRows = materialDeferred.await(),
            dailyLogs = dailyLogsDeferred.await(),
            workCategories = workCategoriesDeferred.await()
        )
    }

    private fun keepMapSelection(mapUi: MapUiState, nodes: List<GisNode>, routes: List<GisRoute>): MapUiState {
        val selected = mapUi.selectedNode?.let { current ->
            nodes.firstOrNull { it.code == current.code }
        }
        val selectedRoute = mapUi.selectedRoute?.let { current ->
            routes.firstOrNull { it.code == current.code }
                ?: current.takeIf { route -> routes.any { isSameRouteSelection(route, it) } }
        }
        return mapUi.copy(selectedNode = selected, selectedRoute = selectedRoute)
    }

    private fun isSameRouteSelection(selected: GisRoute, candidate: GisRoute): Boolean {
        if (candidate.code == selected.code) return true
        val selectedPrefix = routeSelectionPrefix(selected.code)
        val candidatePrefix = routeSelectionPrefix(candidate.code)
        return selectedPrefix.isNotBlank() && selectedPrefix == candidatePrefix
    }

    private fun routeSelectionPrefix(code: String): String {
        val markerIndex = if (code.contains("#pm")) code.lastIndexOf("_s") else code.lastIndexOf("_R")
        return if (markerIndex >= 0) code.substring(0, markerIndex) else code
    }

    internal suspend fun markProjectChanged(projectId: String, reason: String) {
        projectRepository.touch(projectId)
        projectSyncRepository.notifyProjectChanged(projectId, reason)
    }


    internal fun buildDashboard(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        progress: List<NodeProgress>,
        materialRows: List<MaterialProgress>
    ): DashboardState {
        return WorkspaceProgressHelper.buildDashboard(nodes, routes, progress, materialRows)
    }

}

private data class WorkspaceCoordinateSummary(
    val validCount: Int,
    val invalidCount: Int,
    val latRangeText: String,
    val lonRangeText: String
)

private fun summarizeNodeCoordinates(nodes: List<GisNode>): WorkspaceCoordinateSummary {
    val validNodes = nodes.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    val invalidCount = nodes.size - validNodes.size
    if (validNodes.isEmpty()) {
        return WorkspaceCoordinateSummary(0, invalidCount, "n/a", "n/a")
    }
    return WorkspaceCoordinateSummary(
        validCount = validNodes.size,
        invalidCount = invalidCount,
        latRangeText = "%.5f..%.5f".format(validNodes.minOf { it.latitude }, validNodes.maxOf { it.latitude }),
        lonRangeText = "%.5f..%.5f".format(validNodes.minOf { it.longitude }, validNodes.maxOf { it.longitude })
    )
}
