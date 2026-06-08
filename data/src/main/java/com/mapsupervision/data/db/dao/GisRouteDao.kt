package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.GisRouteEntity

@Dao
interface GisRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GisRouteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GisRouteEntity>)

    @Query("SELECT * FROM gis_route WHERE projectId = :projectId ORDER BY code")
    suspend fun byProject(projectId: String): List<GisRouteEntity>

    @Query("SELECT * FROM gis_route WHERE projectId = :projectId AND (code LIKE '%' || :query || '%' OR contractor LIKE '%' || :query || '%') ORDER BY code")
    suspend fun search(projectId: String, query: String): List<GisRouteEntity>
}
