package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_route",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
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
data class GisRouteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val startNodeCode: String,
    val endNodeCode: String,
    val points: List<Pair<Double, Double>>,
    val importedFileId: String? = null,
    val designLength: String? = null
)
