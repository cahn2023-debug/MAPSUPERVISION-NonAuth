package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_node",
    indices = [
        Index("importedFileId"),
        Index(value = ["projectId", "code"])
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
    val materialSummary: String,
    val importedFileId: String? = null
)
