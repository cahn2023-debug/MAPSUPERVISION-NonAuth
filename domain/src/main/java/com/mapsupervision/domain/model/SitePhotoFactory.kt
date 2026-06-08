package com.mapsupervision.domain.model

import java.io.File
import java.util.UUID

fun createStoredSitePhoto(
    projectId: String,
    objectCode: String,
    file: File,
    thumbnailFile: File,
    location: PhotoLocationSnapshot,
    engineer: String,
    capturedAtEpochMs: Long = System.currentTimeMillis()
): SitePhoto = SitePhoto(
    id = UUID.randomUUID().toString(),
    projectId = projectId,
    objectCode = objectCode,
    filePath = file.absolutePath,
    thumbnailPath = thumbnailFile.absolutePath,
    latitude = location.latitude,
    longitude = location.longitude,
    locationAccuracyM = location.accuracyM,
    isGpsMocked = location.isMock,
    locationStatus = location.status,
    engineer = engineer,
    capturedAtEpochMs = capturedAtEpochMs
)
