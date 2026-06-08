package com.mapsupervision.data.db

import android.content.Context
import androidx.room.Room
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.db.entity.ProjectEntity
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
                MapSupervisionDatabase.MIGRATION_13_14
            )
            .fallbackToDestructiveMigration()
            .build()
        holders[project.projectDbPath] = DatabaseHolder(database)
        AppLogger.d("project.db.open path=${dbFile.absolutePath}")
        database
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
}
