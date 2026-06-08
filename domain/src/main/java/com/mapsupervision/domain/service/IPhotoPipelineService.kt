package com.mapsupervision.domain.service

import android.content.Context
import android.net.Uri
import java.io.File

interface IPhotoPipelineService {
    fun createCaptureOutputFile(projectId: String, objectCode: String): File
    fun importFromGallery(
        context: Context,
        projectId: String,
        objectCode: String,
        engineer: String,
        sourceUri: Uri
    ): File
    fun createThumbnail(projectId: String, sourceFile: File): File

    fun applyStamp(
        file: File,
        latitude: Double?,
        longitude: Double?,
        address: String,
        note: String,
        bearingDeg: Float
    )
}
