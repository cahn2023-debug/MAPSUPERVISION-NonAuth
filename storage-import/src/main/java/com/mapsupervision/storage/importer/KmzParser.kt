package com.mapsupervision.storage.importer

import java.io.File

/**
 * KMZ parser that extracts KML content from ZIP archives.
 * This parser is now deprecated in favor of parseKmzContent() in UserFileImportService
 * which provides more comprehensive metadata extraction.
 */
@Deprecated("Use parseKmzContent() in UserFileImportService instead for full metadata support")
object KmzParser {

    fun parse(file: File): List<KmlPlacemark> {
        val placemarks = mutableListOf<KmlPlacemark>()
        try {
            val result = parseKmzContent(file, file.name)
            // Convert ParsedImportResult to legacy KmlPlacemark format
            result.nodes.forEach { node ->
                placemarks.add(
                    KmlPlacemark(
                        name = node.mapNumberLabel,
                        description = node.materialSummary,
                        coordinates = "${node.longitude},${node.latitude},0",
                        geometryType = "Point"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return placemarks
    }
}
