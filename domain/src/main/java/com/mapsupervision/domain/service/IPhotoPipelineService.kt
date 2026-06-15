package com.mapsupervision.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio
import java.io.File

enum class CaptureFolderType {
    NODE,
    ROUTE
}

interface IPhotoPipelineService {
    fun createCaptureOutputFile(
        projectId: String,
        objectCode: String,
        folderType: CaptureFolderType = CaptureFolderType.NODE
    ): File

    fun createCaptureVideoOutputFile(
        projectId: String,
        objectCode: String,
        folderType: CaptureFolderType = CaptureFolderType.NODE
    ): File

    fun importFromGallery(
        context: Context,
        projectId: String,
        objectCode: String,
        engineer: String,
        sourceUri: Uri,
        folderType: CaptureFolderType = CaptureFolderType.NODE
    ): File
    fun createThumbnail(projectId: String, sourceFile: File): File

    fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Bitmap? = null
    )

    suspend fun exportVideoStamp(
        file: File,
        stamp: CaptureStamp,
        tileBitmap: Bitmap? = null
    )
}
