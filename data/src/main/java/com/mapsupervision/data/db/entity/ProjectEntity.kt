package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.ProjectStorageMode

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val isArchived: Boolean,
    val createdAtEpochMs: Long,
    val metadataVersion: Int = 3,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val storageMode: ProjectStorageMode = ProjectStorageMode.LEGACY_SHARED,
    val projectDbPath: String = ""
)
