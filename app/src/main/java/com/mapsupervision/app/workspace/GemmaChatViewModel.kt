package com.mapsupervision.app.workspace

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.app.GemmaModelDownloadWorker
import com.mapsupervision.data.mediapipe.GemmaChatController
import com.mapsupervision.data.mediapipe.GemmaDownloadState
import com.mapsupervision.data.mediapipe.GemmaLiteRtChatService
import com.mapsupervision.data.mediapipe.GemmaModelManager
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.ChatAssistantPayload
import com.mapsupervision.domain.ai.ChatAssistantResult
import com.mapsupervision.domain.ai.ChatActionType
import com.mapsupervision.domain.ai.DailyLogDraft
import com.mapsupervision.domain.ai.DailyLogDateResolver
import com.mapsupervision.domain.ai.ChatPendingAction
import com.mapsupervision.domain.ai.SitePhotoUpdateDraft
import com.mapsupervision.domain.ai.ReportDraftDbSaveDraft
import com.mapsupervision.domain.ai.GemmaDeviceSnapshot
import com.mapsupervision.domain.ai.GemmaModelInfo
import com.mapsupervision.domain.ai.GemmaModelSelection
import com.mapsupervision.domain.ai.GemmaModelStatus
import com.mapsupervision.domain.model.ChatHistoryMessage
import com.mapsupervision.domain.repository.ChatHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GemmaChatMessage(
    val role: String,
    val text: String
)

data class GemmaChatUiState(
    val isOpen: Boolean = false,
    val input: String = "",
    val messages: List<GemmaChatMessage> = emptyList(),
    val pendingAction: ChatPendingAction? = null,
    val selectedModel: GemmaModelInfo? = null,
    val availableModels: List<GemmaModelInfo> = emptyList(),
    val modelSelection: GemmaModelSelection = GemmaModelSelection(false, null, "unknown"),
    val modelStatus: GemmaModelStatus = GemmaModelStatus.NOT_DOWNLOADED,
    val showModelPicker: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadMessage: String = "",
    val downloadFailureCode: String = "",
    val downloadFailureReason: String = "",
    val downloadHttpCode: Int = 0,
    val showCellularWarning: Boolean = false,
    val isBusy: Boolean = false,
    val lastError: String = "",
    val chatReady: Boolean = false,
    val chatStatus: String = ""
)

@HiltViewModel
class GemmaChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiOrchestrator: AiOrchestrator,
    private val modelManager: GemmaModelManager,
    private val deviceSnapshotProvider: GemmaDeviceSnapshotProvider,
    private val chatController: GemmaChatController,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {
    companion object {
        private const val CHAT_HISTORY_LIMIT = 6
        private const val CONTEXT_CHAR_LIMIT = 1500
    }

    private val _uiState = MutableStateFlow(GemmaChatUiState())
    val uiState: StateFlow<GemmaChatUiState> = _uiState.asStateFlow()
    private var activeProjectId: String? = null
    private var activeSendJob: Job? = null

    init {
        viewModelScope.launch {
            modelManager.observeDownloadState().collectLatest(::applyDownloadState)
        }
    }

    fun open(projectId: String?) {
        activeProjectId = projectId
        loadHistory(projectId)
        refreshModelState()
        _uiState.update { it.copy(isOpen = true) }
        warmUpSelectedModel()
    }

    fun close() {
        activeSendJob?.cancel()
        activeSendJob = null
        viewModelScope.launch {
            chatController.cancelGeneration()
            chatController.close()
        }
        _uiState.update { it.copy(isOpen = false, isBusy = false, pendingAction = null) }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun refreshModelState(snapshot: GemmaDeviceSnapshot = deviceSnapshotProvider.snapshot()) {
        val selection = modelManager.selectModel(snapshot)
        val selected = _uiState.value.selectedModel
            ?.takeIf { current -> selection.candidates.any { it.downloadFileName == current.downloadFileName } }
            ?: selection.selected
        _uiState.update {
            it.copy(
                selectedModel = selected,
                availableModels = selection.candidates,
                modelSelection = selection,
                modelStatus = selected?.let(modelManager::status) ?: GemmaModelStatus.UNSUPPORTED,
                showModelPicker = false,
                lastError = if (!selection.supported) selection.reason else it.lastError
            )
        }
        applyDownloadState(modelManager.currentDownloadState())
    }

    fun openModelPicker() {
        refreshModelState()
        _uiState.update { it.copy(showModelPicker = true) }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    fun selectModel(model: GemmaModelInfo) {
        _uiState.update {
            it.copy(
                selectedModel = model,
                modelStatus = modelManager.status(model),
                showModelPicker = false,
                chatReady = false,
                chatStatus = "",
                lastError = ""
            )
        }
        applyDownloadState(modelManager.currentDownloadState())
        warmUpSelectedModel()
    }

    fun downloadSelectedModel() {
        val model = _uiState.value.selectedModel ?: return
        if (isCellularNetwork()) {
            _uiState.update { it.copy(showCellularWarning = true, lastError = "") }
            return
        }
        startDownload(model)
    }

    fun confirmCellularDownload() {
        val model = _uiState.value.selectedModel ?: return
        _uiState.update { it.copy(showCellularWarning = false) }
        startDownload(model)
    }

    fun dismissCellularWarning() {
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun deleteModel() {
        val model = _uiState.value.selectedModel ?: return
        GemmaModelDownloadWorker.cancel(context)
        modelManager.delete(model)
        modelManager.clearDownloadState()
        viewModelScope.launch {
            chatController.resetConversation()
        }
        refreshModelState()
    }

    fun cancelDownload() {
        GemmaModelDownloadWorker.cancel(context)
    }

    fun sendMessage(
        contextSummary: String,
        normalizationContext: String,
        canonicalUserMessage: String,
        projectId: String?,
        tab: String,
        selectedNodeCode: String?,
        selectedRouteCode: String?
    ) {
        val text = _uiState.value.input.trim()
        if (text.isBlank()) return
        val boundedContext = trimContext(contextSummary)
        val safeProjectId = projectId ?: activeProjectId
        activeProjectId = safeProjectId

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                input = "",
                messages = it.messages + GemmaChatMessage("user", text),
                isBusy = true,
                lastError = ""
            )
        }

        activeSendJob?.cancel()
        activeSendJob = viewModelScope.launch {
            try {
                persistMessage(safeProjectId, "user", text)

                // 1. Fast path check: Try parsing locally first
                val fastResult = ChatActionParser.parse(
                    message = canonicalUserMessage.ifBlank { text },
                    contextSummary = boundedContext,
                    selectedNodeCode = selectedNodeCode,
                    normalizationContext = normalizationContext,
                    selectedRouteCode = selectedRouteCode
                )
                if (fastResult.pendingAction != null && fastResult.writeDisposition != com.mapsupervision.domain.ai.WriteDisposition.REJECT) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            pendingAction = fastResult.pendingAction,
                            messages = it.messages + GemmaChatMessage("assistant", fastResult.answer)
                        )
                    }
                    persistMessage(safeProjectId, "assistant", fastResult.answer)
                    return@launch
                }

                // 2. Heavy path (Model/Cloud)
                val finalResult: ChatAssistantResult = try {
                    val model = _uiState.value.selectedModel
                    if (model != null && modelManager.isModelDownloadComplete(model) && modelManager.canInitializeLiteRt(model)) {
                        val init = chatController.initialize(model)
                        _uiState.update { state ->
                            state.copy(
                                chatReady = init.ready,
                                chatStatus = init.warning.ifBlank { init.message },
                                lastError = if (init.ready) "" else init.message
                            )
                        }
                        if (!init.ready) {
                            throw GemmaLiteRtChatService.DiagnosticFailure("INIT_FAILED", init.message)
                        }
                        val llmReplyText = chatController.sendPrompt(
                            model = model,
                            history = _uiState.value.messages
                                .dropLast(1)
                                .takeLast(CHAT_HISTORY_LIMIT)
                                .map { GemmaLiteRtChatService.ChatMessage(it.role, it.text) },
                            contextSummary = boundedContext,
                            normalizationContext = normalizationContext,
                            currentTab = tab,
                            selectedNodeCode = selectedNodeCode,
                            selectedRouteCode = selectedRouteCode,
                            userMessage = canonicalUserMessage.ifBlank { text }
                        ).text

                        ChatActionParser.parseLlmResponse(
                            llmResponse = llmReplyText,
                            selectedNodeCode = selectedNodeCode,
                            normalizationContext = normalizationContext,
                            selectedRouteCode = selectedRouteCode
                        )
                    } else {
                        val orchestratorDecision = aiOrchestrator.execute<ChatAssistantResult>(
                            ChatAssistantPayload(
                                projectId = safeProjectId,
                                currentTab = tab,
                                message = canonicalUserMessage.ifBlank { text },
                                contextSummary = boundedContext,
                                normalizationContext = normalizationContext,
                                selectedNodeCode = selectedNodeCode,
                                selectedRouteCode = selectedRouteCode
                            )
                        )
                        orchestratorDecision.result
                    }
                } catch (error: Throwable) {
                    val errorMsg = error.message ?: "Không thể xử lý yêu cầu."
                    _uiState.update { state -> state.copy(lastError = error.message.orEmpty(), chatReady = false) }
                    ChatAssistantResult(answer = errorMsg)
                }

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingAction = finalResult.pendingAction,
                        messages = it.messages + GemmaChatMessage("assistant", finalResult.answer)
                    )
                }
                persistMessage(safeProjectId, "assistant", finalResult.answer)
            } finally {
                activeSendJob = null
                _uiState.update { state -> state.copy(isBusy = false) }
            }
        }
    }

    fun confirmPendingAction(
        workspaceViewModel: WorkspaceViewModel
    ) {
        val action = _uiState.value.pendingAction ?: return
        viewModelScope.launch {
            when (action.type) {
                ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                    val draft = action.constructionProgress ?: return@launch
                    workspaceViewModel.addConstructionProgress(draft.nodeCode, draft.planned, draft.actual)
                }
                ChatActionType.ADD_DAILY_LOG -> {
                    val draft = action.dailyLog ?: return@launch
                    workspaceViewModel.addDailyLog(
                        workItem = draft.workItem,
                        manpower = draft.manpower,
                        note = draft.note,
                        weather = draft.weather,
                        temperature = draft.temperature,
                        nodeCode = draft.nodeCode,
                        routeCode = draft.routeCode,
                        dateEpochDay = draft.dateEpochDay,
                        volume = draft.volume,
                        unit = draft.unit,
                        categoryName = draft.categoryName
                    )
                }
                ChatActionType.UPDATE_SITE_PHOTO -> {
                    val draft = action.sitePhotoUpdate ?: return@launch
                    workspaceViewModel.updateSitePhoto(
                        photoId = draft.photoId,
                        tagCodesCsv = draft.tagCodesCsv,
                        matchedNodeCode = draft.matchedNodeCode,
                        lat = draft.latitude,
                        lon = draft.longitude
                    )
                }
                ChatActionType.SAVE_REPORT_DRAFT -> {
                    val draft = action.reportDraftSave ?: return@launch
                    workspaceViewModel.saveReportDraft(
                        title = draft.title,
                        executiveSummary = draft.executiveSummary,
                        riskSection = draft.riskSection,
                        recommendedActions = draft.recommendedActions
                    )
                }
                ChatActionType.ADD_NOTE -> {
                    val draft = action.noteDraft ?: return@launch
                    workspaceViewModel.addNote(draft.objectCode, draft.content)
                }
                ChatActionType.ADD_TASK -> {
                    val draft = action.taskDraft ?: return@launch
                    workspaceViewModel.addTask(draft.objectCode, draft.title)
                }
                ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                    val draft = action.materialOrVolumeProgress ?: return@launch
                    workspaceViewModel.updateMaterialProgress(draft.nodeCode, draft.materialName, draft.actualQty.toString())
                }
            }
            _uiState.update { it.copy(pendingAction = null) }
        }
    }

    fun dismissPendingAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    fun updatePendingDailyLogDraft(transform: (DailyLogDraft) -> DailyLogDraft) {
        _uiState.update { state ->
            val action = state.pendingAction ?: return@update state
            val draft = action.dailyLog ?: return@update state
            val updated = transform(draft)
            state.copy(
                pendingAction = action.copy(
                    dailyLog = updated,
                    draftJson = buildDailyLogDraftJson(updated)
                )
            )
        }
    }

    private fun buildDailyLogDraftJson(draft: DailyLogDraft): String {
        val nodeCode = draft.nodeCode
        val routeCode = draft.routeCode
        return buildString {
            append("{")
            append("\"workItem\":\"").append(escapeJson(draft.workItem)).append("\",")
            append("\"manpower\":").append(draft.manpower).append(",")
            append("\"note\":\"").append(escapeJson(draft.note)).append("\",")
            append("\"weather\":\"").append(escapeJson(draft.weather)).append("\",")
            append("\"temperature\":").append(draft.temperature).append(",")
            append("\"nodeCode\":")
            if (nodeCode == null) append("null") else append("\"").append(escapeJson(nodeCode)).append("\"")
            append(",")
            append("\"routeCode\":")
            if (routeCode == null) append("null") else append("\"").append(escapeJson(routeCode)).append("\"")
            append(",")
            append("\"dateEpochDay\":").append(draft.dateEpochDay).append(",")
            append("\"date\":\"").append(escapeJson(DailyLogDateResolver.formatEpochDay(draft.dateEpochDay))).append("\",")
            append("\"volume\":").append(draft.volume).append(",")
            append("\"unit\":\"").append(escapeJson(draft.unit)).append("\",")
            append("\"categoryName\":\"").append(escapeJson(draft.categoryName)).append("\"")
            append("}")
        }
    }

    private fun startDownload(model: GemmaModelInfo) {
        _uiState.update {
            it.copy(
                isBusy = false,
                chatReady = false,
                chatStatus = "",
                lastError = "",
                downloadFailureCode = "",
                downloadFailureReason = "",
                downloadHttpCode = 0
            )
        }
        GemmaModelDownloadWorker.start(context, model.downloadFileName)
    }

    private fun applyDownloadState(state: GemmaDownloadState) {
        val selectedModel = _uiState.value.selectedModel
        if (selectedModel == null) return
        if (state != GemmaDownloadState.Idle && modelIdOf(state) != selectedModel.downloadFileName) return

        _uiState.update { current ->
            when (state) {
                GemmaDownloadState.Idle -> current.copy(
                    downloadProgress = 0,
                    downloadMessage = "",
                    downloadFailureCode = "",
                    downloadFailureReason = "",
                    downloadHttpCode = 0,
                    modelStatus = modelManager.status(selectedModel)
                )
                is GemmaDownloadState.Running -> current.copy(
                    downloadProgress = progressOf(state.bytesDownloaded, state.totalBytes),
                    downloadMessage = if (state.warningMessage.isNotBlank()) {
                        state.warningMessage
                    } else {
                        "Đang tải ${progressOf(state.bytesDownloaded, state.totalBytes)}%"
                    },
                    downloadFailureCode = state.warningCode,
                    downloadFailureReason = state.warningMessage,
                    downloadHttpCode = state.httpCode,
                    modelStatus = GemmaModelStatus.DOWNLOADING,
                    chatReady = false,
                    chatStatus = ""
                )
                is GemmaDownloadState.Completed -> {
                    warmUpSelectedModel()
                    current.copy(
                        downloadProgress = 100,
                        downloadMessage = "Tải xong",
                        downloadFailureCode = "",
                        downloadFailureReason = "",
                        downloadHttpCode = 0,
                        modelStatus = if (modelManager.isModelDownloadComplete(selectedModel)) GemmaModelStatus.READY else GemmaModelStatus.LOAD_FAILED,
                        chatStatus = "Model đã sẵn sàng.",
                        lastError = ""
                    )
                }
                is GemmaDownloadState.Failed -> current.copy(
                    downloadProgress = progressOf(state.bytesDownloaded, state.totalBytes),
                    downloadMessage = "Tải thất bại",
                    downloadFailureCode = state.errorCode,
                    downloadFailureReason = state.message,
                    downloadHttpCode = state.httpCode,
                    modelStatus = GemmaModelStatus.LOAD_FAILED,
                    lastError = state.message,
                    chatReady = false
                )
            }
        }
    }

    private fun progressOf(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun trimContext(contextSummary: String): String {
        val normalized = contextSummary.trim()
        if (normalized.length <= CONTEXT_CHAR_LIMIT) return normalized
        return normalized.take(CONTEXT_CHAR_LIMIT)
    }

    private fun loadHistory(projectId: String?) {
        if (projectId.isNullOrBlank()) {
            _uiState.update { it.copy(messages = emptyList(), pendingAction = null) }
            return
        }
        viewModelScope.launch {
            val result = chatHistoryRepository.listRecentByProject(projectId, 50)
            val messages = (result as? com.mapsupervision.core.result.AppResult.Success)?.data.orEmpty()
                .map { ChatHistoryMessage -> GemmaChatMessage(ChatHistoryMessage.role, ChatHistoryMessage.text) }
            _uiState.update { it.copy(messages = messages, pendingAction = null) }
        }
    }

    private suspend fun persistMessage(projectId: String?, role: String, text: String) {
        if (projectId.isNullOrBlank()) return
        chatHistoryRepository.append(
            ChatHistoryMessage(
                id = java.util.UUID.randomUUID().toString(),
                projectId = projectId,
                role = role,
                text = text,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun warmUpSelectedModel() {
        val model = _uiState.value.selectedModel ?: return
        if (_uiState.value.isBusy) return
        if (!modelManager.isModelDownloadComplete(model) || !modelManager.canInitializeLiteRt(model)) return
        viewModelScope.launch {
            val init = runCatching { chatController.initialize(model) }.getOrNull() ?: return@launch
            if (init.ready) {
                _uiState.update { state ->
                    state.copy(
                        chatReady = true,
                        chatStatus = init.warning.ifBlank { init.message },
                        lastError = ""
                    )
                }
            }
        }
    }

    private fun modelIdOf(state: GemmaDownloadState): String? = when (state) {
        GemmaDownloadState.Idle -> null
        is GemmaDownloadState.Running -> state.modelId
        is GemmaDownloadState.Completed -> state.modelId
        is GemmaDownloadState.Failed -> state.modelId
    }

    private fun isCellularNetwork(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
