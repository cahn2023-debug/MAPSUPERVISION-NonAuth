package com.mapsupervision.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapsupervision.domain.service.IPhotoLocationProvider
import com.mapsupervision.domain.service.IPhotoPipelineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Suppress("DEPRECATION")
@Composable
fun CameraOverlay(
    nodeCode: String,
    projectId: String,
    photoPipelineService: IPhotoPipelineService,
    locationProvider: IPhotoLocationProvider,
    onPhotoCaptured: () -> Unit,
    onSavePhoto: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Request WRITE_EXTERNAL_STORAGE on Android <= 9 (API 28) for saving to Downloads
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless — fallback to internal if denied */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            val writeGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!writeGranted) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    var noteText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var stampEnabled by remember { mutableStateOf(true) }
    var bearing by remember { mutableStateOf(0f) }

    // Live location for stamp preview
    var liveLocation by remember { mutableStateOf<com.mapsupervision.domain.model.PhotoLocationSnapshot?>(null) }
    var liveAddress by remember { mutableStateOf("") }
    LaunchedEffect(stampEnabled) {
        if (stampEnabled) {
            val snapshot = withContext(Dispatchers.IO) {
                val loc = locationProvider.lastKnownLocation()
                val lat = loc.latitude
                val lng = loc.longitude
                loc to if (lat != null && lng != null) reverseGeocode(context, lat, lng) else ""
            }
            liveLocation = snapshot.first
            liveAddress = snapshot.second
        }
    }

    // Read compass bearing from sensor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    bearing = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    if (bearing < 0) bearing += 360f
                } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                    bearing = event.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Close camera overlay immediately so user doesn't wait
            onPhotoCaptured()
            onDismiss()

            coroutineScope.launch {
                uris.forEach { uri ->
                    runCatching {
                        val file = withContext(Dispatchers.IO) {
                            photoPipelineService.importFromGallery(context, projectId, nodeCode, "Field", uri)
                        }
                        // Gallery photos: never apply stamp (per requirement)
                        onSavePhoto(file)
                    }
                }
            }
        }
    }

    val previewView = remember { PreviewView(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var targetRotation by remember { mutableStateOf(Surface.ROTATION_0) }

    DisposableEffect(context, previewView, preview, imageCapture) {
                targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        preview.targetRotation = targetRotation
        imageCapture.targetRotation = targetRotation

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val updatedRotation = orientationToSurfaceRotation(orientation)
                if (updatedRotation != targetRotation) {
                    targetRotation = updatedRotation
                    preview.targetRotation = updatedRotation
                    imageCapture.targetRotation = updatedRotation
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose { orientationListener.disable() }
    }

    if (hasCameraPermission) {
        DisposableEffect(lifecycleOwner, preview, imageCapture) {
            val future = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            future.addListener({
                val provider = future.get()
                preview.surfaceProvider = previewView.surfaceProvider
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }, executor)
            onDispose {
                runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            // Permission request screen
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Cần quyền truy cập Camera", color = Color.White, fontWeight = FontWeight.Bold)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Cấp quyền Camera")
                }
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Hủy")
                }
            }
        } else {
            // Camera preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Stamp preview overlay — mirrors exactly what PhotoStampRenderer draws on the photo
            if (stampEnabled) {
                val stampTime = remember(Unit) {
                    java.text.SimpleDateFormat("HH:mm  dd/MM/yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
                val locationText = when {
                    liveAddress.isNotBlank() -> liveAddress
                    liveLocation?.latitude != null && liveLocation?.longitude != null ->
                        "${"%.5f".format(liveLocation!!.latitude)}, ${"%.5f".format(liveLocation!!.longitude)}"
                    else -> "Đang lấy vị trí..."
                }
                val lat = liveLocation?.latitude
                val lng = liveLocation?.longitude

                // Load OSM tile bitmap for real map background
                var tileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(lat, lng) {
                    if (lat != null && lng != null) {
                        tileBitmap = withContext(Dispatchers.IO) {
                            loadOsmTileBitmap(lat, lng, zoom = 17)
                        }
                    }
                }

                val stampRotation = rotationToDegrees(targetRotation).toFloat()
                val (mapAlignment, mapModifier) = when (targetRotation) {
                    Surface.ROTATION_90 -> Alignment.BottomEnd to Modifier.padding(end = 10.dp, bottom = 150.dp)
                    Surface.ROTATION_180 -> Alignment.TopEnd to Modifier.padding(end = 10.dp, top = 150.dp)
                    Surface.ROTATION_270 -> Alignment.TopStart to Modifier.padding(start = 10.dp, top = 150.dp)
                    else -> Alignment.BottomStart to Modifier.padding(start = 10.dp, bottom = 150.dp)
                }
                val (pillAlignment, pillModifier) = when (targetRotation) {
                    Surface.ROTATION_90 -> Alignment.TopEnd to Modifier.padding(end = 10.dp, top = 150.dp)
                    Surface.ROTATION_180 -> Alignment.TopStart to Modifier.padding(start = 10.dp, top = 150.dp)
                    Surface.ROTATION_270 -> Alignment.BottomStart to Modifier.padding(start = 10.dp, bottom = 150.dp)
                    else -> Alignment.BottomEnd to Modifier.padding(end = 10.dp, bottom = 150.dp)
                }

                Box(modifier = Modifier.fillMaxSize()) {

                // Bottom-left: minimap with real OSM tile
                val mapSizeDp = 100.dp
                androidx.compose.foundation.Canvas(
                    modifier = mapModifier
                        .align(mapAlignment)
                        .size(mapSizeDp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .graphicsLayer { rotationZ = stampRotation }
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val scale = w / 300f

                    drawIntoCanvas { composeCanvas ->
                        val nc = composeCanvas.nativeCanvas

                        // Draw real OSM tile if loaded, else fallback background
                        val tile = tileBitmap
                        if (tile != null) {
                            // Crop center square from tile and draw
                            val tileW = tile.width.toFloat()
                            val tileH = tile.height.toFloat()
                            // Calculate pixel offset of our location within the tile
                            val (tileX, tileY) = if (lat != null && lng != null) {
                                osmTilePixelOffset(lat, lng, zoom = 17, tileSize = tileW.toInt())
                            } else Pair(tileW / 2f, tileH / 2f)
                            // Source rect centered on our location
                            val half = minOf(tileW, tileH) / 2f
                            val srcLeft = (tileX - half).coerceIn(0f, tileW - 1f)
                            val srcTop  = (tileY - half).coerceIn(0f, tileH - 1f)
                            val srcRight  = (srcLeft + minOf(tileW, tileH)).coerceAtMost(tileW)
                            val srcBottom = (srcTop  + minOf(tileW, tileH)).coerceAtMost(tileH)
                            val src = android.graphics.Rect(srcLeft.toInt(), srcTop.toInt(), srcRight.toInt(), srcBottom.toInt())
                            val dst = android.graphics.RectF(0f, 0f, w, h)
                            nc.drawBitmap(tile, src, dst, null)
                        } else {
                            // Fallback: OSM-style drawn background
                            nc.drawColor(android.graphics.Color.argb(255, 242, 239, 233))
                            val roadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE
                                strokeWidth = 5f * scale
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                style = android.graphics.Paint.Style.STROKE
                            }
                            nc.drawLine(6f * scale, cy - 6f * scale, w - 6f * scale, cy + 6f * scale, roadPaint)
                            nc.drawLine(cx + 5f * scale, 6f * scale, cx - 5f * scale, h - 6f * scale, roadPaint)
                        }

                        // Bearing cone
                        val bearingRad = Math.toRadians(bearing.toDouble()).toFloat()
                        val coneAngle = Math.toRadians(45.0).toFloat()
                        val coneLen = w * 0.42f
                        val conePath = android.graphics.Path().apply {
                            moveTo(cx, cy)
                            val leftAngle = bearingRad - coneAngle / 2
                            lineTo(cx + kotlin.math.sin(leftAngle) * coneLen, cy - kotlin.math.cos(leftAngle) * coneLen)
                            val sweepDeg = Math.toDegrees(coneAngle.toDouble()).toFloat()
                            val startDeg = Math.toDegrees((bearingRad - coneAngle / 2).toDouble()).toFloat() - 90f
                            arcTo(android.graphics.RectF(cx - coneLen, cy - coneLen, cx + coneLen, cy + coneLen), startDeg, sweepDeg)
                            close()
                        }
                        nc.drawPath(conePath, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(100, 255, 200, 0)
                        })

                        // Location dot — glow + red + white
                        nc.drawCircle(cx, cy, 18f * scale, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(60, 220, 50, 50)
                        })
                        nc.drawCircle(cx, cy, 12f * scale, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(255, 220, 50, 50)
                        })
                        nc.drawCircle(cx, cy, 5f * scale, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                        })

                        // Border
                        nc.drawRoundRect(
                            android.graphics.RectF(0f, 0f, w, h), 10f * scale, 10f * scale,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb(200, 25, 110, 190)
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 3f * scale
                            })
                    }
                }

                // Bottom-right: pills column aligned to right edge
                Column(
                    modifier = pillModifier
                        .align(pillAlignment)
                        .graphicsLayer { rotationZ = stampRotation },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StampPill(icon = "⏱", text = stampTime)
                    StampPill(icon = "📍", text = locationText)
                    if (noteText.isNotBlank()) {
                        StampPill(icon = "📝", text = noteText)
                    }
                }
            }

            // Node info overlay at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = if (nodeCode.isBlank()) "Chụp ảnh hiện trường" else "Đối tượng: $nodeCode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                // Stamp toggle in center
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (stampEnabled) Color(0xFF64B5F6) else Color(0x88FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Stamp",
                        color = if (stampEnabled) Color(0xFF64B5F6) else Color(0x88FFFFFF),
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = stampEnabled,
                        onCheckedChange = { stampEnabled = it },
                        modifier = Modifier.size(width = 44.dp, height = 24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1E78C8),
                            uncheckedThumbColor = Color(0xAAFFFFFF),
                            uncheckedTrackColor = Color(0x44FFFFFF)
                        )
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = Color.White)
                }
            }

            // Bottom controls: note input + gallery + shutter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Note input field
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Ghi chú (tùy chọn)...", color = Color(0xAAFFFFFF), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1E78C8),
                        unfocusedBorderColor = Color(0x88FFFFFF),
                        cursorColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { galleryLauncher.launch("image/*") }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Photo,
                            contentDescription = "Thêm từ máy",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Thêm ảnh", color = Color.White, fontSize = 11.sp)
                    }

                    // Shutter button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                focusManager.clearFocus()
                                val capturedNote    = noteText
                                val capturedBearing = bearing
                                val capturedStamp   = stampEnabled
                                val file = photoPipelineService.createCaptureOutputFile(projectId, nodeCode)
                                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                                imageCapture.targetRotation = targetRotation
                                imageCapture.takePicture(
                                    output,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                            // Close UI immediately when raw photo is ready
                                            onPhotoCaptured()
                                            onDismiss()
                                            
                                            // Process watermark and save to database in background coroutine
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    if (capturedStamp) {
                                                        val loc = locationProvider.lastKnownLocation()
                                                        val lat = loc.latitude
                                                        val lng = loc.longitude
                                                        val address = if (lat != null && lng != null)
                                                            reverseGeocode(context, lat, lng)
                                                        else ""
                                                        photoPipelineService.applyStamp(
                                                            file = file,
                                                            latitude = lat,
                                                            longitude = lng,
                                                            address = address,
                                                            note = capturedNote,
                                                            bearingDeg = capturedBearing
                                                        )
                                                    }
                                                }
                                                onSavePhoto(file)
                                            }
                                        }
                                        override fun onError(e: ImageCaptureException) { onDismiss() }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0E0E0))
                        )
                    }

                    // Spacer to balance layout
                    Spacer(Modifier.width(80.dp))
                }
            }
        }
    }
}
}

@androidx.compose.runtime.Composable
private fun StampPill(icon: String, text: String) {
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .background(
                Color(0xCC1964BE),
                androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.material3.Text(icon, fontSize = 13.sp)
        androidx.compose.material3.Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Download a single OSM tile bitmap for the given lat/lng at the given zoom level. */
private fun loadOsmTileBitmap(lat: Double, lng: Double, zoom: Int): android.graphics.Bitmap? {
    return try {
        val n = 1 shl zoom
        val xTile = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val yTile = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
        val url = java.net.URL("https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png")
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MapSupervision/1.0 (Android)")
            connectTimeout = 4000
            readTimeout = 4000
        }
        if (conn.responseCode == 200) {
            android.graphics.BitmapFactory.decodeStream(conn.inputStream)
        } else null
    } catch (_: Exception) { null }
}

/**
 * Returns the pixel (x, y) within a [tileSize]×[tileSize] OSM tile
 * that corresponds to the given lat/lng at the given zoom level.
 */
private fun osmTilePixelOffset(lat: Double, lng: Double, zoom: Int, tileSize: Int): Pair<Float, Float> {
    val n = 1 shl zoom
    val xFrac = (lng + 180.0) / 360.0 * n
    val latRad = Math.toRadians(lat)
    val yFrac = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
    val px = ((xFrac - xFrac.toInt()) * tileSize).toFloat()
    val py = ((yFrac - yFrac.toInt()) * tileSize).toFloat()
    return Pair(px, py)
}

private fun orientationToSurfaceRotation(orientation: Int): Int {
    return when (orientation) {
        in 45..134 -> Surface.ROTATION_270
        in 135..224 -> Surface.ROTATION_180
        in 225..314 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}

private fun rotationToDegrees(rotation: Int): Int {
    return when (rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}

private fun reverseGeocode(context: android.content.Context, lat: Double, lng: Double): String {
    // 1. Try Android Native Geocoder (Google Play Services powered, fast & offline caching supported)
    if (android.location.Geocoder.isPresent()) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.thoroughfare ?: address.subThoroughfare ?: ""
                val ward = address.subLocality ?: ""
                val district = address.locality ?: address.subAdminArea ?: ""
                val city = address.adminArea ?: ""
                
                val parts = listOf(street, ward, district, city).filter { it.isNotBlank() }
                if (parts.isNotEmpty()) {
                    return parts.joinToString(", ").take(60)
                }
                val featureName = address.featureName
                if (!featureName.isNullOrBlank()) return featureName.take(60)
            }
        } catch (e: Exception) {
            com.mapsupervision.core.logging.AppLogger.e(e, "reverseGeocode.native_failed lat=$lat lng=$lng")
        }
    }

    // 2. Fallback to optimized Nominatim HTTP request with quick 1.2-second timeout
    return try {
        val url = java.net.URL(
            "https://nominatim.openstreetmap.org/reverse?lat="
        )
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MapSupervision/1.0")
            connectTimeout = 1200
            readTimeout = 1200
        }
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().readText()
            val key = "\"display_name\":"
            val start = body.indexOf(key)
            if (start >= 0) {
                val valueStart = body.indexOf('"', start + key.length) + 1
                val valueEnd = body.indexOf('"', valueStart)
                body.substring(valueStart, valueEnd).take(60)
            } else ""
        } else ""
    } catch (_: Exception) { "" }
}
