package com.mapsupervision.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ProjectScopedDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedDatabase: MapSupervisionDatabase
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holders = LinkedHashMap<String, DatabaseHolder>()
    private val idleTimeoutMs = 5 * 60 * 1000L

    init {
        scope.launch {
            while (true) {
                delay(60_000L)
                closeIdleDatabases()
            }
        }
    }

    suspend fun databaseFor(projectId: String): MapSupervisionDatabase? {
        val project = sharedDatabase.projectDao().get(projectId) ?: return null
        if (project.storageMode != ProjectStorageMode.PROJECT_DB || project.projectDbPath.isBlank()) {
            return null
        }
        return openProjectDb(project)
    }

    suspend fun vacuumProjectDb(projectId: String) {
        databaseFor(projectId)?.openHelper?.writableDatabase?.execSQL("VACUUM")
    }

    private suspend fun openProjectDb(project: ProjectEntity): MapSupervisionDatabase = mutex.withLock {
        val existing = holders[project.projectDbPath]
        if (existing != null) {
            existing.lastAccessEpochMs = System.currentTimeMillis()
            return existing.database
        }

        val dbFile = File(project.projectDbPath)
        dbFile.parentFile?.mkdirs()
        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16
            )
            .fallbackToDestructiveMigration()
            .build()
        AppLogger.d("project.db.open path=${dbFile.absolutePath}")
        ensureLegacyHydrated(project, database)
        holders[project.projectDbPath] = DatabaseHolder(database)
        database
    }

    private suspend fun ensureLegacyHydrated(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ) {
        val scopedHasData = hasAnyProjectRows(projectDatabase, project.id)
        val sharedHasData = hasAnyProjectRows(sharedDatabase, project.id)
        val scopedNodes = countProjectRows(projectDatabase, "gis_node", project.id)
        val scopedRoutes = countProjectRows(projectDatabase, "gis_route", project.id)
        val sharedNodes = countProjectRows(sharedDatabase, "gis_node", project.id)
        val sharedRoutes = countProjectRows(sharedDatabase, "gis_route", project.id)
        AppLogger.d(
            "project.db.seed.detect projectId=${project.id} " +
                "sharedNodes=$sharedNodes sharedRoutes=$sharedRoutes " +
                "scopedNodes=$scopedNodes scopedRoutes=$scopedRoutes"
        )
        if (scopedHasData) {
            AppLogger.d("project.db.seed.skip projectId=${project.id} reason=scoped_not_empty")
            return
        }
        if (!sharedHasData) {
            AppLogger.d("project.db.seed.skip projectId=${project.id} reason=shared_empty")
            return
        }

        AppLogger.d("project.db.seed.start projectId=${project.id}")
        val payload = sharedPayload(project.id) ?: run {
            AppLogger.d("project.db.seed.skip projectId=${project.id} reason=shared_payload_empty")
            return
        }
        projectDatabase.withTransaction {
            projectDatabase.projectDao().upsert(project)
            if (payload.importedFiles.isNotEmpty()) projectDatabase.importedFileDao().upsertAll(payload.importedFiles)
            if (payload.nodes.isNotEmpty()) projectDatabase.gisNodeDao().upsertAll(payload.nodes)
            if (payload.routes.isNotEmpty()) projectDatabase.gisRouteDao().upsertAll(payload.routes)
            for (entity in payload.nodeProgress) projectDatabase.nodeProgressDao().upsert(entity)
            for (entity in payload.materialProgress) projectDatabase.materialProgressDao().upsert(entity)
            for (entity in payload.dailyLogs) projectDatabase.dailyLogDao().upsert(entity)
            for (entity in payload.notes) projectDatabase.noteDao().insert(entity)
            for (entity in payload.tasks) projectDatabase.taskDao().upsert(entity)
            for (entity in payload.sitePhotos) projectDatabase.sitePhotoDao().upsert(entity)
            for (entity in payload.workCategories) projectDatabase.workCategoryDao().upsert(entity)
        }
        AppLogger.d(
            "project.db.seed.complete projectId=${project.id} " +
                "nodes=${payload.nodes.size} routes=${payload.routes.size} " +
                "files=${payload.importedFiles.size} nodeProgress=${payload.nodeProgress.size} " +
                "materialProgress=${payload.materialProgress.size} dailyLogs=${payload.dailyLogs.size} " +
                "notes=${payload.notes.size} tasks=${payload.tasks.size} " +
                "sitePhotos=${payload.sitePhotos.size} workCategories=${payload.workCategories.size}"
        )
    }

    private suspend fun sharedPayload(projectId: String): LegacyProjectPayload? {
        val importedFiles = sharedDatabase.importedFileDao().byProject(projectId)
        val nodes = sharedDatabase.gisNodeDao().byProject(projectId)
        val routes = sharedDatabase.gisRouteDao().byProject(projectId)
        val nodeProgress = sharedDatabase.nodeProgressDao().byProject(projectId)
        val materialProgress = sharedDatabase.materialProgressDao().byProject(projectId)
        val dailyLogs = sharedDatabase.dailyLogDao().byProject(projectId)
        val notes = sharedDatabase.noteDao().byProject(projectId)
        val tasks = sharedDatabase.taskDao().byProject(projectId)
        val sitePhotos = sharedDatabase.sitePhotoDao().byProject(projectId)
        val workCategories = sharedDatabase.workCategoryDao().byProject(projectId)
        if (
            importedFiles.isEmpty() &&
            nodes.isEmpty() &&
            routes.isEmpty() &&
            nodeProgress.isEmpty() &&
            materialProgress.isEmpty() &&
            dailyLogs.isEmpty() &&
            notes.isEmpty() &&
            tasks.isEmpty() &&
            sitePhotos.isEmpty() &&
            workCategories.isEmpty()
        ) {
            return null
        }
        return LegacyProjectPayload(
            importedFiles = importedFiles,
            nodes = nodes,
            routes = routes,
            nodeProgress = nodeProgress,
            materialProgress = materialProgress,
            dailyLogs = dailyLogs,
            notes = notes,
            tasks = tasks,
            sitePhotos = sitePhotos,
            workCategories = workCategories
        )
    }

    private fun countProjectRows(
        database: MapSupervisionDatabase,
        tableName: String,
        projectId: String
    ): Int {
        val query = SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM `$tableName` WHERE `projectId` = ?",
            arrayOf(projectId)
        )
        return database.openHelper.readableDatabase.query(query).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun hasAnyProjectRows(database: MapSupervisionDatabase, projectId: String): Boolean {
        val query = SimpleSQLiteQuery(
            """
            SELECT
                EXISTS(SELECT 1 FROM `imported_files` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `gis_node` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `gis_route` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `node_progress` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `material_progress` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `daily_log` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `note` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `task` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `site_photos` WHERE `projectId` = ? LIMIT 1) OR
                EXISTS(SELECT 1 FROM `work_categories` WHERE `projectId` = ? LIMIT 1)
            """.trimIndent(),
            arrayOf(
                projectId, projectId, projectId, projectId, projectId,
                projectId, projectId, projectId, projectId, projectId
            )
        )
        return database.openHelper.readableDatabase.query(query).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) != 0
        }
    }

    private suspend fun closeIdleDatabases() = mutex.withLock {
        val now = System.currentTimeMillis()
        val iterator = holders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastAccessEpochMs >= idleTimeoutMs) {
                runCatching { entry.value.database.close() }
                AppLogger.d("project.db.close path=${entry.key}")
                iterator.remove()
            }
        }
    }

    private class DatabaseHolder(
        val database: MapSupervisionDatabase,
        var lastAccessEpochMs: Long = System.currentTimeMillis()
    )

    private data class LegacyProjectPayload(
        val importedFiles: List<ImportedFileEntity>,
        val nodes: List<GisNodeEntity>,
        val routes: List<GisRouteEntity>,
        val nodeProgress: List<NodeProgressEntity>,
        val materialProgress: List<MaterialProgressEntity>,
        val dailyLogs: List<DailyLogEntity>,
        val notes: List<NoteEntity>,
        val tasks: List<TaskEntity>,
        val sitePhotos: List<SitePhotoEntity>,
        val workCategories: List<WorkCategoryEntity>
    )
}
