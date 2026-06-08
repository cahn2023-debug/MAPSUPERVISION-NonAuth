package com.mapsupervision.gis.maplibre

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.location.Location
import android.location.LocationManager
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.gis.ui.GisMapBridge
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.MapLayerType
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MapRenderKey(
    val nodeSignature: Long,
    val routeSignature: Long,
    val nodeCount: Int,
    val routeCount: Int,
    val labelField: GisLabelField,
    val showNumberLabels: Boolean,
    val colorByContractor: Boolean,
    val contractorColorsHash: Int
)

internal fun buildMapRenderKey(
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    labelField: GisLabelField,
    showNumberLabels: Boolean,
    colorByContractor: Boolean,
    contractorColors: Map<String, String>
): MapRenderKey {
    return MapRenderKey(
        nodeSignature = nodes.stableNodeSignature(),
        routeSignature = routes.stableRouteSignature(),
        nodeCount = nodes.size,
        routeCount = routes.size,
        labelField = labelField,
        showNumberLabels = showNumberLabels,
        colorByContractor = colorByContractor,
        contractorColorsHash = contractorColors.hashCode()
    )
}

private fun List<GisNode>.stableNodeSignature(): Long {
    var signature = 1L
    for (node in this) {
        signature = signature.mix(node.id)
            .mix(node.code)
            .mix(node.contractor)
            .mix(node.latitude)
            .mix(node.longitude)
            .mix(node.mapNumberLabel)
            .mix(node.importedFileId)
    }
    return signature
}

private fun List<GisRoute>.stableRouteSignature(): Long {
    var signature = 1L
    for (route in this) {
        signature = signature.mix(route.id)
            .mix(route.code)
            .mix(route.contractor)
            .mix(route.startNodeCode)
            .mix(route.endNodeCode)
            .mix(route.importedFileId)
    }
    return signature
}

private fun Long.mix(value: Any?): Long = 31L * this + (value?.hashCode() ?: 0).toLong()

object MapBridgeInstaller {
    @JvmStatic
    fun install(context: android.content.Context) {
        runCatching {
            MapLibre.getInstance(context, "", WellKnownTileServer.MapLibre)
        }
        GisMapBridgeRegistry.bridge = MapLibreGisMapBridge()
    }
}

private class MapLibreGisMapBridge : GisMapBridge {
    companion object {
        private const val TAG = "MapLibreGisMapBridge"
        private const val NODES_SOURCE_ID = "nodes_source"
        private const val ROUTES_SOURCE_ID = "routes_source"
        private const val MEASURE_SOURCE_ID = "measure_source"
        private const val NODES_LAYER_ID = "nodes"
        private const val NODE_LABELS_LAYER_ID = "nodes_labels"
        private const val ROUTES_LAYER_ID = "routes"
        private const val MEASURE_LAYER_ID = "measure_line"
    }

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var map: MapLibreMap? = null
    private var appContext: Context? = null
    private var nodesSnapshot: List<GisNode> = emptyList()
    private var routesSnapshot: List<GisRoute> = emptyList()
    private var latestLabelField: GisLabelField = GisLabelField.CODE
    private var latestShowNumberLabels: Boolean = false
    private var latestColorByContractor: Boolean = true
    private var latestContractorColors: Map<String, String> = emptyMap()
    private var latestShowNodes: Boolean = true
    private var latestShowRoutes: Boolean = true
    private var measureEnabled: Boolean = false
    private var selectedNodeSnapshot: GisNode? = null
    private var selectedRouteSnapshot: GisRoute? = null
    private var measurePoints: MutableList<LatLng> = mutableListOf()
    private var onNodeClickCallback: ((GisNode) -> Unit)? = null
    private var onRouteClickCallback: ((GisRoute) -> Unit)? = null
    private var onMeasureDistanceCallback: ((Double) -> Unit)? = null
    private var clickListenerAttached = false
    private var didFitBoundsOnce = false
    private var lastLayerSignature: Int = 0
    private var lastMeasureSignature: Int = 0
    private var lastFocusedSelectionKey: String? = null
    private var lastRenderKey: MapRenderKey? = null
    private var mapUpdateJob: Job? = null
    private var styleEpoch: Int = 0
    private val missingSourceWarnings = mutableSetOf<String>()
    private val missingLayerWarnings = mutableSetOf<String>()

    @Composable
    override fun Render(
        styleJson: String,
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        showNumberLabels: Boolean,
        colorByContractor: Boolean,
        contractorColors: Map<String, String>,
        labelField: GisLabelField,
        showNodes: Boolean,
        showRoutes: Boolean,
        measureEnabled: Boolean,
        selectedNode: GisNode?,
        selectedRoute: GisRoute?,
        onNodeClick: (GisNode) -> Unit,
        onRouteClick: (GisRoute) -> Unit,
        onMeasureDistance: (Double) -> Unit
    ) {
        val context = LocalContext.current
        appContext = context.applicationContext
        nodesSnapshot = nodes
        routesSnapshot = routes
        latestShowNumberLabels = showNumberLabels
        latestColorByContractor = colorByContractor
        latestContractorColors = contractorColors
        latestLabelField = labelField
        latestShowNodes = showNodes
        latestShowRoutes = showRoutes
        this.measureEnabled = measureEnabled
        selectedNodeSnapshot = selectedNode
        selectedRouteSnapshot = selectedRoute
        onNodeClickCallback = onNodeClick
        onRouteClickCallback = onRouteClick
        onMeasureDistanceCallback = onMeasureDistance
        logSnapshot("Render", nodes, routes)

        val mapView = remember { 
            MapView(context).also { mv ->
                mv.onCreate(null)
                resetRuntimeState(keepSnapshots = true)
            }
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        mapView.onResume()
                        mapView.getMapAsync { loadedMap ->
                            loadedMap.getStyle {
                                map = loadedMap
                                val wasFitted = didFitBoundsOnce
                                resetRuntimeState(keepSnapshots = true)
                                didFitBoundsOnce = wasFitted
                                ensureClickListener(loadedMap)
                                updateMapData()
                                applyLayerVisibility()
                                updateMeasureGeoJson()
                            }
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                        // Prevent camera/data operations on stale map references.
                        map = null
                        clickListenerAttached = false
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDestroy()
                map = null
                resetRuntimeState(keepSnapshots = true)
            }
        }

        DisposableEffect(mapView) {
            mapView.getMapAsync { loadedMap ->
                if (map == null || loadedMap != map) {
                    bindMap(loadedMap, styleJson)
                }
            }
            onDispose {}
        }

        AndroidView(
            factory = { mapView },
            update = {
                // Explicitly reference all state-dependent variables inside the update block 
                // so Jetpack Compose knows it must re-run this block when the inputs change.
                val _style = styleJson
                val _nodes = nodes
                val _routes = routes
                val _showLabels = showNumberLabels
                val _colorBy = colorByContractor
                val _label = labelField
                val _showN = showNodes
                val _showR = showRoutes
                val _measure = measureEnabled

                val currentMap = map
                if (currentMap != null && currentMap.style != null) {
                    ensureClickListener(currentMap)
                    updateMapData()
                    applyLayerVisibility()
                    updateMeasureGeoJson()
                    if (!focusSelectionIfNeeded()) {
                        fitIfNeeded()
                    }
                } else {
                    it.getMapAsync { loadedMap ->
                        bindMap(loadedMap, styleJson)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    override fun zoomIn() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        mapRef.animateCamera(CameraUpdateFactory.zoomIn())
    }

    override fun zoomOut() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        mapRef.animateCamera(CameraUpdateFactory.zoomOut())
    }

    override fun fitToObjects() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val points = nodesSnapshot.filter(::isRenderableNode)
        if (points.isEmpty()) return
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        mapRef.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        Log.d(TAG, "fitToObjects success nodes=${points.size} latRange=%.5f..%.5f lonRange=%.5f..%.5f".format(minLat, maxLat, minLon, maxLon))
    }

    override fun centerOnMyLocation(): Boolean {
        val context = appContext ?: return false
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val best: Location? = providers
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        val point = best ?: return false
        val mapRef = map ?: return false
        if (mapRef.style == null) return false
        mapRef.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 15.5)
        )
        return true
    }

    override fun setLayerVisibility(showNodes: Boolean, showRoutes: Boolean) {
        latestShowNodes = showNodes
        latestShowRoutes = showRoutes
        applyLayerVisibility()
    }

    override fun setMeasureEnabled(enabled: Boolean) {
        measureEnabled = enabled
        if (!enabled) {
            measurePoints.clear()
            updateMeasureGeoJson()
        }
    }

    override fun setBaseMap(type: MapLayerType) {
        val mapRef = map ?: return
        val styleUri = when (type) {
            MapLayerType.STREET -> "asset://style_street.json"
            MapLayerType.SATELLITE -> "asset://style_satellite.json"
            MapLayerType.DARK -> "asset://style_dark.json"
        }
        styleEpoch++
        mapRef.setStyle(Style.Builder().fromUri(styleUri)) {
            resetRuntimeState(keepSnapshots = true)
            ensureStyleBindings(it)
            Log.d(TAG, "setStyle callback baseMap=$type epoch=$styleEpoch")
            ensureClickListener(mapRef)
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            if (!focusSelectionIfNeeded()) {
                fitIfNeeded()
            }
        }
    }

    override fun centerOnLocation(lat: Double, lng: Double, zoom: Double) {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        mapRef.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom)
        )
    }

    private fun ensureClickListener(map: MapLibreMap) {
        if (clickListenerAttached) return
        map.addOnMapClickListener { point ->
            if (measureEnabled) {
                onMeasureTap(point)
                return@addOnMapClickListener true
            }
            val screenPoint = map.projection.toScreenLocation(point)

            // Larger hitbox for nodes (easier to tap on mobile)
            val nodeHitBox = RectF(
                screenPoint.x - 44f,
                screenPoint.y - 44f,
                screenPoint.x + 44f,
                screenPoint.y + 44f
            )
            // Wider hitbox for routes (line is thin, needs generous tolerance)
            val routeHitBox = RectF(
                screenPoint.x - 28f,
                screenPoint.y - 28f,
                screenPoint.x + 28f,
                screenPoint.y + 28f
            )

            // Check nodes FIRST — nodes take priority over routes
            val clickedNodes = map.queryRenderedFeatures(nodeHitBox, "nodes")
            val nodeCode = clickedNodes.firstOrNull()?.getProperty("code")?.asString
            if (!nodeCode.isNullOrBlank()) {
                nodesSnapshot.firstOrNull { it.code == nodeCode }?.let {
                    onNodeClickCallback?.invoke(it)
                    return@addOnMapClickListener true
                }
            }

            // Then check routes
            val clickedRoutes = map.queryRenderedFeatures(routeHitBox, "routes")
            val routeCode = clickedRoutes.firstOrNull()?.getProperty("code")?.asString
            if (!routeCode.isNullOrBlank()) {
                routesSnapshot.firstOrNull { it.code == routeCode }?.let {
                    onRouteClickCallback?.invoke(it)
                    return@addOnMapClickListener true
                }
            }

            false
        }
        clickListenerAttached = true
    }

    private fun onMeasureTap(point: LatLng) {
        if (measurePoints.size >= 2) {
            measurePoints.clear()
        }
        measurePoints.add(point)
        updateMeasureGeoJson()
        if (measurePoints.size == 2) {
            val distM = haversineMeters(measurePoints[0], measurePoints[1])
            onMeasureDistanceCallback?.invoke(distM)
        }
    }

    private fun updateMapData() {
        val mapRef = map ?: return
        val style = mapRef.style ?: return
        if (!ensureStyleBindings(style)) return

        val renderKey = buildRenderKey(
            nodes = nodesSnapshot,
            routes = routesSnapshot,
            labelField = latestLabelField,
            showNumberLabels = latestShowNumberLabels,
            colorByContractor = latestColorByContractor,
            contractorColors = latestContractorColors
        )
        if (renderKey == lastRenderKey) {
            return
        }

        lastRenderKey = renderKey
        mapUpdateJob?.cancel()

        val localNodes = nodesSnapshot.toList()
        val localRoutes = routesSnapshot.toList()
        val localLabelField = latestLabelField
        val localShowNumberLabels = latestShowNumberLabels
        val localColorByContractor = latestColorByContractor
        val localContractorColors = latestContractorColors
        val localStyleEpoch = styleEpoch
        val localValidNodes = localNodes.filter(::isRenderableNode)

        mapUpdateJob = renderScope.launch {
            val renderPayload = withContext(Dispatchers.Default) {
                val nodeByCode = localValidNodes.associateBy { it.code.trim().uppercase() }

                val nodeFeatures = localValidNodes.map { node ->
                    Feature.fromGeometry(Point.fromLngLat(node.longitude, node.latitude)).apply {
                        addStringProperty("code", node.code)
                        addStringProperty("contractor", node.contractor)
                        addStringProperty(
                            "label",
                            if (!localShowNumberLabels) "" else formatNodeLabel(node, localLabelField)
                        )
                        addStringProperty(
                            "color",
                            if (localColorByContractor) colorForContractor(node.contractor, localContractorColors) else "#f97316"
                        )
                    }
                }

                var skippedRoutes = 0
                val routeFeatures = localRoutes.mapNotNull { r ->
                    val start = nodeByCode[r.startNodeCode.trim().uppercase()]
                    val end = nodeByCode[r.endNodeCode.trim().uppercase()]
                    if (start != null && end != null) {
                        Feature.fromGeometry(
                            LineString.fromLngLats(
                                listOf(
                                    Point.fromLngLat(start.longitude, start.latitude),
                                    Point.fromLngLat(end.longitude, end.latitude)
                                )
                            )
                        ).apply {
                            addStringProperty("code", r.code)
                        }
                    } else {
                        skippedRoutes++
                        null
                    }
                }

                RenderPayload(
                    nodeFeatureCollection = FeatureCollection.fromFeatures(nodeFeatures),
                    routeFeatureCollection = FeatureCollection.fromFeatures(routeFeatures),
                    nodeFeaturesBuilt = nodeFeatures.size,
                    routeFeaturesBuilt = routeFeatures.size,
                    skippedRoutes = skippedRoutes
                )
            }

            if (localStyleEpoch != styleEpoch) {
                Log.d(TAG, "updateMapData skipped stale render epoch=$localStyleEpoch current=$styleEpoch")
                return@launch
            }

            val currentStyle = map?.style ?: return@launch
            if (!ensureStyleBindings(currentStyle)) return@launch

            currentStyle.getSourceAs<GeoJsonSource>(NODES_SOURCE_ID)
                ?.setGeoJson(renderPayload.nodeFeatureCollection)
            currentStyle.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)
                ?.setGeoJson(renderPayload.routeFeatureCollection)
            Log.d(
                TAG,
                "updateMapData nodesIn=${localNodes.size} nodesValid=${localValidNodes.size} " +
                    "nodeFeatures=${renderPayload.nodeFeaturesBuilt} routesIn=${localRoutes.size} " +
                    "routeFeatures=${renderPayload.routeFeaturesBuilt} skippedRoutes=${renderPayload.skippedRoutes}"
            )
        }
    }

    private fun buildRenderKey(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        labelField: GisLabelField,
        showNumberLabels: Boolean,
        colorByContractor: Boolean,
        contractorColors: Map<String, String>
    ): MapRenderKey = buildMapRenderKey(nodes, routes, labelField, showNumberLabels, colorByContractor, contractorColors)

    private fun clearPendingMapUpdate() {
        mapUpdateJob?.cancel()
        mapUpdateJob = null
    }

    private fun updateMeasureGeoJson() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val currentMeasureSignature = measurePoints.fold(1) { acc, p ->
            31 * acc + p.latitude.hashCode() + p.longitude.hashCode()
        } * 31 + if (measureEnabled) 1 else 0
        if (currentMeasureSignature == lastMeasureSignature) return
        lastMeasureSignature = currentMeasureSignature
        mapRef.getStyle()?.let { style ->
            val source = style.getSourceAs<GeoJsonSource>(MEASURE_SOURCE_ID)
            if (source == null) {
                Log.w(TAG, "Missing source: $MEASURE_SOURCE_ID")
                return@let
            }
            val features = mutableListOf<Feature>()
            if (measurePoints.size == 2) {
                val line = LineString.fromLngLats(
                    measurePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                )
                features += Feature.fromGeometry(line).apply {
                    addNumberProperty("distance_m", haversineMeters(measurePoints[0], measurePoints[1]))
                }
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun applyLayerVisibility() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val signature = (if (latestShowNodes) 1 else 0) * 100 +
            (if (latestShowRoutes) 1 else 0) * 10 +
            (if (measureEnabled) 1 else 0)
        if (signature == lastLayerSignature) return
        lastLayerSignature = signature
        Log.d(TAG, "applyLayerVisibility showNodes=$latestShowNodes showRoutes=$latestShowRoutes measureEnabled=$measureEnabled")
        mapRef.getStyle()?.let { style ->
            val nodeVisibility = if (latestShowNodes) Property.VISIBLE else Property.NONE
            val routeVisibility = if (latestShowRoutes) Property.VISIBLE else Property.NONE
            style.getLayer(NODES_LAYER_ID)?.setProperties(PropertyFactory.visibility(nodeVisibility))
                ?: warnMissingLayerOnce(NODES_LAYER_ID)
            style.getLayer(NODE_LABELS_LAYER_ID)?.setProperties(PropertyFactory.visibility(nodeVisibility))
                ?: warnMissingLayerOnce(NODE_LABELS_LAYER_ID)
            style.getLayer(ROUTES_LAYER_ID)?.setProperties(PropertyFactory.visibility(routeVisibility))
                ?: warnMissingLayerOnce(ROUTES_LAYER_ID)
            style.getLayer(MEASURE_LAYER_ID)?.setProperties(
                PropertyFactory.visibility(if (measureEnabled) Property.VISIBLE else Property.NONE)
            ) ?: warnMissingLayerOnce(MEASURE_LAYER_ID)
        }
    }

    private fun formatNodeLabel(node: GisNode, labelField: GisLabelField): String = when (labelField) {
        GisLabelField.CODE -> compactLabel(node.mapNumberLabel.ifBlank { node.code })
        GisLabelField.CONTRACTOR -> compactLabel(node.contractor)
        GisLabelField.COORDINATE -> compactLabel("${"%.5f".format(node.latitude)},${"%.5f".format(node.longitude)}")
    }

    private fun compactLabel(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val firstNumber = Regex("""\d+""").find(trimmed)?.value
        if (!firstNumber.isNullOrBlank()) {
            return firstNumber.take(4)
        }
        return trimmed.take(3).uppercase()
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun colorForContractor(contractor: String, customColors: Map<String, String> = emptyMap()): String {
        customColors[contractor]?.let { return it }
        val palette = listOf("#f97316", "#22c55e", "#06b6d4", "#a855f7", "#ef4444", "#f59e0b", "#3b82f6")
        return palette[abs(contractor.hashCode()) % palette.size]
    }

    private fun fitIfNeeded() {
        if (!didFitBoundsOnce && nodesSnapshot.isNotEmpty()) {
            fitToObjects()
            didFitBoundsOnce = true
        }
    }

    private fun focusSelectionIfNeeded(): Boolean {
        val mapRef = map ?: return false
        if (mapRef.style == null) return false

        selectedNodeSnapshot?.let { node ->
            val key = "node:${node.id}:${node.latitude}:${node.longitude}"
            if (lastFocusedSelectionKey != key) {
                mapRef.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(node.latitude, node.longitude), 20.0)
                )
                lastFocusedSelectionKey = key
                didFitBoundsOnce = true
            }
            return true
        }

        selectedRouteSnapshot?.let { route ->
            val nodesByCode = nodesSnapshot.associateBy { it.code.trim().uppercase() }
            val startNode = nodesByCode[route.startNodeCode.trim().uppercase()]
            val endNode = nodesByCode[route.endNodeCode.trim().uppercase()]
            if (startNode != null && endNode != null) {
                val midLat = (startNode.latitude + endNode.latitude) / 2.0
                val midLng = (startNode.longitude + endNode.longitude) / 2.0
                val key = "route:${route.id}:${route.code}:${startNode.id}:${endNode.id}"
                if (lastFocusedSelectionKey != key) {
                    mapRef.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(midLat, midLng), 20.0)
                    )
                    lastFocusedSelectionKey = key
                    didFitBoundsOnce = true
                }
                return true
            }
        }

        lastFocusedSelectionKey = null
        return false
    }

    private fun bindMap(loadedMap: MapLibreMap, styleJson: String) {
        val styleBuilder = if (styleJson.startsWith("asset://")) {
            Style.Builder().fromUri(styleJson)
        } else {
            Style.Builder().fromJson(styleJson)
        }

        val currentStyle = loadedMap.style
        if (currentStyle != null && map == loadedMap) {
            // Already initialized with a style, just update data
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            return
        }

        map = loadedMap
        styleEpoch++
        
        // Only set default camera if we haven't moved yet
        if (!didFitBoundsOnce) {
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(13.8, 109.8))
                .zoom(4.8)
                .build()
        }

        loadedMap.setStyle(styleBuilder) {
            resetRuntimeState(keepSnapshots = true)
            ensureClickListener(loadedMap)
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            if (!focusSelectionIfNeeded()) {
                fitIfNeeded()
            }
        }
        ensureClickListener(loadedMap)
    }

    private fun resetRuntimeState(keepSnapshots: Boolean) {
        clearPendingMapUpdate()
        lastLayerSignature = 0
        lastMeasureSignature = 0
        lastRenderKey = null
        lastFocusedSelectionKey = null
        didFitBoundsOnce = false
        clickListenerAttached = false
        missingSourceWarnings.clear()
        missingLayerWarnings.clear()
        if (!keepSnapshots) {
            nodesSnapshot = emptyList()
            routesSnapshot = emptyList()
        }
    }

    private fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        return activityManager.isLowRamDevice || activityManager.memoryClass <= 192
    }

    private fun ensureStyleBindings(style: Style): Boolean {
        ensureSource(style, NODES_SOURCE_ID)
        ensureSource(style, ROUTES_SOURCE_ID)
        ensureSource(style, MEASURE_SOURCE_ID)
        ensureRouteLayer(style)
        ensureMeasureLayer(style)
        ensureNodeLayer(style)
        ensureNodeLabelsLayer(style)

        val ready = style.getSourceAs<GeoJsonSource>(NODES_SOURCE_ID) != null &&
            style.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID) != null &&
            style.getSourceAs<GeoJsonSource>(MEASURE_SOURCE_ID) != null &&
            style.getLayer(NODES_LAYER_ID) != null &&
            style.getLayer(NODE_LABELS_LAYER_ID) != null &&
            style.getLayer(ROUTES_LAYER_ID) != null &&
            style.getLayer(MEASURE_LAYER_ID) != null
        Log.d(TAG, "ensureStyleBindings ready=$ready")
        return ready
    }

    private fun ensureSource(style: Style, sourceId: String) {
        if (style.getSourceAs<GeoJsonSource>(sourceId) != null) return
        style.addSource(
            GeoJsonSource(
                sourceId,
                FeatureCollection.fromFeatures(emptyArray())
            )
        )
        Log.d(TAG, "ensureSource added=$sourceId")
    }

    private fun ensureRouteLayer(style: Style) {
        if (style.getLayer(ROUTES_LAYER_ID) != null) return
        style.addLayer(
            LineLayer(ROUTES_LAYER_ID, ROUTES_SOURCE_ID).withProperties(
                PropertyFactory.lineColor("#1a73e8"),
                PropertyFactory.lineWidth(4f)
            )
        )
        Log.d(TAG, "ensureLayer added=$ROUTES_LAYER_ID")
    }

    private fun ensureMeasureLayer(style: Style) {
        if (style.getLayer(MEASURE_LAYER_ID) != null) return
        style.addLayer(
            LineLayer(MEASURE_LAYER_ID, MEASURE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor("#ef4444"),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        )
        Log.d(TAG, "ensureLayer added=$MEASURE_LAYER_ID")
    }

    private fun ensureNodeLayer(style: Style) {
        if (style.getLayer(NODES_LAYER_ID) != null) return
        style.addLayer(
            CircleLayer(NODES_LAYER_ID, NODES_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(coalesce(get("color"), literal("#f97316"))),
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleStrokeColor("#ffffff"),
                PropertyFactory.circleStrokeWidth(3f)
            )
        )
        Log.d(TAG, "ensureLayer added=$NODES_LAYER_ID")
    }

    private fun ensureNodeLabelsLayer(style: Style) {
        if (style.getLayer(NODE_LABELS_LAYER_ID) != null) return
        style.addLayer(
            SymbolLayer(NODE_LABELS_LAYER_ID, NODES_SOURCE_ID).withProperties(
                PropertyFactory.textField("{label}"),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular", "Arial Unicode MS Regular")),
                PropertyFactory.textSize(11f),
                PropertyFactory.textOffset(arrayOf(0f, 0f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_CENTER),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textColor("#ffffff"),
                PropertyFactory.textHaloColor("#ffffff"),
                PropertyFactory.textHaloWidth(0f)
            )
        )
        Log.d(TAG, "ensureLayer added=$NODE_LABELS_LAYER_ID")
    }

    private fun warnMissingSourceOnce(sourceId: String) {
        if (missingSourceWarnings.add(sourceId)) {
            Log.w(TAG, "Missing source: $sourceId")
        }
    }

    private fun warnMissingLayerOnce(layerId: String) {
        if (missingLayerWarnings.add(layerId)) {
            Log.w(TAG, "Missing layer: $layerId")
        }
    }

    private fun logSnapshot(stage: String, nodes: List<GisNode>, routes: List<GisRoute>) {
        val summary = summarizeCoordinates(nodes)
        Log.d(
            TAG,
            "$stage nodes=${nodes.size} routes=${routes.size} nodesValid=${summary.validCount} invalidNodes=${summary.invalidCount} " +
                "latRange=${summary.latRangeText} lonRange=${summary.lonRangeText}"
        )
    }

    private data class RenderPayload(
        val nodeFeatureCollection: FeatureCollection,
        val routeFeatureCollection: FeatureCollection,
        val nodeFeaturesBuilt: Int,
        val routeFeaturesBuilt: Int,
        val skippedRoutes: Int
    )
}

internal fun isRenderableNode(node: GisNode): Boolean = isValidCoordinate(node.latitude, node.longitude)

internal fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
    return latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal data class CoordinateSummary(
    val validCount: Int,
    val invalidCount: Int,
    val latRangeText: String,
    val lonRangeText: String
)

internal fun summarizeCoordinates(nodes: List<GisNode>): CoordinateSummary {
    val validNodes = nodes.filter(::isRenderableNode)
    val invalidCount = nodes.size - validNodes.size
    if (validNodes.isEmpty()) {
        return CoordinateSummary(
            validCount = 0,
            invalidCount = invalidCount,
            latRangeText = "n/a",
            lonRangeText = "n/a"
        )
    }
    val minLat = validNodes.minOf { it.latitude }
    val maxLat = validNodes.maxOf { it.latitude }
    val minLon = validNodes.minOf { it.longitude }
    val maxLon = validNodes.maxOf { it.longitude }
    return CoordinateSummary(
        validCount = validNodes.size,
        invalidCount = invalidCount,
        latRangeText = "%.5f..%.5f".format(minLat, maxLat),
        lonRangeText = "%.5f..%.5f".format(minLon, maxLon)
    )
}
