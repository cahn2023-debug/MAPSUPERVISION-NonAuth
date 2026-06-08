package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus

@Entity(
    tableName = "task",
    indices = [
        Index("projectId"),
        Index("objectCode"),
        Index("status"),
        Index("createdAtEpochMs"),
        Index(value = ["projectId", "objectCode"]),
        Index(value = ["projectId", "createdAtEpochMs"]),
        Index(value = ["projectId", "objectCode", "createdAtEpochMs"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val objectCode: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null
) {
    fun toDomain() = Task(
        id = id,
        projectId = projectId,
        objectCode = objectCode,
        title = title,
        description = description,
        status = TaskStatus.valueOf(status),
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs
    )

    companion object {
        fun fromDomain(domain: Task) = TaskEntity(
            id = domain.id,
            projectId = domain.projectId,
            objectCode = domain.objectCode,
            title = domain.title,
            description = domain.description,
            status = domain.status.name,
            createdAtEpochMs = domain.createdAtEpochMs,
            completedAtEpochMs = domain.completedAtEpochMs
        )
    }
}
