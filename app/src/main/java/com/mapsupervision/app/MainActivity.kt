package com.mapsupervision.app

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapsupervision.app.ui.theme.MapSupervisionTheme
import com.mapsupervision.app.workspace.DataHubScreen
import com.mapsupervision.app.workspace.MapHubScreen
import com.mapsupervision.app.workspace.ProgressHubScreen
import com.mapsupervision.app.workspace.WorkspaceViewModel
import com.mapsupervision.app.workspace.*
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.project.ui.ProjectViewModel
import com.mapsupervision.reporting.ui.ReportingScreen
import com.mapsupervision.reporting.ui.ReportPreviewDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var photoPipelineService: IPhotoPipelineService
    @Inject lateinit var locationProvider: IPhotoLocationProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapSupervisionTheme {
                StartupPermissionWrapper {
                    MainShell(photoPipelineService, locationProvider)
                }
            }
        }
    }
}

@Composable
private fun StartupPermissionWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissionsToRequest = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    var allPermissionsGranted by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val hasLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val hasCamera = results[Manifest.permission.CAMERA] == true
        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                    results[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        allPermissionsGranted = hasLocation && hasCamera && hasStorage
    }

    if (allPermissionsGranted) {
        content()
    } else {
        PermissionIntroScreen(
            onRequestPermissions = {
                launcher.launch(permissionsToRequest)
            }
        )
    }
}

@Composable
private fun PermissionIntroScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E3C72),
                        Color(0xFF2A5298)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E78C8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Cấp quyền truy cập",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3C72)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Để MapSupervision hoạt động ổn định và trơn tru, vui lòng cấp các quyền truy cập cần thiết bên dưới:",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionItem(
                    icon = "GPS",
                    title = "Vị trí địa lý",
                    description = "Hiển thị vị trí trên bản đồ và định vị chính xác khi ghi nhận tiến độ."
                )
                PermissionItem(
                    icon = "CAM",
                    title = "Máy ảnh & Đèn Flash",
                    description = "Chụp ảnh hiện trường trực tiếp và đóng dấu thông tin thực địa."
                )
                PermissionItem(
                    icon = "FILE",
                    title = "Ảnh & Video",
                    description = "Chọn hình ảnh, tài liệu hoặc tệp thiết kế thi công từ thư viện."
                )
            }

            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E78C8),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Cấp quyền & Tiếp tục",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE3F2FD)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF7F8C8D),
                lineHeight = 16.sp
            )
        }
    }
}
private enum class Tab(val label: String) {
    MAP("Bản đồ"), PROGRESS("Tiến độ và nhật ký"), PHOTOS("Nhập liệu"), REPORTS("Báo cáo")
}

@Composable
private fun MainShell(
    photoPipelineService: IPhotoPipelineService,
    locationProvider: IPhotoLocationProvider
) {
    var selected by remember { mutableStateOf(Tab.MAP) }
    val workspaceViewModel: WorkspaceViewModel = hiltViewModel()
    val projectViewModel: ProjectViewModel = hiltViewModel()
    val workspaceState by workspaceViewModel.state.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()
    val activity = LocalContext.current as MainActivity

    // Reporting integrations
    val reportingViewModel: com.mapsupervision.reporting.ui.ReportingViewModel = hiltViewModel()
    val reportingPhotos by reportingViewModel.photos.collectAsState()

    var previewNodeCode by remember { mutableStateOf<String?>(null) }
    var showPreviewFromMap by remember { mutableStateOf(false) }
    var exportedFilePath by remember { mutableStateOf<String?>(null) }

    val previewPhotos = remember(previewNodeCode, reportingPhotos) {
        if (previewNodeCode == null) emptyList()
        else reportingPhotos.filter { it.objectCode == previewNodeCode }
    }

    val previewMaterialRows = remember(previewNodeCode, workspaceState.mapUi.selectedNode, workspaceState.materialProgress) {
        workspaceViewModel.getPreviewMaterialRows(previewNodeCode)
    }

    val lastPdfPath by reportingViewModel.lastReportPath.collectAsState()
    val lastWordPath by reportingViewModel.lastWordReportPath.collectAsState()

    LaunchedEffect(projectState.activeProjectId) {
        if (projectState.activeProjectId != null) {
            reportingViewModel.refreshReportData()
        }
    }

    LaunchedEffect(lastPdfPath) {
        if (!lastPdfPath.isNullOrBlank()) {
            exportedFilePath = lastPdfPath
        }
    }

    LaunchedEffect(lastWordPath) {
        if (!lastWordPath.isNullOrBlank()) {
            exportedFilePath = lastWordPath
        }
    }

    LaunchedEffect(Unit) {
        projectViewModel.refresh()
    }

    LaunchedEffect(projectState.activeProjectId) {
        if (projectState.activeProjectId != null && selected == Tab.MAP) {
            workspaceViewModel.onEnterMapTab()
        }
    }

    LaunchedEffect(selected) {
        if (selected == Tab.MAP) {
            workspaceViewModel.onEnterMapTab()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.MAP -> Icons.Outlined.Map
                                        Tab.PROGRESS -> Icons.Outlined.Assessment
                                        Tab.PHOTOS -> Icons.Outlined.CameraAlt
                                        Tab.REPORTS -> Icons.Outlined.Description
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                // 1. Map Tab - ALWAYS alive in the background for instant switching
                // We use graphicsLayer to hide/show and move it off-screen when not active to avoid click issues.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val isMapSelected = selected == Tab.MAP
                            alpha = if (isMapSelected) 1f else 0f
                            // Move way off-screen when not selected so it doesn't intercept clicks
                            translationX = if (isMapSelected) 0f else 100000f 
                        }
                ) {
                    val mapDesignNodes = remember(
                        workspaceState.activeProjectId,
                        workspaceState.designNodes,
                        workspaceState.designRoutes,
                        workspaceState.mapUi.filterContractor,
                        workspaceState.mapUi.searchQuery
                    ) {
                        workspaceViewModel.getFilteredDesignNodesForMap()
                    }
                    val mapDesignRoutes = remember(
                        workspaceState.activeProjectId,
                        workspaceState.designNodes,
                        workspaceState.designRoutes,
                        workspaceState.mapUi.filterContractor,
                        workspaceState.mapUi.searchQuery
                    ) {
                        workspaceViewModel.getFilteredDesignRoutes()
                    }

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
                        onViewPhotos = {
                            workspaceViewModel.loadPhotosForSelectedNode()
                        },
                        onCapturePicture = {
                            workspaceViewModel.triggerCapture()
                        },
                        onFileReport = { nodeCode ->
                            previewNodeCode = nodeCode
                            showPreviewFromMap = true
                        },
                        onAddRouteNote = workspaceViewModel::updateRouteNote,
                        onMeasureDistance = workspaceViewModel::updateMeasureDistance,
                        onCreateProject = { name -> projectViewModel.createProject(name) },
                        onSwitchProject = { projectId -> projectViewModel.switchProject(projectId) },
                        onCloneProject = { id, newName -> projectViewModel.cloneProject(id, newName) },
                        onDeleteProject = { id -> projectViewModel.archiveProject(id) },
                        onExportProject = { project -> projectViewModel.exportProject(activity, project) },
                        onImportProject = { uri -> projectViewModel.importProject(activity, uri) },
                        onResolveDuplicateProject = { uri, overwrite, createCopy ->
                            projectViewModel.importProject(activity, uri, overwrite, createCopy)
                        },
                        onDismissDuplicateDialog = { projectViewModel.dismissDuplicateDialog() },
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

                // 2. Functional Tabs - Overlay Map when selected
                if (selected != Tab.MAP) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        when (selected) {
                            Tab.PROGRESS -> ProgressHubScreen(
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
                            Tab.PHOTOS -> DataHubScreen(
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
                                    selected = Tab.MAP
                                },
                                onOpenRouteOnMap = { route ->
                                    workspaceViewModel.selectMapRoute(route)
                                    selected = Tab.MAP
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
                            Tab.REPORTS -> ReportingScreen(
                                activeProjectId = projectState.activeProjectId,
                                photoFilterNodeCode = workspaceState.photoFilterNodeCode,
                                photoSaveCount = workspaceState.photoSaveCount,
                                materialProgress = workspaceState.materialProgress,
                                onClearPhotoFilter = workspaceViewModel::clearPhotoNodeFilter
                            )
                            else -> {}
                        }
                    }
                }
            }
        }

        // Camera overlay
        val pendingCapture = workspaceState.pendingCaptureNodeCode
        if (pendingCapture != null) {
            CameraOverlay(
                nodeCode = pendingCapture,
                projectId = workspaceState.activeProjectId ?: "",
                photoPipelineService = photoPipelineService,
                locationProvider = locationProvider,
                onPhotoCaptured = { workspaceViewModel.refresh() },
                onSavePhoto = { file -> workspaceViewModel.savePhoto(file, pendingCapture) },
                onDismiss = { workspaceViewModel.clearCaptureRequest() }
            )
        }

        ReportPreviewDialog(
            showDialog = showPreviewFromMap,
            onDismiss = { showPreviewFromMap = false },
            projectId = projectState.activeProjectId ?: "",
            filterNodeCode = previewNodeCode,
            selectedExportFormat = "PDF",
            onConfirmExport = { format ->
                if (format == "PDF") {
                    reportingViewModel.exportPdf(previewNodeCode)
                } else {
                    reportingViewModel.exportWord(previewNodeCode)
                }
                showPreviewFromMap = false
            },
            photos = previewPhotos,
            materialRows = previewMaterialRows,
            aiDraft = null
        )

        if (exportedFilePath != null) {
            AlertDialog(
                onDismissRequest = { exportedFilePath = null },
                title = { Text("Xuất Báo Cáo Thành Công", fontWeight = FontWeight.Bold) },
                text = { Text("Đã lưu báo cáo tại:\n${exportedFilePath?.substringAfterLast("/")}") },
                confirmButton = {
                    Button(
                        onClick = {
                            val file = java.io.File(exportedFilePath!!)
                            if (file.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    activity,
                                    "${activity.packageName}.fileprovider",
                                    file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, if (file.name.endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                activity.startActivity(android.content.Intent.createChooser(intent, "Mở báo cáo"))
                            }
                            exportedFilePath = null
                        }
                    ) {
                        Text("Mở Báo Cáo")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            val file = java.io.File(exportedFilePath!!)
                            if (file.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    activity,
                                    "${activity.packageName}.fileprovider",
                                    file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = if (file.name.endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                activity.startActivity(android.content.Intent.createChooser(intent, "Chia sẻ báo cáo"))
                            }
                            exportedFilePath = null
                        }
                    ) {
                        Text("Chia sẻ")
                    }
                }
            )
        }
    }
}
