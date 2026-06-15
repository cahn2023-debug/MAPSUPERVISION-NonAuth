package com.mapsupervision.photo.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.mapsupervision.data.mlkit.DailyLogDataResult
import com.mapsupervision.data.mlkit.MaterialDataResult
import com.mapsupervision.data.mlkit.MlKitScannerService
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class PhotoPipelineService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val storageManager: ProjectStorageManager,
    private val mlKitScannerService: MlKitScannerService
) : IPhotoPipelineService {
    companion object {
        private const val mainJpegQuality = 78
        private const val thumbJpegQuality = 72
        private const val thumbLongEdgePx = 320
    }

    override fun createCaptureOutputFile(
        projectId: String,
        objectCode: String,
        folderType: CaptureFolderType
    ): File {
        val root = storageManager.privateProjectRoot(projectId)
        val photosDir = photoFolder(root, folderType, objectCode)
        val fileName = "${projectId}_${objectCode}_${System.currentTimeMillis()}.jpg"
        val out = File(photosDir, fileName)
        AppLogger.d(
            "photo.pipeline.capture.output projectId=$projectId objectCode=$objectCode root=${root.absolutePath} file=${out.absolutePath}"
        )
        return out
    }

    override fun createCaptureVideoOutputFile(
        projectId: String,
        objectCode: String,
        folderType: CaptureFolderType
    ): File {
        val root = storageManager.privateProjectRoot(projectId)
        val videosDir = videoFolder(root, folderType, objectCode)
        val fileName = "${projectId}_${objectCode}_${System.currentTimeMillis()}.mp4"
        val out = File(videosDir, fileName)
        AppLogger.d(
            "photo.pipeline.capture.video.output projectId=$projectId objectCode=$objectCode root=${root.absolutePath} file=${out.absolutePath}"
        )
        return out
    }

    fun createEmptyPhoto(
        projectId: String,
        objectCode: String,
        engineer: String,
        folderType: CaptureFolderType = CaptureFolderType.NODE
    ): File {
        val out = createCaptureOutputFile(projectId, objectCode, folderType)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Thời gian: $time", 32f, 80f, paint)
        canvas.drawText("Đối tượng: $objectCode", 32f, 130f, paint)
        canvas.drawText("Kỹ sư: $engineer", 32f, 180f, paint)
        FileOutputStream(out).use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, mainJpegQuality, fos) }
        bitmap.recycle()
        return out
    }

    fun applyWatermark(file: File, objectCode: String, engineer: String) {
        AppLogger.d(
            "photo.pipeline.watermark.start file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
        )
        val mutable = PhotoStampRenderer.loadMutableNormalizedBitmap(file) ?: run {
            AppLogger.d(
                "photo.pipeline.watermark.decode.fail file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
            )
            return
        }
        val canvas = Canvas(mutable)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Thời gian: $time", 20f, mutable.height - 120f, paint)
        canvas.drawText("Đối tượng: $objectCode", 20f, mutable.height - 80f, paint)
        canvas.drawText("Kỹ sư: $engineer", 20f, mutable.height - 40f, paint)
        PhotoStampRenderer.writeBitmap(file, mutable, mainJpegQuality)
        AppLogger.d(
            "photo.pipeline.watermark.done file=${file.absolutePath} objectCode=$objectCode engineer=$engineer"
        )
    }

    override fun importFromGallery(
        context: Context,
        projectId: String,
        objectCode: String,
        engineer: String,
        sourceUri: Uri,
        folderType: CaptureFolderType
    ): File {
        AppLogger.d(
            "photo.pipeline.gallery.import.start projectId=$projectId objectCode=$objectCode sourceUri=$sourceUri"
        )
        val mimeType = context.contentResolver.getType(sourceUri) ?: ""
        val isVideo = mimeType.startsWith("video/") || sourceUri.path?.endsWith(".mp4", ignoreCase = true) == true
        val out = if (isVideo) {
            createCaptureVideoOutputFile(projectId, objectCode, folderType)
        } else {
            createCaptureOutputFile(projectId, objectCode, folderType)
        }
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Cannot open gallery input stream" }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        AppLogger.d(
            "photo.pipeline.gallery.import.done projectId=$projectId objectCode=$objectCode file=${out.absolutePath}"
        )
        return out
    }

    private fun photoFolder(root: File, folderType: CaptureFolderType, objectCode: String): File {
        val category = when (folderType) {
            CaptureFolderType.NODE -> "photos/Nodes"
            CaptureFolderType.ROUTE -> "photos/Routes"
        }
        return File(File(root, category), storageManager.sanitizeFolderName(objectCode)).apply { mkdirs() }
    }

    private fun videoFolder(root: File, folderType: CaptureFolderType, objectCode: String): File {
        val category = when (folderType) {
            CaptureFolderType.NODE -> "media/videos/Nodes"
            CaptureFolderType.ROUTE -> "media/videos/Routes"
        }
        return File(File(root, category), storageManager.sanitizeFolderName(objectCode)).apply { mkdirs() }
    }

    override fun createThumbnail(projectId: String, sourceFile: File): File {
        AppLogger.d(
            "photo.pipeline.thumbnail.start projectId=$projectId source=${sourceFile.absolutePath}"
        )
        val isVideo = sourceFile.name.endsWith(".mp4", ignoreCase = true)
        val thumbsDir = File(storageManager.privateProjectRoot(projectId), "thumbs").apply { mkdirs() }
        val out = File(thumbsDir, "${sourceFile.nameWithoutExtension}_thumb.jpg")

        if (isVideo) {
            val retriever = android.media.MediaMetadataRetriever()
            var bitmap: Bitmap? = null
            try {
                retriever.setDataSource(sourceFile.absolutePath)
                bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                AppLogger.e(e, "photo.pipeline.thumbnail.video.retriever.fail file=${sourceFile.absolutePath}")
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            val targetBitmap = bitmap ?: Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(Color.GRAY)
            }
            val scale = thumbLongEdgePx.toFloat() / maxOf(targetBitmap.width, targetBitmap.height).toFloat()
            val targetWidth = (targetBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (targetBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(targetBitmap, targetWidth, targetHeight, true)
            if (scaled !== targetBitmap && bitmap != null) {
                targetBitmap.recycle()
            }
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, thumbJpegQuality, fos)
            }
            scaled.recycle()
            AppLogger.d(
                "photo.pipeline.thumbnail.video.done projectId=$projectId source=${sourceFile.absolutePath} thumb=${out.absolutePath}"
            )
            return out
        }

        val bitmap = PhotoStampRenderer.loadMutableNormalizedBitmap(sourceFile)
            ?: run {
                AppLogger.d(
                    "photo.pipeline.thumbnail.decode.fail projectId=$projectId source=${sourceFile.absolutePath}"
                )
                throw IllegalStateException("Cannot decode source image for thumbnail")
            }
        val scale = thumbLongEdgePx.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        FileOutputStream(out).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, thumbJpegQuality, fos)
        }
        scaled.recycle()
        AppLogger.d(
            "photo.pipeline.thumbnail.done projectId=$projectId source=${sourceFile.absolutePath} thumb=${out.absolutePath}"
        )
        return out
    }

    /**
     * Extract material data from photo using OCR
     * @param photoFile Photo file to process
     * @return Material data extracted from the photo
     */
    suspend fun extractMaterialDataFromPhoto(photoFile: File): MaterialDataResult {
        return mlKitScannerService.extractMaterialData(photoFile.absolutePath)
    }

    /**
     * Extract daily log data from photo using OCR
     * @param photoFile Photo file to process
     * @return Daily log data extracted from the photo
     */
    suspend fun extractDailyLogDataFromPhoto(photoFile: File): DailyLogDataResult {
        return mlKitScannerService.extractDailyLogData(photoFile.absolutePath)
    }

    override fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Bitmap?
    ) {
        AppLogger.d(
            "photo.pipeline.stamp.start file=${file.absolutePath} lat=${stamp.latitude} lng=${stamp.longitude} bearingDeg=${stamp.bearingDeg}"
        )
        PhotoStampRenderer.applyStamp(file, stamp, ratio, tileBitmap)
        AppLogger.d("photo.pipeline.stamp.done file=${file.absolutePath}")
    }

    @OptIn(UnstableApi::class)
    override suspend fun exportVideoStamp(file: File, stamp: CaptureStamp, tileBitmap: Bitmap?) {
        val (frameWidth, frameHeight) = PhotoStampRenderer.resolveVideoOverlaySize(file)
        val overlayBitmap = PhotoStampRenderer.createStampOverlayBitmap(
            frameWidthPx = frameWidth,
            frameHeightPx = frameHeight,
            stamp = stamp,
            tileBitmap = tileBitmap
        )
        try {
            exportVideoStampWithTempSwap(file) { tempOutput ->
                val exportDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
                val overlayEffect = OverlayEffect(
                    listOf(BitmapOverlay.createStaticBitmapOverlay(overlayBitmap))
                )
                val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(file.toUri()))
                    .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                    .build()
                val composition = Composition.Builder(
                    EditedMediaItemSequence.Builder(listOf(editedMediaItem)).build()
                ).build()
                withContext(Dispatchers.Main) {
                    val transformer = Transformer.Builder(appContext)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    exportDeferred.complete(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    exportDeferred.completeExceptionally(exportException)
                                }
                            }
                        )
                        .build()

                    AppLogger.d("photo.pipeline.video.stamp.start file=${file.absolutePath} temp=${tempOutput.absolutePath}")
                    transformer.start(composition, tempOutput.absolutePath)
                }
                kotlinx.coroutines.withTimeout(5 * 60 * 1000) {
                    exportDeferred.await()
                }
            }
            AppLogger.d("photo.pipeline.video.stamp.done file=${file.absolutePath}")
        } catch (e: Throwable) {
            AppLogger.e(e, "photo.pipeline.video.stamp.failed file=${file.absolutePath}")
            throw e
        } finally {
            overlayBitmap.recycle()
        }
    }

    internal suspend fun exportVideoStampWithTempSwap(
        file: File,
        exportToTemp: suspend (File) -> Unit
    ) {
        val tempOutput = buildStampedVideoTempFile(file)
        try {
            exportToTemp(tempOutput)
            replaceFileSafely(tempOutput, file)
        } finally {
            if (tempOutput.exists()) {
                tempOutput.delete()
            }
        }
    }

    internal fun buildStampedVideoTempFile(sourceFile: File): File {
        val parent = sourceFile.parentFile ?: error("Missing parent for ${sourceFile.absolutePath}")
        return File(parent, "${sourceFile.nameWithoutExtension}_stamped.mp4")
    }

    internal fun replaceFileSafely(source: File, destination: File) {
        val backup = File(destination.parentFile, "${destination.nameWithoutExtension}_backup.${destination.extension}")
        if (backup.exists()) {
            backup.delete()
        }
        if (destination.exists() && !destination.renameTo(backup)) {
            throw IllegalStateException("Cannot move original video before replacing it")
        }
        try {
            if (!source.renameTo(destination)) {
                source.copyTo(destination, overwrite = true)
            }
            if (backup.exists()) {
                backup.delete()
            }
        } catch (e: Throwable) {
            AppLogger.e(e, "photo.pipeline.video.replace.failed source=${source.absolutePath} dest=${destination.absolutePath}")
            if (backup.exists()) {
                try {
                    if (destination.exists()) {
                        destination.delete()
                    }
                    backup.renameTo(destination)
                } catch (restoreError: Throwable) {
                    AppLogger.e(restoreError, "photo.pipeline.video.restore.backup.failed")
                }
            }
            throw e
        }
    }
}
