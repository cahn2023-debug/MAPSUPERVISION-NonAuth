package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

import com.mapsupervision.domain.model.PhotoLocationStatus

@Entity(
    tableName = "site_photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "capturedAtEpochMs"]),
        Index(value = ["projectId", "objectCode", "capturedAtEpochMs"]),
        Index("objectCode"),
        Index(value = ["projectId", "matchedNodeCode"]),
        Index(value = ["projectId", "matchedRouteCode"])
    ]
)
data class SitePhotoEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val objectCode: String,
    val tagCodesCsv: String,
    val matchedNodeCode: String?,
    val matchedRouteCode: String?,
    val filePath: String,
    val thumbnailPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val isGpsMocked: Boolean,
    val locationStatus: PhotoLocationStatus,
    val engineer: String,
    val capturedAtEpochMs: Long,
    val matchedAtEpochMs: Long,
    val matchingTimeOffsetMs: Long,
    val mediaType: com.mapsupervision.domain.model.MediaType = com.mapsupervision.domain.model.MediaType.IMAGE,
    val mimeType: String = "image/jpeg",
    val durationMs: Long = 0L
)

