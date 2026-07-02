package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.WorkCategory
import com.mapsupervision.domain.model.WorkVolumeProgress

internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

internal data class WorkspaceRefreshSnapshot(
    val imports: List<ImportedFile>,
    val nodes: List<GisNode>,
    val routes: List<GisRoute>,
    val progress: List<NodeProgress>,
    val workVolumeRows: List<WorkVolumeProgress>,
    val dailyLogs: List<DailyLog>,
    val workCategories: List<WorkCategory>,
    val materialHandovers: List<MaterialHandover>,
    val materialDeclarations: List<MaterialDeclaration>
)
