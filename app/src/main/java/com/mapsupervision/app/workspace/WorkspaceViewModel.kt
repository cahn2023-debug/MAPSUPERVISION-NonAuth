package com.mapsupervision.app.workspace

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.WorkVolumeProgressRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.repository.MaterialDeclarationRepository
import com.mapsupervision.domain.repository.MaterialHandoverRepository
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.storage.importer.UserFileImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class FilteredMapUpdateReason { SNAPSHOT, FILTER, SEARCH }

internal fun resolveFilteredMapUpdateDelayMs(reason: FilteredMapUpdateReason): Long = when (reason) {
    FilteredMapUpdateReason.SNAPSHOT -> 0L
    FilteredMapUpdateReason.FILTER,
    FilteredMapUpdateReason.SEARCH -> 180L
}

internal fun shouldPublishFilteredMapData(
    previousNodes: List<GisNode>,
    previousRoutes: List<GisRoute>,
    nextNodes: List<GisNode>,
    nextRoutes: List<GisRoute>
): Boolean = previousNodes != nextNodes || previousRoutes != nextRoutes

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val activeProjectRepository: ActiveProjectRepository,
    internal val importedFileRepository: ImportedFileRepository,
    internal val progressRepository: ProgressRepository,
    internal val workVolumeProgressRepository: WorkVolumeProgressRepository,
    internal val projectRepository: ProjectRepository,
    internal val projectSyncRepository: ProjectSyncRepository,
    internal val gisRepository: GisRepository,
    internal val importService: UserFileImportService,
    internal val aiOrchestrator: AiOrchestrator,
    internal val photoRepository: PhotoRepository,
    internal val photoPipelineService: IPhotoPipelineService,
    internal val locationProvider: IPhotoLocationProvider,
    internal val dailyLogRepository: DailyLogRepository,
    internal val noteRepository: NoteRepository,
    internal val taskRepository: TaskRepository,
    internal val workCategoryRepository: WorkCategoryRepository,
    internal val workPlanRepository: com.mapsupervision.domain.repository.WorkPlanRepository,
    internal val weatherService: WeatherService,
    internal val reportDraftRepository: com.mapsupervision.domain.repository.ReportDraftRepository,
    internal val materialDeclarationRepository: MaterialDeclarationRepository,
    internal val materialHandoverRepository: MaterialHandoverRepository,
    private val observeWorkspaceSnapshot: ObserveWorkspaceSnapshotUseCase,
    private val migrationService: com.mapsupervision.domain.service.ProjectStorageMigrationService
) : ViewModel() {

    internal val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkspaceEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<WorkspaceEffect> = _effects.asSharedFlow()

    internal var mapSearchJob: Job? = null
    internal var aiOpsJob: Job? = null
    internal var filteredMapUpdateJob: Job? = null
    internal val workVolumeProgressPersistJobs = mutableMapOf<String, Job>()
    internal var cachedIndexes = WorkspaceIndexes()
    internal var cachedNodesRef: List<GisNode> = emptyList()
    internal var cachedRoutesRef: List<GisRoute> = emptyList()
    internal var cachedProgressRef: List<NodeProgress> = emptyList()
    internal var cachedWorkVolumeRowsRef: List<WorkVolumeProgress> = emptyList()
    internal var cachedDailyLogsRef: List<DailyLog> = emptyList()
    private var lastAiOpsInput: AiOpsInput? = null
    private var lastMigrationProjectId: String? = null
    internal val directCaptureSaveDeduper = DirectCaptureSaveDeduper()

    // Keep StateFlows for filtered nodes/routes to avoid filtering on every recomposition in the UI.
    private val _filteredNodesForMap = MutableStateFlow<List<GisNode>>(emptyList())
    val filteredNodesForMap: StateFlow<List<GisNode>> = _filteredNodesForMap.asStateFlow()

    private val _filteredRoutesForMap = MutableStateFlow<List<GisRoute>>(emptyList())
    val filteredRoutesForMap: StateFlow<List<GisRoute>> = _filteredRoutesForMap.asStateFlow()

    private val colorPrefs by lazy {
        context.getSharedPreferences("contractor_colors", Context.MODE_PRIVATE)
    }

    internal fun saveContractorColor(projectId: String, contractor: String, hexColor: String) {
        colorPrefs.edit().putString("${projectId}_$contractor", hexColor).apply()
    }

    internal fun loadContractorColors(projectId: String): Map<String, String> {
        val all = colorPrefs.all as? Map<*, *> ?: return emptyMap()
        val prefix = "${projectId}_"
        return all.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            if (key.startsWith(prefix)) {
                key.substring(prefix.length) to value
            } else {
                null
            }
        }.toMap()
    }

    internal val visibilityPrefs by lazy {
        context.getSharedPreferences("hidden_contractors", Context.MODE_PRIVATE)
    }

    internal fun saveContractorVisibility(projectId: String, contractor: String, isHidden: Boolean) {
        visibilityPrefs.edit().putBoolean("${projectId}_$contractor", isHidden).apply()
    }

    internal fun loadHiddenContractors(projectId: String): Set<String> {
        val all = visibilityPrefs.all as? Map<*, *> ?: return emptySet()
        val prefix = "${projectId}_"
        return all.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? Boolean ?: return@mapNotNull null
            if (key.startsWith(prefix) && value) {
                key.substring(prefix.length)
            } else {
                null
            }
        }.toSet()
    }

    init {
        observeWorkspace()
        observeProjectSync()
    }

    fun dispatch(action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.SelectTab -> selectTab(action.tab)
            is WorkspaceAction.UpdateLayoutMode -> {
                _uiState.value = _uiState.value.copy(layoutMode = action.mode)
            }
            is WorkspaceAction.ShowReportPreview -> {
                _uiState.value = _uiState.value.copy(
                    showReportPreview = true,
                    previewNodeCode = action.nodeCode
                )
            }
            WorkspaceAction.DismissReportPreview -> {
                _uiState.value = _uiState.value.copy(
                    showReportPreview = false,
                    previewNodeCode = null
                )
            }
            is WorkspaceAction.SetPendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = action.pendingSharedImport)
            }
            is WorkspaceAction.UpdatePendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = action.pendingSharedImport)
            }
            WorkspaceAction.ClearPendingSharedImport -> {
                _state.value = _state.value.copy(pendingSharedImport = null)
            }
        }
    }

    fun selectTab(tab: WorkspaceTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == WorkspaceTab.MAP) {
            onEnterMapTab()
        }
    }

    fun onReportExported(path: String) {
        if (path.isBlank()) return
        _effects.tryEmit(WorkspaceEffect.OpenExportedFile(path))
    }

    fun showMessage(message: String) {
        if (message.isBlank()) return
        _effects.tryEmit(WorkspaceEffect.ShowMessage(message))
    }

    fun refresh() {
        _state.value = _state.value.copy(
            isRefreshing = true,
            lastRefreshedAtEpochMs = System.currentTimeMillis()
        )
        requestAiOpsRefresh(force = true)
    }

    private fun observeWorkspace() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId
                .flatMapLatest { projectId ->
                    if (projectId.isNullOrBlank()) {
                        lastMigrationProjectId = null
                        flowOf<WorkspaceSnapshot?>(null)
                    } else {
                        scheduleProjectMigration(projectId)
                        observeWorkspaceSnapshot(projectId)
                    }
                }
                .collectLatest { snapshot ->
                    if (snapshot == null) {
                        _state.value = WorkspaceState(
                            importUi = ImportUiState(
                                status = ImportStatus.IDLE,
                                message = "Chưa chọn dự án active"
                            )
                        )
                        return@collectLatest
                    }
                    applySnapshot(snapshot)
                }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.debounce(250).collectLatest { event ->
                val activeProjectId = _state.value.activeProjectId
                if (event.projectId != null && event.projectId != activeProjectId) return@collectLatest
                when (event.reason) {
                    "design_import_completed" -> showMessage("Đã cập nhật dữ liệu thiết kế")
                    "photo_saved" -> showMessage("Đã lưu ảnh hiện trường")
                    "project_imported" -> showMessage("Đã nhập dự án")
                }
            }
        }
    }

    private fun scheduleProjectMigration(projectId: String) {
        if (lastMigrationProjectId == projectId) return
        lastMigrationProjectId = projectId
        viewModelScope.launch(Dispatchers.IO) {
            val projects = (projectRepository.list(true) as? com.mapsupervision.core.result.AppResult.Success)?.data.orEmpty()
            val activeProject = projects.find { it.id == projectId } ?: return@launch
            val migrationStatus = migrationService.migrateProjectIfNeeded(activeProject)
            if (migrationStatus.migrated || migrationStatus.verified) {
                projectSyncRepository.notifyProjectChanged(projectId, "project_migrated")
            }
        }
    }

    internal fun updateFilteredMapData(reason: FilteredMapUpdateReason = FilteredMapUpdateReason.SNAPSHOT) {
        val delayMs = resolveFilteredMapUpdateDelayMs(reason)
        filteredMapUpdateJob?.cancel()
        filteredMapUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            if (delayMs > 0L) delay(delayMs)
            val stateSnapshot = _state.value
            val indexes = ensureIndexes(stateSnapshot)
            val nodes = buildMapDesignNodes(stateSnapshot, indexes)
            val routes = filterRoutes(stateSnapshot.designRoutes, stateSnapshot.mapUi, indexes, nodes)
            if (!shouldPublishFilteredMapData(_filteredNodesForMap.value, _filteredRoutesForMap.value, nodes, routes)) {
                return@launch
            }
            _filteredNodesForMap.value = nodes
            _filteredRoutesForMap.value = routes
        }
    }

    private fun applySnapshot(snapshot: WorkspaceSnapshot) {
        viewModelScope.launch(Dispatchers.Default) {
            val loadedWorkVolumeProgress = snapshot.workVolumeRows.associate { row ->
                "${row.nodeCode}_${row.workName}" to row.actualQty.toInt().toString()
            }
            val dashboard = buildDashboard(
                snapshot.designNodes,
                snapshot.designRoutes,
                snapshot.constructionProgress,
                snapshot.workVolumeRows
            )
            val coordSummary = summarizeNodeCoordinates(snapshot.designNodes)
            AppLogger.d("map.refresh nodes=${snapshot.designNodes.size} routes=${snapshot.designRoutes.size} project=${snapshot.projectId}")
            AppLogger.d(
                "map.refresh.coords project=${snapshot.projectId} nodesValid=${coordSummary.validCount} invalidNodes=${coordSummary.invalidCount} " +
                    "latRange=${coordSummary.latRangeText} lonRange=${coordSummary.lonRangeText}"
            )

            val current = _state.value
            val selectedNodeCode = current.mapUi.selectedNode?.code
            val nextSelectedPhotos = if (selectedNodeCode != null && current.selectedNodePhotos.isNotEmpty()) {
                snapshot.sitePhotos.filter { it.objectCode == selectedNodeCode }
            } else {
                current.selectedNodePhotos
            }

            val savedColors = loadContractorColors(snapshot.projectId)
            val savedHidden = loadHiddenContractors(snapshot.projectId)

            val nextState = applyWorkspaceSnapshotToState(
                current = current,
                snapshot = snapshot,
                dashboard = dashboard,
                savedColors = savedColors,
                savedHidden = savedHidden,
                loadedWorkVolumeProgress = loadedWorkVolumeProgress,
                nextSelectedPhotos = nextSelectedPhotos,
                selectedMapUi = keepMapSelection(current.mapUi, snapshot.designNodes, snapshot.designRoutes),
                refreshedAtEpochMs = System.currentTimeMillis()
            )

            _state.value = nextState
            updateFilteredMapData()
            requestAiOpsRefresh()
        }
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

    private suspend fun runAiOpsRecommendations(state: WorkspaceState) {
        val dashboard = state.dashboard
        val aiOps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                aiOrchestrator.execute<com.mapsupervision.domain.ai.OpsRecommendationResult>(
                    OpsRecommendationPayload(
                        totalNodes = dashboard.totalDesignNodes,
                        delayedNodes = dashboard.delayedCount,
                        completionPercent = dashboard.completionPercent,
                        importWarnings = state.importUi.warnings.size
                    )
                )
            }.getOrNull()
        } ?: return

        _state.value = _state.value.copy(
            mapUi = _state.value.mapUi.copy(
                message = aiOps.result.prioritizedActions.firstOrNull().orEmpty()
            ),
            aiOpsActions = aiOps.result.prioritizedActions,
            aiOpsPriority = aiOps.result.priority
        )
    }

    private fun requestAiOpsRefresh(force: Boolean = false) {
        val stateSnapshot = _state.value
        val nextInput = AiOpsInput(
            totalNodes = stateSnapshot.dashboard.totalDesignNodes,
            delayedNodes = stateSnapshot.dashboard.delayedCount,
            completionPercent = stateSnapshot.dashboard.completionPercent,
            importWarnings = stateSnapshot.importUi.warnings.size
        )
        if (!force && nextInput == lastAiOpsInput) {
            _state.value = _state.value.copy(isRefreshing = false)
            return
        }
        lastAiOpsInput = nextInput
        aiOpsJob?.cancel()
        aiOpsJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(500)
            runAiOpsRecommendations(stateSnapshot)
            _state.value = _state.value.copy(isRefreshing = false)
        }
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
        workVolumeRows: List<WorkVolumeProgress>
    ): DashboardState {
        return WorkspaceProgressHelper.buildDashboard(nodes, routes, progress, workVolumeRows)
    }
}

internal fun applyWorkspaceSnapshotToState(
    current: WorkspaceState,
    snapshot: WorkspaceSnapshot,
    dashboard: DashboardState,
    savedColors: Map<String, String>,
    savedHidden: Set<String>,
    loadedWorkVolumeProgress: Map<String, String>,
    nextSelectedPhotos: List<SitePhoto>,
    selectedMapUi: MapUiState,
    refreshedAtEpochMs: Long
): WorkspaceState {
    return current.copy(
        activeProjectId = snapshot.projectId,
        importedFiles = snapshot.importedFiles,
        designNodes = snapshot.designNodes,
        designRoutes = snapshot.designRoutes,
        constructionProgress = snapshot.constructionProgress,
        dashboard = dashboard,
        mapUi = selectedMapUi.copy(
            contractorColors = savedColors,
            hiddenContractors = savedHidden
        ),
        workVolumeRows = snapshot.workVolumeRows,
        workVolumeProgress = loadedWorkVolumeProgress,
        dailyLogs = snapshot.dailyLogs,
        workCategories = snapshot.workCategories,
        materialHandovers = snapshot.materialHandovers,
        materialDeclarations = snapshot.materialDeclarations,
        workPlans = snapshot.workPlans,
        projectTasks = snapshot.projectTasks,
        selectedNodePhotos = nextSelectedPhotos,
        projectPhotos = snapshot.sitePhotos,
        isRefreshing = false,
        lastRefreshedAtEpochMs = refreshedAtEpochMs
    )
}

private data class AiOpsInput(
    val totalNodes: Int,
    val delayedNodes: Int,
    val completionPercent: Float,
    val importWarnings: Int
)

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

internal fun filterRoutes(
    routes: List<GisRoute>,
    mapUi: MapUiState,
    indexes: WorkspaceIndexes,
    liveNodes: List<GisNode>
): List<GisRoute> {
    val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeMapSearchText(mapUi.searchQuery)
    val liveNodeCodesUpper = liveNodes.map { it.code.trim().uppercase() }.toSet()
    return routes.filter { route ->
        val byContractor = mapUi.filterContractor.isNullOrBlank() ||
            route.contractor.equals(mapUi.filterContractor, ignoreCase = true)
        val byVisibility = !isContractorHidden(mapUi, route.contractor)
        val byQuery = mapUi.searchQuery.isBlank() ||
            indexes.normalizedRouteSearch[route.code].orEmpty().contains(normalizedQuery)
        val byLiveNodes = routeHasRenderablePolyline(route) ||
            liveNodeCodesUpper.contains(route.startNodeCode.trim().uppercase()) ||
            liveNodeCodesUpper.contains(route.endNodeCode.trim().uppercase())
        byContractor && byVisibility && byQuery && byLiveNodes
    }
}

private fun routeHasRenderablePolyline(route: GisRoute): Boolean {
    var validPointCount = 0
    for ((latitude, longitude) in route.points) {
        val isValid = (latitude in -90.0..90.0 && longitude in -180.0..180.0) ||
            (longitude in -90.0..90.0 && latitude in -180.0..180.0)
        if (isValid) {
            validPointCount += 1
            if (validPointCount >= 2) return true
        }
    }
    return false
}

