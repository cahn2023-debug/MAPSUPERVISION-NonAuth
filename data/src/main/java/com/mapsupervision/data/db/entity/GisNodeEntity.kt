package com.mapsupervision.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_node",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ImportedFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["importedFileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("importedFileId"),
        Index(value = ["projectId", "code"], unique = true)
    ]
)
data class GisNodeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val latitude: Double,
    val longitude: Double,
    val mapNumberLabel: String,
    @ColumnInfo(name = "workVolumeSummary")
    val workVolumeSummary: String,
    val importedFileId: String? = null,
    val updatedAtEpochMs: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null
)

