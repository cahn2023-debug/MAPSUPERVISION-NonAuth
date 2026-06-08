package com.mapsupervision.gis.maplibre

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleAssetsTest {
    private val assetDir = File("src/main/assets")
    private val styleFiles = listOf(
        "style_street.json",
        "style_satellite.json",
        "style_dark.json"
    )

    @Test
    fun bundled_styles_keep_base_raster_and_do_not_define_gis_overlay_layers() {
        styleFiles.forEach { fileName ->
            val content = File(assetDir, fileName).readText()

            assertTrue("$fileName missing base_raster source", content.contains("\"base_raster\""))
            assertTrue("$fileName missing base layer", content.contains("\"id\": \"base\""))
            assertFalse("$fileName should not define nodes_source in style asset", content.contains("\"nodes_source\""))
            assertFalse("$fileName should not define routes_source in style asset", content.contains("\"routes_source\""))
            assertFalse("$fileName should not define measure_source in style asset", content.contains("\"measure_source\""))
            assertFalse("$fileName should not define nodes layer", content.contains("\"id\": \"nodes\""))
            assertFalse("$fileName should not define nodes_labels layer", content.contains("\"id\": \"nodes_labels\""))
            assertFalse("$fileName should not define routes layer", content.contains("\"id\": \"routes\""))
            assertFalse("$fileName should not define measure layer", content.contains("\"id\": \"measure_line\""))
        }
    }

    @Test
    fun bundled_styles_do_not_include_cluster_or_point_count_filters() {
        styleFiles.forEach { fileName ->
            val content = File(assetDir, fileName).readText()

            assertFalse("$fileName should not include cluster-only layers", content.contains("\"id\": \"node_clusters\""))
            assertFalse("$fileName should not include cluster count layer", content.contains("\"id\": \"node_cluster_count\""))
            assertFalse("$fileName should not include point_count filters", content.contains("\"point_count\""))
        }
    }
}
