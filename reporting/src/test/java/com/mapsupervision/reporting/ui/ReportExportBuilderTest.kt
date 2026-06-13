package com.mapsupervision.reporting.ui

import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.model.DailyLog
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.NodeProgress
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.SitePhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportBuilderTest {
    @Test
    fun buildReportExportContent_withoutFilterKeepsWholeProjectData() {
        val content = buildReportExportContent(
            projectId = "project-1",
            filterContractor = null,
            photos = samplePhotos(),
            progress = sampleProgress(),
            materialRowsRaw = sampleMaterialRows(),
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            dailyLogs = sampleDailyLogs(),
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.any { it.contains("Summary") })
        assertTrue(content.lines.any { it.contains("Risk") })
        assertEquals(listOf("Cable", "Pipe", "Tổng"), content.materialRows.map { it.materialName })
        assertTrue(content.materialRows.last().isTotal)
        assertEquals(30f, content.materialRows[0].totalPlannedQty, 0.001f)
        assertEquals(20f, content.materialRows[0].totalActualQty, 0.001f)
        assertEquals(66.666664f, content.materialRows[0].completionPercent, 0.001f)
    }

    @Test
    fun buildReportExportContent_withFilterContractorAKeepsOnlyAData() {
        val content = buildReportExportContent(
            projectId = "project-1",
            filterContractor = "Contractor A",
            photos = samplePhotos(),
            progress = sampleProgress(),
            materialRowsRaw = sampleMaterialRows(),
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            dailyLogs = sampleDailyLogs(),
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.any { it.contains("Summary") })
        assertTrue(content.lines.any { it.contains("Risk") })
        assertEquals(listOf("Cable", "Tổng"), content.materialRows.map { it.materialName })
        assertEquals(10f, content.materialRows[0].totalPlannedQty, 0.001f)
        assertEquals(6f, content.materialRows[0].totalActualQty, 0.001f)
        assertEquals(60f, content.materialRows[0].completionPercent, 0.001f)
        assertEquals(10f, content.materialRows.last().totalPlannedQty, 0.001f)
        assertEquals(6f, content.materialRows.last().totalActualQty, 0.001f)
        assertEquals(60f, content.materialRows.last().completionPercent, 0.001f)
    }

    @Test
    fun buildReportExportContent_fromSnapshotUsesSnapshotData() {
        val snapshot = ReportingSnapshot(
            projectId = "project-1",
            projectName = "Project One",
            nodes = sampleNodes(),
            routes = sampleRoutes(),
            photos = samplePhotos(),
            progress = sampleProgress(),
            materialRowsRaw = sampleMaterialRows(),
            materialRows = buildMaterialReportRows(sampleNodes(), sampleRoutes(), sampleMaterialRows()),
            dailyLogs = sampleDailyLogs()
        )

        val content = buildReportExportContent(
            snapshot = snapshot,
            filterContractor = "Contractor A",
            activeDraft = sampleDraft()
        )

        assertEquals("project-1", content.targetId)
        assertEquals(2, content.photos.size)
        assertEquals(2, content.dailyLogLines.size)
        assertEquals(8, content.lines.size)
        assertTrue(content.lines.first().startsWith("B"))
        assertEquals(listOf("Cable", "Tổng"), content.materialRows.map { it.materialName })
    }


    @Test
    fun buildMaterialReportRows_usesPlannedQtyFallbackWhenSummaryMissing() {
        val nodes = listOf(
            GisNode(
                id = "node-c-id",
                projectId = "project-1",
                code = "NODE-C",
                contractor = "Contractor C",
                latitude = 0.0,
                longitude = 0.0,
                materialSummary = ""
            )
        )
        val rows = listOf(
            MaterialProgress(
                id = "material-c",
                projectId = "project-1",
                nodeCode = "NODE-C",
                materialName = "Clamp",
                plannedQty = 7f,
                actualQty = 3f,
                updatedAtEpochMs = 1_000L,
                unit = ""
            )
        )

        val materialRows = buildMaterialReportRows(nodes, emptyList(), rows, filterContractor = "Contractor C")

        assertEquals(listOf("Clamp", "Tổng"), materialRows.map { it.materialName })
        assertEquals(7f, materialRows[0].totalPlannedQty, 0.001f)
        assertEquals(3f, materialRows[0].totalActualQty, 0.001f)
        assertEquals(42.857143f, materialRows[0].completionPercent, 0.001f)
        assertEquals(7f, materialRows.last().totalPlannedQty, 0.001f)
        assertEquals(3f, materialRows.last().totalActualQty, 0.001f)
    }

    private fun sampleDraft() = ReportDraftResult(
        executiveSummary = "Summary",
        riskSection = "Risk",
        recommendedActions = listOf("Action 1")
    )

    private fun samplePhotos() = listOf(
        SitePhoto(
            id = "photo-a",
            projectId = "project-1",
            objectCode = "NODE-A",
            filePath = "/tmp/photo-a.jpg",
            thumbnailPath = "/tmp/photo-a-thumb.jpg",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = PhotoLocationStatus.OK,
            engineer = "Engineer",
            capturedAtEpochMs = 1_000L,
            matchedNodeCode = "NODE-A"
        ),
        SitePhoto(
            id = "photo-b",
            projectId = "project-1",
            objectCode = "NODE-B",
            filePath = "/tmp/photo-b.jpg",
            thumbnailPath = "/tmp/photo-b-thumb.jpg",
            latitude = null,
            longitude = null,
            locationAccuracyM = null,
            isGpsMocked = false,
            locationStatus = PhotoLocationStatus.OK,
            engineer = "Engineer",
            capturedAtEpochMs = 2_000L,
            matchedNodeCode = "NODE-B"
        )
    )

    private fun sampleProgress() = listOf(
        NodeProgress(
            id = "progress-a",
            projectId = "project-1",
            nodeCode = "NODE-A",
            planned = 50f,
            actual = 40f,
            remain = 10f,
            delayed = true
        ),
        NodeProgress(
            id = "progress-b",
            projectId = "project-1",
            nodeCode = "NODE-B",
            planned = 60f,
            actual = 60f,
            remain = 0f,
            delayed = false
        )
    )

    private fun sampleMaterialRows() = listOf(
        MaterialProgress(
            id = "material-a",
            projectId = "project-1",
            nodeCode = "NODE-A",
            materialName = "Cable",
            plannedQty = 10f,
            actualQty = 6f,
            updatedAtEpochMs = 1_000L,
            unit = ""
        ),
        MaterialProgress(
            id = "material-b",
            projectId = "project-1",
            nodeCode = "NODE-B",
            materialName = "Cable",
            plannedQty = 20f,
            actualQty = 14f,
            updatedAtEpochMs = 2_000L,
            unit = ""
        ),
        MaterialProgress(
            id = "material-c",
            projectId = "project-1",
            nodeCode = "NODE-B",
            materialName = "Pipe",
            plannedQty = 5f,
            actualQty = 2f,
            updatedAtEpochMs = 3_000L,
            unit = ""
        )
    )

    private fun sampleNodes() = listOf(
        GisNode(
            id = "node-a-id",
            projectId = "project-1",
            code = "NODE-A",
            contractor = "Contractor A",
            latitude = 0.0,
            longitude = 0.0,
            materialSummary = "Cable: 10"
        ),
        GisNode(
            id = "node-b-id",
            projectId = "project-1",
            code = "NODE-B",
            contractor = "Contractor B",
            latitude = 0.0,
            longitude = 0.0,
            materialSummary = "Cable: 20\nPipe: 5"
        )
    )

    private fun sampleRoutes() = listOf(
        GisRoute(
            id = "route-1",
            projectId = "project-1",
            code = "ROUTE-1",
            contractor = "Contractor A",
            startNodeCode = "NODE-A",
            endNodeCode = "NODE-B"
        )
    )

    private fun sampleDailyLogs() = listOf(
        DailyLog(
            id = "log-a",
            projectId = "project-1",
            workItem = "Work A",
            manpower = 3,
            note = "Note A",
            createdAtEpochMs = 1_000L,
            nodeCode = "NODE-A",
            volume = 1.0,
            unit = "m"
        ),
        DailyLog(
            id = "log-b",
            projectId = "project-1",
            workItem = "Work B",
            manpower = 2,
            note = "Note B",
            createdAtEpochMs = 2_000L,
            nodeCode = "NODE-B",
            volume = 2.0,
            unit = "m"
        )
    )
}



