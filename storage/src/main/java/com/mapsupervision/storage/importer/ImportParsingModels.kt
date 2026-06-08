package com.mapsupervision.storage.importer

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.xmlpull.v1.XmlPullParserFactory
enum class KmlGeometryKind { POINT, LINE }

data class KmlGeometryBlock(
    val kind: KmlGeometryKind,
    val points: List<Pair<Double, Double>>
)

internal fun parseKmlContent(
    stream: InputStream,
    sourceName: String,
    projectId: String = "",
    mapping: NonExcelImportMapping? = null
): ParsedImportResult {
    val doc = documentBuilderFactory.newDocumentBuilder().parse(stream)
    val placemarkNodes = findElementsByLocalTagName(doc.documentElement, "placemark")
    val coordinateNodes = findElementsByLocalTagName(doc.documentElement, "coordinates")
    val nodes = ArrayList<GisNode>(placemarkNodes.size * 2)
    val routes = ArrayList<GisRoute>(placemarkNodes.size)
    var totalRouteLengthMeters = 0.0
    var geometryBlockCount = 0
    val base = sourceName.substringBeforeLast(".").take(12).uppercase(Locale.US)

    placemarkNodes.forEachIndexed { index, placemark ->
        val nameNode = firstChildByLocalTagName(placemark, "name")
        val name = nameNode?.textContent.orEmpty().trim()
        val descNode = firstChildByLocalTagName(placemark, "description")
        val description = stripKmlHtml(descNode?.textContent.orEmpty().trim())
        val extendedData = parseExtendedData(placemark)
        val geometryBlocks = collectPlacemarkGeometries(placemark)
        if (geometryBlocks.isEmpty()) return@forEachIndexed
        geometryBlockCount += geometryBlocks.size

        val placemarkOrdinal = index + 1
        val routeDisplayName = name.ifBlank { "${base}_LINE_$placemarkOrdinal" }
        fun nodeCode(blockIndex: Int, pointIndex: Int): String =
            "${routeDisplayName}#pm${placemarkOrdinal}_b${blockIndex + 1}_p${pointIndex + 1}"
        fun routeCode(blockIndex: Int, segmentIndex: Int): String =
            "${routeDisplayName}#pm${placemarkOrdinal}_b${blockIndex + 1}_s${segmentIndex + 1}"

        // Find which keys are mapped to Excel fields
        val mappedKeys = mutableSetOf<String>()
        if (mapping != null) {
            if (mapping.positionField.isNotBlank()) mappedKeys.add(mapping.positionField)
            if (mapping.coordinateField?.isNotBlank() == true) mappedKeys.add(mapping.coordinateField)
            if (mapping.latitudeField?.isNotBlank() == true) mappedKeys.add(mapping.latitudeField)
            if (mapping.longitudeField?.isNotBlank() == true) mappedKeys.add(mapping.longitudeField)
            if (mapping.contractorField?.isNotBlank() == true) mappedKeys.add(mapping.contractorField)
            if (mapping.mapNumberField?.isNotBlank() == true) mappedKeys.add(mapping.mapNumberField)
            if (mapping.objectTypeField?.isNotBlank() == true) mappedKeys.add(mapping.objectTypeField)
            if (mapping.routeLengthField?.isNotBlank() == true) mappedKeys.add(mapping.routeLengthField)
            mappedKeys.addAll(mapping.itemFields)
        }

        // Determine custom (unmapped) fields from extendedData
        val customFields = extendedData.filterKeys { it !in mappedKeys }

        val extractedContractor = if (mapping != null) {
            val field = mapping.contractorField
            if (field.isNullOrBlank()) {
                ""
            } else {
                when (field) {
                    "UPLOAD" -> "UPLOAD"
                    else -> extendedData[field]?.trim() ?: ""
                }
            }
        } else {
            extendedData["contractor"]?.trim() ?: ""
        }

        val extractedMapNumber = if (mapping != null) {
            val field = mapping.mapNumberField
            if (field.isNullOrBlank()) {
                ""
            } else {
                when (field) {
                    "Mã tự sinh từ đối tượng" -> name
                    else -> extendedData[field]?.trim() ?: ""
                }
            }
        } else {
            name
        }

        fun createNode(code: String, point: Pair<Double, Double>) {
            val extractedCode = if (mapping != null) {
                when (mapping.positionField) {
                    "Tên đối tượng (Placemark)" -> name.ifBlank { code }
                    "Tự sinh mã" -> code
                    else -> extendedData[mapping.positionField]?.trim()?.ifBlank { name } ?: name.ifBlank { code }
                }
            } else {
                code
            }

            val materialSummary = buildString {
                // Include mapped items first
                if (mapping != null && mapping.itemFields.isNotEmpty()) {
                    var hasItems = false
                    mapping.itemFields.forEach { itemKey ->
                        val value = extendedData[itemKey]?.trim()
                        if (!value.isNullOrBlank()) {
                            if (!hasItems) {
                                append("Vật tư:\n")
                                hasItems = true
                            }
                            append("  $itemKey: $value\n")
                        }
                    }
                }
                
                // Include unmapped/custom fields (split to new fields)
                if (customFields.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("ExtendedData:\n")
                    customFields.forEach { (key, value) ->
                        append("$key: $value\n")
                    }
                }

                if (description.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("Description: $description")
                }
            }.trim()

            nodes += GisNode(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                code = extractedCode,
                contractor = extractedContractor,
                latitude = point.first,
                longitude = point.second,
                mapNumberLabel = extractedMapNumber,
                materialSummary = materialSummary
            )
        }

        geometryBlocks.forEachIndexed { blockIndex, block ->
            when (block.kind) {
                KmlGeometryKind.POINT -> {
                    createNode(nodeCode(blockIndex, 0), block.points.first())
                }
                KmlGeometryKind.LINE -> {
                    if (block.points.isEmpty()) return@forEachIndexed
                    block.points.forEachIndexed { pointIndex, point ->
                        createNode(nodeCode(blockIndex, pointIndex), point)
                    }
                    if (block.points.size > 1) {
                        for (pointIndex in 1 until block.points.size) {
                            totalRouteLengthMeters += haversineMeters(
                                point = block.points[pointIndex],
                                previous = block.points[pointIndex - 1]
                            )
                            val startCode = nodeCode(blockIndex, pointIndex - 1)
                            val endCode = nodeCode(blockIndex, pointIndex)
                            routes += GisRoute(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                code = routeCode(blockIndex, pointIndex - 1),
                                contractor = extractedContractor,
                                startNodeCode = startCode,
                                endNodeCode = endCode
                            )
                        }
                    }
                }
            }
        }
    }

    val segmentCount = routes.size
    val summary = if (nodes.isEmpty() && routes.isEmpty()) {
        "KML parsed: ${placemarkNodes.size} placemarks, ${coordinateNodes.size} coordinate blocks; valid file but no supported geometry found"
    } else {
        "KML parsed: ${placemarkNodes.size} placemarks, $geometryBlockCount geometry blocks, segments=$segmentCount, ${nodes.size} nodes, ${routes.size} edges, routeLength=${"%.2f".format(Locale.US, totalRouteLengthMeters)}m"
    }
    return ParsedImportResult(summary = summary, nodes = nodes, routes = routes, routeLengthMeters = totalRouteLengthMeters)
}

internal fun parseKmzContent(
    file: File,
    sourceName: String,
    projectId: String = "",
    mapping: NonExcelImportMapping? = null
): ParsedImportResult {
    ZipFile(file).use { zip ->
        val kmlEntries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.lowercase(Locale.US).endsWith(".kml") }
            .sortedWith(
                compareBy<java.util.zip.ZipEntry>(
                    { entry ->
                        val normalized = entry.name.substringAfterLast('/').lowercase(Locale.US)
                        when {
                            normalized == "doc.kml" -> 0
                            entry.name.equals("doc.kml", ignoreCase = true) -> 0
                            else -> 1
                        }
                    },
                    { it.name.lowercase(Locale.US) }
                )
            )
            .toList()
        if (kmlEntries.isEmpty()) {
            return ParsedImportResult("KMZ parsed: no embedded KML found")
        }

        val allNodes = ArrayList<GisNode>()
        val allRoutes = ArrayList<GisRoute>()
        var totalRouteLengthMeters = 0.0
        val fileSummaries = ArrayList<String>(kmlEntries.size)

        for (entry in kmlEntries) {
            val entrySourceName = "${sourceName.substringBeforeLast('.')}/${entry.name}"
            val parsed = zip.getInputStream(entry).use { parseKmlContent(it, entrySourceName, projectId, mapping) }
            allNodes += parsed.nodes
            allRoutes += parsed.routes
            totalRouteLengthMeters += parsed.routeLengthMeters
            fileSummaries += "${entry.name}(${parsed.nodes.size}n/${parsed.routes.size}r)"
        }

        return ParsedImportResult(
            summary = buildString {
                append("KMZ parsed: ${kmlEntries.size} KML file(s) [")
                append(fileSummaries.joinToString("; "))
                append("]; ")
                append("${allNodes.size} nodes, ${allRoutes.size} routes, routeLength=")
                append("%.2f".format(Locale.US, totalRouteLengthMeters))
                append("m")
            },
            nodes = allNodes,
            routes = allRoutes,
            routeLengthMeters = totalRouteLengthMeters
        )
    }
}

fun haversineMeters(point: Pair<Double, Double>, previous: Pair<Double, Double>): Double {
    val earthRadius = 6_371_000.0
    val lat1 = Math.toRadians(previous.first)
    val lat2 = Math.toRadians(point.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(point.second - previous.second)
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
}

fun findElementsByLocalTagName(root: org.w3c.dom.Node?, expectedLower: String): List<org.w3c.dom.Node> {
    if (root == null) return emptyList()
    val result = ArrayList<org.w3c.dom.Node>(32)
    fun visit(node: org.w3c.dom.Node?) {
        if (node == null) return
        if (nodeLocalNameLower(node) == expectedLower) result += node
        val children = node.childNodes
        for (i in 0 until children.length) visit(children.item(i))
    }
    visit(root)
    return result
}

fun firstChildByLocalTagName(parent: org.w3c.dom.Node?, expectedLower: String): org.w3c.dom.Node? {
    if (parent == null) return null
    val children = parent.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (nodeLocalNameLower(child) == expectedLower) return child
    }
    return null
}

fun nodeLocalNameLower(node: org.w3c.dom.Node): String {
    val local = node.localName ?: node.nodeName
    return local.substringAfter(':').lowercase(Locale.US)
}

fun collectPlacemarkGeometries(placemark: org.w3c.dom.Node): List<KmlGeometryBlock> {
    val blocks = ArrayList<KmlGeometryBlock>(4)
    for (point in findElementsByLocalTagName(placemark, "point")) {
        val coords = parseKmlCoordinates(coordinatesText(point))
        if (coords.isNotEmpty()) {
            blocks += KmlGeometryBlock(KmlGeometryKind.POINT, listOf(coords.first()))
        }
    }
    for (line in findElementsByLocalTagName(placemark, "linestring")) {
        val coords = parseKmlCoordinates(coordinatesText(line))
        if (coords.isNotEmpty()) {
            blocks += KmlGeometryBlock(KmlGeometryKind.LINE, coords)
        }
    }
    for (polygon in findElementsByLocalTagName(placemark, "polygon")) {
        val outer = firstChildByLocalTagName(polygon, "outerboundaryis")
        val ring = outer?.let { firstChildByLocalTagName(it, "linearring") }
        val coords = parseKmlCoordinates(coordinatesText(ring))
        if (coords.isNotEmpty()) {
            blocks += KmlGeometryBlock(KmlGeometryKind.LINE, coords)
        }
    }
    return blocks
}

fun coordinatesText(geometryNode: org.w3c.dom.Node?): String {
    if (geometryNode == null) return ""
    val coordNode = firstChildByLocalTagName(geometryNode, "coordinates")
        ?: findElementsByLocalTagName(geometryNode, "coordinates").firstOrNull()
    return coordNode?.textContent.orEmpty().trim()
}

fun stripKmlHtml(text: String): String =
    text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

fun parseKmlCoordinates(text: String): List<Pair<Double, Double>> {
    if (text.isBlank()) return emptyList()
    val result = ArrayList<Pair<Double, Double>>(8)
    // Some exporters use semicolons between coordinate tuples instead of whitespace.
    val normalized = text.trim().replace(';', ' ')
    val tokens = normalized.split(Regex("\\s+"))
    for (token in tokens) {
        if (token.isBlank()) continue
        val parts = token.split(',')
        if (parts.size < 2) continue
        val p1 = parts[0].trim().toDoubleOrNull() ?: continue
        val p2 = parts[1].trim().toDoubleOrNull() ?: continue
        val pair = parseKmlCoordinatePair(p1, p2) ?: continue
        result += pair
    }
    return result
}

fun parseKmlCoordinatePair(p1: Double, p2: Double): Pair<Double, Double>? {
    // KML standard specifies: longitude (p1), latitude (p2).
    // We return Pair(latitude, longitude) -> p2 to p1.
    if (p2 in -90.0..90.0 && p1 in -180.0..180.0) return p2 to p1
    // Fallback if the coordinates order is reversed:
    if (p1 in -90.0..90.0 && p2 in -180.0..180.0) return p1 to p2
    return null
}

fun parseExtendedData(placemark: org.w3c.dom.Node): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val extendedDataNode = firstChildByLocalTagName(placemark, "extendeddata")
    if (extendedDataNode != null) {
        val dataNodes = findElementsByLocalTagName(extendedDataNode, "data")
        dataNodes.forEach { dataNode ->
            val nameAttr = dataNode.attributes?.getNamedItem("name")?.textContent?.trim()
            val valueNode = firstChildByLocalTagName(dataNode, "value")
            val value = valueNode?.textContent?.trim().orEmpty()
            if (nameAttr != null && value.isNotEmpty()) {
                result[nameAttr] = value
            }
        }
        val simpleDataNodes = findElementsByLocalTagName(extendedDataNode, "simpledata")
        simpleDataNodes.forEach { dataNode ->
            val nameAttr = dataNode.attributes?.getNamedItem("name")?.textContent?.trim()
            val value = dataNode.textContent?.trim().orEmpty()
            if (nameAttr != null && value.isNotEmpty()) {
                result[nameAttr] = value
            }
        }
    }
    return result
}

internal fun parseKmlContentStreaming(
    stream: InputStream,
    sourceName: String,
    projectId: String = "",
    mapping: NonExcelImportMapping? = null
): ParsedImportResult {
    val nodes = ArrayList<GisNode>(128)
    val routes = ArrayList<GisRoute>(128)
    var totalRouteLengthMeters = 0.0
    var geometryBlockCount = 0
    var placemarkCount = 0
    var coordinateBlocks = 0
    val base = sourceName.substringBeforeLast(".").take(12).uppercase(Locale.US)
    val parser = xmlPullParserFactory.newPullParser()
    parser.setInput(stream.reader())
    var currentPlacemark: StreamingPlacemark? = null
    var currentGeometryTag: String? = null
    var currentDataName: String? = null
    var currentSimpleDataName: String? = null
    var pendingText = ""

    fun localTag(name: String?): String = name.orEmpty().substringAfter(':').lowercase(Locale.US)

    fun finalizePlacemark(placemark: StreamingPlacemark) {
        if (placemark.geometryBlocks.isEmpty()) return
        placemarkCount += 1
        coordinateBlocks += placemark.geometryBlocks.size
        geometryBlockCount += placemark.geometryBlocks.size
        val routeDisplayName = placemark.name.ifBlank { "${base}_LINE_$placemarkCount" }
        val mappedKeys = mutableSetOf<String>()
        if (mapping != null) {
            if (mapping.positionField.isNotBlank()) mappedKeys.add(mapping.positionField)
            if (mapping.coordinateField?.isNotBlank() == true) mappedKeys.add(mapping.coordinateField)
            if (mapping.latitudeField?.isNotBlank() == true) mappedKeys.add(mapping.latitudeField)
            if (mapping.longitudeField?.isNotBlank() == true) mappedKeys.add(mapping.longitudeField)
            if (mapping.contractorField?.isNotBlank() == true) mappedKeys.add(mapping.contractorField)
            if (mapping.mapNumberField?.isNotBlank() == true) mappedKeys.add(mapping.mapNumberField)
            if (mapping.objectTypeField?.isNotBlank() == true) mappedKeys.add(mapping.objectTypeField)
            if (mapping.routeLengthField?.isNotBlank() == true) mappedKeys.add(mapping.routeLengthField)
            mappedKeys.addAll(mapping.itemFields)
        }
        val customFields = placemark.extendedData.filterKeys { it !in mappedKeys }
        val extractedContractor = if (mapping?.contractorField.isNullOrBlank()) {
            placemark.extendedData["contractor"].orEmpty()
        } else {
            when (mapping?.contractorField) {
                "UPLOAD" -> "UPLOAD"
                else -> placemark.extendedData[mapping?.contractorField].orEmpty()
            }
        }
        val extractedMapNumber = if (mapping?.mapNumberField.isNullOrBlank()) {
            placemark.name
        } else {
            when (mapping?.mapNumberField) {
                "MÃ£ tá»± sinh tá»« Ä‘á»‘i tÆ°á»£ng" -> placemark.name
                else -> placemark.extendedData[mapping?.mapNumberField].orEmpty()
            }
        }

        fun nodeCode(blockIndex: Int, pointIndex: Int): String =
            "${routeDisplayName}#pm${placemarkCount}_b${blockIndex + 1}_p${pointIndex + 1}"

        fun routeCode(blockIndex: Int, segmentIndex: Int): String =
            "${routeDisplayName}#pm${placemarkCount}_b${blockIndex + 1}_s${segmentIndex + 1}"

        fun extractedCode(defaultCode: String): String =
            if (mapping != null) {
                when (mapping.positionField) {
                    "TÃªn Ä‘á»‘i tÆ°á»£ng (Placemark)" -> placemark.name.ifBlank { defaultCode }
                    "Tá»± sinh mÃ£" -> defaultCode
                    else -> placemark.extendedData[mapping.positionField]?.ifBlank { placemark.name } ?: placemark.name.ifBlank { defaultCode }
                }
            } else {
                defaultCode
            }

        val materialSummary = buildString {
            if (mapping != null && mapping.itemFields.isNotEmpty()) {
                var hasItems = false
                mapping.itemFields.forEach { itemKey ->
                    val value = placemark.extendedData[itemKey]?.trim()
                    if (!value.isNullOrBlank()) {
                        if (!hasItems) {
                            append("Váº­t tÆ°:\n")
                            hasItems = true
                        }
                        append("  $itemKey: $value\n")
                    }
                }
            }
            if (customFields.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("ExtendedData:\n")
                customFields.forEach { (key, value) ->
                    append("$key: $value\n")
                }
            }
            if (placemark.description.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("Description: ${placemark.description}")
            }
        }.trim()

        placemark.geometryBlocks.forEachIndexed { blockIndex, block ->
            when (block.kind) {
                KmlGeometryKind.POINT -> {
                    val point = block.points.firstOrNull() ?: return@forEachIndexed
                    nodes += GisNode(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        code = extractedCode(nodeCode(blockIndex, 0)),
                        contractor = extractedContractor,
                        latitude = point.first,
                        longitude = point.second,
                        mapNumberLabel = extractedMapNumber,
                        materialSummary = materialSummary
                    )
                }
                KmlGeometryKind.LINE -> {
                    if (block.points.isEmpty()) return@forEachIndexed
                    block.points.forEachIndexed { pointIndex, point ->
                        nodes += GisNode(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            code = extractedCode(nodeCode(blockIndex, pointIndex)),
                            contractor = extractedContractor,
                            latitude = point.first,
                            longitude = point.second,
                            mapNumberLabel = extractedMapNumber,
                            materialSummary = materialSummary
                        )
                    }
                    for (pointIndex in 1 until block.points.size) {
                        totalRouteLengthMeters += haversineMeters(block.points[pointIndex], block.points[pointIndex - 1])
                        routes += GisRoute(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            code = routeCode(blockIndex, pointIndex - 1),
                            contractor = extractedContractor,
                            startNodeCode = extractedCode(nodeCode(blockIndex, pointIndex - 1)),
                            endNodeCode = extractedCode(nodeCode(blockIndex, pointIndex))
                        )
                    }
                }
            }
        }
    }

    while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                pendingText = ""
                when (localTag(parser.name)) {
                    "placemark" -> currentPlacemark = StreamingPlacemark()
                    "point", "linestring", "polygon" -> currentGeometryTag = localTag(parser.name)
                    "data" -> currentDataName = parser.getAttributeValue(null, "name")?.trim()
                    "simpledata" -> currentSimpleDataName = parser.getAttributeValue(null, "name")?.trim()
                }
            }
            org.xmlpull.v1.XmlPullParser.TEXT -> pendingText = parser.text.orEmpty()
            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                val tag = localTag(parser.name)
                val text = pendingText.trim()
                when (tag) {
                    "name" -> if (currentPlacemark != null && currentGeometryTag == null) {
                        currentPlacemark = currentPlacemark?.copy(name = text)
                    }
                    "description" -> currentPlacemark = currentPlacemark?.copy(description = stripKmlHtml(text))
                    "coordinates" -> {
                        val points = parseKmlCoordinates(text)
                        val block = when (currentGeometryTag) {
                            "point" -> points.firstOrNull()?.let { KmlGeometryBlock(KmlGeometryKind.POINT, listOf(it)) }
                            "linestring", "polygon" -> if (points.isEmpty()) null else KmlGeometryBlock(KmlGeometryKind.LINE, points)
                            else -> null
                        }
                        if (block != null) {
                            currentPlacemark?.geometryBlocks?.add(block)
                        }
                    }
                    "value" -> if (!currentDataName.isNullOrBlank() && text.isNotBlank()) {
                        currentPlacemark?.extendedData?.set(currentDataName!!, text)
                    }
                    "simpledata" -> {
                        if (!currentSimpleDataName.isNullOrBlank() && text.isNotBlank()) {
                            currentPlacemark?.extendedData?.set(currentSimpleDataName!!, text)
                        }
                        currentSimpleDataName = null
                    }
                    "data" -> currentDataName = null
                    "point", "linestring", "polygon" -> currentGeometryTag = null
                    "placemark" -> {
                        currentPlacemark?.let(::finalizePlacemark)
                        currentPlacemark = null
                    }
                }
                pendingText = ""
            }
        }
        parser.next()
    }

    val segmentCount = routes.size
    val summary = if (nodes.isEmpty() && routes.isEmpty()) {
        "KML parsed: $placemarkCount placemarks, $coordinateBlocks coordinate blocks; valid file but no supported geometry found"
    } else {
        "KML parsed: $placemarkCount placemarks, $geometryBlockCount geometry blocks, segments=$segmentCount, ${nodes.size} nodes, ${routes.size} edges, routeLength=${"%.2f".format(Locale.US, totalRouteLengthMeters)}m"
    }
    return ParsedImportResult(summary = summary, nodes = nodes, routes = routes, routeLengthMeters = totalRouteLengthMeters)
}

private data class StreamingPlacemark(
    val name: String = "",
    val description: String = "",
    val geometryBlocks: MutableList<KmlGeometryBlock> = mutableListOf(),
    val extendedData: MutableMap<String, String> = linkedMapOf()
)

data class XlsxTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

data class MergeRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
)

data class WorksheetScan(
    val rowMaps: HashMap<Int, MutableMap<Int, String>>,
    val merges: List<MergeRange>,
    val maxCol: Int
)

fun String.toPlainNumberOrNull(): Double? =
    trim()
        .replace(" ", "")
        .replace(",", ".")
        .toDoubleOrNull()

data class ImportedFileDraft(
    val fileName: String,
    val fileType: String,
    val storedPath: String,
    val summary: String,
    val suggestedNodes: List<GisNode> = emptyList(),
    val suggestedRoutes: List<GisRoute> = emptyList(),
    val routeLengthMeters: Double = 0.0
)

data class ParsedImportResult(
    val summary: String,
    val nodes: List<GisNode> = emptyList(),
    val routes: List<GisRoute> = emptyList(),
    val routeLengthMeters: Double = 0.0
)

data class ExcelColumnMapping(
    val positionColumn: String,
    val coordinateColumn: String? = null,
    val latitudeColumn: String? = null,
    val longitudeColumn: String? = null,
    val contractorColumn: String? = null,
    val mapNumberColumn: String? = null,
    val objectTypeColumn: String? = null,
    val classificationMode: ExcelClassificationMode = ExcelClassificationMode.AUTO,
    val itemColumns: List<String> = emptyList()
)

data class ExcelPreview(
    val fileName: String,
    val headers: List<String>,
    val sampleRows: List<Map<String, String>>,
    val suggestedMapping: ExcelColumnMapping? = null,
    val suggestedMappingConfidence: Int = 0,
    val sheets: List<String> = emptyList()
)

data class NonExcelPreview(
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val summary: String,
    val routeLengthMeters: Double = 0.0
)

data class NonExcelFieldCandidateSet(
    val positionOptions: List<String>,
    val coordinateOptions: List<String>,
    val latitudeOptions: List<String>,
    val longitudeOptions: List<String>,
    val contractorOptions: List<String>,
    val mapNumberOptions: List<String>,
    val objectTypeOptions: List<String>,
    val itemOptions: List<String>,
    val routeLengthOptions: List<String>
)

data class NonExcelFieldPreview(
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val summary: String,
    val routeLengthMeters: Double = 0.0,
    val candidates: NonExcelFieldCandidateSet,
    val sampleRows: List<Map<String, String>> = emptyList()
)

data class NonExcelImportMapping(
    val positionField: String,
    val coordinateField: String? = null,
    val latitudeField: String? = null,
    val longitudeField: String? = null,
    val contractorField: String? = null,
    val mapNumberField: String? = null,
    val objectTypeField: String? = null,
    val itemFields: List<String> = emptyList(),
    val routeLengthField: String? = null
)

data class ConfirmedFieldFlags(
    val positionField: Boolean = false,
    val coordinateField: Boolean = false,
    val latitudeField: Boolean = false,
    val longitudeField: Boolean = false,
    val contractorField: Boolean = false,
    val mapNumberField: Boolean = false,
    val objectTypeField: Boolean = false,
    val itemFields: Boolean = false,
    val routeLengthField: Boolean = false
)

enum class ObjectKind {
    AUTO, NODE, ROUTE
}

data class ExcelMappingSuggestion(
    val mapping: ExcelColumnMapping,
    val confidence: Int
)

enum class ExcelClassificationMode {
    AUTO,
    BY_OBJECT_TYPE_COLUMN,
    FORCE_NODE,
    FORCE_ROUTE
}

val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
val NON_ALNUM_SPACE_REGEX = Regex("[^a-z0-9]+")
val MULTI_SPACE_REGEX = Regex("\\s+")
val documentBuilderFactory: DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
val xmlPullParserFactory: XmlPullParserFactory by lazy {
    XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = false
    }
}
val ITEM_COLUMN_KEYWORDS = listOf(
    "khoi luong",
    "so luong",
    "hang muc",
    "vat tu",
    "chieu dai",
    "don gia",
    "cap",
    "camera",
    "tu",
    "cot",
    "be",
    "ong",
    "manhole",
    "material"
)
