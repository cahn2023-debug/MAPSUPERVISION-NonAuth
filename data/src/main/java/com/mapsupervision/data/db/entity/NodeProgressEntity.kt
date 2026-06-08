package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "node_progress",
    indices = [Index("projectId"), Index("nodeCode"), Index(value = ["projectId", "nodeCode"])]
)
data class NodeProgressEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val nodeCode: String,
    val planned: Float,
    val actual: Float,
    val remain: Float,
    val delayed: Boolean,
    val updatedAtEpochMs: Long = 0L
)
