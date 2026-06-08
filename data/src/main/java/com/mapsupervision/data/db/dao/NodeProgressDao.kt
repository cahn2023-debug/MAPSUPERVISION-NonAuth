package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.NodeProgressEntity

@Dao
interface NodeProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NodeProgressEntity)

    @Query("SELECT * FROM node_progress WHERE projectId = :projectId ORDER BY nodeCode")
    suspend fun byProject(projectId: String): List<NodeProgressEntity>
}
