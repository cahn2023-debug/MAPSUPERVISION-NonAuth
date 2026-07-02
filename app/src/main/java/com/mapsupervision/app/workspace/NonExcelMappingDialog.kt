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
fun NonExcelMappingDialog(
    state: ImportMappingUiState,
    onDismiss: () -> Unit,
    onUpdateMapping: (
        positionField: String?,
        coordinateField: String?,
        contractorField: String?,
        mapNumberField: String?,
        objectTypeField: String?,
        workVolumeFieldsCsv: String?,
        routeLengthField: String?,
        confirmedPositionField: Boolean?,
        confirmedCoordinateField: Boolean?,
        confirmedContractorField: Boolean?,
        confirmedMapNumberField: Boolean?,
        confirmedObjectTypeField: Boolean?,
        confirmedWorkVolumeFields: Boolean?,
        confirmedRouteLengthField: Boolean?
    ) -> Unit,
    onConfirmParse: () -> Unit
) {
    val candidates = state.candidates
    val selectedItems = remember(state.workVolumeFieldsCsv) {
        mutableStateListOf<String>().apply {
            addAll(state.workVolumeFieldsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ánh xạ dữ liệu bản vẽ KML / KMZ / GeoJSON", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ánh xạ các thuộc tính từ tệp Vector vào các trường đối tượng GIS. Tích chọn Xác nhận để nhập trường dữ liệu đó.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                }

                // 1. Trường Vị trí (Bắt buộc)
                ColumnSectionWithConfirm(
                    label = "1. Trường tên đối tượng / vị trí trạm (Bắt buộc)",
                    selected = state.positionField,
                    options = candidates.positionOptions,
                    confirmed = state.confirmedPositionField,
                    onSelected = { onUpdateMapping(it, null, null, null, null, null, null, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, it, null, null, null, null, null, null) }
                )

                // 2. Trường Tọa độ GPS
                ColumnSectionWithConfirm(
                    label = "2. Trường tọa độ hình học (GPS Coordinates)",
                    selected = state.coordinateField,
                    options = candidates.coordinateOptions,
                    confirmed = state.confirmedCoordinateField,
                    onSelected = { onUpdateMapping(null, it, null, null, null, null, null, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, it, null, null, null, null, null) }
                )

                // 3. Trường Nhà thầu
                ColumnSectionWithConfirm(
                    label = "3. Trường nhà thầu thi công",
                    selected = state.contractorField,
                    options = candidates.contractorOptions,
                    confirmed = state.confirmedContractorField,
                    onSelected = { onUpdateMapping(null, null, it, null, null, null, null, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, null, it, null, null, null, null) }
                )

                // 4. Trường Số hiển thị (Map Number)
                ColumnSectionWithConfirm(
                    label = "4. Trường số ký hiệu hiển thị trên bản đồ",
                    selected = state.mapNumberField,
                    options = candidates.mapNumberOptions,
                    confirmed = state.confirmedMapNumberField,
                    onSelected = { onUpdateMapping(null, null, null, it, null, null, null, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, null, null, it, null, null, null) }
                )

                // 5. Trường Loại đối tượng (Object Type)
                ColumnSectionWithConfirm(
                    label = "5. Trường loại đối tượng (Point / LineString)",
                    selected = state.objectTypeField,
                    options = candidates.objectTypeOptions,
                    confirmed = state.confirmedObjectTypeField,
                    onSelected = { onUpdateMapping(null, null, null, null, it, null, null, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, null, null, null, it, null, null) }
                )

                // 6. Trường Chiều dài tuyến
                ColumnSectionWithConfirm(
                    label = "6. Trường tự động tính toán chiều dài tuyến cáp",
                    selected = state.routeLengthField,
                    options = candidates.routeLengthOptions,
                    confirmed = state.confirmedRouteLengthField,
                    onSelected = { onUpdateMapping(null, null, null, null, null, null, it, null, null, null, null, null, null, null) },
                    onConfirmedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, null, null, null, null, null, it) }
                )

                // 7. Trường công việc / khối lượng công việc
                if (candidates.itemOptions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("7. Chọn trường công việc đi kèm", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Tất cả",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        selectedItems.clear()
                                        selectedItems.addAll(candidates.itemOptions)
                                        onUpdateMapping(null, null, null, null, null, selectedItems.joinToString(","), null, null, null, null, null, null, null, null)
                                    }.padding(4.dp)
                                )
                                Text(
                                    text = "Bỏ chọn",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        selectedItems.clear()
                                        onUpdateMapping(null, null, null, null, null, "", null, null, null, null, null, null, null, null)
                                    }.padding(4.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            candidates.itemOptions.forEach { opt ->
                                val checked = selectedItems.contains(opt)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selectedItems.remove(opt) else selectedItems.add(opt)
                                            onUpdateMapping(null, null, null, null, null, selectedItems.joinToString(","), null, null, null, null, null, null, null, null)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedItems.add(opt) else selectedItems.remove(opt)
                                            onUpdateMapping(null, null, null, null, null, selectedItems.joinToString(","), null, null, null, null, null, null, null, null)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(opt, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.confirmedWorkVolumeFields,
                                onCheckedChange = { onUpdateMapping(null, null, null, null, null, null, null, null, null, null, null, null, it, null) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Xác nhận nhập thông tin công việc đi kèm", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmParse,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Xác nhận nhập bản vẽ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy", color = Color.Gray) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ColumnSectionWithConfirm(
    label: String,
    selected: String,
    options: List<String>,
    confirmed: Boolean,
    onSelected: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = selected.ifBlank { "Không cấu hình" },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Checkbox(
                checked = confirmed,
                onCheckedChange = onConfirmedChange,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Xác nhận trường dữ liệu này", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

