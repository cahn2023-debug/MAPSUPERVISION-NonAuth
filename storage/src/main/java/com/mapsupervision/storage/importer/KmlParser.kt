package com.mapsupervision.storage.importer

import java.io.InputStream

data class KmlPlacemark(
    val name: String,
    val description: String,
    val coordinates: String,
    val geometryType: String
)

@Deprecated("Use parseKmlContent() in UserFileImportService for full metadata and geometry support")
object KmlParser {

    fun parse(inputStream: InputStream): List<KmlPlacemark> {
        val result = parseKmlContent(inputStream, "legacy.kml")
        return result.nodes.map { node ->
            KmlPlacemark(
                name = node.mapNumberLabel,
                description = node.materialSummary,
                coordinates = "${node.longitude},${node.latitude},0",
                geometryType = "Point"
            )
        }
    }
}
