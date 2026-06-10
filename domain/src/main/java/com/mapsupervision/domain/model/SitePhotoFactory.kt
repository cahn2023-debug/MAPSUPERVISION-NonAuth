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
    capturedAtEpochMs: Long = System.currentTimeMillis(),
    tagCodesCsv: String = "",
    matchedNodeCode: String? = null,
    matchedRouteCode: String? = null,
    matchedAtEpochMs: Long = 0L,
    matchingTimeOffsetMs: Long = 0L
): SitePhoto = SitePhoto(
    id = UUID.randomUUID().toString(),
    projectId = projectId,
    objectCode = objectCode,
    tagCodesCsv = tagCodesCsv,
    matchedNodeCode = matchedNodeCode,
    matchedRouteCode = matchedRouteCode,
    filePath = file.absolutePath,
    thumbnailPath = thumbnailFile.absolutePath,
    latitude = location.latitude,
    longitude = location.longitude,
    locationAccuracyM = location.accuracyM,
    isGpsMocked = location.isMock,
    locationStatus = location.status,
    engineer = engineer,
    capturedAtEpochMs = capturedAtEpochMs,
    matchedAtEpochMs = matchedAtEpochMs,
    matchingTimeOffsetMs = matchingTimeOffsetMs
)
