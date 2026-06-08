package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceMapFilteringTest {
    @Test
    fun build_map_design_nodes_returns_all_nodes_when_no_map_filters_are_active() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
            )
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(listOf("P-001", "P-002"), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_only_nodes_and_does_not_retain_route_endpoints() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val nodeC = GisNode("n3", "p1", "P-003", "C", 10.2, 106.2)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
            ),
            mapUi = MapUiState(searchQuery = "R-001")
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(emptyList<String>(), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_to_selected_contractor() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1)
        val nodeC = GisNode("n3", "p1", "P-003", "B", 10.2, 106.2)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002"),
                GisRoute("r2", "p1", "R-002", "B", "P-002", "P-003")
            ),
            mapUi = MapUiState(filterContractor = "A")
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(listOf("P-001", "P-002"), result.map { it.code })
    }
}
