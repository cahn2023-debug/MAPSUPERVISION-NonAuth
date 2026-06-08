package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gis_route",
    indices = [
        Index("importedFileId"),
        Index(value = ["projectId", "code"])
    ]
)
data class GisRouteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val code: String,
    val contractor: String,
    val startNodeCode: String,
    val endNodeCode: String,
    val importedFileId: String? = null
)
