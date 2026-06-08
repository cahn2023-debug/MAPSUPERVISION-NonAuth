package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.GisNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GisNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GisNodeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<GisNodeEntity>)

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId ORDER BY code")
    suspend fun byProject(projectId: String): List<GisNodeEntity>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId ORDER BY code")
    fun observeByProject(projectId: String): Flow<List<GisNodeEntity>>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND (code LIKE '%' || :query || '%' OR contractor LIKE '%' || :query || '%') ORDER BY code")
    suspend fun search(projectId: String, query: String): List<GisNodeEntity>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND (code LIKE '%' || :query || '%' OR contractor LIKE '%' || :query || '%') ORDER BY code")
    fun observeSearch(projectId: String, query: String): Flow<List<GisNodeEntity>>

    @Query("SELECT * FROM gis_node WHERE projectId = :projectId AND code = :code LIMIT 1")
    suspend fun findByCode(projectId: String, code: String): GisNodeEntity?
}
