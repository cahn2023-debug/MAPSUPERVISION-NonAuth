package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.TaskEntity

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM task WHERE projectId = :projectId AND objectCode = :objectCode ORDER BY createdAtEpochMs DESC")
    suspend fun byObject(projectId: String, objectCode: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<TaskEntity>
}
