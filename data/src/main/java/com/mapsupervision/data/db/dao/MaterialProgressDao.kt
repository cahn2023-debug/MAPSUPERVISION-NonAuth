package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.MaterialProgressEntity

@Dao
interface MaterialProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MaterialProgressEntity)

    @Query("SELECT * FROM material_progress WHERE projectId = :projectId ORDER BY nodeCode, materialName")
    suspend fun byProject(projectId: String): List<MaterialProgressEntity>

    @Query("SELECT * FROM material_progress WHERE projectId = :projectId AND nodeCode = :nodeCode AND materialName = :materialName LIMIT 1")
    suspend fun findByNaturalKey(
        projectId: String,
        nodeCode: String,
        materialName: String
    ): MaterialProgressEntity?
}
