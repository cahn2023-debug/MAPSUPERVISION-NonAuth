package com.mapsupervision.storage.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.storage.ProjectStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@Singleton
class UserFileImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: ProjectStorageManager
) {
    private val nonExcelDraftOverrides = HashMap<String, ImportedFileDraft>()

    fun inspectNonExcelFields(uri: Uri): NonExcelFieldPreview {
        val preview = inspectNonExcel(uri)
        val ext = preview.fileType.lowercase(Locale.US)
        val candidates = when (ext) {
            "kml", "kmz" -> {
                // Extract metadata keys from KML/KMZ
                val metadataKeys = extractKmlMetadata(uri)
                NonExcelFieldCandidateSet(
                    positionOptions = listOf("Tên đối tượng (Placemark)", "Tự sinh mã") + metadataKeys,
                    coordinateOptions = listOf("Geometry coordinates"),
                    latitudeOptions = emptyList(),
                    longitudeOptions = emptyList(),
                    contractorOptions = listOf("UPLOAD") + metadataKeys,
                    mapNumberOptions = listOf("Mã tự sinh từ đối tượng") + metadataKeys,
                    objectTypeOptions = listOf("Point/LineString/Polygon") + metadataKeys,
                    itemOptions = emptyList(),
                    routeLengthOptions = listOf("Chieu dai tuyen (tu tinh)")
                )
            }
            "geojson", "json" -> {
                // Extract metadata keys from GeoJSON
                val metadataKeys = extractGeoJsonMetadata(uri)
                NonExcelFieldCandidateSet(
                    positionOptions = listOf("properties.name", "properties.id", "Tự sinh mã") + metadataKeys,
                    coordinateOptions = listOf("geometry.coordinates"),
                    latitudeOptions = emptyList(),
                    longitudeOptions = emptyList(),
                    contractorOptions = listOf("properties.contractor", "UPLOAD") + metadataKeys,
                    mapNumberOptions = listOf("properties.mapNumber", "Mã tự sinh từ đối tượng") + metadataKeys,
                    objectTypeOptions = listOf("Point/LineString/Polygon") + metadataKeys,
                    itemOptions = emptyList(),
                    routeLengthOptions = listOf("Chieu dai tuyen (tu tinh)")
                )
            }
            else -> NonExcelFieldCandidateSet(
                positionOptions = listOf("Tự sinh mã"),
                coordinateOptions = emptyList(),
                latitudeOptions = emptyList(),
                longitudeOptions = emptyList(),
                contractorOptions = listOf("UNKNOWN"),
                mapNumberOptions = emptyList(),
                objectTypeOptions = emptyList(),
                itemOptions = emptyList(),
                routeLengthOptions = emptyList()
            )
        }
        val sampleRows = when (ext) {
            "kml", "kmz" -> extractKmlSampleRows(uri)
            "geojson", "json" -> extractGeoJsonSampleRows(uri)
            else -> emptyList()
        }
        return NonExcelFieldPreview(
            fileName = preview.fileName,
            fileType = preview.fileType,
            sizeBytes = preview.sizeBytes,
            summary = preview.summary,
            routeLengthMeters = preview.routeLengthMeters,
            candidates = candidates,
            sampleRows = sampleRows
        )
    }

    fun importNonExcelWithMapping(
        projectId: String,
        uri: Uri,
        mapping: NonExcelImportMapping,
        confirmed: ConfirmedFieldFlags
    ): ImportedFileDraft {
        if (!confirmed.positionField) {
            throw IllegalArgumentException("E_PARSE: position field must be confirmed")
        }
        persistReadPermission(uri)
        val name = resolveDisplayName(uri)?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("E_URI: missing display name")
        val ext = resolveFileExtension(uri, name)
        if (ext.isBlank()) throw IllegalArgumentException("E_URI: unsupported extension")

        val pendingFile = copyUriToImports(projectId, uri, name)
        val target = try {
            val parsedPreview = parseSummary(pendingFile, ext, name, projectId, mapping)
            moveImportFile(projectId, pendingFile, "processed") to parsedPreview
        } catch (e: Exception) {
            moveImportFile(projectId, pendingFile, "failed")
            throw e
        }
        val parsed = target.second
        val draft = ImportedFileDraft(
            fileName = name,
            fileType = ext,
            storedPath = target.first.absolutePath,
            summary = parsed.summary,
            suggestedNodes = parsed.nodes,
            suggestedRoutes = parsed.routes,
            routeLengthMeters = parsed.routeLengthMeters
        )
        val isKmlOrKmz = isKmlOrKmz(draft.fileType)
        val shouldIncludeGeometry = shouldIncludeNonExcelGeometry(draft.fileType, confirmed)
        
        val nodes = if (!shouldIncludeGeometry) {
            emptyList()
        } else {
            draft.suggestedNodes.map { node ->
                node.copy(
                    contractor = if (isKmlOrKmz) {
                        node.contractor
                    } else if (confirmed.contractorField) {
                        node.contractor
                    } else "",
                    mapNumberLabel = if (confirmed.mapNumberField) node.mapNumberLabel else "",
                    materialSummary = if (confirmed.itemFields) node.materialSummary else ""
                )
            }
        }
        val routes = if (!shouldIncludeGeometry) {
            emptyList()
        } else {
            draft.suggestedRoutes.map { route ->
                route.copy(
                    designLength = if (confirmed.routeLengthField) route.designLength else null
                )
            }
        }
        val mappedNodes = nodes
        val applied = confirmedFieldNames(confirmed).joinToString(",")
        val mappedDraft = draft.copy(
            summary = "${draft.summary}; confirmed=[$applied]",
            suggestedNodes = mappedNodes,
            suggestedRoutes = routes
        )
        // Don't store draft override for KML/KMZ files to ensure fresh import
        if (!isKmlOrKmz) {
            synchronized(nonExcelDraftOverrides) {
                nonExcelDraftOverrides[uri.toString()] = mappedDraft
            }
        }
        return mappedDraft
    }

    private fun isKmlOrKmz(fileType: String): Boolean =
        fileType.equals("kml", ignoreCase = true) || fileType.equals("kmz", ignoreCase = true)

    private fun shouldIncludeNonExcelGeometry(fileType: String, confirmed: ConfirmedFieldFlags): Boolean {
        if (isKmlOrKmz(fileType)) return true
        return confirmed.coordinateField || confirmed.latitudeField || confirmed.longitudeField
    }

    private fun confirmedFieldNames(confirmed: ConfirmedFieldFlags): List<String> = buildList {
        add("position")
        if (confirmed.coordinateField) add("coordinate")
        if (confirmed.latitudeField) add("latitude")
        if (confirmed.longitudeField) add("longitude")
        if (confirmed.contractorField) add("contractor")
        if (confirmed.mapNumberField) add("mapNumber")
        if (confirmed.objectTypeField) add("objectType")
        if (confirmed.itemFields) add("items")
        if (confirmed.routeLengthField) add("routeLength")
    }

    fun inspectNonExcel(uri: Uri): NonExcelPreview {
        persistReadPermission(uri)
        val name = resolveDisplayName(uri)?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("E_URI: missing display name")
        val ext = resolveFileExtension(uri, name)
        if (ext.isBlank()) throw IllegalArgumentException("E_URI: unsupported extension")
        if (ext == "xlsx" || ext == "xls") {
            throw IllegalArgumentException("E_PARSE: use excel preview for .$ext")
        }
        val temp = copyUriToTempFile(uri, name)
        val parsed = parseSummary(temp, ext, name, projectId = "")
        return NonExcelPreview(
            fileName = name,
            fileType = ext,
            sizeBytes = temp.length(),
            summary = parsed.summary,
            routeLengthMeters = parsed.routeLengthMeters
        )
    }

    fun inspectExcel(uri: Uri, sheetName: String? = null): ExcelPreview {
        persistReadPermission(uri)
        val name = resolveDisplayName(uri)?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("E_URI: missing display name")
        val ext = resolveFileExtension(uri, name)
        if (ext != "xlsx") throw IllegalArgumentException("E_PARSE: only .xlsx is supported in Excel parser")

        val temp = copyUriToTempFile(uri, name)
        val sheets = listExcelSheets(uri)
        val parsed = readXlsxTable(temp, sheetName)
        val headers = parsed.headers
        if (headers.isEmpty()) throw IllegalArgumentException("E_PARSE: excel has no header row")
        val suggested = autoDetectExcelMappingSuggestion(parsed)
        val sample = parsed.rows.take(5).map { row ->
            headers.mapIndexed { index, header -> header to row.getOrNull(index).orEmpty() }.toMap()
        }
        return ExcelPreview(
            fileName = name,
            headers = headers,
            sampleRows = sample,
            suggestedMapping = suggested?.mapping,
            suggestedMappingConfidence = suggested?.confidence ?: 0,
            sheets = sheets
        )
    }

    fun importExcelWithMapping(projectId: String, uri: Uri, mapping: ExcelColumnMapping, sheetName: String? = null): ImportedFileDraft {
        persistReadPermission(uri)
        val name = resolveDisplayName(uri)?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("E_URI: missing display name")
        val ext = resolveFileExtension(uri, name)
        if (ext != "xlsx") throw IllegalArgumentException("E_PARSE: only .xlsx is supported in Excel parser")

        val pending = copyUriToImports(projectId, uri, name)
        return try {
            val parsed = readXlsxTable(pending, sheetName)
            val processed = moveImportFile(projectId, pending, "processed")
            buildExcelDraftFromTable(projectId, name, processed.absolutePath, parsed, mapping, autoDetected = false)
        } catch (e: Exception) {
            moveImportFile(projectId, pending, "failed")
            throw e
        }
    }

    private fun buildExcelDraftFromTable(
        projectId: String,
        name: String,
        storedPath: String,
        parsed: XlsxTable,
        mapping: ExcelColumnMapping,
        autoDetected: Boolean
    ): ImportedFileDraft {
        val startedAtMs = System.currentTimeMillis()
        val headerIndex = HashMap<String, Int>(parsed.headers.size * 2)
        for (i in parsed.headers.indices) {
            headerIndex[parsed.headers[i].trim()] = i
        }

        val positionIdx = headerIndex[mapping.positionColumn.trim()]
            ?: throw IllegalArgumentException("E_PARSE: position column not found")
        val coordinateIdx = mapping.coordinateColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val latitudeIdx = mapping.latitudeColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val longitudeIdx = mapping.longitudeColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val contractorIdx = mapping.contractorColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val objectTypeIdx = mapping.objectTypeColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val mapNumberIdx = mapping.mapNumberColumn?.takeIf { it.isNotBlank() }?.let { headerIndex[it.trim()] }
        val itemColumnIndexList = ArrayList<Int>(mapping.itemColumns.size)
        val itemLabels = ArrayList<String>(mapping.itemColumns.size)
        for (rawColumn in mapping.itemColumns) {
            val normalizedColumn = rawColumn.trim()
            val idx = headerIndex[normalizedColumn] ?: continue
            itemColumnIndexList.add(idx)
            itemLabels.add(normalizedColumn)
        }
        val itemColumnIndexes = IntArray(itemColumnIndexList.size)
        for (i in itemColumnIndexList.indices) itemColumnIndexes[i] = itemColumnIndexList[i]

        val estimatedRows = parsed.rows.size
        val nodes = ArrayList<GisNode>(estimatedRows)
        val routes = ArrayList<GisRoute>(maxOf(16, estimatedRows / 2))
        val objectKindCache = mutableMapOf<String, ObjectKind>()
        val coordinateParseCache = HashMap<String, List<Pair<Double, Double>>>(1024)
        val flexibleNumberCache = HashMap<String, Double?>(1024)
        var skipped = 0
        var itemFilledCount = 0

        fun parseFlexibleNumberCached(raw: String?): Double? {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isEmpty()) return null
            return flexibleNumberCache.getOrPut(normalized) {
                if (normalized.indexOf(',') >= 0) normalized.replace(",", ".").toDoubleOrNull()
                else normalized.toDoubleOrNull()
            }
        }

        for (rowIndex in parsed.rows.indices) {
            val row = parsed.rows[rowIndex]
            val rowSize = row.size
            val codeRaw = if (positionIdx < rowSize) row[positionIdx].trim().uppercase() else ""
            if (codeRaw.isBlank()) continue

            val coordinateText = coordinateIdx?.let { idx ->
                if (idx < rowSize) row[idx].trim() else ""
            }.orEmpty()
            val parsedCoords = if (coordinateText.isNotEmpty()) {
                coordinateParseCache.getOrPut(coordinateText) { parseCoordinatesRobust(coordinateText) }
            } else {
                emptyList()
            }
            val firstCoord = parsedCoords.firstOrNull()
            val lat = firstCoord?.first
                ?: latitudeIdx?.let { idx -> parseFlexibleNumberCached(if (idx < rowSize) row[idx] else null) }
            val lon = firstCoord?.second
                ?: longitudeIdx?.let { idx -> parseFlexibleNumberCached(if (idx < rowSize) row[idx] else null) }

            if (lat == null || lon == null) {
                skipped++
                continue
            }
            val contractor = contractorIdx?.let { idx ->
                if (idx < rowSize) row[idx].trim() else ""
            }.orEmpty().ifBlank { "UNKNOWN" }
            val mapNumberLabel = mapNumberIdx?.let { idx ->
                if (idx < rowSize) row[idx].trim() else ""
            }.orEmpty()
            val objectType = objectTypeIdx?.let { idx ->
                if (idx < rowSize) row[idx].trim() else ""
            }.orEmpty()
            val kind = when (mapping.classificationMode) {
                ExcelClassificationMode.FORCE_NODE -> ObjectKind.NODE
                ExcelClassificationMode.FORCE_ROUTE -> ObjectKind.ROUTE
                ExcelClassificationMode.BY_OBJECT_TYPE_COLUMN -> objectKindCache.getOrPut(objectType) { parseObjectKind(objectType) }
                ExcelClassificationMode.AUTO -> objectKindCache.getOrPut(objectType) { parseObjectKind(objectType) }
            }
            for (i in itemColumnIndexes.indices) {
                val idx = itemColumnIndexes[i]
                if (idx < row.size && row[idx].isNotBlank()) itemFilledCount++
            }
            val materialSummary = if (itemColumnIndexes.isEmpty()) {
                ""
            } else {
                buildString {
                    for (i in itemColumnIndexes.indices) {
                        val idx = itemColumnIndexes[i]
                        if (idx >= rowSize) continue
                        val value = row[idx].trim()
                        if (value.isBlank()) continue
                        if (isNotEmpty()) append('\n')
                        append(itemLabels[i].trim())
                        append(": ")
                        append(value)
                    }
                }
            }
            val isRoute = kind == ObjectKind.ROUTE || (kind == ObjectKind.AUTO && parsedCoords.size > 1)
            if (isRoute) {
                val designLength = if (parsedCoords.size > 1) {
                    var routeLength = 0.0
                    for (pointIndex in 1 until parsedCoords.size) {
                        routeLength += haversineMeters(parsedCoords[pointIndex], parsedCoords[pointIndex - 1])
                    }
                    "%.2f".format(Locale.US, routeLength) + " m"
                } else null

                routes += GisRoute(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    code = codeRaw,
                    contractor = contractor,
                    startNodeCode = "",
                    endNodeCode = "",
                    points = parsedCoords,
                    designLength = designLength
                )
            } else {
                nodes += GisNode(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    code = codeRaw,
                    contractor = contractor,
                    latitude = lat,
                    longitude = lon,
                    mapNumberLabel = mapNumberLabel,
                    materialSummary = materialSummary
                )
            }
        }

        val summary = buildString {
            append(if (autoDetected) "XLSX auto-classified" else "XLSX mapped")
            append(": ${nodes.size} nodes")
            if (skipped > 0) append(", skipped $skipped rows (missing coordinates)")
            if (routes.isNotEmpty()) append(", ${routes.size} routes")
            if (itemLabels.isNotEmpty()) append(", items=${itemLabels.joinToString(";")}, itemCellsFilled=$itemFilledCount")
        }
        AppLogger.d(
            "perf.xlsx.map rows=${parsed.rows.size} headers=${parsed.headers.size} " +
                "nodes=${nodes.size} routes=${routes.size} skipped=$skipped " +
                "mapMs=${System.currentTimeMillis() - startedAtMs}"
        )
        return ImportedFileDraft(
            fileName = name,
            fileType = "xlsx",
            storedPath = storedPath,
            summary = summary,
            suggestedNodes = nodes,
            suggestedRoutes = routes
        )
    }

    fun importFile(projectId: String, uri: Uri): ImportedFileDraft {
        synchronized(nonExcelDraftOverrides) {
            val override = nonExcelDraftOverrides.remove(uri.toString())
            if (override != null) return override
        }
        persistReadPermission(uri)

        val name = resolveDisplayName(uri)?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("E_URI: missing display name")

        val ext = resolveFileExtension(uri, name)
        if (ext.isBlank()) throw IllegalArgumentException("E_URI: unsupported extension")

        val pending = copyUriToImports(projectId, uri, name)
        val parsed = try {
            parseSummary(pending, ext, name, projectId)
        } catch (e: Exception) {
            moveImportFile(projectId, pending, "failed")
            throw e
        }
        val processed = moveImportFile(projectId, pending, "processed")
        return ImportedFileDraft(
            fileName = name,
            fileType = ext,
            storedPath = processed.absolutePath,
            summary = parsed.summary,
            suggestedNodes = parsed.nodes,
            suggestedRoutes = parsed.routes,
            routeLengthMeters = parsed.routeLengthMeters
        )
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: return null).name
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        val lastSegment = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        if (!lastSegment.isNullOrBlank() && lastSegment.contains('.')) return lastSegment
        return null
    }

    private fun resolveFileExtension(uri: Uri, displayName: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        if (fromName.isNotBlank()) return fromName
        val mime = resolveMimeType(uri)?.lowercase(Locale.US).orEmpty()
        return when {
            mime.contains("kmz") -> "kmz"
            mime.contains("kml") -> "kml"
            mime.contains("geo+json") || mime.contains("json") -> "json"
            mime.contains("spreadsheetml") || mime.contains("excel") -> "xlsx"
            else -> uri.toString().substringAfterLast('.', "").lowercase(Locale.US)
        }
    }

    private fun resolveMimeType(uri: Uri): String? {
        if (uri.scheme == "file") {
            return when (File(uri.path ?: return null).extension.lowercase(Locale.US)) {
                "kmz" -> "application/vnd.google-earth.kmz"
                "kml" -> "application/vnd.google-earth.kml+xml"
                "geojson", "json" -> "application/geo+json"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "xls" -> "application/vnd.ms-excel"
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "doc" -> "application/msword"
                else -> null
            }
        }
        return context.contentResolver.getType(uri)
    }

    private fun parseSummary(
        file: File,
        ext: String,
        sourceName: String,
        projectId: String,
        mapping: NonExcelImportMapping? = null
    ): ParsedImportResult = runCatching {
        when (ext) {
            "kml" -> parseKml(file.inputStream(), sourceName, projectId, mapping)
            "kmz" -> parseKmz(file, sourceName, projectId, mapping)
            "geojson", "json" -> parseGeoJson(file, sourceName, mapping)
            "xlsx" -> parseXlsxDesign(file, sourceName, projectId = projectId)
            "xls" -> ParsedImportResult("Excel (.xls) imported; parser fallback (metadata only).")
            "docx" -> ParsedImportResult(parseDocx(file))
            "doc" -> ParsedImportResult("Word (.doc) imported; parser fallback (metadata only).")
            "pdf" -> ParsedImportResult(parsePdf(file))
            else -> ParsedImportResult("Imported file type .$ext")
        }
    }.getOrElse { throw IllegalStateException("E_PARSE: ${it.message}", it) }

    private fun parseKml(
        stream: java.io.InputStream,
        sourceName: String,
        projectId: String,
        mapping: NonExcelImportMapping? = null
    ): ParsedImportResult =
        parseKmlContentStreaming(stream, sourceName, projectId, mapping)

    private fun parseKmz(
        file: File,
        sourceName: String,
        projectId: String,
        mapping: NonExcelImportMapping? = null
    ): ParsedImportResult {
        return parseKmzContent(file, sourceName, projectId, mapping)
    }

    private fun parseGeoJson(
        file: File,
        sourceName: String,
        mapping: NonExcelImportMapping? = null
    ): ParsedImportResult {
        val text = file.readText(Charsets.UTF_8)
        val root = JSONObject(text)
        val features = when (root.optString("type")) {
            "FeatureCollection" -> root.optJSONArray("features") ?: JSONArray()
            "Feature" -> JSONArray().put(root)
            else -> JSONArray()
        }
        val nodes = mutableListOf<GisNode>()
        val routes = mutableListOf<GisRoute>()
        val localLineSegments = ArrayList<CollectedLineSegment>()
        var totalRouteLengthMeters = 0.0
        val base = sourceName.substringBeforeLast(".").take(12).uppercase(Locale.US)

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val geometryType = geometry.optString("type")
            val props = feature.optJSONObject("properties") ?: JSONObject()

            // Find all properties keys and their values as a Map
            val propertiesMap = mutableMapOf<String, String>()
            val keys = props.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = props.optString(key).trim()
                if (value.isNotEmpty()) {
                    propertiesMap[key] = value
                }
            }

            // Find which keys are mapped to Excel fields
            val mappedKeys = mutableSetOf<String>()
            if (mapping != null) {
                if (mapping.positionField.isNotBlank()) {
                    val cleanKey = mapping.positionField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.positionField)
                }
                if (mapping.coordinateField?.isNotBlank() == true) {
                    val cleanKey = mapping.coordinateField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.coordinateField)
                }
                if (mapping.contractorField?.isNotBlank() == true) {
                    val cleanKey = mapping.contractorField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.contractorField)
                }
                if (mapping.mapNumberField?.isNotBlank() == true) {
                    val cleanKey = mapping.mapNumberField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.mapNumberField)
                }
                if (mapping.objectTypeField?.isNotBlank() == true) {
                    val cleanKey = mapping.objectTypeField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.objectTypeField)
                }
                if (mapping.routeLengthField?.isNotBlank() == true) {
                    val cleanKey = mapping.routeLengthField.removePrefix("properties.")
                    mappedKeys.add(cleanKey)
                    mappedKeys.add(mapping.routeLengthField)
                }
                mapping.itemFields.forEach {
                    mappedKeys.add(it)
                    mappedKeys.add(it.removePrefix("properties."))
                }
            }

            val defaultCode = props.optString("name").ifBlank { props.optString("id").ifBlank { "$base-$i" } }

            val extractedCode = if (mapping != null) {
                val field = mapping.positionField
                val cleanField = field.removePrefix("properties.")
                when (field) {
                    "Tự sinh mã" -> defaultCode
                    else -> props.optString(field).ifBlank { props.optString(cleanField) }.ifBlank { defaultCode }
                }
            } else {
                defaultCode
            }

            val extractedContractor = if (mapping != null) {
                val field = mapping.contractorField
                if (field.isNullOrBlank()) {
                    ""
                } else {
                    val cleanField = field.removePrefix("properties.")
                    when (field) {
                        "UPLOAD" -> "UPLOAD"
                        else -> props.optString(field).ifBlank { props.optString(cleanField) }
                    }
                }
            } else {
                props.optString("contractor").ifBlank { "" }
            }

            val extractedMapNumber = if (mapping != null) {
                val field = mapping.mapNumberField
                if (field.isNullOrBlank()) {
                    ""
                } else {
                    val cleanField = field.removePrefix("properties.")
                    when (field) {
                        "Mã tự sinh từ đối tượng" -> defaultCode
                        else -> props.optString(field).ifBlank { props.optString(cleanField) }
                    }
                }
            } else {
                props.optString("mapNumber")
            }

            // Determine custom (unmapped) fields from properties
            val customFields = propertiesMap.filterKeys { it !in mappedKeys }

            // Build materialSummary
            val materialSummary = buildString {
                // Include mapped items first
                if (mapping != null && mapping.itemFields.isNotEmpty()) {
                    var hasItems = false
                    mapping.itemFields.forEach { itemKey ->
                        val cleanKey = itemKey.removePrefix("properties.")
                        val value = props.optString(itemKey).ifBlank { props.optString(cleanKey) }.trim()
                        if (value.isNotEmpty()) {
                            if (!hasItems) {
                                append("Vật tư:\n")
                                hasItems = true
                            }
                            append("  $cleanKey: $value\n")
                        }
                    }
                }
                
                // Include unmapped/custom fields (split to new fields)
                if (customFields.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("Thuộc tính khác:\n")
                    customFields.forEach { (key, value) ->
                        append("  $key: $value\n")
                    }
                }
            }.trim()

            when (geometryType) {
                "Point" -> {
                    val point = geometry.optJSONArray("coordinates") ?: continue
                    if (point.length() < 2) continue
                    val lon = point.optDouble(0, Double.NaN)
                    val lat = point.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    nodes += GisNode(
                        id = UUID.randomUUID().toString(),
                        projectId = "",
                        code = extractedCode,
                        contractor = extractedContractor,
                        latitude = lat,
                        longitude = lon,
                        mapNumberLabel = extractedMapNumber,
                        materialSummary = materialSummary
                    )
                }
                "LineString" -> {
                    val arr = geometry.optJSONArray("coordinates") ?: continue
                    val points = mutableListOf<Pair<Double, Double>>()
                    for (p in 0 until arr.length()) {
                        val item = arr.optJSONArray(p) ?: continue
                        if (item.length() < 2) continue
                        val lon = item.optDouble(0, Double.NaN)
                        val lat = item.optDouble(1, Double.NaN)
                        if (!lat.isNaN() && !lon.isNaN()) points += lat to lon
                    }
                    if (points.isNotEmpty()) {
                        localLineSegments.add(
                            CollectedLineSegment(
                                routeDisplayName = extractedCode,
                                contractor = extractedContractor,
                                mapNumber = extractedMapNumber,
                                materialSummary = "",
                                description = "",
                                points = points,
                                extendedData = propertiesMap,
                                customFields = customFields
                            )
                        )
                    }
                }
            }
        }
        totalRouteLengthMeters = mergeAndProcessLines(localLineSegments, "", mapping, nodes, routes, base)
        return ParsedImportResult(
            summary = "GeoJSON parsed: features=${features.length()}, nodes=${nodes.size}, routes=${routes.size}, routeLength=${"%.2f".format(Locale.US, totalRouteLengthMeters)}m",
            nodes = nodes,
            routes = routes,
            routeLengthMeters = totalRouteLengthMeters
        )
    }

    /**
     * Extracts distinct metadata field names from a KML or KMZ file.
     * Returns a list of keys found in <ExtendedData> sections (Data or SimpleData).
     */
    private fun extractKmlMetadata(uri: Uri): List<String> {
        // Resolve display name and copy to a temporary file for safe processing.
        val name = resolveDisplayName(uri) ?: return emptyList()
        val tempFile = copyUriToTempFile(uri, name)
        // If the source is a KMZ archive, unzip to locate the inner KML file.
        val kmlFile = if (name.lowercase(Locale.US).endsWith(".kmz")) {
            // Extract the first .kml entry found in the archive.
            val kmzTempDir = File(context.cacheDir, "kmz_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                ZipFile(tempFile).use { zip ->
                    var kmlEntry: java.util.zip.ZipEntry? = null
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (!e.isDirectory && e.name.lowercase(Locale.US).endsWith(".kml")) {
                            kmlEntry = e
                            break
                        }
                    }
                    if (kmlEntry == null) return emptyList()
                    val out = File(kmzTempDir, kmlEntry.name.substringAfterLast('/'))
                    zip.getInputStream(kmlEntry).use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    out
                }
            } catch (e: Exception) {
                AppLogger.e(e, "extractKmlMetadata: KMZ processing error")
                return emptyList()
            }
        } else {
            tempFile
        }

        return try {
            val doc = parseXml(kmlFile.inputStream())
            val nodeList = doc.getElementsByTagName("ExtendedData")
            val keys = linkedSetOf<String>()
            for (i in 0 until nodeList.length) {
                val ext = nodeList.item(i) as org.w3c.dom.Element
                // <Data name="..."> elements
                val dataNodes = ext.getElementsByTagName("Data")
                for (j in 0 until dataNodes.length) {
                    val dataElem = dataNodes.item(j) as org.w3c.dom.Element
                    val nameAttr = dataElem.getAttribute("name").trim()
                    if (nameAttr.isNotEmpty()) keys.add(nameAttr)
                }
                // <SimpleData name="..."> elements
                val simpleNodes = ext.getElementsByTagName("SimpleData")
                for (j in 0 until simpleNodes.length) {
                    val simpleElem = simpleNodes.item(j) as org.w3c.dom.Element
                    val nameAttr = simpleElem.getAttribute("name").trim()
                    if (nameAttr.isNotEmpty()) keys.add(nameAttr)
                }
            }
            keys.toList()
        } catch (e: Exception) {
            AppLogger.e(e, "extractKmlMetadata parsing error")
            emptyList()
        }
    }

    /**
     * Extracts distinct property keys from a GeoJSON (or generic .json) file.
     */
    private fun extractGeoJsonMetadata(uri: Uri): List<String> {
        val name = resolveDisplayName(uri) ?: return emptyList()
        val tempFile = copyUriToTempFile(uri, name)
        return try {
            val text = tempFile.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val features = when (root.optString("type")) {
                "FeatureCollection" -> root.optJSONArray("features") ?: JSONArray()
                "Feature" -> JSONArray().put(root)
                else -> JSONArray()
            }
            val keys = linkedSetOf<String>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val it = props.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k.isNotBlank()) keys.add(k)
                }
            }
            keys.toList()
        } catch (e: Exception) {
            AppLogger.e(e, "extractGeoJsonMetadata parsing error")
            emptyList()
        }
    }

    private fun extractKmlSampleRows(uri: Uri): List<Map<String, String>> {
        val name = resolveDisplayName(uri) ?: return emptyList()
        val tempFile = copyUriToTempFile(uri, name)
        val kmlFile = if (name.lowercase(Locale.US).endsWith(".kmz")) {
            val kmzTempDir = File(context.cacheDir, "kmz_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                ZipFile(tempFile).use { zip ->
                    var kmlEntry: java.util.zip.ZipEntry? = null
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (!e.isDirectory && e.name.lowercase(Locale.US).endsWith(".kml")) {
                            kmlEntry = e
                            break
                        }
                    }
                    if (kmlEntry == null) return emptyList()
                    val out = File(kmzTempDir, kmlEntry.name.substringAfterLast('/'))
                    zip.getInputStream(kmlEntry).use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    out
                }
            } catch (e: Exception) {
                return emptyList()
            }
        } else {
            tempFile
        }

        return try {
            val doc = parseXml(kmlFile.inputStream())
            val placemarks = findElementsByLocalTagName(doc.documentElement, "placemark")
            val samples = mutableListOf<Map<String, String>>()
            for (i in 0 until minOf(5, placemarks.size)) {
                val placemark = placemarks[i]
                val nameNode = firstChildByLocalTagName(placemark, "name")
                val pmName = nameNode?.textContent.orEmpty().trim()
                val extendedData = parseExtendedData(placemark)
                val row = mutableMapOf<String, String>()
                row["Tên đối tượng (Placemark)"] = pmName
                row["Tự sinh mã"] = pmName.ifBlank { "PM_${i + 1}" }
                row["Mã tự sinh từ đối tượng"] = pmName
                extendedData.forEach { (k, v) -> row[k] = v }
                samples.add(row)
            }
            samples
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractGeoJsonSampleRows(uri: Uri): List<Map<String, String>> {
        return try {
            val name = resolveDisplayName(uri) ?: return emptyList()
            val tempFile = copyUriToTempFile(uri, name)
            val text = tempFile.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val features = when (root.optString("type")) {
                "FeatureCollection" -> root.optJSONArray("features") ?: JSONArray()
                "Feature" -> JSONArray().put(root)
                else -> JSONArray()
            }
            val samples = mutableListOf<Map<String, String>>()
            val base = name.substringBeforeLast(".").take(12).uppercase(Locale.US)
            for (i in 0 until minOf(5, features.length())) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: JSONObject()
                val row = mutableMapOf<String, String>()
                val defaultCode = props.optString("name").ifBlank { props.optString("id").ifBlank { "$base-$i" } }
                row["properties.name"] = props.optString("name")
                row["properties.id"] = props.optString("id")
                row["Tự sinh mã"] = defaultCode
                row["properties.contractor"] = props.optString("contractor")
                row["properties.mapNumber"] = props.optString("mapNumber")
                row["Mã tự sinh từ đối tượng"] = defaultCode
                
                val keys = props.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    row[key] = props.optString(key)
                    row["properties.$key"] = props.optString(key)
                }
                samples.add(row)
            }
            samples
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseXlsxDesign(file: File, sourceName: String, projectId: String): ParsedImportResult {
        val table = readXlsxTable(file)
        if (table.headers.isEmpty()) return ParsedImportResult(parseXlsx(file))

        val mapping = autoDetectExcelMapping(table)
            ?: return ParsedImportResult("${parseXlsx(file)}; no GIS columns detected")

        val draft = buildExcelDraftFromTable(
            projectId = projectId,
            name = sourceName,
            storedPath = file.absolutePath,
            parsed = table,
            mapping = mapping,
            autoDetected = true
        )
        return ParsedImportResult(
            summary = draft.summary,
            nodes = draft.suggestedNodes,
            routes = draft.suggestedRoutes
        )
    }

    private fun parseXlsx(file: File): String {
        ZipFile(file).use { zip ->
            var sheetCount = 0
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (entry.name.startsWith("xl/worksheets/sheet")) sheetCount++
            }
            val shared = zip.getEntry("xl/sharedStrings.xml")
            val stringCount = if (shared != null) {
                zip.getInputStream(shared).bufferedReader().use { reader ->
                    countTokenInReader(reader, "<si")
                }
            } else 0
            return "XLSX parsed: $sheetCount sheets, $stringCount shared strings"
        }
    }


    private fun copyUriToTempFile(uri: Uri, name: String): File {
        val temp = File.createTempFile("xlsx_", "_$name", context.cacheDir)
        openUriInputStream(uri).use { input ->
            requireNotNull(input) { "E_URI: cannot open input stream" }
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        }
        return temp
    }

    private fun copyUriToImports(projectId: String, uri: Uri, name: String): File {
        val importsDir = File(storageManager.projectRoot(projectId), "imports/pending").apply { mkdirs() }
        val target = File(importsDir, "${UUID.randomUUID()}_$name")
        openUriInputStream(uri).use { input ->
            requireNotNull(input) { "E_URI: cannot open input stream" }
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun openUriInputStream(uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null
            return FileInputStream(file)
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun moveImportFile(projectId: String, file: File, bucket: String): File {
        val targetDir = File(storageManager.projectRoot(projectId), "imports/$bucket").apply { mkdirs() }
        val target = File(targetDir, file.name)
        if (file.absolutePath == target.absolutePath) return file
        if (target.exists()) {
            target.delete()
        }
        return if (file.renameTo(target)) target else file
    }


    fun listExcelSheets(uri: Uri): List<String> {
        persistReadPermission(uri)
        val name = resolveDisplayName(uri)?.trim().orEmpty()
        val temp = copyUriToTempFile(uri, name)
        return try {
            ZipFile(temp).use { zip ->
                val entry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
                val sheets = ArrayList<String>()
                zip.getInputStream(entry).use { input ->
                    val parser = xmlPullParserFactory.newPullParser()
                    parser.setInput(InputStreamReader(input, Charsets.UTF_8))
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                            val sheetName = parser.getAttributeValue(null, "name")
                            if (!sheetName.isNullOrBlank()) {
                                sheets.add(sheetName)
                            }
                        }
                        eventType = parser.next()
                    }
                }
                sheets
            }
        } catch (e: Exception) {
            AppLogger.e(e, "listExcelSheets failed")
            emptyList()
        } finally {
            temp.delete()
        }
    }

}

