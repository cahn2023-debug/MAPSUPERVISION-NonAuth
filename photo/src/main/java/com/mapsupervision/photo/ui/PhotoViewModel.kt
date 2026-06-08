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
import com.mapsupervision.domain.model.createStoredSitePhoto
import com.mapsupervision.domain.repository.ActiveProjectRepository
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
        val model = createStoredSitePhoto(
            projectId = projectId,
            objectCode = objectCode,
            file = file,
            thumbnailFile = thumb,
            location = location,
            engineer = engineer
        )
        photoRepository.add(model)
    }
}
