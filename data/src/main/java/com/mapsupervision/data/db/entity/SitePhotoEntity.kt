package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.mapsupervision.domain.model.PhotoLocationStatus

@Entity(
    tableName = "site_photos",
    indices = [
        Index(value = ["projectId", "capturedAtEpochMs"]),
        Index(value = ["projectId", "objectCode", "capturedAtEpochMs"]),
        Index("objectCode")
    ]
)
data class SitePhotoEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val objectCode: String,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val isGpsMocked: Boolean,
    val locationStatus: PhotoLocationStatus,
    val engineer: String,
    val capturedAtEpochMs: Long
)
