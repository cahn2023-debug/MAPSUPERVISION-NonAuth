package com.mapsupervision.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.NoteRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.repository.TaskRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.domain.service.WeatherService
import com.mapsupervision.domain.usecase.ObserveWorkspaceSnapshotUseCase
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.storage.importer.UserFileImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
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
    internal val weatherService: WeatherService,
    internal val reportDraftRepository: com.mapsupervision.domain.repository.ReportDraftRepository,
    private val observeWorkspaceSnapshot: ObserveWorkspaceSnapshotUseCase
) : ViewModel() {

    internal val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<WorkspaceEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<WorkspaceEffect> = _effects.asSharedFlow()

    internal var mapSearchJob: Job? = null
    internal var aiOpsJob: Job? = null
    internal val materialProgressPersistJobs = mutableMapOf<String, Job>()
    internal var cachedIndexes = WorkspaceIndexes()
    internal var cachedNodesRef: List<GisNode> = emptyList()
    internal var cachedRoutesRef: List<GisRoute> = emptyList()
    internal var cachedProgressRef: List<NodeProgress> = emptyList()
    internal var cachedMaterialRowsRef: List<MaterialProgress> = emptyList()
    internal var cachedDailyLogsRef: List<DailyLog> = emptyList()
    private var lastAiOpsInput: AiOpsInput? = null

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
                        flowOf<WorkspaceSnapshot?>(null)
                    } else {
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
            projectSyncRepository.events.collectLatest { event ->
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

    private fun applySnapshot(snapshot: WorkspaceSnapshot) {
        val current = _state.value
        val loadedMaterialProgress = snapshot.materialRows.associate { row ->
            "${row.nodeCode}_${row.materialName}" to row.actualQty.toInt().toString()
        }
        val dashboard = buildDashboard(
            snapshot.designNodes,
            snapshot.designRoutes,
            snapshot.constructionProgress,
            snapshot.materialRows
        )
        val coordSummary = summarizeNodeCoordinates(snapshot.designNodes)
        AppLogger.d("map.refresh nodes=${snapshot.designNodes.size} routes=${snapshot.designRoutes.size} project=${snapshot.projectId}")
        AppLogger.d(
            "map.refresh.coords project=${snapshot.projectId} nodesValid=${coordSummary.validCount} invalidNodes=${coordSummary.invalidCount} " +
                "latRange=${coordSummary.latRangeText} lonRange=${coordSummary.lonRangeText}"
        )

        val selectedNodeCode = current.mapUi.selectedNode?.code
        val nextSelectedPhotos = if (selectedNodeCode != null && current.selectedNodePhotos.isNotEmpty()) {
            snapshot.sitePhotos.filter { it.objectCode == selectedNodeCode }
        } else {
            current.selectedNodePhotos
        }

        _state.value = current.copy(
            activeProjectId = snapshot.projectId,
            importedFiles = snapshot.importedFiles,
            designNodes = snapshot.designNodes,
            designRoutes = snapshot.designRoutes,
            constructionProgress = snapshot.constructionProgress,
            dashboard = dashboard,
            mapUi = keepMapSelection(current.mapUi, snapshot.designNodes, snapshot.designRoutes),
            materialRows = snapshot.materialRows,
            materialProgress = loadedMaterialProgress,
            dailyLogs = snapshot.dailyLogs,
            workCategories = snapshot.workCategories,
            selectedNodePhotos = nextSelectedPhotos,
            isRefreshing = false,
            lastRefreshedAtEpochMs = System.currentTimeMillis()
        )

        requestAiOpsRefresh()
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
        val aiOps = runCatching {
            aiOrchestrator.execute<com.mapsupervision.domain.ai.OpsRecommendationResult>(
                OpsRecommendationPayload(
                    totalNodes = dashboard.totalDesignNodes,
                    delayedNodes = dashboard.delayedCount,
                    completionPercent = dashboard.completionPercent,
                    importWarnings = state.importUi.warnings.size
                )
            )
        }.getOrNull() ?: return

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
        materialRows: List<MaterialProgress>
    ): DashboardState {
        return WorkspaceProgressHelper.buildDashboard(nodes, routes, progress, materialRows)
    }
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
