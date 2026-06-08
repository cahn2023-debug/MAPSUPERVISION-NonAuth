package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "work_categories",
    indices = [
        Index("projectId")
    ]
)
data class WorkCategoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val unit: String,
    val createdAtEpochMs: Long
)
