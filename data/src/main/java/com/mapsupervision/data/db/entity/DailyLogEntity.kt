package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log",
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "dateEpochDay"])
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
    val dateEpochDay: Long,
    val volume: Double,
    val unit: String,
    val categoryName: String
)
