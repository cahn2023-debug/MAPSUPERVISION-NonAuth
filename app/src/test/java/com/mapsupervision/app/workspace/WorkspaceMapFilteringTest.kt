package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMapFilteringTest {

    @Test
    fun retain_route_endpoint_nodes_keeps_missing_endpoints_for_visible_routes() {
        val start = GisNode("n1", "p1", "N-001", "A", 10.0, 106.0)
        val end = GisNode("n2", "p1", "N-002", "B", 10.1, 106.1)
        val unrelated = GisNode("n3", "p1", "N-003", "C", 10.2, 106.2)
        val route = GisRoute("r1", "p1", "R-001", "A", "N-001", "N-002")

        val result = retainRouteEndpointNodes(
            filteredNodes = listOf(start),
            filteredRoutes = listOf(route),
            allNodes = listOf(start, end, unrelated)
        )

        assertEquals(listOf("N-001", "N-002"), result.map { it.code })
    }

    @Test
    fun retain_route_endpoint_nodes_deduplicates_existing_nodes() {
        val start = GisNode("n1", "p1", "N-001", "A", 10.0, 106.0)
        val end = GisNode("n2", "p1", "N-002", "A", 10.1, 106.1)
        val route = GisRoute("r1", "p1", "R-001", "A", "N-001", "N-002")

        val result = retainRouteEndpointNodes(
            filteredNodes = listOf(start, end),
            filteredRoutes = listOf(route),
            allNodes = listOf(start, end)
        )

        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(start, end)))
    }

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
    fun build_map_design_nodes_keeps_route_endpoints_when_search_filters_nodes_out() {
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

        assertEquals(listOf("P-001", "P-002"), result.map { it.code })
    }
}
