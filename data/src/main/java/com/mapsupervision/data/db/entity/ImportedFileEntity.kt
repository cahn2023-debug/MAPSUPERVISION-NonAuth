package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "imported_files",
    indices = [
        Index("projectId"),
        Index("fileType"),
        Index(value = ["projectId", "importedAtEpochMs"])
    ]
)
data class ImportedFileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val importedAtEpochMs: Long
)
