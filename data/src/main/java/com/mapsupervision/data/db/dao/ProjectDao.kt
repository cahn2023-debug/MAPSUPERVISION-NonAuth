package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mapsupervision.data.db.entity.ProjectEntity

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectEntity)

    @Query("SELECT * FROM projects WHERE (:includeArchived = 1 OR isArchived = 0) ORDER BY createdAtEpochMs DESC")
    suspend fun list(includeArchived: Boolean): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectEntity?

    @Query("UPDATE projects SET metadataVersion = :metadataVersion, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
    suspend fun touch(projectId: String, metadataVersion: Int, updatedAtEpochMs: Long)

    @Query("UPDATE projects SET isArchived = 1 WHERE id = :projectId")
    suspend fun archive(projectId: String)

    @Query("DELETE FROM gis_node WHERE projectId = :projectId")
    suspend fun deleteGisNodes(projectId: String)

    @Query("DELETE FROM gis_route WHERE projectId = :projectId")
    suspend fun deleteGisRoutes(projectId: String)

    @Query("DELETE FROM note WHERE projectId = :projectId")
    suspend fun deleteNotes(projectId: String)

    @Query("DELETE FROM task WHERE projectId = :projectId")
    suspend fun deleteTasks(projectId: String)

    @Query("DELETE FROM material_progress WHERE projectId = :projectId")
    suspend fun deleteMaterialProgress(projectId: String)

    @Query("DELETE FROM node_progress WHERE projectId = :projectId")
    suspend fun deleteNodeProgress(projectId: String)

    @Query("DELETE FROM site_photos WHERE projectId = :projectId")
    suspend fun deleteSitePhotos(projectId: String)

    @Query("DELETE FROM imported_files WHERE projectId = :projectId")
    suspend fun deleteImportedFiles(projectId: String)

    @Query("DELETE FROM daily_log WHERE projectId = :projectId")
    suspend fun deleteDailyLogs(projectId: String)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectRecord(projectId: String)

    @Transaction
    suspend fun clearProjectData(projectId: String) {
        deleteGisNodes(projectId)
        deleteGisRoutes(projectId)
        deleteNotes(projectId)
        deleteTasks(projectId)
        deleteMaterialProgress(projectId)
        deleteNodeProgress(projectId)
        deleteSitePhotos(projectId)
        deleteImportedFiles(projectId)
        deleteDailyLogs(projectId)
        deleteProjectRecord(projectId)
    }
}
