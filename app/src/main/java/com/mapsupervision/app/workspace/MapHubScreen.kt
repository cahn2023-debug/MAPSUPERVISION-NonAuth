package com.mapsupervision.app.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Category
import androidx.compose.ui.text.style.TextDecoration
import android.graphics.Color as AndroidColor
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.GisScreen
import com.mapsupervision.gis.ui.MapLayerType
import com.mapsupervision.project.ui.ProjectUiState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.mapsupervision.app.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHubScreen(
    designNodes: List<GisNode>,
    designRoutes: List<GisRoute>,
    mapUi: MapUiState,
    routeProperties: List<Pair<String, String>>,
    materialProgress: Map<String, String>,
    contractorOptions: List<String>,
    materialTypeOptions: List<String> = emptyList(),
    selectedNodeMaterialLines: List<PreparedMaterialLine>,
    showNumberOnMap: Boolean,
    colorByContractorOnMap: Boolean,
    projectState: ProjectUiState,
    onRefresh: () -> Unit,
    onRefreshProjects: () -> Unit,
    onCreateProject: (String) -> Unit,
    onSwitchProject: (String) -> Unit,
    onCloneProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSelectNode: (GisNode) -> Unit,
    onSelectRoute: (GisRoute) -> Unit,
    onCloseNodeCard: () -> Unit,
    onCloseRouteCard: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    onMapBaseMapChanged: (MapLayerType) -> Unit,
    onToggleMeasure: () -> Unit,
    onLabelFieldChanged: (GisLabelField) -> Unit,
    onFilterContractorChanged: (String?) -> Unit,
    onFilterMaterialTypeChanged: (String?) -> Unit = {},
    onContractorColorChanged: (String, String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onUpdateMaterialProgress: (String, String, String) -> Unit,
    onViewPhotos: () -> Unit,
    onCapturePicture: () -> Unit,
    onFileReport: (String) -> Unit,
    onAddRouteNote: (String) -> Unit,
    onMeasureDistance: (Double) -> Unit,
    selectedNodePhotos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    onDismissPhotoPopup: () -> Unit = {},
    selectedObjectNotes: List<Note> = emptyList(),
    selectedObjectTasks: List<Task> = emptyList(),
    aiNoteSummary: String = "",
    aiTaskSuggestions: List<String> = emptyList(),
    isAiLoading: Boolean = false,
    onLoadNotesAndTasks: (String) -> Unit = {},
    onAddNote: (String, String) -> Unit = { _, _ -> },
    onDeleteNote: (String, String) -> Unit = { _, _ -> },
    onAddTask: (String, String) -> Unit = { _, _ -> },
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit = { _, _, _ -> },
    onDeleteTask: (String, String) -> Unit = { _, _ -> },
    onSummarizeNotes: (String) -> Unit = {},
    onSuggestTasks: (String) -> Unit = {},
    onExportProject: (com.mapsupervision.domain.model.Project) -> Unit = {},
    onImportProject: (Uri) -> Unit = {},
    onResolveDuplicateProject: (Uri, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDismissDuplicateDialog: () -> Unit = {}
) {
    val defaultPalette = remember { listOf("#f97316", "#22c55e", "#06b6d4", "#a855f7", "#ef4444", "#f59e0b", "#3b82f6") }
    val extendedColorPalette = remember {
        listOf(
            "#f97316", "#22c55e", "#06b6d4", "#a855f7",
            "#ef4444", "#f59e0b", "#3b82f6", "#ec4899",
            "#14b8a6", "#84cc16", "#f43f5e", "#8b5cf6"
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val zipImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImportProject(uri)
    }
    var projectName by remember { mutableStateOf("") }
    var showContractorMenu by remember { mutableStateOf(false) }
    var showMaterialMenu by remember { mutableStateOf(false) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showNotesAndTasksSheet by remember { mutableStateOf(false) }
    var notesAndTasksObjectCode by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showPhotoPopup by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val darkBgColor = colors.background
    val cardBgColor = extendedColors.panelBackgroundAlt
    val orangeColor = extendedColors.mapAccent
    val textColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val dangerColor = extendedColors.danger
    val dividerColor = colors.outlineVariant
    val surfaceColor = colors.surface
    val onSurfaceColor = colors.onSurface
    val onPrimaryColor = colors.onPrimary
    LaunchedEffect(
        designNodes.size,
        designNodes.firstOrNull()?.id,
        designNodes.lastOrNull()?.id
    ) {
        if ((designNodes.isNotEmpty() || designRoutes.isNotEmpty()) &&
            mapUi.selectedNode == null &&
            mapUi.selectedRoute == null
        ) {
            GisMapBridgeRegistry.bridge?.fitToObjects()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = darkBgColor,
                drawerContentColor = textColor,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Quản lý dự án", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = orangeColor)

                    val focusManager = LocalFocusManager.current
                    androidx.compose.material3.OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Tên dự án mới", color = secondaryTextColor) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = orangeColor,
                            unfocusedBorderColor = secondaryTextColor,
                            cursorColor = orangeColor
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (projectName.isNotBlank()) {
                                    onCreateProject(projectName)
                                    projectName = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                        ) { Text("Tạo mới", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = onRefreshProjects,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, secondaryTextColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                        ) { Text("Làm mới") }
                    }

                    OutlinedButton(
                        onClick = { zipImportLauncher.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, orangeColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircle, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp),
                            tint = orangeColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nhập dự án (.zip)", fontWeight = FontWeight.Bold)
                    }

                    if (projectState.importMessage.isNotBlank()) {
                        Text(
                            text = projectState.importMessage,
                            color = orangeColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Text("Danh sách dự án", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(projectState.projects, key = { project -> "${project.id}:${project.slug}" }) { p ->
                            val isActive = projectState.activeProjectId == p.id
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(p.id) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (!isActive) {
                                                    onSwitchProject(p.id)
                                                    scope.launch { drawerState.close() }
                                                } else {
                                                    scope.launch { drawerState.close() }
                                                }
                                            }
                                        )
                                    },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = if (isActive) orangeColor else cardBgColor
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column {
                                        Text(
                                            p.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isActive) onPrimaryColor else textColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Mã: ${p.slug}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isActive) colors.onPrimaryContainer else secondaryTextColor
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .background(colors.primaryContainer, MaterialTheme.shapes.small)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("ĐANG HOẠT ĐỘNG", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Text("KHÔNG HOẠT ĐỘNG", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { onExportProject(p) }) {
                                                 Icon(
                                                     imageVector = Icons.Default.Share,
                                                     contentDescription = "Export Project",
                                                     tint = if (isActive) onPrimaryColor else secondaryTextColor
                                                 )
                                            }
                                            IconButton(onClick = { onCloneProject(p.id, "${p.name} - Copy") }) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Clone", tint = if (isActive) onPrimaryColor else secondaryTextColor)
                                            }
                                            if (!isActive) {
                                                IconButton(onClick = { onDeleteProject(p.id) }) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = dangerColor)
                                                }
                                                Button(
                                                    onClick = {
                                                        onSwitchProject(p.id)
                                                        scope.launch { drawerState.close() }
                                                    },
                                                    shape = MaterialTheme.shapes.small,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                                                ) { Text("Mở", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        if (searchExpanded) {
                            val focusManager = LocalFocusManager.current
                            TextField(
                                value = mapUi.searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Tìm mã node hoặc địa chỉ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MapSupervision", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            onRefreshProjects()
                            scope.launch { drawerState.open() }
                        }) { Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface) }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showContractorMenu = true }) {
                                Icon(
                                    Icons.Outlined.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (mapUi.filterContractor != null) orangeColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showContractorMenu,
                                onDismissRequest = { showContractorMenu = false }
                            ) {
                                // "Tất cả" row
                                val allSelected = mapUi.filterContractor == null
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color.Transparent, CircleShape)
                                                    .border(1.dp, if (allSelected) orangeColor else dividerColor, CircleShape)
                                            )
                                            Text(
                                                "Tất cả",
                                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (allSelected) orangeColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = { onFilterContractorChanged(null) }
                                )
                                contractorOptions.forEach { contractor ->
                                    val isSelected = mapUi.filterContractor == contractor
                                    val customHex = mapUi.contractorColors[contractor]
                                    val defaultHex = run {
                                        defaultPalette[Math.abs(contractor.hashCode()) % defaultPalette.size]
                                    }
                                    val hexColor = customHex ?: defaultHex
                                    val swatchColor = try {
                                        Color(AndroidColor.parseColor(hexColor))
                                    } catch (_: Exception) { orangeColor }

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Color swatch – tap to cycle color
                                                val colorPalette = extendedColorPalette
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(swatchColor, CircleShape)
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) colors.onSurface else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable(
                                                            indication = null,
                                                            interactionSource = remember { MutableInteractionSource() }
                                                        ) {
                                                            val currentIdx = colorPalette.indexOf(hexColor)
                                                            val nextHex = colorPalette[(currentIdx + 1) % colorPalette.size]
                                                            onContractorColorChanged(contractor, nextHex)
                                                        }
                                                )
                                                Text(
                                                    contractor,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) swatchColor else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(swatchColor, CircleShape)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onFilterContractorChanged(
                                                if (isSelected) null else contractor
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMaterialMenu = true }) {
                                Icon(
                                    Icons.Outlined.Category,
                                    contentDescription = "Vật tư",
                                    tint = if (mapUi.filterMaterialType != null) orangeColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showMaterialMenu,
                                onDismissRequest = { showMaterialMenu = false }
                            ) {
                                val allSelected = mapUi.filterMaterialType == null
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color.Transparent, CircleShape)
                                                    .border(1.dp, if (allSelected) orangeColor else dividerColor, CircleShape)
                                            )
                                            Text(
                                                "Tất cả",
                                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (allSelected) orangeColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        onFilterMaterialTypeChanged(null)
                                        showMaterialMenu = false
                                    }
                                )
                                materialTypeOptions.forEach { materialType ->
                                    val isSelected = mapUi.filterMaterialType == materialType
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(if (isSelected) orangeColor else Color.Transparent, CircleShape)
                                                        .border(1.dp, if (isSelected) orangeColor else dividerColor, CircleShape)
                                                )
                                                Text(
                                                    materialType,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) orangeColor else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onFilterMaterialTypeChanged(
                                                if (isSelected) null else materialType
                                            )
                                            showMaterialMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (searchExpanded && mapUi.searchQuery.isNotBlank()) {
                                onRefresh()
                            }
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) {
                                onSearchQueryChanged("")
                            }
                        }) {
                            Icon(
                                if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    GisScreen(
                        nodes = designNodes,
                        routes = designRoutes,
                        showNumberLabels = showNumberOnMap,
                        colorByContractor = colorByContractorOnMap,
                        contractorColors = mapUi.contractorColors,
                        labelField = mapUi.labelField,
                        showNodes = mapUi.showNodes,
                        showRoutes = mapUi.showRoutes,
                        measureEnabled = mapUi.measureEnabled,
                        selectedNode = mapUi.selectedNode,
                        selectedRoute = mapUi.selectedRoute,
                        onNodeClick = onSelectNode,
                        onRouteClick = onSelectRoute,
                        onMeasureDistance = onMeasureDistance
                    )
                }
            }


            // Measure distance banner
            if (mapUi.measureEnabled) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp)
                        .widthIn(max = 300.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (mapUi.measureDistanceText.isNotBlank())
                            dangerColor else cardBgColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icons.Outlined.Straighten.let {
                            Icon(it, contentDescription = null, tint = onSurfaceColor, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = if (mapUi.measureDistanceText.isNotBlank())
                                "onMeasureDistance"
                            else "Chạm 2 điểm để đo",
                            color = onSurfaceColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Search result / message banner � only show during active search
            if (mapUi.message.isNotBlank() && !mapUi.measureEnabled && mapUi.searchQuery.isNotBlank()) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 84.dp)
                        .widthIn(max = 360.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = cardBgColor)
                ) {
                    Text(
                        text = mapUi.message,
                        color = onSurfaceColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 90.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ElevatedCard(shape = RoundedCornerShape(10.dp), colors = CardDefaults.elevatedCardColors(containerColor = surfaceColor)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onZoomIn) { Icon(Icons.Outlined.Add, contentDescription = "Zoom In", tint = onSurfaceColor) }
                        IconButton(onClick = onZoomOut) { Icon(Icons.Outlined.Remove, contentDescription = "Zoom Out", tint = onSurfaceColor) }
                        IconButton(onClick = { GisMapBridgeRegistry.bridge?.fitToObjects() }) {
                            Icon(Icons.Outlined.ZoomOutMap, contentDescription = "Zoom Fit", tint = onSurfaceColor)
                        }
                    }
                }
                ElevatedCard(shape = RoundedCornerShape(10.dp), colors = CardDefaults.elevatedCardColors(containerColor = surfaceColor)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onMyLocation) { Icon(Icons.Outlined.LocationSearching, contentDescription = "Location", tint = onSurfaceColor) }
                        Box {
                            IconButton(onClick = { showLayerMenu = true }) {
                                Icon(Icons.Outlined.Layers, contentDescription = "Layer", tint = onSurfaceColor)
                            }
                            DropdownMenu(expanded = showLayerMenu, onDismissRequest = { showLayerMenu = false }) {
                                DropdownMenuItem(text = { Text("Đường phố") }, onClick = { onMapBaseMapChanged(MapLayerType.STREET); showLayerMenu = false })
                                DropdownMenuItem(text = { Text("Vệ tinh") }, onClick = { onMapBaseMapChanged(MapLayerType.SATELLITE); showLayerMenu = false })
                                DropdownMenuItem(text = { Text("Nền tối") }, onClick = { onMapBaseMapChanged(MapLayerType.DARK); showLayerMenu = false })
                            }
                        }
                        IconButton(onClick = onToggleMeasure) {
                                Icon(
                                    Icons.Outlined.Straighten,
                                    contentDescription = "Measure",
                                    tint = if (mapUi.measureEnabled) dangerColor else onSurfaceColor
                                )
                            }
                        }
                }
            }

            // Popup for selected route � floats over map, no overlay so map touch still works
            val selectedRoute = mapUi.selectedRoute
            if (selectedRoute != null) {
                BackHandler { onCloseRouteCard() }
                // Transparent overlay � click outside the card to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCloseRouteCard() }
                )
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 12.dp, end = 12.dp)
                        .widthIn(max = 720.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks inside card */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thông tin tuyến", fontWeight = FontWeight.Bold)
                            IconButton(onClick = onCloseRouteCard) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close")
                            }
                        }
                        // Properties list
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            routeProperties.forEach { (key, value) ->
                                if (value.isNotBlank()) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "$key: ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.widthIn(min = 120.dp)
                                        )
                                        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                        // Note input
                        val focusManager = LocalFocusManager.current
                        Spacer(modifier = Modifier.padding(top = 4.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = mapUi.routeNote,
                            onValueChange = onAddRouteNote,
                            placeholder = { Text("Thêm ghi chú...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        // Action buttons
                        Spacer(modifier = Modifier.padding(top = 6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onViewPhotos()
                                    showPhotoPopup = true
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)
                            ) { Text("Xem ảnh", fontSize = 10.sp) }
                            Button(
                                onClick = onCapturePicture,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(12.dp))
                                    Text("Chụp ảnh", fontSize = 10.sp)
                                }
                            }
                            Button(
                                onClick = { onFileReport(selectedRoute.code) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) { Text("Báo cáo", fontSize = 10.sp) }
                            Button(
                                onClick = {
                                    notesAndTasksObjectCode = selectedRoute.code
                                    onLoadNotesAndTasks(selectedRoute.code)
                                    showNotesAndTasksSheet = true
                                },
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(12.dp))
                                    Text("Ghi chú & CV", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            val selectedNode = mapUi.selectedNode
            if (selectedNode != null) {
                BackHandler { onCloseNodeCard() }
                // Transparent overlay � click outside the card to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCloseNodeCard() }
                )
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp, start = 12.dp, end = 12.dp)
                        .widthIn(max = 760.dp)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks inside card */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).fillMaxWidth()
                    ) {
                      Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        // Header row: code + status + close
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Mã: ${selectedNode.code}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (mapUi.status.isNotBlank()) {
                                Text(mapUi.status, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            IconButton(onClick = onCloseNodeCard, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                            }
                        }

                        Text(describeNodeByField(selectedNode, mapUi.labelField), color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (selectedNode.contractor.isNotBlank()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("NHÀ THẦU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(selectedNode.contractor, fontSize = 13.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TỌA ĐỘ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("${selectedNode.latitude}, ${selectedNode.longitude}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }

                        // Only show completion/inspection row if data is meaningful
                        if (mapUi.expectedCompletion.isNotBlank() || mapUi.lastInspection.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (mapUi.expectedCompletion.isNotBlank()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("DỰ KIẾN HOÀN THÀNH", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(mapUi.expectedCompletion, fontSize = 13.sp, color = if (mapUi.status.contains("Chậm")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (mapUi.lastInspection.isNotBlank()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("KIỂM TRA GẦN NHẤT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(mapUi.lastInspection, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Text("Vật tư / khối lượng", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        if (selectedNodeMaterialLines.isEmpty()) {
                            Text("Không có dữ liệu vật tư", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))) {
                                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                                    Text("Nội dung", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("KL thiết kế", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Text("KL thi công", modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                                selectedNodeMaterialLines.forEach { materialLine ->
                                    val itemName = materialLine.itemName
                                    val itemCount = materialLine.plannedText
                                    val currentValue = materialLine.actualText
                                    
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(itemName, modifier = Modifier.weight(0.5f), fontSize = 12.sp)
                                        Text(itemCount, modifier = Modifier.weight(0.25f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Box(
                                            modifier = Modifier.weight(0.25f).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val focusManager = LocalFocusManager.current
                                            BasicTextField(
                                                value = currentValue,
                                                onValueChange = { newValue ->
                                                    if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                                                        onUpdateMaterialProgress(selectedNode.id, itemName, newValue)
                                                    }
                                                },
                                                textStyle = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                      } // End scrollable column
                      
                      Spacer(modifier = Modifier.padding(top = 8.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                          OutlinedButton(onClick = { onViewPhotos(); showPhotoPopup = true }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Xem ảnh", fontSize = 11.sp) }
                          Button(onClick = onCapturePicture, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), contentPadding = PaddingValues(horizontal = 4.dp)) {
                              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                  Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(14.dp))
                                  Text("Chụp ảnh", fontSize = 11.sp)
                              }
                          }
                          Button(onClick = { onFileReport(selectedNode.code) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Báo cáo", fontSize = 11.sp) }
                          Button(
                              onClick = {
                                  notesAndTasksObjectCode = selectedNode.code
                                  onLoadNotesAndTasks(selectedNode.code)
                                  showNotesAndTasksSheet = true
                              },
                              modifier = Modifier.weight(1.2f),
                              colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = onPrimaryColor),
                              contentPadding = PaddingValues(horizontal = 4.dp)
                          ) {
                              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                  Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = onPrimaryColor, modifier = Modifier.size(14.dp))
                                  Text("Ghi chú & CV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                              }
                          }
                      }
                    }
                }
            }
        }

        if (showNotesAndTasksSheet && notesAndTasksObjectCode.isNotBlank()) {
            NotesAndTasksBottomSheet(
                objectCode = notesAndTasksObjectCode,
                notes = selectedObjectNotes,
                tasks = selectedObjectTasks,
                aiSummary = aiNoteSummary,
                aiSuggestions = aiTaskSuggestions,
                isAiLoading = isAiLoading,
                onDismiss = { showNotesAndTasksSheet = false },
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onAddTask = onAddTask,
                onToggleTask = onToggleTaskStatus,
                onDeleteTask = onDeleteTask,
                onSummarize = onSummarizeNotes,
                onSuggest = onSuggestTasks
            )
        }

        // Duplicate Project Resolution Dialog
        val duplicateProject = projectState.duplicateProjectToResolve
        val duplicateUri = projectState.duplicateZipUri
        if (duplicateProject != null && duplicateUri != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissDuplicateDialog,
                title = { Text("Trùng lặp dự án", fontWeight = FontWeight.Bold, color = onSurfaceColor) },
                text = {
                    Text(
                        "Dự án với mã '${duplicateProject.slug}' và tên '${duplicateProject.name}' đã tồn tại trong hệ thống. " +
                                "Bạn muốn ghi đè lên dữ liệu cũ hay tạo một dự án mới làm bản sao?",
                        color = secondaryTextColor
                    )
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onResolveDuplicateProject(duplicateUri, false, true) },
                            border = BorderStroke(1.dp, orangeColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor)
                        ) {
                            Text("Tạo bản sao", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onResolveDuplicateProject(duplicateUri, true, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = onSurfaceColor)
                        ) {
                            Text("Ghi đè", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismissDuplicateDialog,
                        border = BorderStroke(1.dp, dividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor)
                    ) {
                        Text("Hủy")
                    }
                },
                containerColor = cardBgColor
            )
        }

        // Photo viewer popup
        if (showPhotoPopup) {
            NodePhotoViewerDialog(
                photos = selectedNodePhotos,
                onDismiss = {
                    showPhotoPopup = false
                    onDismissPhotoPopup()
                }
            )
        }
    }
}

