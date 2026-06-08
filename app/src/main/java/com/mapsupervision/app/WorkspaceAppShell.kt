package com.mapsupervision.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mapsupervision.app.workspace.MapHubScreen
import com.mapsupervision.app.workspace.WorkspaceAction
import com.mapsupervision.app.workspace.WorkspaceEffect
import com.mapsupervision.app.workspace.WorkspaceLayoutMode
import com.mapsupervision.app.workspace.WorkspaceTab
import com.mapsupervision.app.workspace.WorkspaceViewModel
import com.mapsupervision.app.workspace.DataHubRoute
import com.mapsupervision.app.workspace.ProgressHubRoute
import com.mapsupervision.app.workspace.addConstructionProgress
import com.mapsupervision.app.workspace.addDailyLog
import com.mapsupervision.app.workspace.addNote
import com.mapsupervision.app.workspace.addTask
import com.mapsupervision.app.workspace.addWorkCategory
import com.mapsupervision.app.workspace.clearCaptureRequest
import com.mapsupervision.app.workspace.clearMapNodeSelection
import com.mapsupervision.app.workspace.clearMapRouteSelection
import com.mapsupervision.app.workspace.clearPhotoNodeFilter
import com.mapsupervision.app.workspace.clearSelectedNodePhotos
import com.mapsupervision.app.workspace.combineImportedFiles
import com.mapsupervision.app.workspace.deleteImportedFile
import com.mapsupervision.app.workspace.deleteNote
import com.mapsupervision.app.workspace.deleteTask
import com.mapsupervision.app.workspace.fetchWeatherAuto
import com.mapsupervision.app.workspace.getDataHubUiState
import com.mapsupervision.app.workspace.getFilteredDesignNodesForMap
import com.mapsupervision.app.workspace.getFilteredDesignRoutes
import com.mapsupervision.app.workspace.getPreviewMaterialRows
import com.mapsupervision.app.workspace.getProgressUiState
import com.mapsupervision.app.workspace.getRouteProperties
import com.mapsupervision.app.workspace.getSelectedNodeMaterialLines
import com.mapsupervision.app.workspace.importDesignFiles
import com.mapsupervision.app.workspace.loadExcelPreview
import com.mapsupervision.app.workspace.loadNonExcelPreview
import com.mapsupervision.app.workspace.loadNotesAndTasks
import com.mapsupervision.app.workspace.loadPhotosForSelectedNode
import com.mapsupervision.app.workspace.onContractorColorChanged
import com.mapsupervision.app.workspace.onFilterContractorChanged
import com.mapsupervision.app.workspace.onMapBaseMapChanged
import com.mapsupervision.app.workspace.onMapMyLocation
import com.mapsupervision.app.workspace.onMapToggleMeasure
import com.mapsupervision.app.workspace.onMapZoomIn
import com.mapsupervision.app.workspace.onMapZoomOut
import com.mapsupervision.app.workspace.onOpenPicker
import com.mapsupervision.app.workspace.onPickerEmpty
import com.mapsupervision.app.workspace.onSearchQueryChanged
import com.mapsupervision.app.workspace.parseExcelToDesign
import com.mapsupervision.app.workspace.parseNonExcelToDesign
import com.mapsupervision.app.workspace.retryFailedImports
import com.mapsupervision.app.workspace.savePhoto
import com.mapsupervision.app.workspace.selectMapNode
import com.mapsupervision.app.workspace.selectMapRoute
import com.mapsupervision.app.workspace.setExcelMappingDialogVisible
import com.mapsupervision.app.workspace.setImportMappingDialogVisible
import com.mapsupervision.app.workspace.suggestTasks
import com.mapsupervision.app.workspace.summarizeNotes
import com.mapsupervision.app.workspace.toggleTaskStatus
import com.mapsupervision.app.workspace.triggerCapture
import com.mapsupervision.app.workspace.updateExcelClassificationMode
import com.mapsupervision.app.workspace.updateExcelCoordinateMode
import com.mapsupervision.app.workspace.updateExcelMapping
import com.mapsupervision.app.workspace.updateImportMappingUi
import com.mapsupervision.app.workspace.updateMapLabelField
import com.mapsupervision.app.workspace.updateMapVisualOptions
import com.mapsupervision.app.workspace.updateMaterialProgress
import com.mapsupervision.app.workspace.updateMeasureDistance
import com.mapsupervision.app.workspace.updateRouteNote
import com.mapsupervision.app.workspace.updateSelectedExcelSheet
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.project.ui.ProjectViewModel
import com.mapsupervision.reporting.ui.ReportPreviewDialog
import com.mapsupervision.reporting.ui.ReportingScreen
import com.mapsupervision.reporting.ui.ReportingViewModel
import java.io.File
import kotlin.collections.List
import kotlinx.coroutines.flow.collectLatest

private data class ShellDestination(
    val tab: WorkspaceTab,
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val shellDestinations = listOf(
    ShellDestination(WorkspaceTab.MAP, "map", "Bản đồ", Icons.Outlined.Map),
    ShellDestination(WorkspaceTab.PROGRESS, "progress", "Tiến độ", Icons.Outlined.Assessment),
    ShellDestination(WorkspaceTab.DATA, "data", "Nhập liệu", Icons.Outlined.CameraAlt),
    ShellDestination(WorkspaceTab.REPORTS, "reports", "Báo cáo", Icons.Outlined.Description)
)

@Composable
fun WorkspaceAppShell(
    photoPipelineService: IPhotoPipelineService,
    locationProvider: IPhotoLocationProvider
) {
    val workspaceViewModel: WorkspaceViewModel = hiltViewModel()
    val projectViewModel: ProjectViewModel = hiltViewModel()
    val reportingViewModel: ReportingViewModel = hiltViewModel()

    val workspaceState by workspaceViewModel.state.collectAsState()
    val workspaceUiState by workspaceViewModel.uiState.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()
    val reportingPhotos by reportingViewModel.photos.collectAsState()
    val lastPdfPath by reportingViewModel.lastReportPath.collectAsState()
    val lastWordPath by reportingViewModel.lastWordReportPath.collectAsState()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 840

    LaunchedEffect(isExpanded) {
        workspaceViewModel.dispatch(
            WorkspaceAction.UpdateLayoutMode(
                if (isExpanded) WorkspaceLayoutMode.EXPANDED else WorkspaceLayoutMode.COMPACT
            )
        )
    }

    LaunchedEffect(workspaceUiState.selectedTab) {
        val targetRoute = shellDestinations.first { it.tab == workspaceUiState.selectedTab }.route
        if (navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    LaunchedEffect(lastPdfPath) {
        if (!lastPdfPath.isNullOrBlank()) {
            workspaceViewModel.onReportExported(lastPdfPath!!)
        }
    }

    LaunchedEffect(lastWordPath) {
        if (!lastWordPath.isNullOrBlank()) {
            workspaceViewModel.onReportExported(lastWordPath!!)
        }
    }

    LaunchedEffect(Unit) {
        workspaceViewModel.effects.collectLatest { effect ->
            when (effect) {
                is WorkspaceEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is WorkspaceEffect.OpenExportedFile -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Đã xuất báo cáo: ${File(effect.path).name}",
                        actionLabel = "Mở"
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        openExportedFile(context, effect.path)
                    }
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val previewPhotos = remember(workspaceUiState.previewNodeCode, reportingPhotos) {
        val nodeCode = workspaceUiState.previewNodeCode
        if (nodeCode == null) emptyList<com.mapsupervision.domain.model.SitePhoto>()
        else reportingPhotos.filter { photo -> photo.objectCode == nodeCode }
    }
    val previewMaterialRows = remember(
        workspaceUiState.previewNodeCode,
        workspaceState.mapUi.selectedNode,
        workspaceState.materialProgress
    ) {
        workspaceViewModel.getPreviewMaterialRows(workspaceUiState.previewNodeCode)
    }

    val navChrome: @Composable () -> Unit = {
        if (workspaceUiState.layoutMode == WorkspaceLayoutMode.EXPANDED) {
            NavigationRail {
                shellDestinations.forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { workspaceViewModel.dispatch(WorkspaceAction.SelectTab(destination.tab)) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        } else {
            NavigationBar {
                shellDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { workspaceViewModel.dispatch(WorkspaceAction.SelectTab(destination.tab)) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    }

    val hostContent: @Composable () -> Unit = {
        NavHost(
            navController = navController,
            startDestination = shellDestinations.first().route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("map") {
                val mapDesignNodes = workspaceViewModel.getFilteredDesignNodesForMap()
                val mapDesignRoutes = workspaceViewModel.getFilteredDesignRoutes()
                MapHubScreen(
                    designNodes = mapDesignNodes,
                    designRoutes = mapDesignRoutes,
                    mapUi = workspaceState.mapUi,
                    routeProperties = workspaceState.mapUi.selectedRoute?.let { workspaceViewModel.getRouteProperties(it) }.orEmpty(),
                    materialProgress = workspaceState.materialProgress,
                    contractorOptions = workspaceViewModel.getDataHubUiState().contractorOptions.filter { it != "Tất cả" },
                    selectedNodeMaterialLines = workspaceViewModel.getSelectedNodeMaterialLines(),
                    showNumberOnMap = workspaceState.excelParserUi.showNumberOnMap,
                    colorByContractorOnMap = workspaceState.excelParserUi.colorByContractorOnMap,
                    projectState = projectState,
                    onRefresh = workspaceViewModel::refresh,
                    onRefreshProjects = projectViewModel::refresh,
                    onSelectNode = workspaceViewModel::selectMapNode,
                    onSelectRoute = workspaceViewModel::selectMapRoute,
                    onUpdateMaterialProgress = workspaceViewModel::updateMaterialProgress,
                    onCloseNodeCard = workspaceViewModel::clearMapNodeSelection,
                    onCloseRouteCard = workspaceViewModel::clearMapRouteSelection,
                    onZoomIn = workspaceViewModel::onMapZoomIn,
                    onZoomOut = workspaceViewModel::onMapZoomOut,
                    onMyLocation = workspaceViewModel::onMapMyLocation,
                    onMapBaseMapChanged = workspaceViewModel::onMapBaseMapChanged,
                    onToggleMeasure = workspaceViewModel::onMapToggleMeasure,
                    onLabelFieldChanged = workspaceViewModel::updateMapLabelField,
                    onFilterContractorChanged = workspaceViewModel::onFilterContractorChanged,
                    onContractorColorChanged = workspaceViewModel::onContractorColorChanged,
                    onSearchQueryChanged = workspaceViewModel::onSearchQueryChanged,
                    onViewPhotos = workspaceViewModel::loadPhotosForSelectedNode,
                    onCapturePicture = workspaceViewModel::triggerCapture,
                    onFileReport = { nodeCode ->
                        workspaceViewModel.dispatch(WorkspaceAction.ShowReportPreview(nodeCode))
                    },
                    onAddRouteNote = workspaceViewModel::updateRouteNote,
                    onMeasureDistance = workspaceViewModel::updateMeasureDistance,
                    onCreateProject = projectViewModel::createProject,
                    onSwitchProject = projectViewModel::switchProject,
                    onCloneProject = projectViewModel::cloneProject,
                    onDeleteProject = projectViewModel::archiveProject,
                    onExportProject = { project -> projectViewModel.exportProject(context, project) },
                    onImportProject = { uri -> projectViewModel.importProject(context, uri) },
                    onResolveDuplicateProject = { uri, overwrite, createCopy ->
                        projectViewModel.importProject(context, uri, overwrite, createCopy)
                    },
                    onDismissDuplicateDialog = projectViewModel::dismissDuplicateDialog,
                    selectedNodePhotos = workspaceState.selectedNodePhotos,
                    onDismissPhotoPopup = workspaceViewModel::clearSelectedNodePhotos,
                    selectedObjectNotes = workspaceState.selectedObjectNotes,
                    selectedObjectTasks = workspaceState.selectedObjectTasks,
                    aiNoteSummary = workspaceState.aiNoteSummary,
                    aiTaskSuggestions = workspaceState.aiTaskSuggestions,
                    isAiLoading = workspaceState.isAiLoading,
                    onLoadNotesAndTasks = workspaceViewModel::loadNotesAndTasks,
                    onAddNote = workspaceViewModel::addNote,
                    onDeleteNote = workspaceViewModel::deleteNote,
                    onAddTask = workspaceViewModel::addTask,
                    onToggleTaskStatus = workspaceViewModel::toggleTaskStatus,
                    onDeleteTask = workspaceViewModel::deleteTask,
                    onSummarizeNotes = workspaceViewModel::summarizeNotes,
                    onSuggestTasks = workspaceViewModel::suggestTasks
                )
            }
            composable("progress") {
                ProgressHubRoute(
                    activeProjectId = workspaceState.activeProjectId,
                    constructionProgress = workspaceState.constructionProgress,
                    dailyLogs = workspaceState.dailyLogs,
                    dashboardState = workspaceState.dashboard,
                    progressUiState = workspaceViewModel.getProgressUiState(),
                    workCategories = workspaceState.workCategories,
                    photos = reportingPhotos,
                    activeProjectName = projectState.projects.firstOrNull { it.id == projectState.activeProjectId }?.name,
                    onAddConstruction = workspaceViewModel::addConstructionProgress,
                    onAddDailyLog = { workItem, manpower, note, weather, temp, nodeCode, dateDay, volume, unit, categoryName ->
                        workspaceViewModel.addDailyLog(workItem, manpower, note, weather, temp, nodeCode, dateDay, volume, unit, categoryName)
                    },
                    onAddWorkCategory = workspaceViewModel::addWorkCategory,
                    onFetchWeatherAuto = workspaceViewModel::fetchWeatherAuto
                )
            }
            composable("data") {
                DataHubRoute(
                    state = workspaceState,
                    dataHubUiState = workspaceViewModel.getDataHubUiState(),
                    onOpenPicker = workspaceViewModel::onOpenPicker,
                    onPickerEmpty = workspaceViewModel::onPickerEmpty,
                    onUploadDesign = workspaceViewModel::importDesignFiles,
                    onLoadNonExcelPreview = { uri, existingFileId -> workspaceViewModel.loadNonExcelPreview(uri, existingFileId) },
                    onUpdateImportMappingUi = workspaceViewModel::updateImportMappingUi,
                    onSetImportMappingDialogVisible = workspaceViewModel::setImportMappingDialogVisible,
                    onParseNonExcelToDesign = workspaceViewModel::parseNonExcelToDesign,
                    onRetryFailedImports = workspaceViewModel::retryFailedImports,
                    onLoadExcelPreview = { uri, existingFileId -> workspaceViewModel.loadExcelPreview(uri, existingFileId) },
                    onUpdateExcelMapping = workspaceViewModel::updateExcelMapping,
                    onUpdateExcelClassificationMode = workspaceViewModel::updateExcelClassificationMode,
                    onSetExcelMappingDialogVisible = workspaceViewModel::setExcelMappingDialogVisible,
                    onUpdateExcelCoordinateMode = workspaceViewModel::updateExcelCoordinateMode,
                    onUpdateMapVisualOptions = workspaceViewModel::updateMapVisualOptions,
                    onParseExcelToDesign = workspaceViewModel::parseExcelToDesign,
                    onAddConstruction = workspaceViewModel::addConstructionProgress,
                    onUpdateMaterialProgress = workspaceViewModel::updateMaterialProgress,
                    onOpenNodeOnMap = { node ->
                        workspaceViewModel.selectMapNode(node)
                        workspaceViewModel.dispatch(WorkspaceAction.SelectTab(WorkspaceTab.MAP))
                    },
                    onOpenRouteOnMap = { route ->
                        workspaceViewModel.selectMapRoute(route)
                        workspaceViewModel.dispatch(WorkspaceAction.SelectTab(WorkspaceTab.MAP))
                    },
                    onDeleteImportedFile = workspaceViewModel::deleteImportedFile,
                    photoFilterNodeCode = workspaceState.photoFilterNodeCode,
                    onClearPhotoFilter = workspaceViewModel::clearPhotoNodeFilter,
                    onLoadNotesAndTasks = workspaceViewModel::loadNotesAndTasks,
                    onAddNote = workspaceViewModel::addNote,
                    onDeleteNote = workspaceViewModel::deleteNote,
                    onAddTask = workspaceViewModel::addTask,
                    onToggleTaskStatus = workspaceViewModel::toggleTaskStatus,
                    onDeleteTask = workspaceViewModel::deleteTask,
                    onSummarizeNotes = workspaceViewModel::summarizeNotes,
                    onSuggestTasks = workspaceViewModel::suggestTasks,
                    onCombineFiles = workspaceViewModel::combineImportedFiles,
                    onUpdateSelectedExcelSheet = workspaceViewModel::updateSelectedExcelSheet,
                    onRefresh = workspaceViewModel::refresh
                )
            }
            composable("reports") {
                ReportingScreen(
                    activeProjectId = projectState.activeProjectId,
                    photoFilterNodeCode = workspaceState.photoFilterNodeCode,
                    photoSaveCount = workspaceState.photoSaveCount,
                    materialProgress = workspaceState.materialProgress,
                    onClearPhotoFilter = workspaceViewModel::clearPhotoNodeFilter
                )
            }
        }
    }

    if (workspaceUiState.layoutMode == WorkspaceLayoutMode.EXPANDED) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            navChrome()
            Box(modifier = Modifier.weight(1f)) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        hostContent()
                    }
                }
            }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = navChrome,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                hostContent()
            }
        }
    }

    val pendingCapture = workspaceState.pendingCaptureNodeCode
    if (pendingCapture != null) {
        CameraOverlay(
            nodeCode = pendingCapture,
            projectId = workspaceState.activeProjectId ?: "",
            photoPipelineService = photoPipelineService,
            locationProvider = locationProvider,
            onPhotoCaptured = workspaceViewModel::refresh,
            onSavePhoto = { file -> workspaceViewModel.savePhoto(file, pendingCapture) },
            onDismiss = workspaceViewModel::clearCaptureRequest
        )
    }

    ReportPreviewDialog(
        showDialog = workspaceUiState.showReportPreview,
        onDismiss = { workspaceViewModel.dispatch(WorkspaceAction.DismissReportPreview) },
        projectId = projectState.activeProjectId ?: "",
        filterNodeCode = workspaceUiState.previewNodeCode,
        selectedExportFormat = "PDF",
        onConfirmExport = { format ->
            if (format == "PDF") {
                reportingViewModel.exportPdf(workspaceUiState.previewNodeCode)
            } else {
                reportingViewModel.exportWord(workspaceUiState.previewNodeCode)
            }
            workspaceViewModel.dispatch(WorkspaceAction.DismissReportPreview)
        },
        photos = previewPhotos,
        materialRows = previewMaterialRows,
        aiDraft = null
    )
}

private fun openExportedFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val type = when {
        file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        file.name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Mở báo cáo"))
}
