package com.mapsupervision.photo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoScreen(viewModel: PhotoViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsState()
    val aiQuality by viewModel.lastAiPhotoQuality.collectAsState()
    val selectedForReview by viewModel.selectedPhotoForReview.collectAsState()
    val currentAiQuality = aiQuality
    val currentSelectedForReview = selectedForReview
    var objectCode by remember { mutableStateOf("NODE") }
    var engineer by remember { mutableStateOf("Engineer") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromGallery(uris, objectCode, engineer)
    }
    val availableTagOptions by viewModel.availableTagOptions.collectAsState()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quan ly hinh anh", style = MaterialTheme.typography.titleLarge)
        if (currentAiQuality != null) {
            Text(
                "AI chat luong anh: ${currentAiQuality.score}/100 - ${currentAiQuality.recommendation}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = objectCode, onValueChange = { objectCode = it }, label = { Text("Doi tuong") })
            OutlinedTextField(value = engineer, onValueChange = { engineer = it }, label = { Text("Ky su") })
        }

        if (!hasCameraPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Cap quyen Camera") }
        } else {
            CameraXCaptureView(
                createCaptureFile = { viewModel.createCaptureFile(objectCode) },
                onCaptured = { file -> viewModel.registerCapturedPhoto(file, objectCode, engineer) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!hasLocationPermission) {
                Button(onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text("Cap quyen Vi tri") }
            }
            Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Nhap tu Thu vien") }
            Button(onClick = { viewModel.addDemoPhoto(objectCode, engineer) }) { Text("Them Demo") }
            Button(onClick = { viewModel.refresh() }) { Text("Lam moi") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = statusFilter == "ALL",
                onClick = { statusFilter = "ALL" },
                label = { Text("Tất cả") }
            )
            FilterChip(
                selected = statusFilter == "MATCHED",
                onClick = { statusFilter = "MATCHED" },
                label = { Text("Khớp") }
            )
            FilterChip(
                selected = statusFilter == "UNMATCHED",
                onClick = { statusFilter = "UNMATCHED" },
                label = { Text("Chưa khớp") }
            )
        }

        val matchedPhotos = photos.filter { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() }
        val unmatchedPhotos = photos.filterNot { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() }
        val visiblePhotos = when (statusFilter) {
            "MATCHED" -> matchedPhotos
            "UNMATCHED" -> unmatchedPhotos
            else -> photos
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ảnh đã khớp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (matchedPhotos.isEmpty()) {
                    Text("Chưa có ảnh khớp tag/thời gian.", style = MaterialTheme.typography.bodySmall)
                } else {
                    matchedPhotos.take(10).forEach { photo ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | tag=${photo.tagCodesCsv.ifBlank { "-" }} | offset=${photo.matchingTimeOffsetMs / 60000}m", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ảnh chưa khớp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                if (unmatchedPhotos.isEmpty()) {
                    Text("Không có ảnh chưa khớp.", style = MaterialTheme.typography.bodySmall)
                } else {
                    unmatchedPhotos.take(10).forEach { photo ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | ${photo.filePath}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visiblePhotos) { photo ->
                ElevatedCard(
                    onClick = { viewModel.selectPhotoForReview(photo.id) },
                    colors = CardDefaults.elevatedCardColors(containerColor = if (photo.id == selectedForReview?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            MatchBadge(photo = photo)
                            Text("${photo.objectCode} | ${photo.filePath}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("tag=${photo.tagCodesCsv.ifBlank { "-" }} | offset=${photo.matchingTimeOffsetMs / 60000}m", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (currentSelectedForReview != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearPhotoReviewSelection() },
            title = { Text("Chỉnh ảnh đối chiếu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val selectedPhoto = currentSelectedForReview
                    Text("Ảnh: ${selectedPhoto.objectCode}", style = MaterialTheme.typography.bodyMedium)
                    var offsetText by remember(selectedPhoto.id, selectedPhoto.matchingTimeOffsetMs) { mutableStateOf((selectedPhoto.matchingTimeOffsetMs / 60000).toString()) }
                    Text("Chọn node/tuyến", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTagOptions.forEach { tag ->
                            val selected = selectedPhoto.tagCodesCsv.split(',').map { it.trim() }.any { it == tag }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleSelectedPhotoTag(tag) },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Text(
                        text = "Đã chọn: " + (selectedPhoto.tagCodesCsv.ifBlank { "Chưa chọn" }),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = {
                            offsetText = it
                            val minutes = it.toIntOrNull() ?: 0
                            viewModel.updateSelectedPhotoOffsetMinutes(minutes)
                        },
                        label = { Text("Offset thời gian (phút)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Giờ khớp: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(selectedPhoto.matchedAtEpochMs.takeIf { it > 0L } ?: selectedPhoto.capturedAtEpochMs))}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveSelectedPhotoReview()
                    viewModel.clearPhotoReviewSelection()
                }) { Text("Lưu") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.clearPhotoReviewSelection() }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun MatchBadge(photo: com.mapsupervision.domain.model.SitePhoto) {
    val isMatched = photo.matchedNodeCode != null || photo.matchedRouteCode != null || photo.tagCodesCsv.isNotBlank()
    val text = if (isMatched) "Đã khớp" else "Chưa khớp"
    val bg = if (isMatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    val fg = if (isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun CameraXCaptureView(
    createCaptureFile: () -> File?,
    onCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                val updatedRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
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

    DisposableEffect(lifecycleOwner, preview, imageCapture) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val runnable = Runnable {
            val cameraProvider = providerFuture.get()
            preview.surfaceProvider = previewView.surfaceProvider
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
        }
        providerFuture.addListener(runnable, executor)

        onDispose {
            runCatching {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        Button(onClick = {
            val file = createCaptureFile() ?: return@Button
            val output = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.targetRotation = targetRotation
            imageCapture.takePicture(
                output,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onCaptured(file)
                    }

                    override fun onError(exception: ImageCaptureException) = Unit
                }
            )
        }) {
            Text("Chup anh")
        }
    }
}
