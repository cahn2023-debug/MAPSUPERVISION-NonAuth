package com.mapsupervision.data.db

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ProjectScopedDatabaseProviderTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var sharedDatabase: MapSupervisionDatabase
    private val openedDatabases = mutableListOf<MapSupervisionDatabase>()

    @Before
    fun setUp() {
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext())
        tempDir = Files.createTempDirectory("project-scoped-db-test").toFile()
        sharedDatabase = createDatabase(File(tempDir, "shared.sqlite"))
    }

    @After
    fun tearDown() {
        openedDatabases.distinct().forEach { runCatching { it.close() } }
        runCatching { sharedDatabase.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `databaseFor seeds empty scoped db from shared db`() = runBlocking {
        val project = projectEntity("project-1", File(tempDir, "scoped/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.importedFileDao().upsert(
            ImportedFileEntity(
                id = "file-1",
                projectId = project.id,
                fileName = "nodes.kml",
                fileType = "KML",
                storedPath = "/tmp/nodes.kml",
                summary = "seed",
                importedAtEpochMs = 100L
            )
        )
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "node-1",
                projectId = project.id,
                code = "N-1",
                contractor = "Nam Ky",
                latitude = 21.028,
                longitude = 105.854,
                mapNumberLabel = "229",
                materialSummary = "Cable",
                importedFileId = "file-1"
            )
        )
        sharedDatabase.gisRouteDao().upsert(
            GisRouteEntity(
                id = "route-1",
                projectId = project.id,
                code = "R-1",
                contractor = "Nam Ky",
                startNodeCode = "N-1",
                endNodeCode = "N-2",
                points = emptyList(),
                importedFileId = "file-1"
            )
        )
        sharedDatabase.nodeProgressDao().upsert(
            NodeProgressEntity(
                id = "progress-1",
                projectId = project.id,
                nodeCode = "N-1",
                planned = 10f,
                actual = 3f,
                remain = 7f,
                delayed = false,
                updatedAtEpochMs = 200L
            )
        )

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertNotNull(scopedDatabase.projectDao().get(project.id))
        assertEquals(1, scopedDatabase.importedFileDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisNodeDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.gisRouteDao().byProject(project.id).size)
        assertEquals(1, scopedDatabase.nodeProgressDao().byProject(project.id).size)
    }

    @Test
    fun `databaseFor skips seed when scoped db already has project data`() = runBlocking {
        val scopedFile = File(tempDir, "existing/project.sqlite")
        val project = projectEntity("project-2", scopedFile)
        sharedDatabase.projectDao().upsert(project)
        sharedDatabase.gisNodeDao().upsert(
            GisNodeEntity(
                id = "shared-node",
                projectId = project.id,
                code = "SHARED",
                contractor = "Shared",
                latitude = 21.0,
                longitude = 105.0,
                mapNumberLabel = "1",
                materialSummary = ""
            )
        )

        createDatabase(scopedFile).also { scopedSeed ->
            openedDatabases += scopedSeed
            scopedSeed.projectDao().upsert(project)
            scopedSeed.gisNodeDao().upsert(
                GisNodeEntity(
                    id = "scoped-node",
                    projectId = project.id,
                    code = "SCOPED",
                    contractor = "Scoped",
                    latitude = 20.0,
                    longitude = 106.0,
                    mapNumberLabel = "2",
                    materialSummary = ""
                )
            )
            scopedSeed.close()
        }

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        val nodes = scopedDatabase.gisNodeDao().byProject(project.id)
        assertEquals(1, nodes.size)
        assertEquals("SCOPED", nodes.single().code)
    }

    @Test
    fun `databaseFor skips seed when shared db has no legacy project data`() = runBlocking {
        val project = projectEntity("project-3", File(tempDir, "empty/project.sqlite"))
        sharedDatabase.projectDao().upsert(project)

        val provider = ProjectScopedDatabaseProvider(context, sharedDatabase)
        val scopedDatabase = provider.databaseFor(project.id)
        openedDatabases += scopedDatabase!!

        assertNull(scopedDatabase.gisNodeDao().findByCode(project.id, "N-1"))
        assertEquals(0, scopedDatabase.gisRouteDao().byProject(project.id).size)
        assertEquals(0, scopedDatabase.importedFileDao().byProject(project.id).size)
    }

    private fun createDatabase(file: File): MapSupervisionDatabase {
        file.parentFile?.mkdirs()
        return Room.databaseBuilder(context, MapSupervisionDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
    }

    private fun projectEntity(projectId: String, scopedFile: File) = ProjectEntity(
        id = projectId,
        name = projectId,
        slug = projectId,
        isArchived = false,
        createdAtEpochMs = 1L,
        storageMode = ProjectStorageMode.PROJECT_DB,
        projectDbPath = scopedFile.absolutePath
    )

    private class TestDatabaseContext(base: Context) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File {
            return if (name.contains(File.separatorChar) || name.contains('/')) {
                File(name)
            } else {
                super.getDatabasePath(name)
            }
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(path, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase {
            val path = getDatabasePath(name)
            path.parentFile?.mkdirs()
            return SQLiteDatabase.openDatabase(
                path.absolutePath,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler
            )
        }
    }
}
