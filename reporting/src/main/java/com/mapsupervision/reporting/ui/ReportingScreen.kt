package com.mapsupervision.reporting.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ElevatedCard
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.ai.ReportDraftResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortKey { NONE, STT, NAME, PLANNED, ACTUAL, PERCENT }

@Composable
fun ReportingScreen(
    viewModel: ReportingViewModel = hiltViewModel(),
    activeProjectId: String?,
    photoFilterNodeCode: String? = null,
    photoSaveCount: Int = 0,
    materialProgress: Map<String, String> = emptyMap(),
    onClearPhotoFilter: () -> Unit = {}
) {
    val path by viewModel.lastReportPath.collectAsState()
    val wordPath by viewModel.lastWordReportPath.collectAsState()
    val zipPath by viewModel.lastPackagePath.collectAsState()
    val aiDraft by viewModel.aiReportDraft.collectAsState()
    val materialRows by viewModel.materialReportRows.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val projectNodes by viewModel.projectNodes.collectAsState()
    val projectRoutes by viewModel.projectRoutes.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    var showPhotos by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf("") }

    var sortBy by remember { mutableStateOf(SortKey.NONE) }
    var isAscending by remember { mutableStateOf(true) }

    val sortedMaterialRows = remember(materialRows, sortBy, isAscending) {
        val nonTotalRows = materialRows.filter { !it.isTotal }
        val totalRow = materialRows.find { it.isTotal }
        val sorted = when (sortBy) {
            SortKey.NONE -> nonTotalRows
            SortKey.STT -> nonTotalRows
            SortKey.NAME -> nonTotalRows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.materialName })
            SortKey.PLANNED -> nonTotalRows.sortedBy { it.totalPlannedQty }
            SortKey.ACTUAL -> nonTotalRows.sortedBy { it.totalActualQty }
            SortKey.PERCENT -> nonTotalRows.sortedBy { it.completionPercent }
        }
        val finalSorted = if (isAscending || sortBy == SortKey.STT || sortBy == SortKey.NONE) sorted else sorted.reversed()
        if (totalRow != null) finalSorted + totalRow else finalSorted
    }
    val matchedPhotos = remember(photos) { photos.filter { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() } }
    val unmatchedPhotos = remember(photos) { photos.filterNot { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() } }

    fun toggleSort(key: SortKey) {
        if (sortBy == key) {
            isAscending = !isAscending
        } else {
            sortBy = key
            isAscending = true
        }
    }

    LaunchedEffect(activeProjectId, photoSaveCount, materialProgress) {
        if (activeProjectId != null) {
            viewModel.refreshReportData()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (activeProjectId == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Vui lòng tạo hoặc chọn một dự án để tiếp tục",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            return@Scaffold
        }

        val filteredPhotos = if (photoFilterNodeCode != null)
            photos.filter { it.objectCode == photoFilterNodeCode }
        else photos

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Export buttons � always visible at top ---------------------
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1.5f)) {
                        Button(
                            onClick = { showFormatMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                            Spacer(Modifier.size(6.dp))
                            Text("Xuất Báo cáo", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(4.dp))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        }

                        DropdownMenu(
                            expanded = showFormatMenu,
                            onDismissRequest = { showFormatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Xuất báo cáo PDF (.pdf)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444)) },
                                onClick = {
                                    selectedExportFormat = "PDF"
                                    showPreviewDialog = true
                                    showFormatMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xuất báo cáo Word (.docx)", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = Color(0xFF3B82F6)) },
                                onClick = {
                                    selectedExportFormat = "WORD"
                                    showPreviewDialog = true
                                    showFormatMenu = false
                                }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.exportPackageZip() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Đóng gói ZIP")
                    }
                }
            }

            if (isExporting) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Đang xuất báo cáo, vui lòng chờ...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // -- Export status paths ----------------------------------------
            if (path != null || wordPath != null || zipPath != null) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        if (path != null) {
                            Text("PDF: $path", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        if (wordPath != null) {
                            Text("Word: $wordPath", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3B82F6), fontWeight = FontWeight.SemiBold)
                        }
                        if (zipPath != null) {
                            Text("ZIP: $zipPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // -- Photo gallery section --------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Ảnh hiện trường",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (photoFilterNodeCode != null) {
                            Text(
                                "photoFilterNodeCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (photoFilterNodeCode != null) {
                            IconButton(onClick = onClearPhotoFilter) {
                                Icon(Icons.Outlined.Close, contentDescription = "Xóa bộ lọc", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Toggle show/hide photos
                        IconButton(onClick = { showPhotos = !showPhotos }) {
                            Icon(
                                imageVector = if (showPhotos) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPhotos) "Ẩn ảnh" else "Hiện ảnh",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ảnh đối chiếu", fontWeight = FontWeight.Bold)
                        Text("Khớp: ${matchedPhotos.size}", color = MaterialTheme.colorScheme.primary)
                        Text("Chưa khớp: ${unmatchedPhotos.size}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showPhotos) {
                if (filteredPhotos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("Chưa có ảnh nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    // Group photos by objectCode / folder
                    val grouped = filteredPhotos.groupBy { it.objectCode }
                    grouped.forEach { (nodeCode, nodePhotos) ->
                        item {
                            Text(
                                "Tổ $nodeCode (${nodePhotos.size} ảnh, khớp ${nodePhotos.count { it.matchedNodeCode != null || it.tagCodesCsv.isNotBlank() }})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            PhotoGrid(photos = nodePhotos)
                        }
                    }
                }
            } else {
                // Hidden mode: show location list with photo counts, no grids
                if (filteredPhotos.isEmpty()) {
                    item {
                        Text(
                            "Chưa có ảnh nào",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    val grouped = filteredPhotos.groupBy { it.objectCode }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            grouped.forEach { (nodeCode, nodePhotos) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Tổ $nodeCode",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${nodePhotos.size} ảnh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -- Material table --------------------------------------------
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Bảng tổng hợp khối lượng thi công",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (sortedMaterialRows.isEmpty()) {
                        Text("Chưa có dữ liệu khối lượng thi công.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MaterialReportTable(
                            rows = sortedMaterialRows,
                            sortBy = sortBy,
                            isAscending = isAscending,
                            onHeaderClick = { toggleSort(it) }
                        )
                    }
                }
            }

            // -- Bảng tóm tắt tổng hợp (AI) ----------------------------------

        }

        ReportPreviewDialog(
            showDialog = showPreviewDialog,
            onDismiss = { showPreviewDialog = false },
            projectId = activeProjectId,
            selectedExportFormat = selectedExportFormat,
            isExporting = isExporting,
            onUpdatePhotoOffset = viewModel::updatePhotoOffset,
            projectNodes = projectNodes,
            projectRoutes = projectRoutes,
            onConfirmExport = { finalFormat ->
                if (finalFormat == "PDF") viewModel.exportPdf()
                else viewModel.exportWord()
                showPreviewDialog = false
            },
            photos = photos,
            materialRows = materialRows,
            aiDraft = aiDraft
        )

        val context = LocalContext.current
        val activeExportPath = path ?: wordPath ?: zipPath
        if (activeExportPath != null) {
            val file = File(activeExportPath)
            val fileTypeLabel = when (file.extension.lowercase(Locale.US)) {
                "pdf" -> "Báo cáo PDF"
                "docx" -> "Báo cáo Word"
                "zip" -> "Đóng gói ZIP"
                else -> "Tập tin"
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearExportPaths() },
                title = {
                    Text(
                        text = "Xuất báo cáo thành công",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Đã xuất tập tin $fileTypeLabel thành công.",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = file.name,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val mimeType = when (file.extension.lowercase(Locale.US)) {
                                        "pdf" -> "application/pdf"
                                        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        "zip" -> "application/zip"
                                        else -> "*/*"
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Chia sẻ tập tin"))
                                    viewModel.clearExportPaths()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chia sẻ")
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val mimeType = when (file.extension.lowercase(Locale.US)) {
                                        "pdf" -> "application/pdf"
                                        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        "zip" -> "application/zip"
                                        else -> "*/*"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    viewModel.clearExportPaths()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623), contentColor = Color.Black)
                        ) {
                            Text("Mở file", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.clearExportPaths() }
                    ) {
                        Text("Đóng", color = Color.Gray)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
