package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.SitePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SitePhotoEntity)

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId ORDER BY capturedAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<SitePhotoEntity>

    @Query("SELECT * FROM site_photos WHERE projectId = :projectId ORDER BY capturedAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<SitePhotoEntity>>

    @Query(
        "SELECT * FROM site_photos WHERE projectId = :projectId AND objectCode = :objectCode " +
            "ORDER BY capturedAtEpochMs DESC"
    )
    suspend fun byObjectCode(projectId: String, objectCode: String): List<SitePhotoEntity>
}
