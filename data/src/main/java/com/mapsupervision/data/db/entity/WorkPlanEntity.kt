package com.mapsupervision.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mapsupervision.domain.model.WorkPlan

@Entity(
    tableName = "work_plan",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "plannedDateEpochDay"])
    ]
)
data class WorkPlanEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val plannedDateEpochDay: Long,
    val nodeCode: String?,
    val routeCode: String?,
    val taskId: String?,
    val sourceRawInput: String,
    val createdAtEpochMs: Long
) {
    fun toDomain() = WorkPlan(
        id = id,
        projectId = projectId,
        title = title,
        description = description,
        plannedDateEpochDay = plannedDateEpochDay,
        nodeCode = nodeCode,
        routeCode = routeCode,
        taskId = taskId,
        sourceRawInput = sourceRawInput,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: WorkPlan) = WorkPlanEntity(
            id = domain.id,
            projectId = domain.projectId,
            title = domain.title,
            description = domain.description,
            plannedDateEpochDay = domain.plannedDateEpochDay,
            nodeCode = domain.nodeCode,
            routeCode = domain.routeCode,
            taskId = domain.taskId,
            sourceRawInput = domain.sourceRawInput,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}
