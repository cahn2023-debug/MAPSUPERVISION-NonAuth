package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

@Composable
fun NonExcelMappingDialog(
    state: ImportMappingUiState,
    onDismiss: () -> Unit,
    onUpdateMapping: (
        String?, String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?,
        Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?,
        Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?
    ) -> Unit,
    onConfirmParse: () -> Unit
) {
    val candidates = state.candidates
    val selectedItems = remember(state.workVolumeFieldsCsv) {
        mutableStateListOf<String>().apply {
            addAll(state.workVolumeFieldsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    fun update(
        positionField: String? = null,
        coordinateField: String? = null,
        contractorField: String? = null,
        mapNumberField: String? = null,
        objectTypeField: String? = null,
        workVolumeFieldsCsv: String? = null,
        routeLengthField: String? = null,
        ipAddressField: String? = null,
        subnetField: String? = null,
        gatewayField: String? = null,
        signalStatusField: String? = null,
        fiberCoreCountField: String? = null,
        fiberConnectionField: String? = null,
        confirmedPositionField: Boolean? = null,
        confirmedCoordinateField: Boolean? = null,
        confirmedContractorField: Boolean? = null,
        confirmedMapNumberField: Boolean? = null,
        confirmedObjectTypeField: Boolean? = null,
        confirmedWorkVolumeFields: Boolean? = null,
        confirmedRouteLengthField: Boolean? = null,
        confirmedIpAddressField: Boolean? = null,
        confirmedSubnetField: Boolean? = null,
        confirmedGatewayField: Boolean? = null,
        confirmedSignalStatusField: Boolean? = null,
        confirmedFiberCoreCountField: Boolean? = null,
        confirmedFiberConnectionField: Boolean? = null
    ) {
        onUpdateMapping(
            positionField,
            coordinateField,
            contractorField,
            mapNumberField,
            objectTypeField,
            workVolumeFieldsCsv,
            routeLengthField,
            ipAddressField,
            subnetField,
            gatewayField,
            signalStatusField,
            fiberCoreCountField,
            fiberConnectionField,
            confirmedPositionField,
            confirmedCoordinateField,
            confirmedContractorField,
            confirmedMapNumberField,
            confirmedObjectTypeField,
            confirmedWorkVolumeFields,
            confirmedRouteLengthField,
            confirmedIpAddressField,
            confirmedSubnetField,
            confirmedGatewayField,
            confirmedSignalStatusField,
            confirmedFiberCoreCountField,
            confirmedFiberConnectionField
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anh xa du lieu ban ve KML / KMZ / GeoJSON", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Anh xa cac thuoc tinh tu tep vector vao truong GIS. Chi truong da xac nhan moi duoc ap dung.",
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

                ColumnSectionWithConfirm(
                    label = "1. Truong ten doi tuong / vi tri tram",
                    selected = state.positionField,
                    options = candidates.positionOptions,
                    confirmed = state.confirmedPositionField,
                    onSelected = { update(positionField = it) },
                    onConfirmedChange = { update(confirmedPositionField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "2. Truong toa do hinh hoc",
                    selected = state.coordinateField,
                    options = candidates.coordinateOptions,
                    confirmed = state.confirmedCoordinateField,
                    onSelected = { update(coordinateField = it) },
                    onConfirmedChange = { update(confirmedCoordinateField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "3. Truong nha thau",
                    selected = state.contractorField,
                    options = candidates.contractorOptions,
                    confirmed = state.confirmedContractorField,
                    onSelected = { update(contractorField = it) },
                    onConfirmedChange = { update(confirmedContractorField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "4. Truong so hien thi ban do",
                    selected = state.mapNumberField,
                    options = candidates.mapNumberOptions,
                    confirmed = state.confirmedMapNumberField,
                    onSelected = { update(mapNumberField = it) },
                    onConfirmedChange = { update(confirmedMapNumberField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "5. Truong loai doi tuong",
                    selected = state.objectTypeField,
                    options = candidates.objectTypeOptions,
                    confirmed = state.confirmedObjectTypeField,
                    onSelected = { update(objectTypeField = it) },
                    onConfirmedChange = { update(confirmedObjectTypeField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "6. Truong chieu dai tuyen",
                    selected = state.routeLengthField,
                    options = candidates.routeLengthOptions,
                    confirmed = state.confirmedRouteLengthField,
                    onSelected = { update(routeLengthField = it) },
                    onConfirmedChange = { update(confirmedRouteLengthField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "7. Truong IP",
                    selected = state.ipAddressField,
                    options = candidates.ipAddressOptions,
                    confirmed = state.confirmedIpAddressField,
                    onSelected = { update(ipAddressField = it) },
                    onConfirmedChange = { update(confirmedIpAddressField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "8. Truong subnet",
                    selected = state.subnetField,
                    options = candidates.subnetOptions,
                    confirmed = state.confirmedSubnetField,
                    onSelected = { update(subnetField = it) },
                    onConfirmedChange = { update(confirmedSubnetField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "9. Truong gateway",
                    selected = state.gatewayField,
                    options = candidates.gatewayOptions,
                    confirmed = state.confirmedGatewayField,
                    onSelected = { update(gatewayField = it) },
                    onConfirmedChange = { update(confirmedGatewayField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "10. Truong trang thai tin hieu",
                    selected = state.signalStatusField,
                    options = candidates.signalStatusOptions,
                    confirmed = state.confirmedSignalStatusField,
                    onSelected = { update(signalStatusField = it) },
                    onConfirmedChange = { update(confirmedSignalStatusField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "11. Truong so core quang",
                    selected = state.fiberCoreCountField,
                    options = candidates.fiberCoreCountOptions,
                    confirmed = state.confirmedFiberCoreCountField,
                    onSelected = { update(fiberCoreCountField = it) },
                    onConfirmedChange = { update(confirmedFiberCoreCountField = it) }
                )
                ColumnSectionWithConfirm(
                    label = "12. Truong soi ket noi",
                    selected = state.fiberConnectionField,
                    options = candidates.fiberConnectionOptions,
                    confirmed = state.confirmedFiberConnectionField,
                    onSelected = { update(fiberConnectionField = it) },
                    onConfirmedChange = { update(confirmedFiberConnectionField = it) }
                )

                if (candidates.itemOptions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("13. Chon truong cong viec di kem", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Tat ca",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedItems.clear()
                                            selectedItems.addAll(candidates.itemOptions)
                                            update(workVolumeFieldsCsv = selectedItems.joinToString(","))
                                        }
                                        .padding(4.dp)
                                )
                                Text(
                                    text = "Bo chon",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedItems.clear()
                                            update(workVolumeFieldsCsv = "")
                                        }
                                        .padding(4.dp)
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
                                            update(workVolumeFieldsCsv = selectedItems.joinToString(","))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedItems.add(opt) else selectedItems.remove(opt)
                                            update(workVolumeFieldsCsv = selectedItems.joinToString(","))
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
                                onCheckedChange = { update(confirmedWorkVolumeFields = it) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Xac nhan nhap thong tin cong viec di kem", color = Color.LightGray, fontSize = 12.sp)
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
                Text("Xac nhan nhap ban ve", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huy", color = Color.Gray) } },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .fillMaxHeight(0.97f)
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(16.dp),
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
                value = selected.ifBlank { "Khong cau hinh" },
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
            Text("Xac nhan truong du lieu nay", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
