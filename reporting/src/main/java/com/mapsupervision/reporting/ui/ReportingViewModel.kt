package com.mapsupervision.reporting.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.ReportDraftPayload
import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.reporting.docx.DocxReportGenerator
import com.mapsupervision.reporting.pdf.PdfReportGenerator
import com.mapsupervision.storage.ProjectPackageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class ReportingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeProjectRepository: ActiveProjectRepository,
    private val photoRepository: PhotoRepository,
    private val progressRepository: ProgressRepository,
    private val materialProgressRepository: MaterialProgressRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val gisRepository: GisRepository,
    private val aiOrchestrator: AiOrchestrator,
    private val pdfReportGenerator: PdfReportGenerator,
    private val docxReportGenerator: DocxReportGenerator,
    private val projectPackageService: ProjectPackageService,
    private val projectRepository: ProjectRepository,
    private val projectSyncRepository: ProjectSyncRepository
) : ViewModel() {
    private val _lastReportPath = MutableStateFlow<String?>(null)
    val lastReportPath: StateFlow<String?> = _lastReportPath.asStateFlow()

    private val _lastWordReportPath = MutableStateFlow<String?>(null)
    val lastWordReportPath: StateFlow<String?> = _lastWordReportPath.asStateFlow()

    private val _aiReportDraft = MutableStateFlow<ReportDraftResult?>(null)
    val aiReportDraft: StateFlow<ReportDraftResult?> = _aiReportDraft.asStateFlow()

    private val _lastPackagePath = MutableStateFlow<String?>(null)
    val lastPackagePath: StateFlow<String?> = _lastPackagePath.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _materialReportRows = MutableStateFlow<List<MaterialReportRow>>(emptyList())
    val materialReportRows: StateFlow<List<MaterialReportRow>> = _materialReportRows.asStateFlow()

    private val _projectNodes = MutableStateFlow<List<GisNode>>(emptyList())
    val projectNodes: StateFlow<List<GisNode>> = _projectNodes.asStateFlow()

    private val _projectRoutes = MutableStateFlow<List<GisRoute>>(emptyList())
    val projectRoutes: StateFlow<List<GisRoute>> = _projectRoutes.asStateFlow()

    private val _photos = MutableStateFlow<List<com.mapsupervision.domain.model.SitePhoto>>(emptyList())
    val photos: StateFlow<List<com.mapsupervision.domain.model.SitePhoto>> = _photos.asStateFlow()

    init {
        observeActiveProject()
        observeProjectSync()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refreshReportData()
            }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refreshReportData()
                }
            }
        }
    }

    fun refreshReportData() {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: run {
                _materialReportRows.value = emptyList()
                _photos.value = emptyList()
                _aiReportDraft.value = null
                return@launch
            }
            val rows = (materialProgressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val nodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            _projectNodes.value = nodes
            _projectRoutes.value = routes
            _materialReportRows.value = buildMaterialReportRows(nodes, routes, rows)
            _photos.value = (photoRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            
            // Tự động sinh báo cáo tổng hợp AI khi mở tab hoặc reload dữ liệu
            generateAiReportDraft(projectId)
        }
    }

    fun updatePhotoOffset(photo: SitePhoto, offsetMinutes: Int) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val offsetMs = offsetMinutes.toLong() * 60_000L
            val updated = photo.copy(
                matchingTimeOffsetMs = offsetMs,
                matchedAtEpochMs = photo.capturedAtEpochMs + offsetMs
            )
            photoRepository.add(updated)
            if (photo.projectId == projectId) {
                refreshReportData()
            }
        }
    }

    private suspend fun generateAiReportDraft(projectId: String, filterNodeCode: String? = null): ReportDraftResult {
        var photos = (photoRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
        var progress = (progressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
        var gisNodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        var logs = (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()

        if (filterNodeCode != null) {
            photos = photos.filter { it.objectCode == filterNodeCode }
            progress = progress.filter { it.nodeCode == filterNodeCode }
            gisNodes = gisNodes.filter { it.code == filterNodeCode || it.id == filterNodeCode }
        }

        val projects = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
        val project = projects.find { it.id == projectId }
        val projectIdentifier = project?.name ?: projectId

        val totalNodesCount = gisNodes.size
        val inProgressCount = progress.count { it.actual > 0f && it.actual < 100f }
        val photoNodesList = photos.map { it.objectCode }.distinct().sorted()
        val photoNodesSummary = photoNodesList.joinToString(", ")

        val delayed = progress.count { it.delayed }
        val avg = if (progress.isEmpty()) 0f else progress.map { it.actual }.average().toFloat()

        val nodesSummary = progress.joinToString("\n") { p ->
            val matchingGis = gisNodes.find { it.code == p.nodeCode }
            "- Nút/Tuyến [${p.nodeCode}]: Kế hoạch ${"%.1f".format(p.planned)}%, thực tế đạt ${"%.1f".format(p.actual)}%${if (p.delayed) " [CHẬM TIẾN ĐỘ]" else ""}. Nhà thầu: ${matchingGis?.contractor ?: "Chưa rõ"}."
        }

        val delayedNodesByContractor = progress.filter { it.delayed }.groupBy { p ->
            gisNodes.find { it.code == p.nodeCode }?.contractor ?: "Chưa rõ"
        }
        val contractorsSummary = if (delayedNodesByContractor.isEmpty()) {
            "- Tất cả các nhà thầu đều đảm bảo đúng tiến độ thi công theo kế hoạch."
        } else {
            delayedNodesByContractor.entries.joinToString("\n") { (contractor, nodes) ->
                "- Nhà thầu [$contractor] đang chậm tiến độ tại ${nodes.size} điểm: ${nodes.joinToString(", ") { it.nodeCode }}. Yêu cầu bổ sung nhân sự, tăng ca để bù tiến độ bị chậm trễ."
            }
        }

        val notesSummary = logs.filter { it.note.isNotBlank() }.joinToString("\n") { l ->
            "- Hạng mục [${l.workItem}]${l.nodeCode?.let { " ($it)" } ?: ""}: ${l.note}"
        }

        val result = aiOrchestrator.execute<ReportDraftResult>(
            ReportDraftPayload(
                projectId = projectIdentifier,
                totalNodes = totalNodesCount,
                delayedNodes = delayed,
                avgActualProgress = avg,
                totalPhotos = photos.size,
                nodesSummary = nodesSummary,
                contractorsSummary = contractorsSummary,
                notesSummary = notesSummary,
                inProgressNodes = inProgressCount,
                photoNodesSummary = photoNodesSummary
            )
        ).result
        
        if (filterNodeCode == null) {
            _aiReportDraft.value = result
        }
        return result
    }

    private fun launchExport(action: suspend () -> Unit) {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } finally {
                _isExporting.value = false
            }
        }
    }


    fun exportPdf(filterNodeCode: String? = null) {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val photos = (photoRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val progress = (progressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val materialRowsRaw = (materialProgressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val nodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            val dailyLogs = (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val activeDraft = if (filterNodeCode != null) {
                generateAiReportDraft(projectId, filterNodeCode)
            } else {
                _aiReportDraft.value ?: generateAiReportDraft(projectId)
            }
            val exportContent = buildReportExportContent(
                projectId = projectId,
                filterNodeCode = filterNodeCode,
                photos = photos,
                progress = progress,
                materialRowsRaw = materialRowsRaw,
                nodes = nodes,
                routes = routes,
                dailyLogs = dailyLogs,
                activeDraft = activeDraft
            )
            val file = pdfReportGenerator.exportProjectSummary(
                context,
                exportContent.targetId,
                exportContent.lines,
                exportContent.materialRows,
                exportContent.photos,
                exportContent.dailyLogLines
            )
            _lastReportPath.value = file.absolutePath
        }
    }

    fun exportWord(filterNodeCode: String? = null) {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val photos = (photoRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val progress = (progressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val materialRowsRaw = (materialProgressRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val nodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
            val dailyLogs = (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
            val activeDraft = if (filterNodeCode != null) {
                generateAiReportDraft(projectId, filterNodeCode)
            } else {
                _aiReportDraft.value ?: generateAiReportDraft(projectId)
            }
            val exportContent = buildReportExportContent(
                projectId = projectId,
                filterNodeCode = filterNodeCode,
                photos = photos,
                progress = progress,
                materialRowsRaw = materialRowsRaw,
                nodes = nodes,
                routes = routes,
                dailyLogs = dailyLogs,
                activeDraft = activeDraft
            )
            val file = docxReportGenerator.exportProjectSummary(
                context,
                exportContent.targetId,
                exportContent.lines,
                exportContent.materialRows,
                exportContent.photos
            )
            _lastWordReportPath.value = file.absolutePath
        }
    }

    fun exportPackageZip() {
        launchExport {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launchExport
            val zip = projectPackageService.exportProjectZip(projectId)
            _lastPackagePath.value = zip.absolutePath
        }
    }

    private fun buildMaterialReportRows(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        rows: List<MaterialProgress>
    ): List<MaterialReportRow> {
        val plannedMap = mutableMapOf<String, Float>()
        val nodeCodesByMaterial = mutableMapOf<String, MutableSet<String>>()
        val nodesById = nodes.associateBy { it.id }
        val nodesByCode = nodes.associateBy { it.code }
        nodes.forEach { node ->
            parseMaterialSummary(node.materialSummary).forEach { (name, qty) ->
                plannedMap[name] = (plannedMap[name] ?: 0f) + qty
                val nodeCode = node.code.ifBlank { node.id }
                if (nodeCode.isNotBlank()) {
                    nodeCodesByMaterial.getOrPut(name) { mutableSetOf() }.add(nodeCode)
                }
            }
        }

        rows.forEach { row ->
            val materialName = row.materialName.trim()
            if (materialName.isNotBlank() && !materialName.equals("routeLength", ignoreCase = true)) {
                val nodeCode = nodesById[row.nodeCode]?.code
                    ?: nodesByCode[row.nodeCode]?.code
                    ?: row.nodeCode
                if (nodeCode.isNotBlank()) {
                    nodeCodesByMaterial.getOrPut(materialName) { mutableSetOf() }.add(nodeCode)
                }
            }
        }

        val allMaterialNames = (plannedMap.keys + rows.map { it.materialName.trim() })
            .filter { it.isNotBlank() && !it.equals("routeLength", ignoreCase = true) }
            .distinct()
            .sorted()
        if (allMaterialNames.isEmpty()) return emptyList()

        val materialRows = allMaterialNames.map { materialName ->
            val planned = plannedMap[materialName] ?: 0f
            val actual = rows.filter { it.materialName.trim() == materialName }
                .sumOf { it.actualQty.toDouble() }.toFloat()
            val nodeCodes = nodeCodesByMaterial[materialName].orEmpty()
            MaterialReportRow(
                materialName = materialName,
                nodeCount = nodeCodes.size,
                routeCount = countRoutesForNodes(routes, nodeCodes),
                totalPlannedQty = planned,
                totalActualQty = actual,
                completionPercent = if (planned <= 0f) 0f else (actual / planned) * 100f
            )
        }

        val plannedTotal = materialRows.sumOf { it.totalPlannedQty.toDouble() }.toFloat()
        val actualTotal = materialRows.sumOf { it.totalActualQty.toDouble() }.toFloat()
        val totalNodeCodes = nodeCodesByMaterial.values.flatten().toSet()
        val totalRow = MaterialReportRow(
            materialName = "Tổng",
            nodeCount = totalNodeCodes.size,
            routeCount = countRoutesForNodes(routes, totalNodeCodes),
            totalPlannedQty = plannedTotal,
            totalActualQty = actualTotal,
            completionPercent = if (plannedTotal <= 0f) 0f else (actualTotal / plannedTotal) * 100f,
            isTotal = true
        )
        return materialRows + totalRow
    }

    private fun parseMaterialSummary(summary: String): List<Pair<String, Float>> {
        return summary.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.contains(":")) return@mapNotNull null
            val name = trimmed.substringBefore(":").trim()
            val qty = trimmed.substringAfter(":").trim().toFloatOrNull()
            if (qty == null || name.isBlank() || name.equals("routeLength", ignoreCase = true)) {
                null
            } else {
                name to qty
            }
        }.toList()
    }

    private fun countRoutesForNodes(routes: List<GisRoute>, nodeCodes: Set<String>): Int {
        if (nodeCodes.isEmpty()) return 0
        return routes.count { route ->
            route.startNodeCode in nodeCodes || route.endNodeCode in nodeCodes
        }
    }

    fun clearExportPaths() {
        _lastReportPath.value = null
        _lastWordReportPath.value = null
        _lastPackagePath.value = null
    }
}

data class MaterialReportRow(
    val materialName: String,
    val nodeCount: Int = 0,
    val routeCount: Int = 0,
    val totalPlannedQty: Float,
    val totalActualQty: Float,
    val completionPercent: Float,
    val isTotal: Boolean = false
)
