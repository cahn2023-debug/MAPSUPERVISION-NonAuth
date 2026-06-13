package com.mapsupervision.reporting.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.SitePhoto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoGrid(photos: List<SitePhoto>) {
    var fullscreenPhoto by remember { mutableStateOf<SitePhoto?>(null) }

    // 3-column grid, fixed height per row
    val rows = photos.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { rowPhotos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowPhotos.forEach { photo ->
                    PhotoThumb(
                        photo = photo,
                        modifier = Modifier.weight(1f),
                        onClick = { fullscreenPhoto = photo }
                    )
                }
                // Fill empty cells in last row
                repeat(3 - rowPhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    fullscreenPhoto?.let { photo ->
        PhotoFullscreenDialog(photo = photo, onDismiss = { fullscreenPhoto = null })
    }
}

@Composable
fun PhotoThumb(photo: SitePhoto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val thumbFile = remember(photo) { File(photo.thumbnailPath.ifBlank { photo.filePath }) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        val painter = rememberAsyncImagePainter(thumbFile)
        Image(
            painter = painter,
            contentDescription = photo.objectCode,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val painterState = painter.state
        if (painterState is coil.compose.AsyncImagePainter.State.Error ||
            painterState is coil.compose.AsyncImagePainter.State.Empty) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
        MatchStatusBadge(
            photo = photo,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        )
        // Timestamp badge
        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(photo.capturedAtEpochMs))
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xAA000000))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(ts, color = Color.White, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MatchStatusBadge(photo: SitePhoto, modifier: Modifier = Modifier) {
    val isMatched = photo.matchedNodeCode != null || photo.matchedRouteCode != null || photo.tagCodesCsv.isNotBlank()
    val text = if (isMatched) "Khớp" else "Lệch"
    val background = if (isMatched) Color(0xCC16A34A) else Color(0xCCDC2626)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PhotoFullscreenDialog(photo: SitePhoto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val imageFile = remember(photo) { File(photo.filePath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        ) {
            val painter = rememberAsyncImagePainter(imageFile)
            Image(
                painter = painter,
                contentDescription = photo.objectCode,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            val painterState = painter.state
            if (painterState is coil.compose.AsyncImagePainter.State.Error ||
                painterState is coil.compose.AsyncImagePainter.State.Empty) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(36.dp)
                )
            }

            // Info overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Đối tượng: ${photo.objectCode}", color = Color.White, fontWeight = FontWeight.Bold)
                val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(photo.capturedAtEpochMs))
                Text("Thời gian: $ts", color = Color.White, fontSize = 12.sp)
                if (photo.latitude != null && photo.longitude != null) {
                    Text("Tọa độ: ${"%.6f".format(photo.latitude)}, ${"%.6f".format(photo.longitude)}", color = Color.White, fontSize = 12.sp)
                }
                // Share button
                Button(
                    onClick = {
                        if (imageFile.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                imageFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Chia sẻ ảnh"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Chia sẻ ảnh")
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
            }
        }
    }
}

@Composable
fun RowScope.GridCell(
    text: String,
    weight: Float,
    isTotal: Boolean = false,
    alignment: Alignment = Alignment.CenterStart
) {
    val backgroundColor = when {
        isTotal -> Color(0xFF1E293B)
        else -> Color.Transparent
    }
    val textColor = when {
        isTotal -> Color(0xFFF1F5F9)
        else -> Color(0xFFCBD5E1)
    }
    val fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
    
    Box(
        modifier = Modifier
            .weight(weight)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            maxLines = 2
        )
    }
}

@Composable
fun RowScope.GridHeaderCell(
    text: String,
    weight: Float,
    sortKey: SortKey,
    activeSortBy: SortKey,
    isAscending: Boolean,
    onClick: () -> Unit
) {
    val isActive = activeSortBy == sortKey
    Box(
        modifier = Modifier
            .weight(weight)
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                color = if (isActive) Color(0xFFF5A623) else Color(0xFFF8FAFC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (isActive) {
                Text(
                    text = if (isAscending) " ↑" else " ↓",
                    color = Color(0xFFF5A623),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun MaterialReportTable(
    rows: List<MaterialReportRow>,
    sortBy: SortKey,
    isAscending: Boolean,
    onHeaderClick: (SortKey) -> Unit,
    modifier: Modifier = Modifier,
    bodyMaxHeight: Dp = 420.dp
) {
    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridHeaderCell("STT", 0.09f, SortKey.STT, sortBy, isAscending) { onHeaderClick(SortKey.STT) }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
            GridHeaderCell("Nội dung", 0.44f, SortKey.NAME, sortBy, isAscending) { onHeaderClick(SortKey.NAME) }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
            GridHeaderCell("Tổng thiết kế", 0.22f, SortKey.PLANNED, sortBy, isAscending) { onHeaderClick(SortKey.PLANNED) }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
            GridHeaderCell("Tổng thi công", 0.22f, SortKey.ACTUAL, sortBy, isAscending) { onHeaderClick(SortKey.ACTUAL) }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
            GridHeaderCell("%", 0.13f, SortKey.PERCENT, sortBy, isAscending) { onHeaderClick(SortKey.PERCENT) }
        }
        
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
        ) {
            itemsIndexed(
                items = rows,
                key = { index, row -> "${row.materialName}_${index}_${row.isTotal}" }
            ) { index, row ->
                val isLast = index == rows.lastIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (row.isTotal) Color(0xFF1E293B) else if (index % 2 == 0) Color(0xFF0F172A) else Color(0xFF1E293B).copy(alpha = 0.2f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sttText = if (row.isTotal) "" else (index + 1).toString()
                    GridCell(sttText, 0.09f, isTotal = row.isTotal, alignment = Alignment.Center)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
                    GridCell(row.materialName, 0.44f, isTotal = row.isTotal)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
                    GridCell(row.totalPlannedQty.toInt().toString(), 0.22f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
                    GridCell(row.totalActualQty.toInt().toString(), 0.22f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF334155)))
                    GridCell("${row.completionPercent.toInt()}%", 0.13f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                }
                if (!isLast) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
                }
            }
        }
    }
}

@Composable
fun AiSummaryDetailTable(
    rows: List<MaterialReportRow>,
    bodyMaxHeight: Dp = 320.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiSummaryDetailCell("Nội dung", 0.38f, isHeader = true)
            AiSummaryDetailCell("Nút", 0.12f, isHeader = true, alignment = Alignment.CenterEnd)
            AiSummaryDetailCell("Tuyến", 0.14f, isHeader = true, alignment = Alignment.CenterEnd)
            AiSummaryDetailCell("Thi công", 0.22f, isHeader = true, alignment = Alignment.CenterEnd)
            AiSummaryDetailCell("%", 0.14f, isHeader = true, alignment = Alignment.CenterEnd)
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
        ) {
            itemsIndexed(
                items = rows,
                key = { index, row -> "${row.materialName}_${index}_${row.isTotal}" }
            ) { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (row.isTotal) Color(0xFF1E293B) else if (index % 2 == 0) Color(0xFF0F172A) else Color(0xFF1E293B).copy(alpha = 0.2f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AiSummaryDetailCell(row.materialName, 0.38f, isTotal = row.isTotal)
                    AiSummaryDetailCell(row.nodeCount.toString(), 0.12f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                    AiSummaryDetailCell(row.routeCount.toString(), 0.14f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                    AiSummaryDetailCell(
                        "${row.totalActualQty.toInt()}/${row.totalPlannedQty.toInt()}",
                        0.22f,
                        isTotal = row.isTotal,
                        alignment = Alignment.CenterEnd
                    )
                    AiSummaryDetailCell("${row.completionPercent.toInt()}%", 0.14f, isTotal = row.isTotal, alignment = Alignment.CenterEnd)
                }
                if (index != rows.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
                }
            }
        }
    }
}

@Composable
fun RowScope.AiSummaryDetailCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    isTotal: Boolean = false,
    alignment: Alignment = Alignment.CenterStart
) {
    val textColor = when {
        isHeader -> Color(0xFFF8FAFC)
        isTotal -> Color(0xFFF1F5F9)
        else -> Color(0xFFCBD5E1)
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isHeader || isTotal) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun AiSummaryCard(draft: ReportDraftResult, rows: List<MaterialReportRow>) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0F172A)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFF5A623),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "BẢNG TÓM TẮT TỔNG HỢP (AI)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF8FAFC)
                )
            }

            if (rows.isNotEmpty()) {
                AiSummaryDetailTable(rows)
            }

            // Section 1: Nút giao & Tuyến cáp
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "I. TÌNH HÌNH NÚT GIAO, TUYẾN CÁP & NHÀ THẦU",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF3B82F6)
                )
                Text(
                    text = draft.executiveSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
            
            // Section 2: Khó khăn vướng mắc
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "II. KHÓ KHĂN, VƯỚNG MẮC THỰC ĐỊA",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFEF4444)
                )
                Text(
                    text = draft.riskSection,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
            
            // Section 3: Kế hoạch thực hiện
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "III. KẾ HOẠCH THỰC HIỆN & KHUYẾN NGHỊ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF10B981)
                )
                for (action in draft.recommendedActions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("+", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        Text(
                            text = action,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/*
@Composable
private fun ProjectSituationCard(
    summary: ProjectSituationSummary,
    draft: ReportDraftResult?
) {
    val contractorEvaluation = draft?.executiveSummary?.ifBlank { null }
        ?: "Ch?a c? d? li?u ?? ??nh gi? ti?n ?? nh? th?u."
    val blockers = draft?.riskSection?.ifBlank { null }
        ?: "Ch?a ghi nh?n kh? kh?n, v??ng m?c n?i b?t."
    val recommendations = draft?.recommendedActions
        ?.filter { it.isNotBlank() }
        ?.joinToString("
")
        ?.ifBlank { null }
        ?: "Ch?a c? khuy?n ngh? c? th?."

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0F172A)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "I. TÌNH HÌNH NÚT GIAO, TUYẾN CÁP & NHÀ THẦU",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF8FAFC)
            )

            SummaryInfoLine("Mã dự án", summary.projectId.ifBlank { "..." })
            SummaryInfoLine("Số nút", summary.totalNodes.toString())
            SummaryInfoLine("Tuyến", summary.totalRoutes.toString())
            SummaryInfoLine("Số nút đã thực hiện", summary.completedNodes.toString())
            SummaryInfoLine("Số tuyến đã thực hiện", summary.completedRoutes.toString())
            SummaryInfoLine(
                "Tiến độ thực tế trung bình toàn dự án đạt",
                "${"%.2f".format(summary.avgActualProgress)} %"
            )
            SummaryInfoLine("Dữ liệu hình ảnh", "${summary.totalPhotos} ảnh")

            SummaryTextBlock(
                title = "Đánh giá tiến độ nhà thầu",
                content = contractorEvaluation
            )
            SummaryTextBlock(
                title = "II. KHÓ KHĂN, VƯỚNG MẮC THỰC ĐỊA",
                content = blockers
            )
            SummaryTextBlock(
                title = "III. KẾ HOẠCH THỰC HIỆN & KHUYẾN NGHỊ",
                content = recommendations
            )
        }
    }
}

@Composable
private fun SummaryInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            color = Color(0xFFF8FAFC),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f)
        )
    }
}
*/

@Composable
fun SummaryTextBlock(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = Color(0xFFF8FAFC),
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = content,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}
