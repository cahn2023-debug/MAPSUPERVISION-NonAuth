package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mapsupervision.data.db.entity.ImportedFileEntity

@Dao
interface ImportedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImportedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ImportedFileEntity>)

    @Query("SELECT * FROM imported_files WHERE projectId = :projectId ORDER BY importedAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ImportedFileEntity>

    @Query("DELETE FROM imported_files WHERE id = :id")
    suspend fun deleteFileRecord(id: String)

    @Query("DELETE FROM gis_node WHERE importedFileId = :fileId")
    suspend fun deleteGisNodesByFile(fileId: String)

    @Query("DELETE FROM gis_route WHERE importedFileId = :fileId")
    suspend fun deleteGisRoutesByFile(fileId: String)

    @Query("DELETE FROM material_progress WHERE nodeCode IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)")
    suspend fun deleteMaterialProgressByFile(fileId: String)

    @Query("DELETE FROM node_progress WHERE nodeCode IN (SELECT id FROM gis_node WHERE importedFileId = :fileId)")
    suspend fun deleteNodeProgressByFile(fileId: String)

    @Query("DELETE FROM note WHERE objectCode IN (SELECT code FROM gis_node WHERE importedFileId = :fileId) OR objectCode IN (SELECT code FROM gis_route WHERE importedFileId = :fileId)")
    suspend fun deleteNotesByFile(fileId: String)

    @Query("DELETE FROM task WHERE objectCode IN (SELECT code FROM gis_node WHERE importedFileId = :fileId) OR objectCode IN (SELECT code FROM gis_route WHERE importedFileId = :fileId)")
    suspend fun deleteTasksByFile(fileId: String)

    @Query("DELETE FROM site_photos WHERE objectCode IN (SELECT code FROM gis_node WHERE importedFileId = :fileId) OR objectCode IN (SELECT code FROM gis_route WHERE importedFileId = :fileId)")
    suspend fun deletePhotosByFile(fileId: String)

    @Transaction
    suspend fun deleteById(id: String) {
        deleteMaterialProgressByFile(id)
        deleteNodeProgressByFile(id)
        deleteNotesByFile(id)
        deleteTasksByFile(id)
        deletePhotosByFile(id)
        deleteGisNodesByFile(id)
        deleteGisRoutesByFile(id)
        deleteFileRecord(id)
    }
}
