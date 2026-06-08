package com.mapsupervision.photo.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.mapsupervision.data.mlkit.DailyLogDataResult
import com.mapsupervision.data.mlkit.MaterialDataResult
import com.mapsupervision.data.mlkit.MlKitScannerService
import com.mapsupervision.domain.service.IPhotoPipelineService
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoPipelineService @Inject constructor(
    private val storageManager: ProjectStorageManager,
    private val mlKitScannerService: MlKitScannerService
) : IPhotoPipelineService {
    companion object {
        private const val mainJpegQuality = 78
        private const val thumbJpegQuality = 72
        private const val thumbLongEdgePx = 320
    }

    override fun createCaptureOutputFile(projectId: String, objectCode: String): File {
        val root = storageManager.projectRoot(projectId)
        val photosDir = File(root, "photos/Nodes").apply { mkdirs() }
        val fileName = "${projectId}_${objectCode}_${System.currentTimeMillis()}.jpg"
        return File(photosDir, fileName)
    }

    fun createEmptyPhoto(projectId: String, objectCode: String, engineer: String): File {
        val out = createCaptureOutputFile(projectId, objectCode)
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
        val mutable = PhotoStampRenderer.loadMutableNormalizedBitmap(file) ?: return
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
    }

    override fun importFromGallery(
        context: Context,
        projectId: String,
        objectCode: String,
        engineer: String,
        sourceUri: Uri
    ): File {
        val out = createCaptureOutputFile(projectId, objectCode)
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Cannot open gallery input stream" }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        applyWatermark(out, objectCode, engineer)
        return out
    }

    override fun createThumbnail(projectId: String, sourceFile: File): File {
        val bitmap = PhotoStampRenderer.loadMutableNormalizedBitmap(sourceFile)
            ?: throw IllegalStateException("Cannot decode source image for thumbnail")
        val scale = thumbLongEdgePx.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        val thumbsDir = File(storageManager.projectRoot(projectId), "thumbs").apply { mkdirs() }
        val out = File(thumbsDir, "${sourceFile.nameWithoutExtension}_thumb.jpg")
        FileOutputStream(out).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, thumbJpegQuality, fos)
        }
        scaled.recycle()
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
        latitude: Double?,
        longitude: Double?,
        address: String,
        note: String,
        bearingDeg: Float
    ) {
        PhotoStampRenderer.applyStamp(file, latitude, longitude, address, note, bearingDeg)
    }
}
