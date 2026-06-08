package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.Note
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.storage.importer.ExcelClassificationMode
import com.mapsupervision.app.workspace.WorkspaceImportHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHubScreen(
    state: WorkspaceState,
    dataHubUiState: DataHubUiState,
    onOpenPicker: () -> Unit,
    onPickerEmpty: () -> Unit,
    onUploadDesign: (List<Uri>) -> Unit,
    onLoadNonExcelPreview: (Uri, String?) -> Unit,
    onUpdateImportMappingUi: (String?, String?, String?, String?, String?, String?, String?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onSetImportMappingDialogVisible: (Boolean) -> Unit,
    onParseNonExcelToDesign: () -> Unit,
    onRetryFailedImports: () -> Unit,
    onLoadExcelPreview: (Uri, String?) -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateExcelClassificationMode: (ExcelClassificationMode) -> Unit,
    onSetExcelMappingDialogVisible: (Boolean) -> Unit,
    onUpdateExcelCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onParseExcelToDesign: () -> Unit,
    onAddConstruction: (String, Float, Float) -> Unit,
    onUpdateMaterialProgress: (String, String, String) -> Unit,
    onOpenNodeOnMap: (GisNode) -> Unit,
    onOpenRouteOnMap: (GisRoute) -> Unit,
    onDeleteImportedFile: (String) -> Unit,
    photoFilterNodeCode: String?,
    onClearPhotoFilter: () -> Unit,
    onLoadNotesAndTasks: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarizeNotes: (String) -> Unit,
    onSuggestTasks: (String) -> Unit,
    onCombineFiles: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit,
    onUpdateSelectedExcelSheet: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    var isDesignTab by remember { mutableStateOf(true) }

    val darkBgColor = Color(0xFF1B2130)
    val cardBgColor = Color(0xFF262D3D)
    val orangeColor = Color(0xFFF5A623)
    val textColor = Color(0xFFF8FAFC)
    val secondaryTextColor = Color(0xFF94A3B8)

    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            onPickerEmpty()
        } else {
            if (uris.size == 1) {
                val uri = uris.first()
                val mimeType = context.contentResolver.getType(uri)?.lowercase(java.util.Locale.US)
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }.orEmpty()
                val extFromName = displayName.substringAfterLast('.', "").lowercase(java.util.Locale.US)
                val extFromUri = uri.toString().substringAfterLast('.', "").lowercase(java.util.Locale.US)
                val ext = when {
                    extFromName.isNotBlank() -> extFromName
                    extFromUri.length in 2..5 -> extFromUri
                    mimeType?.contains("kmz") == true -> "kmz"
                    mimeType?.contains("kml") == true -> "kml"
                    mimeType?.contains("json") == true -> "json"
                    else -> ""
                }
                val isExcel = mimeType?.contains("excel") == true || mimeType?.contains("spreadsheet") == true ||
                        ext == "xls" || ext == "xlsx"
                val isKmlOrKmz = ext == "kml" || ext == "kmz" ||
                        mimeType?.contains("kml") == true || mimeType?.contains("kmz") == true
                val isGeoJson = ext == "geojson" || ext == "json" || mimeType?.contains("json") == true
                if (isExcel) {
                    onLoadExcelPreview(uri, null)
                } else if (isKmlOrKmz) {
                    // KML/KMZ geometry requires field mapping. Load preview to show mapping dialog.
                    onLoadNonExcelPreview(uri, null)
                } else if (isGeoJson) {
                    onLoadNonExcelPreview(uri, null)
                } else {
                    onUploadDesign(uris)
                }
            } else {
                onUploadDesign(uris)
            }
        }
    }

    if (state.excelParserUi.showMappingDialog) {
        ExcelMappingDialog(
            state = state.excelParserUi,
            onDismiss = { onSetExcelMappingDialogVisible(false) },
            onUpdateExcelMapping = onUpdateExcelMapping,
            onUpdateCoordinateMode = onUpdateExcelCoordinateMode,
            onUpdateMapVisualOptions = onUpdateMapVisualOptions,
            onConfirmParse = onParseExcelToDesign,
            onUpdateSelectedSheet = onUpdateSelectedExcelSheet
        )
    }

    if (state.importMappingUi.showMappingDialog) {
        NonExcelMappingDialog(
            state = state.importMappingUi,
            onDismiss = { onSetImportMappingDialogVisible(false) },
            onUpdateMapping = onUpdateImportMappingUi,
            onConfirmParse = onParseNonExcelToDesign
        )
    }

    Scaffold(
        containerColor = darkBgColor
    ) { paddingValues ->
        WorkspaceRefreshContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quản lý Danh sách & Dữ liệu", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Quản lý thiết kế và cập nhật tiến độ thi công hạ tầng kỹ thuật.", color = secondaryTextColor, fontSize = 14.sp)
                }
                
                if (isDesignTab) {
                    IconButton(
                        onClick = {
                            onOpenPicker()
                            picker.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/vnd.google-earth.kml+xml",
                                    "application/vnd.google-earth.kmz",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .background(orangeColor, RoundedCornerShape(8.dp))
                            .size(48.dp)
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = "Tải lên dữ liệu", tint = Color.Black)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Thi Công Tab
                Button(
                    onClick = { isDesignTab = false },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isDesignTab) orangeColor else cardBgColor,
                        contentColor = if (!isDesignTab) Color.Black else secondaryTextColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Handyman, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THI CÔNG", fontWeight = FontWeight.Bold)
                }
                
                // Thiết Kế Tab
                Button(
                    onClick = { isDesignTab = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDesignTab) orangeColor else cardBgColor,
                        contentColor = if (isDesignTab) Color.Black else secondaryTextColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Architecture, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THIẾT KẾ", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            DesignTabContent(
                state = state,
                dataHubUiState = dataHubUiState,
                cardBgColor = cardBgColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                orangeColor = orangeColor,
                onLoadExcelPreview = onLoadExcelPreview,
                onLoadNonExcelPreview = onLoadNonExcelPreview,
                onDeleteImportedFile = onDeleteImportedFile,
                isDesignTab = isDesignTab,
                onUpdateMaterialProgress = onUpdateMaterialProgress,
                onOpenNodeOnMap = onOpenNodeOnMap,
                onOpenRouteOnMap = onOpenRouteOnMap,
                onLoadNotesAndTasks = onLoadNotesAndTasks,
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onAddTask = onAddTask,
                onToggleTaskStatus = onToggleTaskStatus,
                onDeleteTask = onDeleteTask,
                onSummarizeNotes = onSummarizeNotes,
                onSuggestTasks = onSuggestTasks,
                onCombineFiles = onCombineFiles
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignTabContent(
    state: WorkspaceState,
    dataHubUiState: DataHubUiState,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    orangeColor: Color,
    onLoadExcelPreview: (Uri, String?) -> Unit,
    onLoadNonExcelPreview: (Uri, String?) -> Unit,
    onDeleteImportedFile: (String) -> Unit,
    isDesignTab: Boolean,
    onUpdateMaterialProgress: (String, String, String) -> Unit,
    onOpenNodeOnMap: (GisNode) -> Unit,
    onOpenRouteOnMap: (GisRoute) -> Unit,
    onLoadNotesAndTasks: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarizeNotes: (String) -> Unit,
    onSuggestTasks: (String) -> Unit,
    onCombineFiles: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var contractorFilter by remember { mutableStateOf("Chọn nhà thầu") }
    var expanded by remember { mutableStateOf(false) }
    // "Tất cả" | "Vị trí" | "Tuyến"
    var objectTypeFilter by remember { mutableStateOf("Tất cả") }
    // "Mã A→Z" | "Mã Z→A"
    var sortOrder by remember { mutableStateOf("Mã A→Z") }
    var sortExpanded by remember { mutableStateOf(false) }

    var showNotesAndTasksSheet by remember { mutableStateOf(false) }
    var notesAndTasksObjectCode by remember { mutableStateOf("") }


    var draggedFile by remember { mutableStateOf<ImportedFile?>(null) }
    var selectedFile by remember { mutableStateOf<ImportedFile?>(null) }
    var fileBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var combineTarget by remember { mutableStateOf<Pair<ImportedFile, ImportedFile>?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    if (combineTarget != null) {
        CombineFilesDialog(
            file1 = combineTarget!!.first,
            file2 = combineTarget!!.second,
            state = state,
            onDismiss = { combineTarget = null },
            onConfirm = { f1, f2, nodes, routes ->
                onCombineFiles(f1, f2, nodes, routes)
                combineTarget = null
            }
        )
    }
    
    
    val contractors = dataHubUiState.contractorOptions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dropdown Filter
        Box(modifier = Modifier.weight(0.4f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = if (contractorFilter == "Chọn nhà thầu") "Chọn nhà thầu" else contractorFilter,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    fontSize = 12.sp
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(cardBgColor)
            ) {
                contractors.forEach { contractor ->
                    DropdownMenuItem(
                        text = { Text(contractor, color = textColor) },
                        onClick = {
                            contractorFilter = contractor
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Search bar
        val searchFocusManager = LocalFocusManager.current
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm theo mã/tên nút...", color = secondaryTextColor, fontSize = 12.sp) },
            modifier = Modifier.weight(0.6f).height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = orangeColor,
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = cardBgColor,
                unfocusedContainerColor = cardBgColor
            ),
            trailingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = secondaryTextColor)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searchFocusManager.clearFocus() }),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Filter row 2: loại đối tượng + sắp xếp ───────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Tất cả", "Vị trí", "Tuyến").forEach { type ->
            val selected = objectTypeFilter == type
            FilterChip(
                selected = selected,
                onClick = { objectTypeFilter = type },
                label = { Text(type, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = orangeColor,
                    containerColor = cardBgColor,
                    selectedLabelColor = Color.Black,
                    labelColor = secondaryTextColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = Color(0xFF334155),
                    selectedBorderColor = orangeColor
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(sortOrder, fontSize = 11.sp, color = secondaryTextColor)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
                modifier = Modifier.background(cardBgColor)
            ) {
                listOf("Mã A→Z", "Mã Z→A").forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = textColor, fontSize = 13.sp) },
                        onClick = { sortOrder = option; sortExpanded = false }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (state.importedFiles.isNotEmpty()) {
        Text("Danh sách file tải lên:", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.importedFiles, key = { it.id }) { file ->
                val isDragged = draggedFile?.id == file.id
                val isSelected = selectedFile?.id == file.id
                val isCombineTarget = draggedFile != null && draggedFile?.id != file.id

                Column {
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                fileBounds = fileBounds + (file.id to bounds)
                            }
                            .pointerInput(file) {
                                detectTapGestures(
                                    onTap = {
                                        // Bỏ chọn nếu đang chọn file này
                                        selectedFile = if (isSelected) null else null
                                    },
                                    onDoubleTap = {
                                        selectedFile = null
                                        val uri = Uri.fromFile(File(file.storedPath))
                                        val ft = file.fileType.lowercase(java.util.Locale.US)
                                        if (ft == "xlsx" || ft == "xls") {
                                            onLoadExcelPreview(uri, file.id)
                                        } else {
                                            onLoadNonExcelPreview(uri, file.id)
                                        }
                                    },
                                    onLongPress = {
                                        selectedFile = if (isSelected) null else file
                                    }
                                )
                            }
                            .pointerInput(file) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        selectedFile = file
                                        draggedFile = file
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    },
                                    onDragEnd = {
                                        val finalPos = fileBounds[file.id]?.center?.plus(dragOffset)
                                        draggedFile = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero

                                        if (finalPos != null) {
                                            val droppedOn = fileBounds.entries.find { (id, rect) ->
                                                id != file.id && rect.contains(finalPos)
                                            }?.key

                                            if (droppedOn != null) {
                                                val targetFile = state.importedFiles.find { it.id == droppedOn }
                                                if (targetFile != null) {
                                                    selectedFile = null
                                                    combineTarget = Pair(file, targetFile)
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggedFile = null
                                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    }
                                )
                            }
                            .offset {
                                if (isDragged) androidx.compose.ui.unit.IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt())
                                else androidx.compose.ui.unit.IntOffset.Zero
                            }
                            .border(
                                width = if (isSelected || isDragged) 2.dp else if (isCombineTarget) 1.dp else 0.dp,
                                color = when {
                                    isSelected || isDragged -> orangeColor
                                    isCombineTarget -> orangeColor.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (isSelected) orangeColor else orangeColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = file.fileName,
                                    color = if (isSelected) orangeColor else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Action bar hiện khi file được chọn (long-press)
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.width(200.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Nút Xóa
                            Button(
                                onClick = {
                                    selectedFile = null
                                    onDeleteImportedFile(file.id)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            // Clickable "Gộp file" dropdown
                            var showCombineMenu by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { showCombineMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MergeType,
                                        contentDescription = null,
                                        tint = orangeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gộp file", fontSize = 11.sp, color = orangeColor)
                                }

                                DropdownMenu(
                                    expanded = showCombineMenu,
                                    onDismissRequest = { showCombineMenu = false },
                                    modifier = Modifier.background(cardBgColor)
                                ) {
                                    state.importedFiles.filter { it.id != file.id }.forEach { otherFile ->
                                        DropdownMenuItem(
                                            text = { Text(otherFile.fileName, color = textColor, fontSize = 12.sp) },
                                            onClick = {
                                                showCombineMenu = false
                                                selectedFile = null
                                                combineTarget = Pair(file, otherFile)
                                            }
                                        )
                                    }
                                    if (state.importedFiles.size <= 1) {
                                        DropdownMenuItem(
                                            text = { Text("Không có file khác để gộp", color = secondaryTextColor, fontSize = 12.sp) },
                                            onClick = { showCombineMenu = false },
                                            enabled = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    val dotColors = listOf(
        Color(0xFFEF4444),
        Color(0xFFF97316),
        Color(0xFFEAB308),
        Color(0xFFA855F7),
        Color(0xFF3B82F6)
    )
    val allItems = remember(dataHubUiState.baseDisplayItems, contractorFilter, searchQuery, objectTypeFilter, sortOrder) {
        val contractorMatch: (String) -> Boolean = { c ->
            contractorFilter == contractors.firstOrNull().orEmpty() || contractorFilter.startsWith("Ch") || c == contractorFilter
        }
        val codeMatch: (String) -> Boolean = { code ->
            searchQuery.isBlank() || code.contains(searchQuery, ignoreCase = true)
        }

        dataHubUiState.baseDisplayItems
            .asSequence()
            .filter { item ->
                contractorMatch(item.contractor) &&
                    codeMatch(item.code) &&
                    when {
                        objectTypeFilter.startsWith("V") -> !item.isRoute
                        objectTypeFilter.startsWith("Tuy") -> item.isRoute
                        else -> true
                    }
            }
            .toList()
            .let { list ->
                if (sortOrder.trim().endsWith("A")) list.sortedByDescending { it.code } else list.sortedBy { it.code }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 350.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        itemsIndexed(allItems, key = { _, item -> item.id }) { index, item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(item.id, item.node, item.route) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        item.node?.let(onOpenNodeOnMap)
                                            ?: item.route?.let(onOpenRouteOnMap)
                                    }
                                )
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColors[index % dotColors.size])
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (item.isRoute) "Tuyến ${item.code}" else "Nút giao NG-${item.code}",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Nhà thầu: ${item.contractor.ifBlank { "Không rõ" }}",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp
                                )
                                if (item.isRoute && item.route != null) {
                                    Text(
                                        text = "${item.route.startNodeCode} → ${item.route.endNodeCode}",
                                        color = secondaryTextColor,
                                        fontSize = 11.sp
                                    )
                                    val markerIndex = if (item.route.code.contains("#pm")) item.route.code.lastIndexOf("_s") else item.route.code.lastIndexOf("_R")
                                    val prefix = if (markerIndex >= 0) item.route.code.substring(0, markerIndex) else item.route.code
                                    val segments = state.designRoutes.filter { r ->
                                        val rMarker = if (r.code.contains("#pm")) r.code.lastIndexOf("_s") else r.code.lastIndexOf("_R")
                                        val rPrefix = if (rMarker >= 0) r.code.substring(0, rMarker) else r.code
                                        rPrefix == prefix
                                    }
                                    var totalDistM = 0.0
                                    segments.forEach { seg ->
                                        val s = state.designNodes.firstOrNull { it.code == seg.startNodeCode }
                                        val e = state.designNodes.firstOrNull { it.code == seg.endNodeCode }
                                        if (s != null && e != null) {
                                            totalDistM += com.mapsupervision.domain.util.Haversine.distanceInMeters(
                                                s.latitude, s.longitude,
                                                e.latitude, e.longitude
                                            )
                                        }
                                    }
                                    if (totalDistM > 0.0) {
                                        val distText = if (totalDistM >= 1000) "${"%.2f".format(totalDistM / 1000)} km" else "${totalDistM.toInt()} m"
                                        Text(
                                            text = "Chiều dài: $distText",
                                            color = orangeColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 80.dp)
                        ) {
                            Icon(
                                if (item.isRoute) Icons.Outlined.Route else Icons.Outlined.FormatListNumbered,
                                contentDescription = null,
                                tint = if (item.isRoute) Color(0xFF3B82F6) else Color(0xFFE11D48),
                                modifier = Modifier.size(24.dp)
                            )
                            val displayCode = item.node?.mapNumberLabel?.ifBlank { item.code } ?: item.code
                            Text(
                                text = displayCode,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (item.isRoute) {
                        Text("Tuyến kết nối", color = secondaryTextColor, fontSize = 12.sp)
                    } else {
                        val node = item.node!!
                        val materialLines = node.materialSummary.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        var materialsExpanded by remember(item.id) { mutableStateOf(false) }

                        if (materialLines.isEmpty()) {
                            Text("Chưa có dữ liệu vật tư/thiết bị", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            // Header row — tap to expand/collapse
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { materialsExpanded = !materialsExpanded }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vật tư / Thiết bị (${materialLines.size})",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (materialsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = if (materialsExpanded) "Thu gọn" else "Mở rộng",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (materialsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    materialLines.forEach { line ->
                                        val parts = line.split(":", limit = 2)
                                        val itemName = parts.getOrNull(0)?.trim() ?: ""
                                        val itemCount = parts.getOrNull(1)?.trim() ?: ""
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .pointerInput(node.id, node.latitude, node.longitude, itemName) {
                                                        detectTapGestures(
                                                            onDoubleTap = { onOpenNodeOnMap(node) }
                                                        )
                                                    }
                                            ) {
                                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = itemName, color = textColor, fontSize = 12.sp, maxLines = 1)
                                            }
                                            if (isDesignTab) {
                                                Text(text = itemCount, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val key = "${node.id}_$itemName"
                                                    val currentValue = state.materialProgress[key] ?: ""
                                                    Box(
                                                        modifier = Modifier.border(1.dp, orangeColor, RoundedCornerShape(4.dp)).padding(vertical = 4.dp).width(36.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val numFocusManager = LocalFocusManager.current
                                                        BasicTextField(
                                                            value = currentValue,
                                                            onValueChange = { newValue ->
                                                                if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                                                                    onUpdateMaterialProgress(node.id, itemName, newValue)
                                                                }
                                                            },
                                                            textStyle = TextStyle(color = orangeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                            keyboardActions = KeyboardActions(onDone = { numFocusManager.clearFocus() }),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                    Text(text = " / $itemCount", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF334155)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                notesAndTasksObjectCode = item.code
                                onLoadNotesAndTasks(item.code)
                                showNotesAndTasksSheet = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, orangeColor),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, tint = orangeColor, modifier = Modifier.size(14.dp))
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
            notes = state.selectedObjectNotes,
            tasks = state.selectedObjectTasks,
            aiSummary = state.aiNoteSummary,
            aiSuggestions = state.aiTaskSuggestions,
            isAiLoading = state.isAiLoading,
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
}
