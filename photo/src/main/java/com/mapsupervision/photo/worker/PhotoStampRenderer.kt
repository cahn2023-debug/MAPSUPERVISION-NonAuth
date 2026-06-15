package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.mapsupervision.domain.model.CaptureStamp
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan
import com.mapsupervision.domain.model.CameraAspectRatio

data class AspectCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

fun calculateAspectCropRect(
    width: Int,
    height: Int,
    ratio: CameraAspectRatio
): AspectCropRect {
    if (ratio == CameraAspectRatio.RATIO_FULL) {
        return AspectCropRect(0, 0, width, height)
    }

    val isLandscape = width > height
    val baseRatio = when (ratio) {
        CameraAspectRatio.RATIO_4_3 -> 4f / 3f
        CameraAspectRatio.RATIO_16_9 -> 16f / 9f
        CameraAspectRatio.RATIO_1_1 -> 1.0f
        CameraAspectRatio.RATIO_FULL -> 1.0f
    }
    val targetRatio = if (isLandscape) baseRatio else (1f / baseRatio)
    val currentRatio = width.toFloat() / height.toFloat()

    return when {
        currentRatio > targetRatio -> {
            val cropWidth = (height * targetRatio).toInt().coerceAtMost(width)
            val left = ((width - cropWidth) / 2).coerceAtLeast(0)
            AspectCropRect(left, 0, cropWidth, height)
        }
        currentRatio < targetRatio -> {
            val cropHeight = (width / targetRatio).toInt().coerceAtMost(height)
            val top = ((height - cropHeight) / 2).coerceAtLeast(0)
            AspectCropRect(0, top, width, cropHeight)
        }
        else -> AspectCropRect(0, 0, width, height)
    }
}

object PhotoStampRenderer {
    internal const val MINIMAP_TILE_ALPHA = 128

    fun cropBitmapToAspectRatio(bitmap: Bitmap, ratio: CameraAspectRatio): Bitmap {
        val crop = calculateAspectCropRect(bitmap.width, bitmap.height, ratio)
        if (crop.width == bitmap.width && crop.height == bitmap.height && crop.left == 0 && crop.top == 0) {
            return bitmap
        }
        val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }

    fun loadMutableNormalizedBitmap(file: File): Bitmap? {
        val src = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().applyOrientationTransform(orientation)
        val normalized = if (matrix.isIdentity) {
            src
        } else {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true).also {
                if (it != src) src.recycle()
            }
        }
        return normalized.copy(Bitmap.Config.ARGB_8888, true)?.also {
            if (it != normalized) normalized.recycle()
        }
    }

    fun writeBitmap(file: File, bitmap: Bitmap, quality: Int) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        bitmap.recycle()
        runCatching {
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                saveAttributes()
            }
        }
    }

    fun applyStamp(
        file: File,
        stamp: CaptureStamp,
        ratio: CameraAspectRatio,
        tileBitmap: Bitmap? = null
    ) {
        var mutable = loadMutableNormalizedBitmap(file) ?: return
        mutable = cropBitmapToAspectRatio(mutable, ratio)
        val resolvedTile = resolveStampTileBitmap(stamp, tileBitmap)
        drawStamp(
            canvas = Canvas(mutable),
            frameWidth = mutable.width.toFloat(),
            frameHeight = mutable.height.toFloat(),
            stamp = stamp,
            tileBitmap = resolvedTile,
            missingLocationText = "Khong co vi tri"
        )
        if (tileBitmap == null) {
            resolvedTile?.recycle()
        }
        writeBitmap(file, mutable, 92)
    }

    fun createStampOverlayBitmap(
        frameWidthPx: Int,
        frameHeightPx: Int,
        stamp: CaptureStamp,
        tileBitmap: Bitmap? = null
    ): Bitmap {
        val overlay = Bitmap.createBitmap(frameWidthPx, frameHeightPx, Bitmap.Config.ARGB_8888)
        val resolvedTile = resolveStampTileBitmap(stamp, tileBitmap)
        drawStamp(
            canvas = Canvas(overlay),
            frameWidth = frameWidthPx.toFloat(),
            frameHeight = frameHeightPx.toFloat(),
            stamp = stamp,
            tileBitmap = resolvedTile,
            missingLocationText = "Khong co vi tri"
        )
        if (tileBitmap == null) {
            resolvedTile?.recycle()
        }
        return overlay
    }

    fun resolveVideoOverlaySize(file: File): Pair<Int, Int> {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 1280
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 720
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        stamp: CaptureStamp,
        tileBitmap: Bitmap? = null,
        missingLocationText: String = "Khong co vi tri"
    ) {
        drawStamp(
            canvas = canvas,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            content = PhotoStampLayoutCalculator.buildContent(
                stamp = stamp,
                missingLocationText = missingLocationText
            ),
            bearingDeg = stamp.bearingDeg,
            tileBitmap = tileBitmap
        )
    }

    fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        timestampMs: Long,
        address: String,
        latitude: Double?,
        longitude: Double?,
        note: String,
        bearingDeg: Float,
        tileBitmap: Bitmap? = null,
        missingLocationText: String = "Khong co vi tri"
    ) {
        drawStamp(
            canvas = canvas,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            content = PhotoStampLayoutCalculator.buildContent(
                timestampMs = timestampMs,
                address = address,
                latitude = latitude,
                longitude = longitude,
                note = note,
                missingLocationText = missingLocationText
            ),
            bearingDeg = bearingDeg,
            tileBitmap = tileBitmap
        )
    }

    internal fun drawStamp(
        canvas: Canvas,
        frameWidth: Float,
        frameHeight: Float,
        content: PhotoStampContent,
        bearingDeg: Float,
        tileBitmap: Bitmap? = null
    ) {
        val layout = PhotoStampLayoutCalculator.calculate(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rows = content.rows,
            textWidth = { 0f },
            iconWidth = { 0f },
            showMap = content.coordinateText != null
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = layout.textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = layout.iconSize
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 25, 110, 190)
        }
        val contentLayout = PhotoStampLayoutCalculator.calculate(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rows = content.rows,
            textWidth = { textPaint.measureText(it) },
            iconWidth = { iconPaint.measureText(it) },
            showMap = content.coordinateText != null
        )

        contentLayout.rowLayouts.forEach { rowLayout ->
            val rect = rowLayout.rect.toRectF()
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pillBgPaint)

            rowLayout.row.lines.forEachIndexed { index, line -> 
                val lineTop = rect.top + index * (contentLayout.pillHeight + contentLayout.lineGap)
                val textBounds = Rect()
                textPaint.getTextBounds(line, 0, line.length, textBounds)
                val baselineY = lineTop + (contentLayout.pillHeight + textBounds.height()) / 2f
                canvas.drawText(line, rect.left + contentLayout.pillPaddingHorizontal, baselineY, textPaint)
            }

            val iconBounds = Rect()
            iconPaint.getTextBounds(rowLayout.row.icon, 0, rowLayout.row.icon.length, iconBounds)
            val iconX = rect.right - contentLayout.pillPaddingHorizontal - iconPaint.measureText(rowLayout.row.icon)
            val iconY = rect.top + (rect.height() + iconBounds.height()) / 2f
            canvas.drawText(rowLayout.row.icon, iconX, iconY, iconPaint)
        }

        val mapRect = contentLayout.mapRect
        if (mapRect != null && content.latitude != null && content.longitude != null) {
            drawMinimap(
                canvas = canvas,
                rect = mapRect.toRectF(),
                cornerR = contentLayout.mapCornerRadius,
                lat = content.latitude,
                lng = content.longitude,
                bearingDeg = bearingDeg,
                scale = contentLayout.scale,
                tileBitmap = tileBitmap,
                borderWidth = contentLayout.mapBorderWidth,
                coordinateText = content.coordinateText,
                coordinateTextSize = contentLayout.coordinateTextSize,
                coordinateOffsetY = contentLayout.mapCoordinateOffsetY,
                outerDotRadius = contentLayout.mapDotOuterRadius,
                innerDotRadius = contentLayout.mapDotInnerRadius,
                coreDotRadius = contentLayout.mapDotCoreRadius
            )
        }
    }

    private fun Matrix.applyOrientationTransform(orientation: Int): Matrix = apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }

    fun fetchOsmTile(lat: Double, lng: Double, zoom: Int): Bitmap? {
        return try {
            val n = 1 shl zoom
            val xTile = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat)
            val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n)
                .toInt().coerceIn(0, n - 1)
            val url = URL("https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MapSupervision/1.0 (Android)")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) BitmapFactory.decodeStream(conn.inputStream) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStampTileBitmap(stamp: CaptureStamp, tileBitmap: Bitmap?): Bitmap? {
        if (tileBitmap != null) return tileBitmap
        val latitude = stamp.latitude
        val longitude = stamp.longitude
        return if (latitude != null && longitude != null) {
            fetchOsmTile(latitude, longitude, zoom = 17)
        } else {
            null
        }
    }

    private fun tilePixelOffset(lat: Double, lng: Double, zoom: Int, tileSize: Int): Pair<Float, Float> {
        val n = 1 shl zoom
        val xFrac = (lng + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yFrac = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val px = ((xFrac - xFrac.toLong()) * tileSize).toFloat()
        val py = ((yFrac - yFrac.toLong()) * tileSize).toFloat()
        return Pair(px, py)
    }

    private fun drawMinimap(
        canvas: Canvas,
        rect: RectF,
        cornerR: Float,
        lat: Double,
        lng: Double,
        bearingDeg: Float,
        scale: Float,
        tileBitmap: Bitmap? = null,
        borderWidth: Float,
        coordinateText: String?,
        coordinateTextSize: Float,
        coordinateOffsetY: Float,
        outerDotRadius: Float,
        innerDotRadius: Float,
        coreDotRadius: Float
    ) {
        val clipPath = Path().apply {
            addRoundRect(rect, cornerR, cornerR, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        if (tileBitmap != null) {
            val tileW = tileBitmap.width.toFloat()
            val tileH = tileBitmap.height.toFloat()
            val (px, py) = tilePixelOffset(lat, lng, zoom = 17, tileSize = tileBitmap.width)
            val half = minOf(tileW, tileH) / 2f
            val srcLeft = (px - half).coerceIn(0f, tileW - 1f)
            val srcTop = (py - half).coerceIn(0f, tileH - 1f)
            val srcRight = (srcLeft + minOf(tileW, tileH)).coerceAtMost(tileW)
            val srcBottom = (srcTop + minOf(tileW, tileH)).coerceAtMost(tileH)
            val src = Rect(srcLeft.toInt(), srcTop.toInt(), srcRight.toInt(), srcBottom.toInt())
            canvas.drawBitmap(tileBitmap, src, rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = MINIMAP_TILE_ALPHA })
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(248, 242, 239, 233) }
            canvas.drawRect(rect, bgPaint)

            val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 160, 210, 230) }
            val waterPath = Path().apply {
                moveTo(rect.left, rect.top + rect.height() * 0.58f)
                cubicTo(
                    rect.left + rect.width() * 0.25f, rect.top + rect.height() * 0.50f,
                    rect.left + rect.width() * 0.60f, rect.top + rect.height() * 0.65f,
                    rect.right, rect.top + rect.height() * 0.55f
                )
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }
            canvas.drawPath(waterPath, waterPaint)

            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 195, 230, 170) }
            canvas.drawRoundRect(
                RectF(
                    rect.left + rect.width() * 0.06f,
                    rect.top + rect.height() * 0.06f,
                    rect.left + rect.width() * 0.38f,
                    rect.top + rect.height() * 0.40f
                ),
                6f * scale,
                6f * scale,
                greenPaint
            )

            val roadMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = 6f * scale
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }
            val cx = rect.centerX()
            val cy = rect.centerY()
            canvas.drawLine(rect.left + 6f * scale, cy - 6f * scale, rect.right - 6f * scale, cy + 6f * scale, roadMainPaint)
            canvas.drawLine(cx + 5f * scale, rect.top + 6f * scale, cx - 5f * scale, rect.bottom - 6f * scale, roadMainPaint)

            val buildingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 205, 198, 188) }
            listOf(
                RectF(rect.left + 8f * scale, rect.top + 8f * scale, cx - 12f * scale, cy - 16f * scale),
                RectF(cx + 12f * scale, rect.top + 8f * scale, rect.right - 8f * scale, cy - 16f * scale),
                RectF(rect.left + 8f * scale, cy + 16f * scale, cx - 12f * scale, rect.bottom - 8f * scale),
                RectF(cx + 12f * scale, cy + 16f * scale, rect.right - 8f * scale, rect.bottom - 8f * scale)
            ).forEach { canvas.drawRect(it, buildingPaint) }
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        val bearingRad = Math.toRadians(bearingDeg.toDouble()).toFloat()
        val coneAngle = Math.toRadians(45.0).toFloat()
        val coneLen = rect.width() * 0.42f

        val coneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 200, 0)
            style = Paint.Style.FILL
        }
        val coneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 220, 160, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        val conePath = Path().apply {
            moveTo(cx, cy)
            val leftAngle = bearingRad - coneAngle / 2
            lineTo(cx + sin(leftAngle) * coneLen, cy - cos(leftAngle) * coneLen)
            val sweepDeg = Math.toDegrees(coneAngle.toDouble()).toFloat()
            val startDeg = Math.toDegrees((bearingRad - coneAngle / 2).toDouble()).toFloat() - 90f
            arcTo(RectF(cx - coneLen, cy - coneLen, cx + coneLen, cy + coneLen), startDeg, sweepDeg)
            close()
        }
        canvas.drawPath(conePath, coneFillPaint)
        canvas.drawPath(conePath, coneStrokePaint)

        canvas.drawCircle(cx, cy, outerDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 220, 50, 50) })
        canvas.drawCircle(cx, cy, innerDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 220, 50, 50) })
        canvas.drawCircle(cx, cy, coreDotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.restore()

        canvas.drawRoundRect(rect, cornerR, cornerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 25, 110, 190)
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        })

        if (coordinateText != null) {
            val coordinateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 25, 110, 190)
                textSize = coordinateTextSize
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(2f * scale, 0f, 1f * scale, Color.WHITE)
            }
            val textWidth = coordinateTextPaint.measureText(coordinateText)
            canvas.drawText(
                coordinateText,
                rect.centerX() - textWidth / 2f,
                rect.bottom + coordinateOffsetY,
                coordinateTextPaint
            )
        }
    }

    private fun PhotoStampRect.toRectF(): RectF = RectF(left, top, right, bottom)
}
