package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.Color
import com.mapsupervision.data.mlkit.MlKitScannerService
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class PhotoPipelineServiceTest {

    @Test
    fun `capture output and thumbnails stay under private project storage`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            mlKitScannerService = MlKitScannerService(context)
        )

        val captureFile = service.createCaptureOutputFile("project-1", "NODE-01", CaptureFolderType.NODE)
        assertTrue(captureFile.absolutePath.contains("MapSupervision"))
        assertTrue(captureFile.absolutePath.contains("project-1"))
        assertTrue(captureFile.absolutePath.contains("photos"))
        assertTrue(captureFile.absolutePath.contains("photos${File.separator}Nodes${File.separator}NODE-01"))

        val source = File.createTempFile("photo-source", ".jpg").apply {
            outputStream().use { output ->
                Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.BLUE)
                    compress(Bitmap.CompressFormat.JPEG, 90, output)
                    recycle()
                }
            }
        }

        val thumbnail = service.createThumbnail("project-1", source)
        assertTrue(thumbnail.absolutePath.contains("MapSupervision"))
        assertTrue(thumbnail.absolutePath.contains("project-1"))
        assertTrue(thumbnail.absolutePath.contains("thumbs"))
        assertTrue(thumbnail.exists())
    }

    @Test
    fun `buildStampedVideoTempFile keeps export beside source video`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            mlKitScannerService = MlKitScannerService(context)
        )

        val source = File(context.cacheDir, "clip.mp4").apply { writeText("video") }
        val temp = service.buildStampedVideoTempFile(source)

        assertTrue(temp.parentFile == source.parentFile)
        assertTrue(temp.name == "clip_stamped.mp4")
    }

    @Test
    fun `route capture output and video files go under route folders`() {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            mlKitScannerService = MlKitScannerService(context)
        )

        val captureFile = service.createCaptureOutputFile("project-1", "ROUTE A/1", CaptureFolderType.ROUTE)
        val videoFile = service.createCaptureVideoOutputFile("project-1", "ROUTE A/1", CaptureFolderType.ROUTE)

        val routePath = "Routes${File.separator}ROUTE A_1"
        assertTrue(captureFile.absolutePath.contains(routePath))
        assertTrue(videoFile.absolutePath.contains("media${File.separator}videos${File.separator}$routePath"))
    }

    @Test
    fun `sanitizeFolderName trims and replaces invalid filesystem characters`() {
        val context = RuntimeEnvironment.getApplication()
        val storage = ProjectStorageManager(context)

        assertEquals("NODE_01", storage.sanitizeFolderName(" NODE/01 "))
        assertEquals("Unnamed", storage.sanitizeFolderName("   "))
    }

    @Test
    fun `replaceFileSafely failure restores original backup`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            mlKitScannerService = MlKitScannerService(context)
        )

        val destFile = File(context.cacheDir, "original_dest.mp4").apply { writeText("original video content") }
        val sourceFile = File(context.cacheDir, "non_existent_source.mp4")

        val result = runCatching {
            service.replaceFileSafely(sourceFile, destFile)
        }

        org.junit.Assert.assertTrue(result.isFailure)
        org.junit.Assert.assertTrue(destFile.exists())
        org.junit.Assert.assertEquals("original video content", destFile.readText())
    }

    @Test
    fun `exportVideoStampWithTempSwap swaps in exported temp file`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val service = PhotoPipelineService(
            appContext = context,
            storageManager = ProjectStorageManager(context),
            mlKitScannerService = MlKitScannerService(context)
        )

        val source = File(context.cacheDir, "video-source.mp4").apply { writeText("raw video") }

        service.exportVideoStampWithTempSwap(source) { temp ->
            temp.writeText("stamped video")
        }

        assertTrue(source.exists())
        org.junit.Assert.assertEquals("stamped video", source.readText())
    }

    @Test
    fun `createStampOverlayBitmap keeps provided tile snapshot`() {
        val tile = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val stamp = com.mapsupervision.domain.model.CaptureStamp(
            timestampMs = 1710000000000L,
            latitude = 10.12345,
            longitude = 106.98765,
            address = "123 Street",
            note = "Overlay",
            bearingDeg = 30f
        )

        val overlay = PhotoStampRenderer.createStampOverlayBitmap(
            frameWidthPx = 320,
            frameHeightPx = 240,
            stamp = stamp,
            tileBitmap = tile
        )

        assertTrue(!tile.isRecycled)
        assertEquals(320, overlay.width)
        assertEquals(240, overlay.height)
        assertEquals(128, PhotoStampRenderer.MINIMAP_TILE_ALPHA)
        overlay.recycle()
        tile.recycle()
    }
}
