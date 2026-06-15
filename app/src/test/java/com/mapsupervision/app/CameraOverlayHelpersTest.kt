package com.mapsupervision.app

import com.mapsupervision.domain.model.PhotoLocationSnapshot
import com.mapsupervision.domain.model.PhotoLocationStatus
import com.mapsupervision.domain.model.CameraAspectRatio
import com.mapsupervision.domain.service.CaptureFolderType
import com.mapsupervision.domain.service.IPhotoPipelineService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class CameraOverlayHelpersTest {

    @Test
    fun `image flash mapping follows selected mode`() {
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_AUTO, resolveImageCaptureFlashMode(CameraFlashMode.AUTO))
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_OFF, resolveImageCaptureFlashMode(CameraFlashMode.OFF))
        assertEquals(androidx.camera.core.ImageCapture.FLASH_MODE_ON, resolveImageCaptureFlashMode(CameraFlashMode.ON))
    }

    @Test
    fun `video torch only turns off for flash off`() {
        assertTrue(resolveVideoTorchEnabled(CameraFlashMode.AUTO))
        assertTrue(resolveVideoTorchEnabled(CameraFlashMode.ON))
        assertFalse(resolveVideoTorchEnabled(CameraFlashMode.OFF))
    }

    @Test
    fun `clampZoomRatio respects min and max`() {
        assertEquals(1f, clampZoomRatio(0.4f, 1f, 4f))
        assertEquals(2.5f, clampZoomRatio(2.5f, 1f, 4f))
        assertEquals(4f, clampZoomRatio(9f, 1f, 4f))
    }

    @Test
    fun `buildCaptureStamp keeps location address note and bearing`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.12345,
            longitude = 106.98765,
            accuracyM = 3.5f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )

        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = location,
            address = " Test address ",
            note = " test note ",
            bearingDeg = 87.6f
        )

        assertEquals(1234L, stamp.timestampMs)
        assertEquals(10.12345, stamp.latitude)
        assertEquals(106.98765, stamp.longitude)
        assertEquals("Test address", stamp.address)
        assertEquals("test note", stamp.note)
        assertEquals(87.6f, stamp.bearingDeg)
    }

    @Test
    fun `preview stamp render key ignores time and bearing churn when inputs stay the same`() {
        val location = PhotoLocationSnapshot(
            latitude = 10.123456,
            longitude = 106.987654,
            accuracyM = 3.5f,
            isMock = false,
            status = PhotoLocationStatus.OK
        )
        val viewport = com.mapsupervision.photo.worker.AspectCropRect(0, 0, 720, 1280)
        val tileKey = roundedLocationKey(location.latitude, location.longitude)

        val first = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = viewport,
            location = location,
            address = " 123 Street ",
            note = "Overlay",
            tileKey = tileKey
        )
        val second = buildPreviewStampRenderKey(
            stampEnabled = true,
            isVideoMode = false,
            aspectRatio = CameraAspectRatio.RATIO_4_3,
            viewport = viewport,
            location = location.copy(accuracyM = 9f),
            address = "123 Street",
            note = "Overlay",
            tileKey = tileKey
        )

        assertEquals(first, second)
    }

    @Test
    fun `post process recorded video exports before save when stamp enabled`() = runBlocking {
        val order = mutableListOf<String>()
        val contextFile = File.createTempFile("camera-video", ".mp4").apply { writeText("raw") }
        val stamp = buildCaptureStamp(
            timestampMs = 1234L,
            location = null,
            address = "",
            note = "",
            bearingDeg = 0f
        )

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun createCaptureVideoOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun importFromGallery(
                context: android.content.Context,
                projectId: String,
                objectCode: String,
                engineer: String,
                sourceUri: android.net.Uri,
                folderType: CaptureFolderType
            ) = error("unused")
            override fun createThumbnail(projectId: String, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: android.graphics.Bitmap?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: android.graphics.Bitmap?) {
                order += "export"
                file.writeText("stamped")
            }
        }

        val saved = postProcessRecordedVideo(
            videoFile = contextFile,
            stampEnabled = true,
            stampAtRecordStart = stamp,
            tileBitmap = null,
            photoPipelineService = pipeline,
            setProcessingVideoStamp = { },
            onSavePhoto = {
                order += "save"
                true
            },
            onPhotoCaptured = { order += "captured" }
        )

        assertTrue(saved)
        assertEquals(listOf("export", "save", "captured"), order)
        assertEquals("stamped", contextFile.readText())
    }

    @Test
    fun `post process recorded video skips export when stamp disabled`() = runBlocking {
        val order = mutableListOf<String>()
        val contextFile = File.createTempFile("camera-video", ".mp4").apply { writeText("raw") }

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun createCaptureVideoOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun importFromGallery(
                context: android.content.Context,
                projectId: String,
                objectCode: String,
                engineer: String,
                sourceUri: android.net.Uri,
                folderType: CaptureFolderType
            ) = error("unused")
            override fun createThumbnail(projectId: String, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: android.graphics.Bitmap?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: android.graphics.Bitmap?) {
                order += "export"
            }
        }

        val saved = postProcessRecordedVideo(
            videoFile = contextFile,
            stampEnabled = false,
            stampAtRecordStart = null,
            tileBitmap = null,
            photoPipelineService = pipeline,
            setProcessingVideoStamp = { },
            onSavePhoto = {
                order += "save"
                true
            },
            onPhotoCaptured = { order += "captured" }
        )

        assertTrue(saved)
        assertEquals(listOf("save", "captured"), order)
    }

    @Test
    fun `post process recorded video clears processing state on export failure`() = runBlocking {
        var processing = false
        var saveCalled = false
        val contextFile = File.createTempFile("camera-video", ".mp4").apply { writeText("raw") }
        val stamp = buildCaptureStamp(1234L, null, "", "", 0f)

        val pipeline = object : IPhotoPipelineService {
            override fun createCaptureOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun createCaptureVideoOutputFile(projectId: String, objectCode: String, folderType: CaptureFolderType) = error("unused")
            override fun importFromGallery(
                context: android.content.Context,
                projectId: String,
                objectCode: String,
                engineer: String,
                sourceUri: android.net.Uri,
                folderType: CaptureFolderType
            ) = error("unused")
            override fun createThumbnail(projectId: String, sourceFile: File) = error("unused")
            override fun applyStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, ratio: com.mapsupervision.domain.model.CameraAspectRatio, tileBitmap: android.graphics.Bitmap?) = error("unused")
            override suspend fun exportVideoStamp(file: File, stamp: com.mapsupervision.domain.model.CaptureStamp, tileBitmap: android.graphics.Bitmap?) {
                error("boom")
            }
        }

        val result = runCatching {
            postProcessRecordedVideo(
                videoFile = contextFile,
                stampEnabled = true,
                stampAtRecordStart = stamp,
                tileBitmap = null,
                photoPipelineService = pipeline,
                setProcessingVideoStamp = { processing = it },
                onSavePhoto = {
                    saveCalled = true
                    true
                },
                onPhotoCaptured = { }
            )
        }

        assertTrue(result.isFailure)
        assertFalse(processing)
        assertFalse(saveCalled)
    }
}
