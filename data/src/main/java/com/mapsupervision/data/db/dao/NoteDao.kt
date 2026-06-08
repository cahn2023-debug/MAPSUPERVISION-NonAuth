package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.NoteEntity

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteEntity)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM note WHERE projectId = :projectId AND objectCode = :objectCode ORDER BY createdAtEpochMs DESC")
    suspend fun byObject(projectId: String, objectCode: String): List<NoteEntity>

    @Query("SELECT * FROM note WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<NoteEntity>
}
