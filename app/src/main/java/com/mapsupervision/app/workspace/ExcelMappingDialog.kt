package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExcelMappingDialog(
    state: ExcelParserUiState,
    onDismiss: () -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onConfirmParse: () -> Unit,
    onUpdateSelectedSheet: (String) -> Unit
) {
    val previews = remember(state.headers, state.sampleRows) {
        state.headers.map { header ->
            val samples = state.sampleRows.mapNotNull { row -> row[header]?.trim()?.takeIf { it.isNotBlank() } }.take(3)
            header to samples
        }
    }
    val allHeaders = state.headers
    val selectedMaterials = remember(state.workVolumeColumnsCsv) {
        mutableStateListOf<String>().apply {
            addAll(state.workVolumeColumnsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ánh xạ cột Excel theo nội dung", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ứng dụng tự phát hiện cột Excel. Vui lòng đối soát mẫu trước khi nhập.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (state.sheets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chọn Worksheet (Sheet)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        DropdownField(
                            selected = state.selectedSheet.ifBlank { state.sheets.firstOrNull().orEmpty() },
                            options = state.sheets,
                            onSelected = onUpdateSelectedSheet
                        )
                    }
                }

                ColumnSection("1. Cột tên đối tượng / vị trí", state.positionColumn, allHeaders, previews) { onUpdateExcelMapping(it, null, null, null, null, null, null, null) }

                Text("2. Định dạng tọa độ trong Excel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onUpdateCoordinateMode(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!state.useTwoColumnCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!state.useTwoColumnCoordinates) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("1 cột (lat,lon)") }
                    Button(
                        onClick = { onUpdateCoordinateMode(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.useTwoColumnCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (state.useTwoColumnCoordinates) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("2 cột (vĩ/kinh)") }
                }

                if (!state.useTwoColumnCoordinates) {
                    ColumnSection("3. Cột tọa độ GPS (lat,lon)", state.coordinateColumn, allHeaders, previews) { onUpdateExcelMapping(null, it, null, null, null, null, null, null) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            ColumnSection("3a. Cột vĩ độ", state.latitudeColumn, allHeaders, previews) { onUpdateExcelMapping(null, null, it, null, null, null, null, null) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            ColumnSection("3b. Cột kinh độ", state.longitudeColumn, allHeaders, previews) { onUpdateExcelMapping(null, null, null, it, null, null, null, null) }
                        }
                    }
                }

                ColumnSection("4. Cột nhà thầu", state.contractorColumn, allHeaders, previews) { onUpdateExcelMapping(null, null, null, null, it, null, null, null) }
                ColumnSection("5. Cột số hiển thị trên bản đồ", state.mapNumberColumn, allHeaders, previews) { onUpdateExcelMapping(null, null, null, null, null, it, null, null) }

                val excluded = setOf(
                    state.positionColumn,
                    state.contractorColumn,
                    state.mapNumberColumn,
                    if (state.useTwoColumnCoordinates) state.latitudeColumn else state.coordinateColumn,
                    if (state.useTwoColumnCoordinates) state.longitudeColumn else ""
                )
                val availableHeaders = allHeaders.filter { it !in excluded }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5. Cột công việc / khối lượng công việc", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Chọn tất cả",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                selectedMaterials.clear()
                                selectedMaterials.addAll(availableHeaders)
                                onUpdateExcelMapping(null, null, null, null, null, null, null, selectedMaterials.joinToString(","))
                            }.padding(4.dp)
                        )
                        Text(
                            text = "Bỏ chọn",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                selectedMaterials.clear()
                                onUpdateExcelMapping(null, null, null, null, null, null, null, "")
                            }.padding(4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    availableHeaders.forEach { header ->
                        val checked = selectedMaterials.contains(header)
                        val sample = previews.firstOrNull { it.first == header }?.second?.joinToString(", ").orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) selectedMaterials.remove(header) else selectedMaterials.add(header)
                                onUpdateExcelMapping(null, null, null, null, null, null, null, selectedMaterials.joinToString(","))
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedMaterials.add(header) else selectedMaterials.remove(header)
                                    onUpdateExcelMapping(null, null, null, null, null, null, null, selectedMaterials.joinToString(","))
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(header, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (sample.isNotBlank()) Text("Mẫu: $sample", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Text("6. Hiển thị số trên bản đồ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onUpdateMapVisualOptions(false, null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!state.showNumberOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!state.showNumberOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("Ẩn số") }
                    Button(
                        onClick = { onUpdateMapVisualOptions(true, null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.showNumberOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (state.showNumberOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("Hiện số") }
                }

                Text("7. Màu đối tượng trên bản đồ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onUpdateMapVisualOptions(null, false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!state.colorByContractorOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!state.colorByContractorOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("Đơn sắc") }
                    Button(
                        onClick = { onUpdateMapVisualOptions(null, true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.colorByContractorOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (state.colorByContractorOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    ) { Text("Theo nhà thầu") }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmParse, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text("Xác nhận nhập liệu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", color = Color.Gray) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ColumnSection(
    label: String,
    selected: String,
    options: List<String>,
    previews: List<Pair<String, List<String>>>,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        DropdownField(selected = selected, options = options, onSelected = onSelected)
        val sample = previews.firstOrNull { it.first == selected }?.second?.joinToString(", ").orEmpty()
        if (sample.isNotBlank()) Text("Nội dung mẫu: $sample", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
    }
}

@Composable
private fun DropdownField(selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "open")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelected(option)
                    expanded = false
                })
            }
        }
    }
}

