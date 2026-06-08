package com.mapsupervision.gis.maplibre

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.gis.ui.GisLabelField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapRenderKeyTest {
    @Test
    fun render_key_is_stable_for_recreated_lists_with_same_content() {
        val nodes1 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes1 = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val nodes2 = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes2 = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val key1 = buildMapRenderKey(nodes1, routes1, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"))
        val key2 = buildMapRenderKey(nodes2, routes2, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"))

        assertEquals(key1, key2)
    }

    @Test
    fun render_key_changes_when_visual_inputs_change() {
        val nodes = listOf(
            GisNode("node-1", "project-1", "N-001", "CTR-A", 10.0, 106.0, "001")
        )
        val routes = listOf(
            GisRoute("route-1", "project-1", "R-001", "CTR-A", "N-001", "N-001")
        )

        val key1 = buildMapRenderKey(nodes, routes, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#f97316"))
        val key2 = buildMapRenderKey(nodes, routes, GisLabelField.CONTRACTOR, true, true, mapOf("CTR-A" to "#f97316"))
        val key3 = buildMapRenderKey(nodes, routes, GisLabelField.CODE, true, true, mapOf("CTR-A" to "#22c55e"))

        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
    }
}
