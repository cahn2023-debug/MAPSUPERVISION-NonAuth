package com.mapsupervision.domain.usecase

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.repository.DailyLogRepository
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportedFileRepository
import com.mapsupervision.domain.repository.MaterialProgressRepository
import com.mapsupervision.domain.repository.PhotoRepository
import com.mapsupervision.domain.repository.ProgressRepository
import com.mapsupervision.domain.repository.WorkCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveWorkspaceSnapshotUseCaseTest {

    @Test
    fun combinesReactiveSourcesIntoSingleSnapshot() = runBlocking {
        val importedFiles = MutableStateFlow(listOf(ImportedFile("f1", "p1", "a.xlsx", "xlsx", "/tmp/a", "summary", 1L)))
        val nodes = MutableStateFlow(listOf(GisNode("n1", "p1", "N-1", "CTR-A", 10.0, 106.0)))
        val routes = MutableStateFlow(listOf(GisRoute("r1", "p1", "R-1", "CTR-A", "N-1", "N-2")))
        val progress = MutableStateFlow(listOf(NodeProgress("pg1", "p1", "N-1", 100f, 60f, 40f, true, 2L)))
        val materialRows = MutableStateFlow(listOf(MaterialProgress("m1", "p1", "N-1", "Cap", 100f, 50f, 3L)))
        val dailyLogs = MutableStateFlow(listOf(DailyLog("d1", "p1", "Đào rãnh", 5, "done", 4L)))
        val workCategories = MutableStateFlow(listOf(WorkCategory("w1", "p1", "Cap", "m", 5L)))
        val photos = MutableStateFlow(
            listOf(
                SitePhoto(
                    id = "s1",
                    projectId = "p1",
                    objectCode = "N-1",
                    tagCodesCsv = "N-1",
                    matchedNodeCode = "N-1",
                    matchedRouteCode = null,
                    filePath = "/tmp/p.jpg",
                    thumbnailPath = "/tmp/t.jpg",
                    latitude = null,
                    longitude = null,
                    locationAccuracyM = null,
                    isGpsMocked = false,
                    engineer = "Field",
                    capturedAtEpochMs = 6L,
                    locationStatus = PhotoLocationStatus.MISSING,
                    matchedAtEpochMs = 6L,
                    matchingTimeOffsetMs = 0L
                )
            )
        )

        val useCase = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = FakeImportedFileRepository(importedFiles),
            gisRepository = FakeGisRepository(nodes, routes),
            progressRepository = FakeProgressRepository(progress),
            materialProgressRepository = FakeMaterialProgressRepository(materialRows),
            dailyLogRepository = FakeDailyLogRepository(dailyLogs),
            workCategoryRepository = FakeWorkCategoryRepository(workCategories),
            photoRepository = FakePhotoRepository(photos)
        )

        val snapshot = useCase("p1").first()

        assertEquals("p1", snapshot.projectId)
        assertEquals(1, snapshot.importedFiles.size)
        assertEquals(1, snapshot.designNodes.size)
        assertEquals(1, snapshot.designRoutes.size)
        assertEquals(1, snapshot.constructionProgress.size)
        assertEquals(1, snapshot.materialRows.size)
        assertEquals(1, snapshot.dailyLogs.size)
        assertEquals(1, snapshot.workCategories.size)
        assertEquals(1, snapshot.sitePhotos.size)
    }

    @Test
    fun suppressesDuplicateSnapshots() = runBlocking {
        val duplicateImportedFile = ImportedFile("f1", "p1", "a.xlsx", "xlsx", "/tmp/a", "summary", 1L)
        val useCase = ObserveWorkspaceSnapshotUseCase(
            importedFileRepository = object : ImportedFileRepository {
                override suspend fun upsert(file: ImportedFile): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = AppResult.Success(listOf(duplicateImportedFile))
                override suspend fun deleteById(id: String): AppResult<Unit> = AppResult.Success(Unit)
                override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flow {
                    emit(listOf(duplicateImportedFile))
                    emit(listOf(duplicateImportedFile))
                }
            },
            gisRepository = object : GisRepository {
                private val nodes = listOf(GisNode("n1", "p1", "N-1", "CTR-A", 10.0, 106.0))
                private val routes = listOf(GisRoute("r1", "p1", "R-1", "CTR-A", "N-1", "N-2"))
                override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(nodes)
                override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(routes)
                override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(nodes.firstOrNull())
                override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = flowOf(nodes)
                override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = flowOf(routes)
            },
            progressRepository = object : ProgressRepository {
                override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flowOf(emptyList())
            },
            materialProgressRepository = object : MaterialProgressRepository {
                override suspend fun upsert(progress: MaterialProgress): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<MaterialProgress>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<MaterialProgress>> = flowOf(emptyList())
            },
            dailyLogRepository = object : DailyLogRepository {
                override suspend fun add(log: DailyLog): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flowOf(emptyList())
            },
            workCategoryRepository = object : WorkCategoryRepository {
                override suspend fun add(category: WorkCategory): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flowOf(emptyList())
            },
            photoRepository = object : PhotoRepository {
                override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
                override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
                override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(emptyList())
                override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flowOf(emptyList())
            }
        )

        val emissions = useCase("p1").toList()

        assertEquals(1, emissions.size)
    }
}

private class FakeImportedFileRepository(
    private val flow: MutableStateFlow<List<ImportedFile>>
) : ImportedFileRepository {
    override suspend fun upsert(file: ImportedFile): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertAll(files: List<ImportedFile>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<ImportedFile>> = AppResult.Success(flow.value)
    override suspend fun deleteById(id: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeByProject(projectId: String): Flow<List<ImportedFile>> = flow
}

private class FakeGisRepository(
    private val nodes: MutableStateFlow<List<GisNode>>,
    private val routes: MutableStateFlow<List<GisRoute>>
) : GisRepository {
    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(nodes.value)
    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(routes.value)
    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(nodes.value.firstOrNull())
    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = nodes
    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = routes
}

private class FakeProgressRepository(
    private val flow: MutableStateFlow<List<NodeProgress>>
) : ProgressRepository {
    override suspend fun upsert(progress: NodeProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<NodeProgress>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<NodeProgress>> = flow
}

private class FakeMaterialProgressRepository(
    private val flow: MutableStateFlow<List<MaterialProgress>>
) : MaterialProgressRepository {
    override suspend fun upsert(progress: MaterialProgress): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<MaterialProgress>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<MaterialProgress>> = flow
}

private class FakeDailyLogRepository(
    private val flow: MutableStateFlow<List<DailyLog>>
) : DailyLogRepository {
    override suspend fun add(log: DailyLog): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<DailyLog>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<DailyLog>> = flow
}

private class FakeWorkCategoryRepository(
    private val flow: MutableStateFlow<List<WorkCategory>>
) : WorkCategoryRepository {
    override suspend fun add(category: WorkCategory): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<WorkCategory>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<WorkCategory>> = flow
}

private class FakePhotoRepository(
    private val flow: MutableStateFlow<List<SitePhoto>>
) : PhotoRepository {
    override suspend fun add(photo: SitePhoto): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun byProject(projectId: String): AppResult<List<SitePhoto>> = AppResult.Success(flow.value)
    override suspend fun byObjectCode(projectId: String, objectCode: String): AppResult<List<SitePhoto>> = AppResult.Success(flow.value)
    override fun observeByProject(projectId: String): Flow<List<SitePhoto>> = flow
}
