package com.mapsupervision.app.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.mapsupervision.domain.ai.GemmaModelFamily
import com.mapsupervision.domain.ai.GemmaModelInfo
import com.mapsupervision.domain.ai.GemmaModelStatus
import com.mapsupervision.domain.ai.ChatActionType
import com.mapsupervision.domain.ai.ChatPendingAction
import com.mapsupervision.domain.ai.DailyLogDateResolver
import com.mapsupervision.domain.ai.DailyLogDraft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GemmaChatSheet(
    state: GemmaChatUiState,
    contextSummary: String,
    projectId: String?,
    currentTab: String,
    selectedNodeCode: String?,
    selectedRouteCode: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onConfirmCellularDownload: () -> Unit,
    onDismissCellularWarning: () -> Unit,
    onDeleteModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onDismissModelPicker: () -> Unit,
    onSelectModel: (GemmaModelInfo) -> Unit,
    onInputChange: (String) -> Unit,
    onConfirmPendingAction: () -> Unit,
    onDismissPendingAction: () -> Unit,
    onUpdatePendingDailyLogDraft: ((DailyLogDraft) -> DailyLogDraft) -> Unit,
    onSend: () -> Unit
) {
    val statusTone = when (state.modelStatus) {
        GemmaModelStatus.READY -> Color(0xFF10B981)
        GemmaModelStatus.DOWNLOADING -> Color(0xFFF59E0B)
        GemmaModelStatus.LOAD_FAILED, GemmaModelStatus.UNSUPPORTED -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.outline
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val view = LocalView.current
        DisposableEffect(view) {
            var parent = view.parent
            while (parent != null) {
                if (parent is DialogWindowProvider) {
                    val window = parent.window
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    break
                }
                parent = parent.parent
            }
            onDispose {}
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 760.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Gemma4 Chatbot",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            state.pendingAction?.let { action ->
                PendingActionCard(
                    action = action,
                    onConfirm = onConfirmPendingAction,
                    onDismiss = onDismissPendingAction,
                    onUpdateDailyLogDraft = onUpdatePendingDailyLogDraft
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = state.selectedModel?.displayName ?: "Chưa có model",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tab: ${currentTab.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.selectedModel?.let { model ->
                                Text(
                                    text = "${model.estimatedSizeMb} MB · RAM ≥ ${model.recommendedMinAvailableRamMb} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = statusTone.copy(alpha = 0.14f),
                            contentColor = statusTone,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = modelStatusLabel(state.modelStatus),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (state.modelStatus == GemmaModelStatus.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = state.downloadMessage.ifBlank { "Đang tải ${state.downloadProgress}%" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (state.chatStatus.isNotBlank()) {
                        Text(
                            text = state.chatStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (state.lastError.isNotBlank()) {
                        Text(
                            text = state.lastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (state.downloadFailureReason.isNotBlank()) {
                        Text(
                            text = buildString {
                                append("Lỗi tải model")
                                if (state.downloadFailureCode.isNotBlank()) {
                                    append(" [").append(state.downloadFailureCode).append("]")
                                }
                                if (state.downloadHttpCode > 0) {
                                    append(" HTTP ").append(state.downloadHttpCode)
                                }
                                append(": ").append(state.downloadFailureReason)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (state.showCellularWarning) {
                AlertDialog(
                    onDismissRequest = onDismissCellularWarning,
                    title = { Text("Dùng dữ liệu di động?") },
                    text = { Text("Tải model qua 4G/5G có thể tốn nhiều dung lượng. Bạn muốn tiếp tục hay hủy?") },
                    confirmButton = {
                        Button(onClick = onConfirmCellularDownload) { Text("Tiếp tục tải") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = onDismissCellularWarning) { Text("Hủy") }
                    }
                )
            }

            if (state.showModelPicker) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Chọn model",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Model nhẹ hơn sẽ tải nhanh và phù hợp với máy yếu hơn.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = onDismissModelPicker) {
                                Text("Đóng")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                state.availableModels,
                                key = { index, model -> "${model.downloadFileName}:$index" }
                            ) { _, model ->
                                ModelPickerItem(
                                    model = model,
                                    isSelected = state.selectedModel?.downloadFileName == model.downloadFileName,
                                    currentStatus = when (modelStatusForPicker(state, model)) {
                                        GemmaModelStatus.READY -> "Đã tải"
                                        GemmaModelStatus.DOWNLOADING -> "Đang tải"
                                        GemmaModelStatus.LOAD_FAILED -> "Lỗi model"
                                        GemmaModelStatus.UNSUPPORTED -> "Không hỗ trợ"
                                        GemmaModelStatus.NOT_DOWNLOADED -> "Chưa tải"
                                    },
                                    onClick = { onSelectModel(model) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onOpenModelPicker,
                    label = { Text("Lựa chọn model") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) }
                )
                AssistChip(
                    onClick = onDownload,
                    label = { Text(if (state.downloadProgress in 1..99) "Tiếp tục tải" else "Tải model") },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                if (state.modelStatus == GemmaModelStatus.DOWNLOADING) {
                    AssistChip(
                        onClick = onCancelDownload,
                        label = { Text("Dừng tải") },
                        leadingIcon = { Icon(Icons.Outlined.PauseCircle, contentDescription = null) }
                    )
                }
                AssistChip(
                    onClick = onDeleteModel,
                    label = { Text("Xóa model") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hỏi về tiến độ, báo cáo, dữ liệu hiện trường hoặc yêu cầu điền nhanh biểu mẫu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            state.messages,
                            key = { index, message -> "${message.role}:${message.text.hashCode()}:$index" }
                        ) { _, message ->
                            MessageBubble(message = message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nhập câu hỏi") },
                placeholder = { Text("Ví dụ: Tổng hợp tiến độ hôm nay") },
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("Đóng")
                }
                Button(
                    onClick = onSend,
                    modifier = Modifier.weight(1.1f),
                    enabled = !state.isBusy && state.input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isBusy) "Đang xử lý" else "Gửi")
                }
            }
        }
    }
}

@Composable
private fun ModelPickerItem(
    model: GemmaModelInfo,
    isSelected: Boolean,
    currentStatus: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        },
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${model.estimatedSizeMb} MB | RAM >= ${model.recommendedMinAvailableRamMb} MB | Storage >= ${model.recommendedMinFreeStorageMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = if (isSelected) "Đang chọn" else currentStatus,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            when (model.family) {
                GemmaModelFamily.QWEN3_0_6B -> Text(
                    text = "Nhẹ nhất",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                GemmaModelFamily.E2B -> Text(
                    text = "Khuyến nghị cho máy trung bình",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GemmaModelFamily.E4B -> Text(
                    text = "Cần máy mạnh hơn",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: GemmaChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val roleLabel = if (isUser) "Bạn" else "AI"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatJson(json: String): String {
    return runCatching {
        val obj = org.json.JSONObject(json)
        val sb = StringBuilder()
        obj.keys().forEach { key ->
            val value = obj.get(key)
            sb.append(key).append(": ").append(value).append("\n")
        }
        sb.toString().trim()
    }.getOrDefault(json)
}

@Composable
private fun PendingActionCard(
    action: ChatPendingAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUpdateDailyLogDraft: ((DailyLogDraft) -> DailyLogDraft) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val dailyLog = action.dailyLog
            if (action.type == ChatActionType.ADD_DAILY_LOG && dailyLog != null) {
                DailyLogDraftEditor(
                    draft = dailyLog,
                    onUpdateDraft = onUpdateDailyLogDraft
                )
            } else {
                Text(
                    text = formatJson(action.draftJson),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onConfirm) { Text("Xác nhận") }
                OutlinedButton(onClick = onDismiss) { Text("Hủy") }
            }
        }
    }
}

@Composable
private fun DailyLogDraftEditor(
    draft: DailyLogDraft,
    onUpdateDraft: ((DailyLogDraft) -> DailyLogDraft) -> Unit
) {
    val dateText = if (draft.dateEpochDay > 0L) DailyLogDateResolver.formatEpochDay(draft.dateEpochDay) else ""
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = dateText,
            onValueChange = { value ->
                val parsed = DailyLogDateResolver.parseDateText(value) ?: draft.dateEpochDay
                onUpdateDraft { current -> current.copy(dateEpochDay = parsed) }
            },
            label = { Text("Ngày ghi nhật ký") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.weather,
                onValueChange = { value -> onUpdateDraft { current -> current.copy(weather = value) } },
                label = { Text("Thời tiết") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = if (draft.temperature > 0.0) draft.temperature.toString() else "",
                onValueChange = { value ->
                    onUpdateDraft { current ->
                        current.copy(temperature = value.toDoubleOrNull() ?: current.temperature)
                    }
                },
                label = { Text("Nhiệt độ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = draft.nodeCode.orEmpty(),
            onValueChange = { value -> onUpdateDraft { current -> current.copy(nodeCode = value.trim().ifBlank { null }) } },
            label = { Text("Node / vị trí") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = draft.routeCode.orEmpty(),
            onValueChange = { value -> onUpdateDraft { current -> current.copy(routeCode = value.trim().ifBlank { null }) } },
            label = { Text("Route / tuyến") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = draft.categoryName,
            onValueChange = { value -> onUpdateDraft { current -> current.copy(categoryName = value) } },
            label = { Text("Hạng mục") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = draft.workItem,
            onValueChange = { value -> onUpdateDraft { current -> current.copy(workItem = value) } },
            label = { Text("Nội dung nhật ký") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = if (draft.volume > 0.0) draft.volume.toString() else "",
                onValueChange = { value ->
                    onUpdateDraft { current -> current.copy(volume = value.toDoubleOrNull() ?: current.volume) }
                },
                label = { Text("Khối lượng") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = draft.unit,
                onValueChange = { value -> onUpdateDraft { current -> current.copy(unit = value) } },
                label = { Text("Đơn vị") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = draft.manpower.toString(),
                onValueChange = { value ->
                    onUpdateDraft { current -> current.copy(manpower = value.toIntOrNull() ?: current.manpower) }
                },
                label = { Text("Nhân công") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = draft.note,
            onValueChange = { value -> onUpdateDraft { current -> current.copy(note = value) } },
            label = { Text("Ghi chú") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
    }
}

private fun modelStatusLabel(status: GemmaModelStatus): String = when (status) {
    GemmaModelStatus.NOT_DOWNLOADED -> "Chưa tải"
    GemmaModelStatus.DOWNLOADING -> "Đang tải"
    GemmaModelStatus.READY -> "Sẵn sàng"
    GemmaModelStatus.UNSUPPORTED -> "Không hỗ trợ"
    GemmaModelStatus.LOAD_FAILED -> "Lỗi model"
}

private fun modelStatusForPicker(state: GemmaChatUiState, model: GemmaModelInfo): GemmaModelStatus {
    val selected = state.selectedModel?.downloadFileName
    if (selected == model.downloadFileName) return state.modelStatus
    return when {
        state.availableModels.any { it.downloadFileName == model.downloadFileName } -> GemmaModelStatus.NOT_DOWNLOADED
        else -> GemmaModelStatus.UNSUPPORTED
    }
}

fun buildChatContextSummary(state: WorkspaceState): String {
    return buildString {
        append("project=").append(state.activeProjectId.orEmpty())
        append("\ndashboard: nodes=").append(state.dashboard.totalDesignNodes)
        append(", routes=").append(state.dashboard.totalDesignRoutes)
        append(", completion=").append(state.dashboard.completionPercent.toInt()).append('%')
        append(", delayed=").append(state.dashboard.delayedCount)
        append(", materials=").append(state.dashboard.materialCompletionPercent.toInt()).append('%')
        state.mapUi.selectedNode?.code?.let { append("\nselected_node=").append(it) }
        state.mapUi.selectedRoute?.code?.let { append("\nselected_route=").append(it) }
        if (state.selectedNodePhotos.isNotEmpty()) {
            append("\nselected_node_photos=").append(state.selectedNodePhotos.size)
        }
        if (state.importUi.warnings.isNotEmpty()) {
            append("\nimport_warnings=").append(
                state.importUi.warnings.take(3).joinToString(" | ") { it.take(80) }
            )
        }
        val delayedNodes = state.constructionProgress
            .filter { it.delayed }
            .sortedByDescending { it.planned - it.actual }
            .take(5)
        if (delayedNodes.isNotEmpty()) {
            append("\ndelayed_nodes=")
            append(
                delayedNodes.joinToString(", ") {
                    "${it.nodeCode}:${it.actual.toInt()}%/${it.planned.toInt()}%"
                }
            )
        }
        val latestLogs = state.dailyLogs
            .sortedByDescending { it.createdAtEpochMs }
            .take(3)
        if (latestLogs.isNotEmpty()) {
            append("\nlatest_logs=")
            append(
                latestLogs.joinToString(" | ") {
                    "${it.workItem}:${it.note.replace('\n', ' ').take(60)}"
                }
            )
        }
        if (state.workCategories.isNotEmpty()) {
            append("\ncategories=")
            append(state.workCategories.joinToString(", ") { "${it.name}:${it.unit}" })
        }
        if (state.designNodes.isNotEmpty()) {
            append("\ndesign_nodes=")
            append(state.designNodes.take(15).joinToString(", ") { it.code })
        }
    }
}

fun buildChatNormalizationSummary(state: WorkspaceState): String {
    return ChatDictionaryResolver.from(state).buildCanonicalPromptContext()
}
