package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceStateModelsTest {

    @Test
    fun toDataStateKeepsReactivePayload() {
        val state = WorkspaceState(
            activeProjectId = "p1",
            designNodes = listOf(GisNode("n1", "p1", "N-1", "CTR-A", 10.0, 106.0)),
            photoSaveCount = 2
        )

        val dataState = state.toDataState()

        assertEquals("p1", dataState.activeProjectId)
        assertEquals(1, dataState.designNodes.size)
        assertEquals(2, dataState.photoSaveCount)
    }
}
