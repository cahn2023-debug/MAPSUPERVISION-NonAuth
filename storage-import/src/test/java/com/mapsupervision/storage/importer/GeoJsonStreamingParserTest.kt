package com.mapsupervision.storage.importer

import com.mapsupervision.domain.model.NonExcelImportMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonStreamingParserTest {

    @Test
    fun parse_geojson_streaming_builds_nodes_and_routes() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "N-01",
                    "contractor": "Team A",
                    "mapNumber": "M-1"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.7, 10.7]
                  }
                },
                {
                  "type": "Feature",
                  "properties": {
                    "name": "R-01",
                    "contractor": "Team A"
                  },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[106.7, 10.7], [106.8, 10.8]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "sample.geojson",
            projectId = "project-1",
            mapping = null
        )

        assertEquals(1, result.nodes.size)
        assertEquals(1, result.routes.size)
        assertEquals("N-01", result.nodes.single().code)
        assertEquals("Team A", result.nodes.single().contractor)
        assertTrue(result.summary.contains("features=2"))
    }

    @Test
    fun scan_geojson_preview_collects_keys_and_sample_rows_without_loading_full_tree() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "Node 1",
                    "contractor": "Alpha",
                    "customField": "A"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.1, 10.1]
                  }
                }
              ]
            }
        """.trimIndent()

        val scan = scanGeoJsonPreview(
            stream = geoJson.byteInputStream(),
            sourceName = "preview.geojson"
        )

        assertTrue(scan.metadataKeys.contains("customField"))
        assertEquals(1, scan.sampleRows.size)
        assertEquals("Node 1", scan.sampleRows.first()["properties.name"])
        assertEquals("Alpha", scan.sampleRows.first()["properties.contractor"])
    }

    @Test
    fun parse_geojson_streaming_respects_mapping_item_fields() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "N-02",
                    "contractor": "Beta",
                    "material": "Cable 24FO",
                    "quantity": "120"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [106.2, 10.2]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parseGeoJsonContentStreaming(
            stream = geoJson.byteInputStream(),
            sourceName = "mapped.geojson",
            projectId = "project-2",
            mapping = NonExcelImportMapping(
                positionField = "properties.name",
                contractorField = "properties.contractor",
                itemFields = listOf("properties.material", "properties.quantity")
            )
        )

        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes.single().workVolumeSummary.contains("material: Cable 24FO"))
        assertTrue(result.nodes.single().workVolumeSummary.contains("quantity: 120"))
    }
}
