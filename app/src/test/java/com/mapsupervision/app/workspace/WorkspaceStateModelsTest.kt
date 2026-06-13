package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.MaterialProgress
import com.mapsupervision.domain.model.WorkCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceStateModelsTest {

    @Test
    fun `dedupeImportedFilesById keeps first occurrence per id`() {
        val files = listOf(
            ImportedFile("file-1", "project-1", "a.xlsx", "xlsx", "/tmp/a", "summary-a", 1L),
            ImportedFile("file-1", "project-1", "a-copy.xlsx", "xlsx", "/tmp/a2", "summary-b", 2L),
            ImportedFile("file-2", "project-1", "b.xlsx", "xlsx", "/tmp/b", "summary-c", 3L)
        )

        val deduped = dedupeImportedFilesById(files)

        assertEquals(2, deduped.size)
        assertEquals(listOf("file-1", "file-2"), deduped.map { it.id })
        assertEquals("a.xlsx", deduped.first().fileName)
    }

    @Test
    fun `template options prefer work category unit over blank material unit for same name`() {
        val state = WorkspaceState(
            activeProjectId = "p1",
            materialRows = listOf(
                MaterialProgress("m1", "p1", "N-1", "Thiết bị đo đếm lưu lượng", 100f, 10f, 1L, "")
            ),
            workCategories = listOf(
                WorkCategory("c1", "p1", "Thiết bị đo đếm lưu lượng", "bộ", 2L)
            )
        )

        val indexes = buildWorkspaceIndexes(state)
        val option = indexes.progressUi.templateOptions.first { it.name == "Thiết bị đo đếm lưu lượng" }

        assertEquals("bộ", option.unit)
        assertEquals("Hạng mục công việc chung", option.source)
    }

    @Test
    fun `resolve work template unit falls back to hierarchical aliases`() {
        val unit = resolveWorkTemplateUnit(
            "Tay vườn > Tay vườn 4m",
            workCategories = listOf(
                WorkCategory("c1", "p1", "Tay vườn 4m", "m", 2L)
            ),
            materialRows = emptyList()
        )

        assertEquals("m", unit)
    }
}
