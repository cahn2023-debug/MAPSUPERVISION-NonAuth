package com.mapsupervision.project.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.*
import com.mapsupervision.domain.repository.*
import com.mapsupervision.storage.ProjectPackageService
import com.mapsupervision.storage.ProjectStorageManager
import com.mapsupervision.storage.importer.UserFileImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val activeProjectRepository: ActiveProjectRepository,
    private val importedFileRepository: ImportedFileRepository,
    private val userFileImportService: UserFileImportService,
    private val projectPackageService: ProjectPackageService,
    private val storageManager: ProjectStorageManager,
    private val gisRepository: GisRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val materialProgressRepository: MaterialProgressRepository,
    private val photoRepository: PhotoRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val progressRepository: ProgressRepository,
    private val projectSyncRepository: ProjectSyncRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    init {
        observeActiveProject()
        observeProjectSync()
        refresh()
    }

    private fun observeActiveProject() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refresh()
            }
        }
    }

    private fun observeProjectSync() {
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _uiState.value
            var activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data
            val projects = (projectRepository.list(false) as? AppResult.Success)?.data.orEmpty()
            if (activeId == null && projects.isNotEmpty()) {
                val defaultProjectId = projects.first().id
                when (activeProjectRepository.setActive(defaultProjectId)) {
                    is AppResult.Success -> activeId = defaultProjectId
                    is AppResult.Error -> {}
                }
            }
            val imported = if (activeId != null) {
                (importedFileRepository.byProject(activeId) as? AppResult.Success)?.data.orEmpty()
            } else emptyList()
            _uiState.value = current.copy(projects = projects, activeProjectId = activeId, importedFiles = imported)
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(message = "Tên dự án không được để trống")
                return@launch
            }
            val created = projectRepository.create(name)
            if (created is AppResult.Success) {
                when (val setActive = activeProjectRepository.setActive(created.data.id)) {
                    is AppResult.Success -> {
                        _uiState.value = _uiState.value.copy(message = "Đã tạo và mở dự án: ${created.data.name}")
                    }
                    is AppResult.Error -> {
                        _uiState.value = _uiState.value.copy(message = "Tạo dự án thành công nhưng không thể kích hoạt: ${setActive.throwable.message}")
                    }
                }
            } else if (created is AppResult.Error) {
                _uiState.value = _uiState.value.copy(message = "Không tạo được dự án: ${created.throwable.message}")
            }
            refresh()
        }
    }

    fun switchProject(projectId: String) {
        viewModelScope.launch {
            when (val setActive = activeProjectRepository.setActive(projectId)) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(message = "Đã mở dự án")
                is AppResult.Error -> _uiState.value = _uiState.value.copy(message = "Không mở được dự án: ${setActive.throwable.message}")
            }
            refresh()
        }
    }

    fun cloneProject(sourceProjectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.clone(sourceProjectId, newName)
            projectSyncRepository.notifyProjectChanged(null, "project_cloned")
            refresh()
        }
    }

    fun archiveProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.archive(projectId)
            projectSyncRepository.notifyProjectChanged(projectId, "project_archived")
            refresh()
        }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val projectId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            _uiState.value = _uiState.value.copy(importMessage = "Đang import ${uris.size} file...")

            var importedCount = 0
            uris.forEach { uri ->
                runCatching {
                    val draft = userFileImportService.importFile(projectId, uri)
                    val file = ImportedFile(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        fileName = draft.fileName,
                        fileType = draft.fileType,
                        storedPath = draft.storedPath,
                        summary = draft.summary,
                        importedAtEpochMs = System.currentTimeMillis()
                    )
                    importedFileRepository.upsert(file)
                    importedCount++
                }
            }

            _uiState.value = _uiState.value.copy(importMessage = "Đã import $importedCount/${uris.size} file")
            projectRepository.touch(projectId)
            projectSyncRepository.notifyProjectChanged(projectId, "project_files_imported")
            refresh()
        }
    }

    fun exportProject(context: Context, project: Project) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "Đang xuất dự án ${project.name}...")

            val nodes = (gisRepository.searchNodes(project.id, "") as? AppResult.Success)?.data.orEmpty()
            val routes = (gisRepository.searchRoutes(project.id, "") as? AppResult.Success)?.data.orEmpty()
            val notes = (noteRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val tasks = (taskRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val materialProgress = (materialProgressRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val dailyLogs = (dailyLogRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val importedFiles = (importedFileRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val photos = (photoRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()
            val progress = (progressRepository.byProject(project.id) as? AppResult.Success)?.data.orEmpty()

            val json = JSONObject().apply {
                put("metadataVersion", project.metadataVersion)
                put("exportedAtEpochMs", System.currentTimeMillis())
                put("updatedAtEpochMs", project.updatedAtEpochMs)
                put("project", JSONObject().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("slug", project.slug)
                    put("isArchived", project.isArchived)
                    put("createdAtEpochMs", project.createdAtEpochMs)
                    put("metadataVersion", project.metadataVersion)
                    put("updatedAtEpochMs", project.updatedAtEpochMs)
                    put("storageMode", project.storageMode.name)
                    put("projectDbPath", project.projectDbPath)
                })
                put("nodes", JSONArray().apply {
                    nodes.forEach { n ->
                        put(JSONObject().apply {
                            put("id", n.id)
                            put("projectId", n.projectId)
                            put("code", n.code)
                            put("contractor", n.contractor)
                            put("latitude", n.latitude)
                            put("longitude", n.longitude)
                            put("mapNumberLabel", n.mapNumberLabel)
                            put("materialSummary", n.materialSummary)
                            put("importedFileId", n.importedFileId)
                        })
                    }
                })
                put("routes", JSONArray().apply {
                    routes.forEach { r ->
                        put(JSONObject().apply {
                            put("id", r.id)
                            put("projectId", r.projectId)
                            put("code", r.code)
                            put("contractor", r.contractor)
                            put("startNodeCode", r.startNodeCode)
                            put("endNodeCode", r.endNodeCode)
                            put("importedFileId", r.importedFileId)
                        })
                    }
                })
                put("notes", JSONArray().apply {
                    notes.forEach { nt ->
                        put(JSONObject().apply {
                            put("id", nt.id)
                            put("projectId", nt.projectId)
                            put("objectCode", nt.objectCode)
                            put("content", nt.content)
                            put("createdAtEpochMs", nt.createdAtEpochMs)
                        })
                    }
                })
                put("tasks", JSONArray().apply {
                    tasks.forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id)
                            put("projectId", t.projectId)
                            put("objectCode", t.objectCode)
                            put("title", t.title)
                            put("description", t.description)
                            put("status", t.status.name)
                            put("createdAtEpochMs", t.createdAtEpochMs)
                            put("completedAtEpochMs", t.completedAtEpochMs ?: JSONObject.NULL)
                        })
                    }
                })
                put("materialProgress", JSONArray().apply {
                    materialProgress.forEach { mp ->
                        put(JSONObject().apply {
                            put("id", mp.id)
                            put("projectId", mp.projectId)
                            put("nodeCode", mp.nodeCode)
                            put("materialName", mp.materialName)
                            put("plannedQty", mp.plannedQty)
                            put("actualQty", mp.actualQty)
                            put("updatedAtEpochMs", mp.updatedAtEpochMs)
                        })
                    }
                })
                put("dailyLogs", JSONArray().apply {
                    dailyLogs.forEach { dl ->
                        put(JSONObject().apply {
                            put("id", dl.id)
                            put("projectId", dl.projectId)
                            put("workItem", dl.workItem)
                            put("manpower", dl.manpower)
                            put("note", dl.note)
                            put("createdAtEpochMs", dl.createdAtEpochMs)
                        })
                    }
                })
                put("importedFiles", JSONArray().apply {
                    importedFiles.forEach { inf ->
                        put(JSONObject().apply {
                            put("id", inf.id)
                            put("projectId", inf.projectId)
                            put("fileName", inf.fileName)
                            put("fileType", inf.fileType)
                            put("storedPath", inf.storedPath)
                            put("summary", inf.summary)
                            put("importedAtEpochMs", inf.importedAtEpochMs)
                        })
                    }
                })
                put("photos", JSONArray().apply {
                    photos.forEach { ph ->
                        put(JSONObject().apply {
                            put("id", ph.id)
                            put("projectId", ph.projectId)
                            put("objectCode", ph.objectCode)
                            put("filePath", ph.filePath)
                            put("thumbnailPath", ph.thumbnailPath)
                            put("latitude", ph.latitude ?: JSONObject.NULL)
                            put("longitude", ph.longitude ?: JSONObject.NULL)
                            put("locationAccuracyM", ph.locationAccuracyM ?: JSONObject.NULL)
                            put("isGpsMocked", ph.isGpsMocked)
                            put("locationStatus", ph.locationStatus.name)
                            put("engineer", ph.engineer)
                            put("capturedAtEpochMs", ph.capturedAtEpochMs)
                        })
                    }
                })
                put("progress", JSONArray().apply {
                    progress.forEach { pr ->
                        put(JSONObject().apply {
                            put("id", pr.id)
                            put("projectId", pr.projectId)
                            put("nodeCode", pr.nodeCode)
                            put("planned", pr.planned)
                            put("actual", pr.actual)
                            put("remain", pr.remain)
                            put("delayed", pr.delayed)
                            put("updatedAtEpochMs", pr.updatedAtEpochMs)
                        })
                    }
                })
            }

            try {
                val projectRoot = storageManager.projectRoot(project.slug)
                val metadataFile = File(projectRoot, "project_metadata.json")
                metadataFile.writeText(json.toString(), Charsets.UTF_8)

                val zipFile = projectPackageService.exportProjectZip(project.slug)

                val publicExportsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MapSupervision/Exports").apply { mkdirs() }
                val publicZip = File(publicExportsDir, "${project.slug}_backup_${System.currentTimeMillis()}.zip")
                zipFile.inputStream().use { input ->
                    publicZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                storageManager.scanFile(publicZip)

                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, publicZip)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Chia sẻ gói dự án").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)

                _uiState.value = _uiState.value.copy(message = "Đã xuất và chia sẻ dự án: ${project.name}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Lỗi khi xuất dự án: ${e.message}")
            }
            refresh()
        }
    }

    fun dismissDuplicateDialog() {
        _uiState.value = _uiState.value.copy(duplicateProjectToResolve = null, duplicateZipUri = null)
    }

    fun importProject(context: Context, zipUri: Uri, overwrite: Boolean = false, createCopy: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importMessage = "Đang kiểm tra tệp dự án...")

            try {
                val tempZip = File(context.cacheDir, "temp_import_${UUID.randomUUID()}.zip")
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val tempDir = File(context.cacheDir, "temp_import_dir_${UUID.randomUUID()}").apply { mkdirs() }
                java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    val buffer = ByteArray(4096)
                    while (entry != null) {
                        val file = File(tempDir, entry.name)
                        if (!file.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                            throw SecurityException("Zip entry lies outside temp dir")
                        }
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fos ->
                                var len = zis.read(buffer)
                                while (len > 0) {
                                    fos.write(buffer, 0, len)
                                    len = zis.read(buffer)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val metadataFile = File(tempDir, "project_metadata.json")
                if (!metadataFile.exists()) {
                    _uiState.value = _uiState.value.copy(importMessage = "Lỗi: Không tìm thấy project_metadata.json trong tệp zip")
                    return@launch
                }

                val json = JSONObject(metadataFile.readText(Charsets.UTF_8))
                val projJson = json.getJSONObject("project")
                val originalId = projJson.getString("id")
                val originalName = projJson.getString("name")
                val originalSlug = projJson.getString("slug")

                val projectsList = (projectRepository.list(true) as? AppResult.Success)?.data.orEmpty()
                val existingProject = projectsList.find { it.slug == originalSlug || it.id == originalId }

                if (existingProject != null && !overwrite && !createCopy) {
                    _uiState.value = _uiState.value.copy(
                        duplicateProjectToResolve = existingProject,
                        duplicateZipUri = zipUri,
                        importMessage = ""
                    )
                    return@launch
                }

                val targetProjectId: String
                val targetSlug: String
                val targetName: String

                if (existingProject != null && overwrite) {
                    targetProjectId = existingProject.id
                    targetSlug = existingProject.slug
                    targetName = existingProject.name
                    projectRepository.clearProject(targetProjectId)
                } else if (createCopy) {
                    targetProjectId = UUID.randomUUID().toString()
                    val newSuffix = " - Bản sao"
                    var nameCandidate = "$originalName$newSuffix"
                    var slugCandidate = "${originalSlug}-copy"
                    var count = 1
                    while (projectsList.any { it.slug == slugCandidate }) {
                        count++
                        nameCandidate = "$originalName$newSuffix ($count)"
                        slugCandidate = "${originalSlug}-copy-$count"
                    }
                    targetName = nameCandidate
                    targetSlug = slugCandidate
                } else {
                    targetProjectId = originalId
                    targetSlug = originalSlug
                    targetName = originalName
                }

                projectPackageService.importProjectZip(tempZip.inputStream(), targetSlug)

                val projObj = Project(
                    id = targetProjectId,
                    name = targetName,
                    slug = targetSlug,
                    isArchived = false,
                    createdAtEpochMs = projJson.optLong("createdAtEpochMs", System.currentTimeMillis()),
                    metadataVersion = projJson.optInt("metadataVersion", json.optInt("metadataVersion", CURRENT_METADATA_VERSION)),
                    updatedAtEpochMs = projJson.optLong("updatedAtEpochMs", json.optLong("updatedAtEpochMs", System.currentTimeMillis())),
                    storageMode = projJson.optString("storageMode").takeIf { it.isNotBlank() }?.let(ProjectStorageMode::valueOf)
                        ?: ProjectStorageMode.PROJECT_DB,
                    projectDbPath = projJson.optString("projectDbPath")
                )
                projectRepository.importProject(projObj)

                val copiedIds = mutableMapOf<String, String>()
                fun mapId(id: String): String =
                    if (createCopy) copiedIds.getOrPut(id) { UUID.randomUUID().toString() } else id

                fun mapOptionalId(id: String?): String? = id?.let(::mapId)

                val nodesArr = json.optJSONArray("nodes")
                if (nodesArr != null) {
                    val nodes = mutableListOf<GisNode>()
                    for (i in 0 until nodesArr.length()) {
                        val obj = nodesArr.getJSONObject(i)
                        nodes += GisNode(
                            id = mapId(obj.getString("id")),
                            projectId = targetProjectId,
                            code = obj.getString("code"),
                            contractor = obj.getString("contractor"),
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            mapNumberLabel = obj.getString("mapNumberLabel"),
                            materialSummary = obj.getString("materialSummary"),
                            importedFileId = mapOptionalId(obj.optString("importedFileId").takeIf { it.isNotBlank() })
                        )
                    }
                    gisRepository.upsertNodes(nodes)
                }

                val routesArr = json.optJSONArray("routes")
                if (routesArr != null) {
                    val routes = mutableListOf<GisRoute>()
                    for (i in 0 until routesArr.length()) {
                        val obj = routesArr.getJSONObject(i)
                        routes += GisRoute(
                            id = mapId(obj.getString("id")),
                            projectId = targetProjectId,
                            code = obj.getString("code"),
                            contractor = obj.getString("contractor"),
                            startNodeCode = obj.getString("startNodeCode"),
                            endNodeCode = obj.getString("endNodeCode"),
                            importedFileId = mapOptionalId(obj.optString("importedFileId").takeIf { it.isNotBlank() })
                        )
                    }
                    gisRepository.upsertRoutes(routes)
                }

                val notesArr = json.optJSONArray("notes")
                if (notesArr != null) {
                    for (i in 0 until notesArr.length()) {
                        val obj = notesArr.getJSONObject(i)
                        noteRepository.add(
                            Note(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                content = obj.getString("content"),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val tasksArr = json.optJSONArray("tasks")
                if (tasksArr != null) {
                    for (i in 0 until tasksArr.length()) {
                        val obj = tasksArr.getJSONObject(i)
                        taskRepository.upsert(
                            Task(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                status = TaskStatus.valueOf(obj.getString("status")),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                                completedAtEpochMs = obj.optPositiveLong("completedAtEpochMs")
                            )
                        )
                    }
                }

                val matArr = json.optJSONArray("materialProgress")
                if (matArr != null) {
                    for (i in 0 until matArr.length()) {
                        val obj = matArr.getJSONObject(i)
                        materialProgressRepository.upsert(
                            MaterialProgress(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                nodeCode = obj.getString("nodeCode"),
                                materialName = obj.getString("materialName"),
                                plannedQty = obj.getDouble("plannedQty").toFloat(),
                                actualQty = obj.getDouble("actualQty").toFloat(),
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val logsArr = json.optJSONArray("dailyLogs")
                if (logsArr != null) {
                    for (i in 0 until logsArr.length()) {
                        val obj = logsArr.getJSONObject(i)
                        dailyLogRepository.add(
                            DailyLog(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                workItem = obj.getString("workItem"),
                                manpower = obj.getInt("manpower"),
                                note = obj.getString("note"),
                                createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val impArr = json.optJSONArray("importedFiles")
                if (impArr != null) {
                    for (i in 0 until impArr.length()) {
                        val obj = impArr.getJSONObject(i)
                        importedFileRepository.upsert(
                            ImportedFile(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                fileName = obj.getString("fileName"),
                                fileType = obj.getString("fileType"),
                                storedPath = obj.getString("storedPath"),
                                summary = obj.getString("summary"),
                                importedAtEpochMs = obj.optLong("importedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val photosArr = json.optJSONArray("photos")
                if (photosArr != null) {
                    for (i in 0 until photosArr.length()) {
                        val obj = photosArr.getJSONObject(i)
                        photoRepository.add(
                            SitePhoto(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                objectCode = obj.getString("objectCode"),
                                filePath = obj.getString("filePath"),
                                thumbnailPath = obj.getString("thumbnailPath"),
                                latitude = obj.optNullableDouble("latitude"),
                                longitude = obj.optNullableDouble("longitude"),
                                locationAccuracyM = obj.optNullableFloat("locationAccuracyM"),
                                isGpsMocked = obj.optBoolean("isGpsMocked", false),
                                locationStatus = obj.optString("locationStatus").takeIf { it.isNotBlank() }?.let(PhotoLocationStatus::valueOf)
                                    ?: PhotoLocationStatus.MISSING,
                                engineer = obj.optString("engineer", "Engineers"),
                                capturedAtEpochMs = obj.optLong("capturedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val progArr = json.optJSONArray("progress")
                if (progArr != null) {
                    for (i in 0 until progArr.length()) {
                        val obj = progArr.getJSONObject(i)
                        progressRepository.upsert(
                            NodeProgress(
                                id = mapId(obj.getString("id")),
                                projectId = targetProjectId,
                                nodeCode = obj.getString("nodeCode"),
                                planned = obj.getDouble("planned").toFloat(),
                                actual = obj.getDouble("actual").toFloat(),
                                remain = obj.getDouble("remain").toFloat(),
                                delayed = obj.getBoolean("delayed"),
                                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                    }
                }

                activeProjectRepository.setActive(targetProjectId)
                projectSyncRepository.notifyProjectChanged(targetProjectId, "project_imported")
                _uiState.value = _uiState.value.copy(
                    importMessage = "Đã nhập dự án thành công: $targetName",
                    duplicateProjectToResolve = null,
                    duplicateZipUri = null
                )

                // Clean up temp
                tempZip.delete()
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importMessage = "Lỗi khi nhập dự án: ${e.message}")
            }
            refresh()
        }
    }
}

data class ProjectUiState(
    val projects: List<Project> = emptyList(),
    val activeProjectId: String? = null,
    val importedFiles: List<ImportedFile> = emptyList(),
    val importMessage: String = "",
    val message: String = "",
    val duplicateProjectToResolve: Project? = null,
    val duplicateZipUri: Uri? = null
)

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun JSONObject.optNullableFloat(name: String): Float? =
    optNullableDouble(name)?.toFloat()

private fun JSONObject.optPositiveLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name).takeIf { it > 0L }
