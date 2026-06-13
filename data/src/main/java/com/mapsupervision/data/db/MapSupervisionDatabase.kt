package com.mapsupervision.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.dao.ChatHistoryDao
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.dao.ImportedFileDao
import com.mapsupervision.data.db.dao.MaterialProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.dao.ReportDraftDao
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity
import com.mapsupervision.data.db.entity.ChatHistoryEntity
import com.mapsupervision.data.db.entity.ReportDraftEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity

import java.util.UUID

@Database(
    entities = [
        ProjectEntity::class,
        NodeProgressEntity::class,
        SitePhotoEntity::class,
        DailyLogEntity::class,
        GisNodeEntity::class,
        GisRouteEntity::class,
        ImportedFileEntity::class,
        MaterialProgressEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        WorkCategoryEntity::class,
        AiDecisionCacheEntity::class,
        ChatHistoryEntity::class,
        ReportDraftEntity::class
    ],
    version = 24,
    exportSchema = true
)
@TypeConverters(DbTypeConverters::class)
abstract class MapSupervisionDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeProgressDao(): NodeProgressDao
    abstract fun sitePhotoDao(): SitePhotoDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun gisNodeDao(): GisNodeDao
    abstract fun gisRouteDao(): GisRouteDao
    abstract fun importedFileDao(): ImportedFileDao
    abstract fun materialProgressDao(): MaterialProgressDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun workCategoryDao(): WorkCategoryDao
    abstract fun aiDecisionCacheDao(): AiDecisionCacheDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun reportDraftDao(): ReportDraftDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `weather` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `temperature` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `nodeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `dateEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `volume` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `categoryName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_categories` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `metadataVersion` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `projects` SET `updatedAtEpochMs` = `createdAtEpochMs` WHERE `updatedAtEpochMs` = 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED'")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `projectDbPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `locationAccuracyM` REAL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `isGpsMocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `locationStatus` TEXT NOT NULL DEFAULT 'MISSING'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_decision_cache` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `payloadHash` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId_capability_payloadHash` ON `ai_decision_cache` (`projectId`, `capability`, `payloadHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId` ON `ai_decision_cache` (`projectId`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "index_daily_log_projectId",
                    "index_daily_log_createdAtEpochMs",
                    "index_gis_node_projectId",
                    "index_gis_node_code",
                    "index_gis_node_contractor",
                    "index_gis_route_projectId",
                    "index_gis_route_code",
                    "index_gis_route_contractor",
                    "index_imported_files_projectId",
                    "index_imported_files_fileType",
                    "index_material_progress_projectId",
                    "index_material_progress_nodeCode",
                    "index_material_progress_materialName",
                    "index_node_progress_projectId",
                    "index_node_progress_nodeCode",
                    "index_note_projectId",
                    "index_note_objectCode",
                    "index_note_createdAtEpochMs",
                    "index_note_projectId_objectCode",
                    "index_task_projectId",
                    "index_task_objectCode",
                    "index_task_status",
                    "index_task_createdAtEpochMs",
                    "index_task_projectId_objectCode",
                    "index_site_photos_projectId",
                    "index_site_photos_objectCode",
                    "index_site_photos_capturedAtEpochMs",
                    "index_site_photos_projectId_objectCode"
                ).forEach { indexName ->
                    db.execSQL("DROP INDEX IF EXISTS `$indexName`")
                }
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_progress_nodeCode` ON `material_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeCode` ON `node_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `routeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `batchGroupId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `appliedNodeCodesCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `linkedPhotoIdsCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `photoMatchOffsetMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `tagCodesCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedNodeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedRouteCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchingTimeOffsetMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_history` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId` ON `chat_history` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId_createdAtEpochMs` ON `chat_history` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `report_draft` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `executiveSummary` TEXT NOT NULL,
                        `riskSection` TEXT NOT NULL,
                        `recommendedActionsCsv` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_draft_projectId_createdAtEpochMs` ON `report_draft` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val projectCols = getTableColumns(db, "projects")
                rebuildProjectsTable(db, projectCols.contains("projectCode"))
                rebuildImportedFilesTable(db)
                rebuildSitePhotosTable(db)
                rebuildTaskTable(db)
                rebuildDailyLogTable(db)
                rebuildNoteTable(db)
                rebuildWorkCategoriesTable(db)
            }

            private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx != -1) {
                        while (cursor.moveToNext()) {
                            columns.add(cursor.getString(nameIdx))
                        }
                    }
                }
                return columns
            }

            private fun rebuildProjectsTable(db: SupportSQLiteDatabase, hasProjectCode: Boolean) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `slug` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `metadataVersion` INTEGER NOT NULL DEFAULT 3,
                        `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                        `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED',
                        `projectDbPath` TEXT NOT NULL DEFAULT '',
                        `projectCode` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `projects_new` (
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`,
                        `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    )
                    SELECT
                        `id`,
                        `name`,
                        `slug`,
                        `isArchived`,
                        `createdAtEpochMs`,
                        COALESCE(`metadataVersion`, 3),
                        CASE
                            WHEN `updatedAtEpochMs` IS NULL OR `updatedAtEpochMs` = 0 THEN `createdAtEpochMs`
                            ELSE `updatedAtEpochMs`
                        END,
                        COALESCE(`storageMode`, 'LEGACY_SHARED'),
                        COALESCE(`projectDbPath`, ''),
                        ${if (hasProjectCode) "`projectCode`" else "NULL"}
                    FROM `projects`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")
            }

            private fun rebuildImportedFilesTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `imported_files_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `importedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `imported_files_new` (
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    FROM `imported_files`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `imported_files`")
                db.execSQL("ALTER TABLE `imported_files_new` RENAME TO `imported_files`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")
            }

            private fun rebuildSitePhotosTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `matchedNodeCode` TEXT,
                        `matchedRouteCode` TEXT,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `site_photos_new` (
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`,
                        `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`,
                        `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`,
                        `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`,
                        `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    FROM `site_photos`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
            }

            private fun rebuildTaskTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `task_new` (
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    FROM `task`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
            }

            private fun rebuildDailyLogTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workItem` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `weather` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `volume` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `batchGroupId` TEXT NOT NULL,
                        `appliedNodeCodesCsv` TEXT NOT NULL,
                        `linkedPhotoIdsCsv` TEXT NOT NULL,
                        `photoMatchOffsetMinutes` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val cols = getTableColumns(db, "daily_log")
                val selectWeather = if (cols.contains("weather")) "`weather`" else "''"
                val selectTemperature = if (cols.contains("temperature")) "`temperature`" else "0.0"
                val selectNodeCode = if (cols.contains("nodeCode")) "`nodeCode`" else "NULL"
                val selectRouteCode = if (cols.contains("routeCode")) "`routeCode`" else "NULL"
                val selectDateEpochDay = if (cols.contains("dateEpochDay")) "`dateEpochDay`" else "0"
                val selectVolume = if (cols.contains("volume")) "`volume`" else "0.0"
                val selectUnit = if (cols.contains("unit")) "`unit`" else "''"
                val selectCategoryName = if (cols.contains("categoryName")) "`categoryName`" else "''"
                val selectBatchGroupId = if (cols.contains("batchGroupId")) "`batchGroupId`" else "''"
                val selectAppliedNodeCodesCsv = if (cols.contains("appliedNodeCodesCsv")) "`appliedNodeCodesCsv`" else "''"
                val selectLinkedPhotoIdsCsv = if (cols.contains("linkedPhotoIdsCsv")) "`linkedPhotoIdsCsv`" else "''"
                val selectPhotoMatchOffsetMinutes = if (cols.contains("photoMatchOffsetMinutes")) "`photoMatchOffsetMinutes`" else "0"

                db.execSQL(
                    """
                    INSERT INTO `daily_log_new` (
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`,
                        `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`,
                        `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`,
                        `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    )
                    SELECT
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`,
                        COALESCE($selectWeather, ''),
                        COALESCE($selectTemperature, 0.0),
                        $selectNodeCode,
                        $selectRouteCode,
                        COALESCE($selectDateEpochDay, 0),
                        COALESCE($selectVolume, 0.0),
                        COALESCE($selectUnit, ''),
                        COALESCE($selectCategoryName, ''),
                        COALESCE($selectBatchGroupId, ''),
                        COALESCE($selectAppliedNodeCodesCsv, ''),
                        COALESCE($selectLinkedPhotoIdsCsv, ''),
                        COALESCE($selectPhotoMatchOffsetMinutes, 0)
                    FROM `daily_log`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
            }

            private fun rebuildNoteTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `note_new` (
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    FROM `note`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
            }

            private fun rebuildWorkCategoriesTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_categories_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `work_categories_new` (
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    FROM `work_categories`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `work_categories`")
                db.execSQL("ALTER TABLE `work_categories_new` RENAME TO `work_categories`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add points column
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `points` TEXT NOT NULL DEFAULT ''")

                // 2. Read all nodes to locate coordinates
                val nodesMap = mutableMapOf<String, Pair<Double, Double>>()
                db.query("SELECT `code`, `latitude`, `longitude` FROM `gis_node`").use { cursor ->
                    val codeIdx = cursor.getColumnIndex("code")
                    val latIdx = cursor.getColumnIndex("latitude")
                    val lonIdx = cursor.getColumnIndex("longitude")
                    if (codeIdx != -1 && latIdx != -1 && lonIdx != -1) {
                        while (cursor.moveToNext()) {
                            val code = cursor.getString(codeIdx)
                            val lat = cursor.getDouble(latIdx)
                            val lon = cursor.getDouble(lonIdx)
                            nodesMap[code.trim().uppercase()] = lat to lon
                        }
                    }
                }

                // 3. Read all routes
                data class TempRoute(
                    val id: String,
                    val projectId: String,
                    val code: String,
                    val contractor: String,
                    val startNodeCode: String,
                    val endNodeCode: String
                )
                val routesList = mutableListOf<TempRoute>()
                db.query("SELECT `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode` FROM `gis_route`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val codeIdx = cursor.getColumnIndex("code")
                    val contrIdx = cursor.getColumnIndex("contractor")
                    val startIdx = cursor.getColumnIndex("startNodeCode")
                    val endIdx = cursor.getColumnIndex("endNodeCode")
                    if (idIdx != -1 && projIdx != -1 && codeIdx != -1 && contrIdx != -1 && startIdx != -1 && endIdx != -1) {
                        while (cursor.moveToNext()) {
                            routesList.add(
                                TempRoute(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    code = cursor.getString(codeIdx),
                                    contractor = cursor.getString(contrIdx),
                                    startNodeCode = cursor.getString(startIdx),
                                    endNodeCode = cursor.getString(endIdx)
                                )
                            )
                        }
                    }
                }

                // Helper to compute distance between coordinates
                fun distanceMeters(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
                    val earthRadius = 6_371_000.0
                    val lat1 = Math.toRadians(p1.first)
                    val lat2 = Math.toRadians(p2.first)
                    val dLat = lat2 - lat1
                    val dLon = Math.toRadians(p2.second - p1.second)
                    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
                    return 2 * earthRadius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
                }

                val segmentRegex = Regex("^(.*)_S(\\d+)$", RegexOption.IGNORE_CASE)
                val groupedRoutes = routesList.groupBy { route ->
                    val match = segmentRegex.find(route.code)
                    if (match != null) match.groupValues[1].trim().uppercase() else route.code.trim().uppercase()
                }

                groupedRoutes.forEach { (baseRouteCode, segments) ->
                    val sortedSegments = segments.sortedBy { route ->
                        val match = segmentRegex.find(route.code)
                        if (match != null) match.groupValues[2].toIntOrNull() ?: 1 else 1
                    }

                    val firstSeg = sortedSegments.first()
                    val lastSeg = sortedSegments.last()

                    // Collect all segment endpoints coordinates in order
                    val mergedPoints = mutableListOf<Pair<Double, Double>>()
                    for (seg in sortedSegments) {
                        val startCoord = nodesMap[seg.startNodeCode.trim().uppercase()]
                        val endCoord = nodesMap[seg.endNodeCode.trim().uppercase()]
                        if (startCoord != null) {
                            if (mergedPoints.isEmpty()) {
                                mergedPoints.add(startCoord)
                            } else {
                                val last = mergedPoints.last()
                                if (distanceMeters(last, startCoord) > 1.0) {
                                    mergedPoints.add(startCoord)
                                }
                            }
                        }
                        if (endCoord != null) {
                            if (mergedPoints.isEmpty()) {
                                mergedPoints.add(endCoord)
                            } else {
                                val last = mergedPoints.last()
                                if (distanceMeters(last, endCoord) > 1.0) {
                                    mergedPoints.add(endCoord)
                                }
                            }
                        }
                    }

                    if (mergedPoints.size > 1) {
                        val startPoint = mergedPoints.first()
                        val endPoint = mergedPoints.last()

                        val startCode = "${baseRouteCode}_P1".uppercase()
                        val endCode = "${baseRouteCode}_P${mergedPoints.size}".uppercase()

                        // Insert start/end nodes if they don't exist
                        db.execSQL(
                            "INSERT OR IGNORE INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(UUID.randomUUID().toString(), firstSeg.projectId, startCode, firstSeg.contractor, startPoint.first, startPoint.second, "", "")
                        )
                        db.execSQL(
                            "INSERT OR IGNORE INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(UUID.randomUUID().toString(), lastSeg.projectId, endCode, lastSeg.contractor, endPoint.first, endPoint.second, "", "")
                        )

                        // Serialize points list: latitude,longitude;latitude,longitude
                        val pointsStr = mergedPoints.joinToString(";") { "${it.first},${it.second}" }

                        // Insert the unified route
                        db.execSQL(
                            "INSERT OR REPLACE INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(UUID.randomUUID().toString(), firstSeg.projectId, baseRouteCode, firstSeg.contractor, startCode, endCode, pointsStr)
                        )

                        // Delete legacy segments
                        segments.forEach { seg ->
                            db.execSQL("DELETE FROM `gis_route` WHERE `id` = ?", arrayOf(seg.id))
                            // Delete intermediate node vertices
                            db.execSQL("DELETE FROM `gis_node` WHERE `code` = ?", arrayOf(seg.startNodeCode))
                            db.execSQL("DELETE FROM `gis_node` WHERE `code` = ?", arrayOf(seg.endNodeCode))
                        }
                    }
                }

                // 4. Group and aggregate Node Progress
                data class TempNodeProgress(
                    val id: String,
                    val projectId: String,
                    val nodeCode: String,
                    val planned: Float,
                    val actual: Float,
                    val remain: Float,
                    val delayed: Int,
                    val updatedAtEpochMs: Long
                )
                val progressList = mutableListOf<TempNodeProgress>()
                db.query("SELECT `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs` FROM `node_progress`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val nodeIdx = cursor.getColumnIndex("nodeCode")
                    val planIdx = cursor.getColumnIndex("planned")
                    val actIdx = cursor.getColumnIndex("actual")
                    val remIdx = cursor.getColumnIndex("remain")
                    val delIdx = cursor.getColumnIndex("delayed")
                    val upIdx = cursor.getColumnIndex("updatedAtEpochMs")
                    if (idIdx != -1 && projIdx != -1 && nodeIdx != -1 && planIdx != -1 && actIdx != -1 && remIdx != -1 && delIdx != -1 && upIdx != -1) {
                        while (cursor.moveToNext()) {
                            progressList.add(
                                TempNodeProgress(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    nodeCode = cursor.getString(nodeIdx),
                                    planned = cursor.getFloat(planIdx),
                                    actual = cursor.getFloat(actIdx),
                                    remain = cursor.getFloat(remIdx),
                                    delayed = cursor.getInt(delIdx),
                                    updatedAtEpochMs = cursor.getLong(upIdx)
                                )
                            )
                        }
                    }
                }

                val progressGroups = progressList.groupBy { progress ->
                    val match = segmentRegex.find(progress.nodeCode)
                    if (match != null) match.groupValues[1].trim().uppercase() else progress.nodeCode.trim().uppercase()
                }

                progressGroups.forEach { (baseRouteCode, items) ->
                    // Find if it was segmented (has items with _S pattern)
                    val segmentedItems = items.filter { segmentRegex.containsMatchIn(it.nodeCode) }
                    if (segmentedItems.isNotEmpty()) {
                        val sumPlanned = items.sumOf { it.planned.toDouble() }.toFloat()
                        val sumActual = items.sumOf { it.actual.toDouble() }.toFloat()
                        val sumRemain = items.sumOf { it.remain.toDouble() }.toFloat()
                        val isDelayed = items.any { it.delayed != 0 }
                        val maxUpdated = items.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
                        val firstItem = items.first()

                        // Insert aggregated progress
                        db.execSQL(
                            "INSERT OR REPLACE INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(UUID.randomUUID().toString(), firstItem.projectId, baseRouteCode, sumPlanned, sumActual, sumRemain, if (isDelayed) 1 else 0, maxUpdated)
                        )

                        // Delete old progress records
                        segmentedItems.forEach { item ->
                            db.execSQL("DELETE FROM `node_progress` WHERE `id` = ?", arrayOf(item.id))
                        }
                    }
                }

                // 5. Group and aggregate Material Progress
                data class TempMaterialProgress(
                    val id: String,
                    val projectId: String,
                    val nodeCode: String,
                    val materialName: String,
                    val plannedQty: Float,
                    val actualQty: Float,
                    val updatedAtEpochMs: Long
                )
                val matProgressList = mutableListOf<TempMaterialProgress>()
                db.query("SELECT `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs` FROM `material_progress`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val nodeIdx = cursor.getColumnIndex("nodeCode")
                    val nameIdx = cursor.getColumnIndex("materialName")
                    val planIdx = cursor.getColumnIndex("plannedQty")
                    val actIdx = cursor.getColumnIndex("actualQty")
                    val upIdx = cursor.getColumnIndex("updatedAtEpochMs")
                    if (idIdx != -1 && projIdx != -1 && nodeIdx != -1 && nameIdx != -1 && planIdx != -1 && actIdx != -1 && upIdx != -1) {
                        while (cursor.moveToNext()) {
                            matProgressList.add(
                                TempMaterialProgress(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    nodeCode = cursor.getString(nodeIdx),
                                    materialName = cursor.getString(nameIdx),
                                    plannedQty = cursor.getFloat(planIdx),
                                    actualQty = cursor.getFloat(actIdx),
                                    updatedAtEpochMs = cursor.getLong(upIdx)
                                )
                            )
                        }
                    }
                }

                val matGroups = matProgressList.groupBy { mat ->
                    val match = segmentRegex.find(mat.nodeCode)
                    val baseCode = if (match != null) match.groupValues[1].trim().uppercase() else mat.nodeCode.trim().uppercase()
                    baseCode to mat.materialName.trim()
                }

                matGroups.forEach { (key, items) ->
                    val (baseRouteCode, materialName) = key
                    val segmentedItems = items.filter { segmentRegex.containsMatchIn(it.nodeCode) }
                    if (segmentedItems.isNotEmpty()) {
                        val sumPlanned = items.sumOf { it.plannedQty.toDouble() }.toFloat()
                        val sumActual = items.sumOf { it.actualQty.toDouble() }.toFloat()
                        val maxUpdated = items.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
                        val firstItem = items.first()

                        db.execSQL(
                            "INSERT OR REPLACE INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(UUID.randomUUID().toString(), firstItem.projectId, baseRouteCode, materialName, sumPlanned, sumActual, maxUpdated)
                        )

                        segmentedItems.forEach { item ->
                            db.execSQL("DELETE FROM `material_progress` WHERE `id` = ?", arrayOf(item.id))
                        }
                    }
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add designLength column to gis_route
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `designLength` TEXT DEFAULT NULL")

                // 2. Read all routes
                data class TempRoute(
                    val id: String,
                    val startNodeCode: String,
                    val endNodeCode: String
                )
                val routesList = mutableListOf<TempRoute>()
                db.query("SELECT `id`, `startNodeCode`, `endNodeCode` FROM `gis_route`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val startIdx = cursor.getColumnIndex("startNodeCode")
                    val endIdx = cursor.getColumnIndex("endNodeCode")
                    if (idIdx != -1 && startIdx != -1 && endIdx != -1) {
                        while (cursor.moveToNext()) {
                            routesList.add(
                                TempRoute(
                                    id = cursor.getString(idIdx),
                                    startNodeCode = cursor.getString(startIdx),
                                    endNodeCode = cursor.getString(endIdx)
                                )
                            )
                        }
                    }
                }

                // 3. Read all nodes to locate materialSummary
                val nodesMap = mutableMapOf<String, Pair<String, String>>()
                db.query("SELECT `id`, `code`, `materialSummary` FROM `gis_node`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val codeIdx = cursor.getColumnIndex("code")
                    val summaryIdx = cursor.getColumnIndex("materialSummary")
                    if (idIdx != -1 && codeIdx != -1 && summaryIdx != -1) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idIdx)
                            val code = cursor.getString(codeIdx).trim().uppercase()
                            val summary = cursor.getString(summaryIdx).orEmpty()
                            nodesMap[code] = id to summary
                        }
                    }
                }

                // Helper to extract routeLength
                fun extractLength(summary: String): String? {
                    return summary.split('\n')
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("routeLength:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                }

                // Helper to remove routeLength line from summary
                fun cleanSummary(summary: String): String {
                    return summary.split('\n')
                        .filter { !it.trim().startsWith("routeLength:", ignoreCase = true) }
                        .joinToString("\n")
                        .trim()
                }

                // 4. Update routes with designLength and clean up node summaries
                routesList.forEach { route ->
                    val startNodeData = nodesMap[route.startNodeCode.trim().uppercase()]
                    val endNodeData = nodesMap[route.endNodeCode.trim().uppercase()]

                    var lengthVal: String? = null
                    if (startNodeData != null) {
                        lengthVal = extractLength(startNodeData.second)
                    }
                    if (lengthVal == null && endNodeData != null) {
                        lengthVal = extractLength(endNodeData.second)
                    }

                    if (lengthVal != null) {
                        // Update route designLength
                        db.execSQL(
                            "UPDATE `gis_route` SET `designLength` = ? WHERE `id` = ?",
                            arrayOf(lengthVal, route.id)
                        )

                        // Clean up node summaries
                        if (startNodeData != null && startNodeData.second.contains("routeLength:", ignoreCase = true)) {
                            val cleaned = cleanSummary(startNodeData.second)
                            db.execSQL(
                                "UPDATE `gis_node` SET `materialSummary` = ? WHERE `id` = ?",
                                arrayOf(cleaned, startNodeData.first)
                            )
                            nodesMap[route.startNodeCode.trim().uppercase()] = startNodeData.first to cleaned
                        }
                        if (endNodeData != null && endNodeData.second.contains("routeLength:", ignoreCase = true)) {
                            val cleaned = cleanSummary(endNodeData.second)
                            db.execSQL(
                                "UPDATE `gis_node` SET `materialSummary` = ? WHERE `id` = ?",
                                arrayOf(cleaned, endNodeData.first)
                            )
                            nodesMap[route.endNodeCode.trim().uppercase()] = endNodeData.first to cleaned
                        }
                    }
                }
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. projects
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `projects_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `slug` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `metadataVersion` INTEGER NOT NULL DEFAULT 3,
                        `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                        `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED',
                        `projectDbPath` TEXT NOT NULL DEFAULT '',
                        `projectCode` TEXT,
                        PRIMARY KEY(`id`),
                        CHECK (`storageMode` IN ('LEGACY_SHARED', 'PROJECT_DB'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `projects_new` (
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    )
                    SELECT
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    FROM `projects`
                """.trimIndent())
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")

                // 2. imported_files
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `imported_files_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `importedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `imported_files_new` (
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    FROM `imported_files`
                """.trimIndent())
                db.execSQL("DROP TABLE `imported_files`")
                db.execSQL("ALTER TABLE `imported_files_new` RENAME TO `imported_files`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")

                // 3. gis_node
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gis_node_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `mapNumberLabel` TEXT NOT NULL,
                        `materialSummary` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `gis_node_new` (
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`
                    FROM `gis_node`
                """.trimIndent())
                db.execSQL("DROP TABLE `gis_node`")
                db.execSQL("ALTER TABLE `gis_node_new` RENAME TO `gis_node`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_importedFileId` ON `gis_node` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")

                // 4. gis_route
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gis_route_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `startNodeCode` TEXT NOT NULL,
                        `endNodeCode` TEXT NOT NULL,
                        `points` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        `designLength` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `gis_route_new` (
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`
                    FROM `gis_route`
                """.trimIndent())
                db.execSQL("DROP TABLE `gis_route`")
                db.execSQL("ALTER TABLE `gis_route_new` RENAME TO `gis_route`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_importedFileId` ON `gis_route` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")

                // 5. node_progress
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `node_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `planned` REAL NOT NULL,
                        `actual` REAL NOT NULL,
                        `remain` REAL NOT NULL,
                        `delayed` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `node_progress_new` (
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`
                    FROM `node_progress`
                """.trimIndent())
                db.execSQL("DROP TABLE `node_progress`")
                db.execSQL("ALTER TABLE `node_progress_new` RENAME TO `node_progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_projectId_nodeCode` ON `node_progress` (`projectId`, `nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeCode` ON `node_progress` (`nodeCode`)")

                // 6. material_progress
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `material_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `plannedQty` REAL NOT NULL,
                        `actualQty` REAL NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `material_progress_new` (
                        `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`
                    FROM `material_progress`
                """.trimIndent())
                db.execSQL("DROP TABLE `material_progress`")
                db.execSQL("ALTER TABLE `material_progress_new` RENAME TO `material_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_material_progress_projectId_nodeCode_materialName` ON `material_progress` (`projectId`, `nodeCode`, `materialName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_progress_nodeCode` ON `material_progress` (`nodeCode`)")

                // 7. daily_log
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_log_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workItem` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `weather` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `volume` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `batchGroupId` TEXT NOT NULL,
                        `appliedNodeCodesCsv` TEXT NOT NULL,
                        `linkedPhotoIdsCsv` TEXT NOT NULL,
                        `photoMatchOffsetMinutes` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `daily_log_new` (
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    )
                    SELECT
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    FROM `daily_log`
                """.trimIndent())
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")

                // 8. site_photos
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `matchedNodeCode` TEXT,
                        `matchedRouteCode` TEXT,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        CHECK (`locationStatus` IN ('OK', 'MISSING', 'INACCURATE'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `site_photos_new` (
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    FROM `site_photos`
                """.trimIndent())
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")

                // 9. task
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        CHECK (`status` IN ('TODO', 'IN_PROGRESS', 'COMPLETED'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `task_new` (
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `title`, `description`,
                        CASE
                            WHEN `status` = 'OPEN' THEN 'TODO'
                            WHEN `status` = 'DONE' THEN 'COMPLETED'
                            ELSE `status`
                        END,
                        `createdAtEpochMs`, `completedAtEpochMs`
                    FROM `task`
                """.trimIndent())
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")

                // 10. note
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `note_new` (
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    FROM `note`
                """.trimIndent())
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")

                // 11. work_categories
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `work_categories_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `work_categories_new` (
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    FROM `work_categories`
                """.trimIndent())
                db.execSQL("DROP TABLE `work_categories`")
                db.execSQL("ALTER TABLE `work_categories_new` RENAME TO `work_categories`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")

                // 12. ai_decision_cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_decision_cache_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `payloadHash` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `ai_decision_cache_new` (
                        `id`, `projectId`, `capability`, `payloadHash`, `resultJson`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `capability`, `payloadHash`, `resultJson`, `createdAtEpochMs`
                    FROM `ai_decision_cache`
                """.trimIndent())
                db.execSQL("DROP TABLE `ai_decision_cache`")
                db.execSQL("ALTER TABLE `ai_decision_cache_new` RENAME TO `ai_decision_cache`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId_capability_payloadHash` ON `ai_decision_cache` (`projectId`, `capability`, `payloadHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId` ON `ai_decision_cache` (`projectId`)")

                // 13. chat_history
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_history_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `chat_history_new` (
                        `id`, `projectId`, `role`, `text`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `role`, `text`, `createdAtEpochMs`
                    FROM `chat_history`
                """.trimIndent())
                db.execSQL("DROP TABLE `chat_history`")
                db.execSQL("ALTER TABLE `chat_history_new` RENAME TO `chat_history`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId_createdAtEpochMs` ON `chat_history` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId` ON `chat_history` (`projectId`)")

                // 14. report_draft
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `report_draft_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `executiveSummary` TEXT NOT NULL,
                        `riskSection` TEXT NOT NULL,
                        `recommendedActionsCsv` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `report_draft_new` (
                        `id`, `projectId`, `title`, `executiveSummary`, `riskSection`, `recommendedActionsCsv`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `title`, `executiveSummary`, `riskSection`, `recommendedActionsCsv`, `createdAtEpochMs`
                    FROM `report_draft`
                """.trimIndent())
                db.execSQL("DROP TABLE `report_draft`")
                db.execSQL("ALTER TABLE `report_draft_new` RENAME TO `report_draft`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_draft_projectId_createdAtEpochMs` ON `report_draft` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `material_progress` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}


