package com.mapsupervision.reporting.ui

import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.SitePhoto

data class ReportingSnapshot(
    val projectId: String? = null,
    val projectName: String = "",
    val version: Long = 0L,
    val loadedAtEpochMs: Long = 0L,
    val nodes: List<GisNode> = emptyList(),
    val routes: List<GisRoute> = emptyList(),
    val photos: List<SitePhoto> = emptyList(),
    val progress: List<NodeProgress> = emptyList(),
    val materialRowsRaw: List<MaterialProgress> = emptyList(),
    val materialRows: List<MaterialReportRow> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val aiDraft: ReportDraftResult? = null
) {
    companion object {
        val Empty = ReportingSnapshot()
    }

    fun isForProject(projectId: String): Boolean = this.projectId == projectId

    fun withDraft(draft: ReportDraftResult?): ReportingSnapshot = copy(aiDraft = draft)
}
