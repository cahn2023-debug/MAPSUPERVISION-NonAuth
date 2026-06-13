package com.mapsupervision.data.db

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.domain.model.PhotoLocationStatus
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapSupervisionDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("mapsupervision-db-migration-test").toFile()
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext(), tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `migration 19 to 20 rebuilds site photos table without legacy defaults`() {
        val dbName = "legacy19.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion19Database(dbFile)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                assertEquals(0, database.sitePhotoDao().byProject("project-1").size)
            }
            assertProjectsTableHasNormalizedDefaults(database)
            assertSitePhotosTableHasNoLegacyDefaults(database)
            assertImportedFilesTableHasNoLegacyDeletedColumn(database)
            assertImportedFilesIndexExists(database)
            assertTaskTableHasNoLegacyColumns(database)
            assertTaskIndexesExist(database)
            assertDailyLogTableHasNormalizedColumns(database)
            assertNoteTableHasNormalizedColumns(database)
            assertWorkCategoriesTableHasNormalizedColumns(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 19 to 20 preserves existing user data`() {
        val dbName = "legacy19_data.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion19Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                INSERT INTO `daily_log` (
                    `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`
                ) VALUES (
                    'log-custom-1', 'project-1', 'Excavation', 5, 'Sunny day work', 150, 'Sunny', 32.5, 'N-1', 2000, 15.0, 'm3', 'Category A'
                )
                """.trimIndent()
            )
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            println("DEBUG: Room DB absolute path = ${database.openHelper.writableDatabase.path}")
            runBlocking {
                val logs = database.dailyLogDao().byProject("project-1")
                assertEquals(1, logs.size)
                val log = logs.single()
                assertEquals("log-custom-1", log.id)
                assertEquals("Excavation", log.workItem)
                assertEquals("Sunny", log.weather)
                assertEquals(32.5, log.temperature, 0.001)
                assertEquals("", log.batchGroupId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 8 to 23 compiles and validates successfully`() {
        val dbName = "legacy8.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion8Database(dbFile)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.writableDatabase
            assertProjectsTableHasNormalizedDefaults(database)
            assertSitePhotosTableHasNoLegacyDefaults(database)
            assertImportedFilesTableHasNoLegacyDeletedColumn(database)
            assertImportedFilesIndexExists(database)
            assertTaskTableHasNoLegacyColumns(database)
            assertTaskIndexesExist(database)
            assertDailyLogTableHasNormalizedColumns(database)
            assertNoteTableHasNormalizedColumns(database)
            assertWorkCategoriesTableHasNormalizedColumns(database)

            // Tightened verification for final version 23 schema
            assertAllTablesExist(database)
            assertGisRouteTableHasPointsAndDesignLength(database)
            verifyVersion23ConstraintsAndForeignKeys(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun confirmRequiredSchemasExist() {
        for (version in 9..24) {
            val file = File("data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            val relativeFile = File("../data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            val alternateFile = File("schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            
            val exists = file.exists() || relativeFile.exists() || alternateFile.exists()
            assertEquals("Room schema artifact for version $version is missing on disk", true, exists)
        }
    }

    private fun assertAllTablesExist(database: MapSupervisionDatabase) {
        val expectedTables = listOf(
            "projects", "node_progress", "site_photos", "daily_log", "gis_node", "gis_route",
            "imported_files", "material_progress", "note", "task", "work_categories",
            "ai_decision_cache", "chat_history", "report_draft"
        )
        database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
            for (table in expectedTables) {
                assertEquals("Table $table does not exist in version 23", true, tables.contains(table))
            }
        }
    }

    private fun assertGisRouteTableHasPointsAndDesignLength(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`gis_route`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            var pointsFound = false
            var designLengthFound = false
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(nameIndex)
                if (columnName == "points") {
                    pointsFound = true
                    assertEquals("TEXT", cursor.getString(typeIndex))
                }
                if (columnName == "designLength") {
                    designLengthFound = true
                    assertEquals("TEXT", cursor.getString(typeIndex))
                }
            }
            assertEquals("points column missing on gis_route", true, pointsFound)
            assertEquals("designLength column missing on gis_route", true, designLengthFound)
        }
    }

    private fun verifyVersion23ConstraintsAndForeignKeys(database: MapSupervisionDatabase) {
        val db = database.openHelper.writableDatabase

        // Ensure a parent project exists for FK validation
        db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) " +
                "VALUES ('proj-fk-validation', 'Project FK Validation', 'proj-fk-validation', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

        // 1. Check CHECK constraints
        try {
            db.execSQL(
                "INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `storageMode`, `projectDbPath`) " +
                "VALUES ('proj-invalid-chk', 'Invalid', 'invalid', 0, 1000, 'INVALID_MODE', '')"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for projects.storageMode")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        try {
            db.execSQL(
                "INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) " +
                "VALUES ('task-invalid-chk', 'proj-fk-validation', 'N1', 'Invalid Task', '', 'INVALID_STATUS', 1200)"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for task.status")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        try {
            db.execSQL(
                "INSERT INTO `site_photos` (`id`, `projectId`, `objectCode`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`) " +
                "VALUES ('photo-invalid-chk', 'proj-fk-validation', 'N1', '', '', '', 0, 'INVALID_STATUS', '', 0, 0, 0)"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for site_photos.locationStatus")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        // 2. Check UNIQUE constraints
        // For gis_node (projectId, code)
        try {
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('node-1', 'proj-fk-validation', 'N1', 'CON1', 10.0, 20.0, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('node-2', 'proj-fk-validation', 'N1', 'CON1', 11.0, 21.0, '', '')")
            org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_node (projectId, code)")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }

        // For gis_route (projectId, code)
        try {
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `designLength`) VALUES ('route-1', 'proj-fk-validation', 'R1', 'CON1', 'N1', 'N2', '[]', NULL)")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `designLength`) VALUES ('route-2', 'proj-fk-validation', 'R1', 'CON1', 'N1', 'N2', '[]', NULL)")
            org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_route (projectId, code)")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }

        // 3. Clean up projects test data
        db.execSQL("DELETE FROM `projects` WHERE `id` = 'proj-fk-validation'")
    }

    private fun createLegacyVersion8Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(9)
        val skippedIndices = setOf(
            "index_gis_node_projectId_code",
            "index_gis_route_projectId_code",
            "index_imported_files_projectId_importedAtEpochMs",
            "index_task_projectId_createdAtEpochMs",
            "index_task_projectId_objectCode_createdAtEpochMs",
            "index_site_photos_projectId_objectCode_capturedAtEpochMs",
            "index_note_projectId_createdAtEpochMs",
            "index_note_projectId_objectCode_createdAtEpochMs"
        )
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)
                
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexName = index.getString("name")
                        if (indexName !in skippedIndices) {
                            val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                            db.execSQL(indexSql)
                        }
                    }
                }
            }
            db.setVersion(8)
        }
    }

    private fun createLegacyVersion19Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(19)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val createSql = when (entity.getString("tableName")) {
                    "projects" -> """
                        CREATE TABLE IF NOT EXISTS `projects` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `slug` TEXT NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `metadataVersion` INTEGER NOT NULL,
                            `updatedAtEpochMs` INTEGER NOT NULL,
                            `storageMode` TEXT NOT NULL,
                            `projectDbPath` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "site_photos" -> """
                        CREATE TABLE IF NOT EXISTS `site_photos` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `objectCode` TEXT NOT NULL,
                            `tagCodesCsv` TEXT NOT NULL DEFAULT '',
                            `matchedNodeCode` TEXT,
                            `matchedRouteCode` TEXT,
                            `filePath` TEXT NOT NULL,
                            `thumbnailPath` TEXT NOT NULL,
                            `latitude` REAL,
                            `longitude` REAL,
                            `locationAccuracyM` REAL,
                            `isGpsMocked` INTEGER NOT NULL DEFAULT 0,
                            `locationStatus` TEXT NOT NULL DEFAULT 'MISSING',
                            `engineer` TEXT NOT NULL,
                            `capturedAtEpochMs` INTEGER NOT NULL,
                            `matchedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                            `matchingTimeOffsetMs` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "imported_files" -> """
                        CREATE TABLE IF NOT EXISTS `imported_files` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `fileType` TEXT NOT NULL,
                            `storedPath` TEXT NOT NULL,
                            `summary` TEXT NOT NULL,
                            `importedAtEpochMs` INTEGER NOT NULL,
                            `deletedAtEpochMs` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "task" -> """
                        CREATE TABLE IF NOT EXISTS `task` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `objectCode` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `completedAtEpochMs` INTEGER,
                            `assignee` TEXT,
                            `dueDateEpochMs` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "daily_log" -> """
                        CREATE TABLE IF NOT EXISTS `daily_log` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `workItem` TEXT NOT NULL,
                            `manpower` INTEGER NOT NULL,
                            `note` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `weather` TEXT NOT NULL,
                            `temperature` REAL NOT NULL,
                            `nodeCode` TEXT,
                            `dateEpochDay` INTEGER NOT NULL,
                            `volume` REAL NOT NULL,
                            `unit` TEXT NOT NULL,
                            `categoryName` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    else -> entity.getString("createSql")
                }
                val resolvedCreateSql = createSql.replace("\${TABLE_NAME}", entity.getString("tableName"))
                db.execSQL(resolvedCreateSql)
            }
            db.execSQL(
                """
                INSERT INTO `projects` (
                    `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`,
                    `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`
                ) VALUES (
                    'project-1', 'Project 1', 'slug-1', 0, 1000, 1, 0, 'LEGACY_SHARED', '/legacy/path'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `imported_files` (
                    `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`, `deletedAtEpochMs`
                ) VALUES (
                    'imported-1', 'project-1', 'nodes.kml', 'KML', '/tmp/nodes.kml', 'seed', 100, 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `task` (
                    `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`, `assignee`, `dueDateEpochMs`
                ) VALUES (
                    'task-1', 'project-1', 'OBJ-1', 'legacy title', 'legacy desc', 'OPEN', 200, NULL, 'user-a', 300
                )
                """.trimIndent()
            )
            db.setVersion(19)
        }
    }

    private fun assertSitePhotosTableHasNoLegacyDefaults(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`site_photos`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            val defaults = mutableMapOf<String, String?>()
            while (cursor.moveToNext()) {
                defaults[cursor.getString(nameIndex)] = cursor.getString(defaultIndex)
            }

            assertNull(defaults["tagCodesCsv"])
            assertNull(defaults["matchedNodeCode"])
            assertNull(defaults["matchedRouteCode"])
            assertNull(defaults["isGpsMocked"])
            assertNull(defaults["locationStatus"])
            assertNull(defaults["matchedAtEpochMs"])
            assertNull(defaults["matchingTimeOffsetMs"])
        }
    }

    private fun assertImportedFilesTableHasNoLegacyDeletedColumn(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`imported_files`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaults = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                defaults += cursor.getString(nameIndex)
            }
            assertEquals(false, defaults.contains("deletedAtEpochMs"))
        }
    }

    private fun assertImportedFilesIndexExists(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA index_list(`imported_files`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
            assertEquals(true, indexes.contains("index_imported_files_projectId_importedAtEpochMs"))
        }
    }

    private fun assertProjectsTableHasNormalizedDefaults(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`projects`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            val defaults = mutableMapOf<String, String?>()
            while (cursor.moveToNext()) {
                defaults[cursor.getString(nameIndex)] = cursor.getString(defaultIndex)
            }

            assertEquals("3", defaults["metadataVersion"])
            assertEquals("0", defaults["updatedAtEpochMs"])
            assertEquals("'LEGACY_SHARED'", defaults["storageMode"])
            assertEquals("''", defaults["projectDbPath"])
            assertNull(defaults["projectCode"])
        }
    }

    private fun assertTaskTableHasNoLegacyColumns(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`task`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            assertEquals(false, columns.contains("assignee"))
            assertEquals(false, columns.contains("dueDateEpochMs"))
        }
    }

    private fun assertTaskIndexesExist(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA index_list(`task`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
            assertEquals(true, indexes.contains("index_task_projectId_createdAtEpochMs"))
            assertEquals(true, indexes.contains("index_task_projectId_objectCode_createdAtEpochMs"))
            assertEquals(true, indexes.contains("index_task_objectCode"))
        }
    }

    private fun assertDailyLogTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`daily_log`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            val expectedCols = listOf(
                "id", "projectId", "workItem", "manpower", "note", "createdAtEpochMs",
                "weather", "temperature", "nodeCode", "routeCode", "dateEpochDay",
                "volume", "unit", "categoryName", "batchGroupId", "appliedNodeCodesCsv",
                "linkedPhotoIdsCsv", "photoMatchOffsetMinutes"
            )
            for (col in expectedCols) {
                assertEquals("daily_log table missing column $col", true, columns.contains(col))
            }
        }
        database.openHelper.readableDatabase.query("PRAGMA index_list(`daily_log`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
            assertEquals(true, indexes.contains("index_daily_log_projectId_createdAtEpochMs"))
            assertEquals(true, indexes.contains("index_daily_log_projectId_dateEpochDay"))
            assertEquals(true, indexes.contains("index_daily_log_projectId_batchGroupId"))
        }
    }

    private fun assertNoteTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`note`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            val expectedCols = listOf("id", "projectId", "objectCode", "content", "createdAtEpochMs")
            for (col in expectedCols) {
                assertEquals(true, columns.contains(col))
            }
        }
        database.openHelper.readableDatabase.query("PRAGMA index_list(`note`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
            assertEquals(true, indexes.contains("index_note_projectId_createdAtEpochMs"))
            assertEquals(true, indexes.contains("index_note_projectId_objectCode_createdAtEpochMs"))
            assertEquals(true, indexes.contains("index_note_objectCode"))
        }
    }

    private fun assertWorkCategoriesTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`work_categories`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
            val expectedCols = listOf("id", "projectId", "name", "unit", "createdAtEpochMs")
            for (col in expectedCols) {
                assertEquals(true, columns.contains(col))
            }
        }
        database.openHelper.readableDatabase.query("PRAGMA index_list(`work_categories`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val indexes = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
            assertEquals(true, indexes.contains("index_work_categories_projectId"))
            assertEquals(true, indexes.contains("index_work_categories_projectId_createdAtEpochMs"))
        }
    }

    private fun loadSchema(version: Int): JSONObject {
        val candidates = listOf(
            File("data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
            File("../data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
            File("schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
        )
        val schemaFile = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate Room schema $version.json")
        return JSONObject(schemaFile.readText(Charsets.UTF_8))
    }

    private class TestDatabaseContext(base: Context, private val baseDir: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File {
            return if (name.startsWith("legacy")) File(baseDir, name) else super.getDatabasePath(name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: android.database.DatabaseErrorHandler?
        ): SQLiteDatabase {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory)
        }
    }

    @Test
    fun `migration 20 to 21 adds points column to gis route`() {
        val dbName = "legacy20.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion20Database(dbFile)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.readableDatabase.query("PRAGMA table_info(`gis_route`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                var pointsColumnFound = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "points") {
                        pointsColumnFound = true
                        assertEquals("TEXT", cursor.getString(typeIndex))
                    }
                }
                assertEquals(true, pointsColumnFound)
            }
        } finally {
            database.close()
        }
    }

    private fun createLegacyVersion20Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(20)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(indexSql)
                    }
                }
            }
            db.setVersion(20)
        }
    }

    @Test
    fun `migration 20 to 21 merges routes and aggregates progress`() {
        val dbName = "legacy20_merge.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion20Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Insert parent project to satisfy FK constraint in MIGRATION_22_23
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

            // Insert legacy nodes
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n1', 'proj1', 'ROUTE_1_P1', 'CON1', 10.0, 20.0, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n2', 'proj1', 'ROUTE_1_P2', 'CON1', 10.1, 20.1, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n3', 'proj1', 'ROUTE_1_P3', 'CON1', 10.2, 20.2, '', '')")

            // Insert segmented routes
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`) VALUES ('r1', 'proj1', 'ROUTE_1_S1', 'CON1', 'ROUTE_1_P1', 'ROUTE_1_P2')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`) VALUES ('r2', 'proj1', 'ROUTE_1_S2', 'CON1', 'ROUTE_1_P2', 'ROUTE_1_P3')")

            // Insert node progress
            db.execSQL("INSERT INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) VALUES ('np1', 'proj1', 'ROUTE_1_S1', 100.0, 40.0, 60.0, 0, 1000)")
            db.execSQL("INSERT INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) VALUES ('np2', 'proj1', 'ROUTE_1_S2', 200.0, 60.0, 140.0, 1, 2000)")

            // Insert material progress
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp1', 'proj1', 'ROUTE_1_S1', 'Concrete', 50.0, 20.0, 1000)")
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp2', 'proj1', 'ROUTE_1_S2', 'Concrete', 80.0, 30.0, 2000)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            // Verify route is merged
            val routes = runBlocking { database.gisRouteDao().byProject("proj1") }
            assertEquals(1, routes.size)
            val mergedRoute = routes.first()
            assertEquals("ROUTE_1", mergedRoute.code)
            assertEquals("ROUTE_1_P1", mergedRoute.startNodeCode)
            assertEquals("ROUTE_1_P3", mergedRoute.endNodeCode)
            assertEquals(listOf(10.0 to 20.0, 10.1 to 20.1, 10.2 to 20.2), mergedRoute.points)

            // Verify progress is aggregated
            val progressList = runBlocking { database.nodeProgressDao().byProject("proj1") }
            assertEquals(1, progressList.size)
            val mergedProgress = progressList.first()
            assertEquals("ROUTE_1", mergedProgress.nodeCode)
            assertEquals(300.0f, mergedProgress.planned)
            assertEquals(100.0f, mergedProgress.actual)
            assertEquals(200.0f, mergedProgress.remain)
            assertEquals(true, mergedProgress.delayed)

            // Verify materials aggregated
            val materials = runBlocking { database.materialProgressDao().byProject("proj1") }
            assertEquals(1, materials.size)
            val mergedMat = materials.first()
            assertEquals("ROUTE_1", mergedMat.nodeCode)
            assertEquals("Concrete", mergedMat.materialName)
            assertEquals(130.0f, mergedMat.plannedQty)
            assertEquals(50.0f, mergedMat.actualQty)
        } finally {
            database.close()
        }
    }

    private fun createLegacyVersion21Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(21)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(indexSql)
                    }
                }
            }
            db.setVersion(21)
        }
    }

    @Test
    fun `migration 21 to 22 adds designLength to route and backfills from node summary`() {
        val dbName = "legacy21.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion21Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Insert parent project to satisfy FK constraint in MIGRATION_22_23
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n1', 'proj1', 'ROUTE_1_P1', 'CON1', 10.0, 20.0, '', 'Vật tư:\n  Pipe: 2\nrouteLength: 125.4 m')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n2', 'proj1', 'ROUTE_1_P2', 'CON1', 10.1, 20.1, '', 'routeLength: 125.4 m')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`) VALUES ('r1', 'proj1', 'ROUTE_1', 'CON1', 'ROUTE_1_P1', 'ROUTE_1_P2', '[]')")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            val routes = runBlocking { database.gisRouteDao().byProject("proj1") }
            assertEquals(1, routes.size)
            val route = routes.first()
            assertEquals("125.4 m", route.designLength)

            val nodes = runBlocking { database.gisNodeDao().byProject("proj1") }
            val n1 = nodes.first { it.id == "n1" }
            val n2 = nodes.first { it.id == "n2" }

            assertEquals("Vật tư:\n  Pipe: 2", n1.materialSummary)
            assertEquals("", n2.materialSummary)
        } finally {
            database.close()
        }
    }

    private fun createLegacyVersion22Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(22)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(indexSql)
                    }
                }
            }
            db.setVersion(22)
        }
    }

    @Test
    fun `migration 22 to 23 adds foreign keys and checks cascade delete`() {
        val dbName = "legacy22.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion22Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj-fk-1', 'Project FK', 'proj-fk', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `imported_files` (`id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`) VALUES ('file-fk-1', 'proj-fk-1', 'test.kml', 'KML', '/path', '', 1100)")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`) VALUES ('node-fk-1', 'proj-fk-1', 'N1', 'CON1', 10.0, 20.0, '', '', 'file-fk-1')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`) VALUES ('route-fk-1', 'proj-fk-1', 'R1', 'CON1', 'N1', 'N2', '[]', 'file-fk-1', NULL)")
            db.execSQL("INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) VALUES ('task-fk-1', 'proj-fk-1', 'N1', 'Task 1', '', 'TODO', 1200)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                val project = database.projectDao().get("proj-fk-1")
                org.junit.Assert.assertNotNull(project)
                
                val nodes = database.gisNodeDao().byProject("proj-fk-1")
                assertEquals(1, nodes.size)
                assertEquals("node-fk-1", nodes.first().id)
                
                val tasks = database.taskDao().byProject("proj-fk-1")
                assertEquals(1, tasks.size)
                assertEquals("task-fk-1", tasks.first().id)
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `storageMode`, `projectDbPath`) " +
                    "VALUES ('proj-invalid', 'Invalid', 'invalid', 0, 1000, 'INVALID_MODE', '')"
                )
                org.junit.Assert.fail("Expected CHECK constraint violation for projects.storageMode")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Expected
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) " +
                    "VALUES ('task-invalid', 'proj-fk-1', 'N1', 'Invalid Task', '', 'INVALID_STATUS', 1200)"
                )
                org.junit.Assert.fail("Expected CHECK constraint violation for task.status")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Expected
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) " +
                    "VALUES ('node-dup', 'proj-fk-1', 'N1', 'CON1', 11.0, 21.0, '', '')"
                )
                org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_node (projectId, code)")
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Expected
            }

            database.openHelper.writableDatabase.execSQL("DELETE FROM `imported_files` WHERE `id` = 'file-fk-1'")
            runBlocking {
                val nodes = database.gisNodeDao().byProject("proj-fk-1")
                assertEquals(1, nodes.size)
                assertNull(nodes.first().importedFileId)
            }

            database.openHelper.writableDatabase.execSQL("DELETE FROM `projects` WHERE `id` = 'proj-fk-1'")
            runBlocking {
                assertNull(database.projectDao().get("proj-fk-1"))
                assertEquals(0, database.gisNodeDao().byProject("proj-fk-1").size)
                assertEquals(0, database.gisRouteDao().byProject("proj-fk-1").size)
                assertEquals(0, database.taskDao().byProject("proj-fk-1").size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 23 to 24 adds unit column to material progress`() {
        val dbName = "legacy23.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion23Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp1', 'proj1', 'N1', 'Concrete', 50.0, 20.0, 1000)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(
                MapSupervisionDatabase.MIGRATION_8_9,
                MapSupervisionDatabase.MIGRATION_9_10,
                MapSupervisionDatabase.MIGRATION_10_11,
                MapSupervisionDatabase.MIGRATION_11_12,
                MapSupervisionDatabase.MIGRATION_12_13,
                MapSupervisionDatabase.MIGRATION_13_14,
                MapSupervisionDatabase.MIGRATION_14_15,
                MapSupervisionDatabase.MIGRATION_15_16,
                MapSupervisionDatabase.MIGRATION_16_17,
                MapSupervisionDatabase.MIGRATION_17_18,
                MapSupervisionDatabase.MIGRATION_18_19,
                MapSupervisionDatabase.MIGRATION_19_20,
                MapSupervisionDatabase.MIGRATION_20_21,
                MapSupervisionDatabase.MIGRATION_21_22,
                MapSupervisionDatabase.MIGRATION_22_23,
                MapSupervisionDatabase.MIGRATION_23_24
            )
            .allowMainThreadQueries()
            .build()

        try {
            val materials = runBlocking { database.materialProgressDao().byProject("proj1") }
            assertEquals(1, materials.size)
            val mat = materials.first()
            assertEquals("mp1", mat.id)
            assertEquals("", mat.unit)
        } finally {
            database.close()
        }
    }

    private fun createLegacyVersion23Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(23)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(indexSql)
                    }
                }
            }
            db.setVersion(23)
        }
    }
}
