package com.mapsupervision.gis.maplibre

import com.mapsupervision.domain.model.GisNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoordinateValidationTest {
    @Test
    fun valid_coordinate_is_renderable() {
        assertTrue(isValidCoordinate(21.02851, 105.80482))
        assertTrue(isRenderableNode(GisNode("n1", "p1", "N-001", "A", 21.02851, 105.80482)))
    }

    @Test
    fun out_of_range_coordinate_is_not_renderable() {
        assertFalse(isValidCoordinate(105.80482, 21.02851))
        assertFalse(isValidCoordinate(21.02851, 181.0))
        assertFalse(isRenderableNode(GisNode("n1", "p1", "N-001", "A", 105.80482, 21.02851)))
    }

    @Test
    fun summarize_coordinates_counts_valid_and_invalid_nodes() {
        val nodes = listOf(
            GisNode("n1", "p1", "N-001", "A", 21.02851, 105.80482),
            GisNode("n2", "p1", "N-002", "A", 10.0, 106.0),
            GisNode("n3", "p1", "N-003", "A", 120.0, 106.0)
        )

        val summary = summarizeCoordinates(nodes)

        assertEquals(2, summary.validCount)
        assertEquals(1, summary.invalidCount)
        assertEquals("10.00000..21.02851", summary.latRangeText)
        assertEquals("105.80482..106.00000", summary.lonRangeText)
    }
}
