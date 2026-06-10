package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_history",
    indices = [
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index("projectId")
    ]
)
data class ChatHistoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val role: String,
    val text: String,
    val createdAtEpochMs: Long
)
