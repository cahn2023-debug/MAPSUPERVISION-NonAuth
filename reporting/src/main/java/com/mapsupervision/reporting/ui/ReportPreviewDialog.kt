package com.mapsupervision.reporting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.SitePhoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportPreviewDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    projectId: String,
    filterNodeCode: String? = null,
    selectedExportFormat: String,
    onConfirmExport: (String) -> Unit, // Returns final selected format: "PDF" or "WORD"
    photos: List<SitePhoto>,
    materialRows: List<MaterialReportRow>,
    aiDraft: ReportDraftResult?
) {
    if (!showDialog) return

    var format by remember(selectedExportFormat) { 
        mutableStateOf(if (selectedExportFormat.isBlank()) "PDF" else selectedExportFormat) 
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (filterNodeCode != null) 
                            "Xem trước Báo cáo điểm: $filterNodeCode"
                        else
                            "Xem trước Báo cáo dự án",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Format Selector Toggles inside dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isPdf = format == "PDF"
                    Button(
                        onClick = { format = "PDF" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPdf) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isPdf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Định dạng PDF (.pdf)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { format = "WORD" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPdf) Color(0xFF3B82F6) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isPdf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Định dạng Word (.docx)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable content preview
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: General Project Info & Stats
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("THÔNG TIN BÁO CÁO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Dự án: $projectId", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (filterNodeCode != null) {
                                    Text("Điểm giám sát: $filterNodeCode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Tổng số ảnh thực địa chọn vào báo cáo: ${photos.size} ảnh", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Thời gian lập báo cáo: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Section 2: AI Draft
                    aiDraft?.let { draft ->
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("BẢN THẢO BÁO CÁO (AI DRAFT)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("Tóm tắt tiến độ:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.executiveSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Đánh giá rủi ro:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.riskSection, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Hành động đề xuất:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.recommendedActions.joinToString("\n• ", prefix = "• "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Section 3: Material Table
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BẢNG TỔNG HỢP VẬT TƯ THI CÔNG", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Nội dung", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
                                    Text("Tổng thiết kế", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                                    Text("Tổng thi công", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                                    Text("%", modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold)
                                }
                                if (materialRows.isEmpty()) {
                                    Text("Không có dữ liệu vật tư.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    materialRows.forEach { row ->
                                        val textStyle = if (row.isTotal) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(row.materialName, modifier = Modifier.weight(0.5f), style = textStyle)
                                            Text(row.totalPlannedQty.toInt().toString(), modifier = Modifier.weight(0.2f), style = textStyle)
                                            Text(row.totalActualQty.toInt().toString(), modifier = Modifier.weight(0.2f), style = textStyle)
                                            Text("${row.completionPercent.toInt()}%", modifier = Modifier.weight(0.1f), style = textStyle)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: Photo Log List
                    if (photos.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("NHẬT KÝ HÌNH ẢNH ĐÃ CHỌN (${photos.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
                                val chunks = photos.chunked(3)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    chunks.forEach { rowPhotos ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rowPhotos.forEach { photo ->
                                                val thumbFile = java.io.File(photo.thumbnailPath.ifBlank { photo.filePath })
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    if (thumbFile.exists()) {
                                                        androidx.compose.foundation.Image(
                                                            painter = coil.compose.rememberAsyncImagePainter(thumbFile),
                                                            contentDescription = photo.objectCode,
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Outlined.Close,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.align(Alignment.Center).size(24.dp)
                                                        )
                                                    }
                                                    // Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .background(Color(0xAA000000))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(photo.objectCode, color = Color.White, fontSize = 8.sp)
                                                    }
                                                }
                                            }
                                            repeat(3 - rowPhotos.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirmExport(format) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (format == "PDF") Color(0xFFEF4444) else Color(0xFF3B82F6),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Xác nhận xuất ${if (format == "PDF") "PDF" else "Word"}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
