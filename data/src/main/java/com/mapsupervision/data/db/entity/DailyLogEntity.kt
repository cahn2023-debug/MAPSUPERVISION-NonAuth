package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "dateEpochDay"]),
        Index(value = ["projectId", "batchGroupId"])
    ]
)
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val workItem: String,
    val manpower: Int,
    val note: String,
    val createdAtEpochMs: Long,
    val weather: String,
    val temperature: Double,
    val nodeCode: String?,
    val routeCode: String?,
    val dateEpochDay: Long,
    val volume: Double,
    val unit: String,
    val categoryName: String,
    val batchGroupId: String,
    val appliedNodeCodesCsv: String,
    val linkedPhotoIdsCsv: String,
    val photoMatchOffsetMinutes: Int
)
