package com.mapsupervision.domain.usecase

import com.mapsupervision.domain.model.WorkspaceSnapshot
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveWorkspaceSnapshotUseCase @Inject constructor(
    private val importedFileRepository: ImportedFileRepository,
    private val gisRepository: GisRepository,
    private val progressRepository: ProgressRepository,
    private val materialProgressRepository: MaterialProgressRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val workCategoryRepository: WorkCategoryRepository,
    private val photoRepository: PhotoRepository
) {
    operator fun invoke(projectId: String): Flow<WorkspaceSnapshot> {
        val geometryFlow = combine(
            importedFileRepository.observeByProject(projectId),
            gisRepository.observeNodes(projectId, ""),
            gisRepository.observeRoutes(projectId, ""),
            progressRepository.observeByProject(projectId)
        ) { importedFiles, nodes, routes, progress ->
            GeometryBundle(importedFiles, nodes, routes, progress)
        }

        return combine(
            geometryFlow,
            materialProgressRepository.observeByProject(projectId),
            dailyLogRepository.observeByProject(projectId),
            workCategoryRepository.observeByProject(projectId),
            photoRepository.observeByProject(projectId)
        ) { geometry, materialRows, dailyLogs, workCategories, photos ->
            WorkspaceSnapshot(
                projectId = projectId,
                importedFiles = geometry.importedFiles,
                designNodes = geometry.nodes,
                designRoutes = geometry.routes,
                constructionProgress = geometry.progress,
                materialRows = materialRows,
                dailyLogs = dailyLogs,
                workCategories = workCategories,
                sitePhotos = photos
            )
        }.distinctUntilChanged()
    }

    private data class GeometryBundle(
        val importedFiles: List<com.mapsupervision.domain.model.ImportedFile>,
        val nodes: List<com.mapsupervision.domain.model.GisNode>,
        val routes: List<com.mapsupervision.domain.model.GisRoute>,
        val progress: List<com.mapsupervision.domain.model.NodeProgress>
    )
}
