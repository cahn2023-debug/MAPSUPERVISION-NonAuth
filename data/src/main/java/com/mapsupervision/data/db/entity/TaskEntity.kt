package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.TaskStatus

@Entity(
    tableName = "task",
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
        Index(value = ["projectId", "objectCode", "createdAtEpochMs"]),
        Index("objectCode")
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
