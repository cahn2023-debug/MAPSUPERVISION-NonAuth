package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.Note

@Entity(
    tableName = "note",
    indices = [
        Index("projectId"),
        Index("objectCode"),
        Index("createdAtEpochMs"),
        Index(value = ["projectId", "objectCode"]),
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "objectCode", "createdAtEpochMs"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val objectCode: String,
    val content: String,
    val createdAtEpochMs: Long
) {
    fun toDomain() = Note(
        id = id,
        projectId = projectId,
        objectCode = objectCode,
        content = content,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: Note) = NoteEntity(
            id = domain.id,
            projectId = domain.projectId,
            objectCode = domain.objectCode,
            content = domain.content,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}
