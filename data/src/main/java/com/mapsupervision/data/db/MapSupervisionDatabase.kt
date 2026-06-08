package com.mapsupervision.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
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
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity
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
        AiDecisionCacheEntity::class
    ],
    version = 16,
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
    }
}
