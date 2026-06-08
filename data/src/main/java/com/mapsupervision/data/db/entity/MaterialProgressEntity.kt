package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "material_progress",
    indices = [
        Index(value = ["projectId", "nodeCode", "materialName"], unique = true),
        Index("nodeCode")
    ]
)
data class MaterialProgressEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val nodeCode: String,
    val materialName: String,
    val plannedQty: Float,
    val actualQty: Float,
    val updatedAtEpochMs: Long
)
