package com.mapsupervision.photo.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.ai.AiOrchestrator
import com.mapsupervision.domain.ai.PhotoQualityPayload
import com.mapsupervision.domain.ai.PhotoQualityResult
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.domain.repository.ProjectSyncRepository
import com.mapsupervision.photo.location.PhotoLocationProvider
import com.mapsupervision.photo.worker.PhotoPipelineService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PhotoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val activeProjectRepository: ActiveProjectRepository,
    private val gisRepository: GisRepository,
    private val projectRepository: ProjectRepository,
    private val projectSyncRepository: ProjectSyncRepository,
    private val locationProvider: PhotoLocationProvider,
    private val photoPipelineService: PhotoPipelineService,
    private val aiOrchestrator: AiOrchestrator
) : ViewModel() {
    private val _photos = MutableStateFlow<List<SitePhoto>>(emptyList())
    val photos: StateFlow<List<SitePhoto>> = _photos.asStateFlow()
    private val _lastAiPhotoQuality = MutableStateFlow<PhotoQualityResult?>(null)
    val lastAiPhotoQuality: StateFlow<PhotoQualityResult?> = _lastAiPhotoQuality.asStateFlow()
    private val _selectedPhotoForReview = MutableStateFlow<SitePhoto?>(null)
    val selectedPhotoForReview: StateFlow<SitePhoto?> = _selectedPhotoForReview.asStateFlow()
    private val _availableTagOptions = MutableStateFlow<List<String>>(emptyList())
    val availableTagOptions: StateFlow<List<String>> = _availableTagOptions.asStateFlow()
    private var activeProjectIdCache: String? = null

    init {
        observeSharedState()
        refresh()
    }

    private fun observeSharedState() {
        viewModelScope.launch {
            activeProjectRepository.activeProjectId.collectLatest {
                refresh()
            }
        }
        viewModelScope.launch {
            projectSyncRepository.events.collectLatest { event ->
                val activeProjectId = activeProjectRepository.activeProjectId.value
                if (event.projectId == null || event.projectId == activeProjectId) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            activeProjectIdCache = activeId
            val result = photoRepository.byProject(activeId)
            _photos.value = (result as? AppResult.Success)?.data.orEmpty()
            refreshTagOptions(activeId)
            _selectedPhotoForReview.value = _selectedPhotoForReview.value?.let { selected ->
                _photos.value.firstOrNull { it.id == selected.id } ?: selected
            }
        }
    }

    private suspend fun refreshTagOptions(projectId: String) {
        val nodes = (gisRepository.searchNodes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        val routes = (gisRepository.searchRoutes(projectId, "") as? AppResult.Success)?.data.orEmpty()
        _availableTagOptions.value = buildSet {
            nodes.forEach { node ->
                if (node.code.isNotBlank()) add(node.code.trim())
            }
            routes.forEach { route ->
                if (route.code.isNotBlank()) add(route.code.trim())
            }
        }.toList().sorted()
    }

    fun selectPhotoForReview(photoId: String) {
        _selectedPhotoForReview.value = _photos.value.firstOrNull { it.id == photoId }
    }

    fun clearPhotoReviewSelection() {
        _selectedPhotoForReview.value = null
    }

    fun updateSelectedPhotoTags(tagCodesCsv: String) {
        val current = _selectedPhotoForReview.value ?: return
        _selectedPhotoForReview.value = current.copy(
            tagCodesCsv = tagCodesCsv,
            matchedNodeCode = tagCodesCsv.split(',').firstOrNull { it.isNotBlank() }?.trim()
                ?: current.matchedNodeCode,
            matchedRouteCode = tagCodesCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
                .drop(1)
                .firstOrNull() ?: current.matchedRouteCode
        )
    }

    fun updateSelectedPhotoOffsetMinutes(offsetMinutes: Int) {
        val current = _selectedPhotoForReview.value ?: return
        val offsetMs = offsetMinutes.toLong() * 60_000L
        val matchedAt = current.capturedAtEpochMs + offsetMs
        _selectedPhotoForReview.value = current.copy(
            matchingTimeOffsetMs = offsetMs,
            matchedAtEpochMs = matchedAt,
            matchedNodeCode = current.tagCodesCsv.split(',').firstOrNull { it.isNotBlank() }?.trim()
                ?: current.matchedNodeCode
        )
    }

    fun toggleSelectedPhotoTag(tagCode: String) {
        val current = _selectedPhotoForReview.value ?: return
        val normalized = tagCode.trim()
        if (normalized.isBlank()) return
        val nextTags = current.tagCodesCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != normalized }
            .toMutableList()
        if (current.tagCodesCsv.split(',').map { it.trim() }.any { it == normalized }) {
            // tag already present; remove it
        } else {
            nextTags.add(normalized)
        }
        updateSelectedPhotoTags(nextTags.joinToString(", "))
    }

    fun saveSelectedPhotoReview() {
        val current = _selectedPhotoForReview.value ?: return
        viewModelScope.launch {
            photoRepository.add(current)
            refresh()
        }
    }

    fun createCaptureFile(objectCode: String): File? {
        val activeId = activeProjectIdCache ?: return null
        return photoPipelineService.createCaptureOutputFile(activeId, objectCode)
    }

    fun registerCapturedPhoto(file: File, objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val location = locationProvider.lastKnownLocation()
            withContext(Dispatchers.IO) {
                photoPipelineService.applyWatermark(file, objectCode, engineer)
                savePhotoModel(activeId, objectCode, engineer, file, location)
                _lastAiPhotoQuality.value = aiOrchestrator.execute<PhotoQualityResult>(
                    PhotoQualityPayload(
                        objectCode = objectCode,
                        engineer = engineer,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        filePath = file.absolutePath
                    )
                ).result
            }
            markProjectChanged(activeId, "photo_registered")
            refresh()
        }
    }

    fun addDemoPhoto(objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val location = locationProvider.lastKnownLocation()
            withContext(Dispatchers.IO) {
                val file = photoPipelineService.createEmptyPhoto(activeId, objectCode, engineer)
                savePhotoModel(activeId, objectCode, engineer, file, location)
                _lastAiPhotoQuality.value = aiOrchestrator.execute<PhotoQualityResult>(
                    PhotoQualityPayload(
                        objectCode = objectCode,
                        engineer = engineer,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        filePath = file.absolutePath
                    )
                ).result
            }
            markProjectChanged(activeId, "demo_photo_added")
            refresh()
        }
    }

    fun importFromGallery(uris: List<Uri>, objectCode: String, engineer: String) {
        viewModelScope.launch {
            val activeId = (activeProjectRepository.getActive() as? AppResult.Success)?.data ?: return@launch
            val location = locationProvider.lastKnownLocation()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        val file = photoPipelineService.importFromGallery(context, activeId, objectCode, engineer, uri)
                        savePhotoModel(activeId, objectCode, engineer, file, location)
                    }
                }
            }
            markProjectChanged(activeId, "gallery_photos_imported")
            refresh()
        }
    }

    private suspend fun markProjectChanged(projectId: String, reason: String) {
        projectRepository.touch(projectId)
        projectSyncRepository.notifyProjectChanged(projectId, reason)
    }

    private suspend fun savePhotoModel(
        projectId: String,
        objectCode: String,
        engineer: String,
        file: File,
        location: com.mapsupervision.domain.model.PhotoLocationSnapshot
    ) {
        val thumb = photoPipelineService.createThumbnail(projectId, file)
        val capturedAt = System.currentTimeMillis()
        val model = SitePhoto(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            objectCode = objectCode,
            tagCodesCsv = objectCode,
            matchedNodeCode = objectCode,
            matchedRouteCode = null,
            filePath = file.absolutePath,
            thumbnailPath = thumb.absolutePath,
            latitude = location.latitude,
            longitude = location.longitude,
            locationAccuracyM = location.accuracyM,
            isGpsMocked = location.isMock,
            locationStatus = location.status,
            engineer = engineer,
            capturedAtEpochMs = capturedAt,
            matchedAtEpochMs = capturedAt,
            matchingTimeOffsetMs = 0L
        )
        photoRepository.add(model)
    }
}
