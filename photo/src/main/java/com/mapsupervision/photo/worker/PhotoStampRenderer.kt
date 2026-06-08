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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

object PhotoStampRenderer {

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
        latitude: Double?,
        longitude: Double?,
        address: String,
        note: String,
        bearingDeg: Float = 0f
    ) {
        val mutable = loadMutableNormalizedBitmap(file) ?: return

        val canvas = Canvas(mutable)
        val w = mutable.width.toFloat()
        val h = mutable.height.toFloat()

        val scale = (w / 3000f).coerceIn(0.4f, 2.5f) * 1.4f
        val margin = 40f * scale
        val pillH = 68f * scale
        val pillGap = 12f * scale
        val pillPadH = 28f * scale
        val textSize = 30f * scale
        val iconSize = 32f * scale
        val lineGap = 6f * scale

        val time = SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.US).format(Date())
        val fullAddress = when {
            address.isNotBlank() -> address
            latitude != null && longitude != null ->
                "${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
            else -> "Không có vị trí"
        }
        val addressLines = wrapText(fullAddress, 42)

        data class PillRow(val icon: String, val lines: List<String>)
        val rows = buildList {
            add(PillRow("⏱", listOf(time)))
            add(PillRow("📍", addressLines))
            if (note.isNotBlank()) add(PillRow("📝", listOf(note.take(80))))
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = iconSize
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 25, 110, 190)
        }

        fun rowHeight(row: PillRow): Float {
            val linesCount = row.lines.size
            return pillH * linesCount + lineGap * (linesCount - 1)
        }

        fun pillWidth(row: PillRow): Float {
            val maxLineW = row.lines.maxOf { textPaint.measureText(it) }
            val iconW = iconPaint.measureText(row.icon)
            return maxLineW + iconW + pillPadH * 2 + 14f * scale
        }

        val totalH = rows.sumOf { rowHeight(it).toDouble() }.toFloat() + pillGap * (rows.size - 1)
        val pillsRight = w - margin
        val pillsBottom = h - margin
        var currentTop = pillsBottom - totalH

        rows.forEach { row ->
            val rowHeight = rowHeight(row)
            val rowWidth = pillWidth(row)
            val left = pillsRight - rowWidth
            val rect = RectF(left, currentTop, pillsRight, currentTop + rowHeight)

            canvas.drawRoundRect(rect, rowHeight / 2f, rowHeight / 2f, pillBgPaint)

            row.lines.forEachIndexed { index, line ->
                val lineTop = currentTop + index * (pillH + lineGap)
                val textBounds = Rect()
                textPaint.getTextBounds(line, 0, line.length, textBounds)
                val baselineY = lineTop + (pillH + textBounds.height()) / 2f
                canvas.drawText(line, left + pillPadH, baselineY, textPaint)
            }

            val iconBounds = Rect()
            iconPaint.getTextBounds(row.icon, 0, row.icon.length, iconBounds)
            val iconX = pillsRight - pillPadH - iconPaint.measureText(row.icon)
            val iconY = currentTop + (rowHeight + iconBounds.height()) / 2f
            canvas.drawText(row.icon, iconX, iconY, iconPaint)

            currentTop += rowHeight + pillGap
        }

        if (latitude != null && longitude != null) {
            val mapSize = totalH * 1.5f
            val mapLeft = margin
            val mapTop = h - margin - mapSize
            val mapRect = RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize)
            val tileBitmap = fetchOsmTile(latitude, longitude, zoom = 17)
            drawMinimap(canvas, mapRect, 20f * scale, latitude, longitude, bearingDeg, scale, tileBitmap)
            tileBitmap?.recycle()
        }

        writeBitmap(file, mutable, 92)
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

    private fun fetchOsmTile(lat: Double, lng: Double, zoom: Int): Bitmap? {
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

    private fun tilePixelOffset(lat: Double, lng: Double, zoom: Int, tileSize: Int): Pair<Float, Float> {
        val n = 1 shl zoom
        val xFrac = (lng + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yFrac = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val px = ((xFrac - xFrac.toLong()) * tileSize).toFloat()
        val py = ((yFrac - yFrac.toLong()) * tileSize).toFloat()
        return Pair(px, py)
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val breakAt = text.lastIndexOf(',', maxChars).takeIf { it > maxChars / 2 }
            ?: text.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 }
            ?: maxChars
        val line1 = text.substring(0, breakAt).trim()
        val line2 = text.substring(breakAt).trimStart(',', ' ')
        return listOf(line1, line2)
    }

    private fun drawMinimap(
        canvas: Canvas,
        rect: RectF,
        cornerR: Float,
        lat: Double,
        lng: Double,
        bearingDeg: Float,
        scale: Float,
        tileBitmap: Bitmap? = null
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
            canvas.drawBitmap(tileBitmap, src, rect, null)
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

        canvas.drawCircle(cx, cy, 26f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 220, 50, 50) })
        canvas.drawCircle(cx, cy, 16f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 220, 50, 50) })
        canvas.drawCircle(cx, cy, 7f * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.restore()

        canvas.drawRoundRect(rect, cornerR, cornerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 25, 110, 190)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * scale
        })

        canvas.drawText(
            "${"%.4f".format(lat)}, ${"%.4f".format(lng)}",
            rect.left,
            rect.bottom + 25f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 25, 110, 190)
                textSize = 19f * scale
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(2f * scale, 0f, 1f * scale, Color.WHITE)
            }
        )
    }
}
