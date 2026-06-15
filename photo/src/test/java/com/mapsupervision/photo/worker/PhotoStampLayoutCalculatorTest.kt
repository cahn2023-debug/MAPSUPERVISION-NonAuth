package com.mapsupervision.photo.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mapsupervision.domain.model.CaptureStamp
import com.mapsupervision.domain.model.CameraAspectRatio

class PhotoStampLayoutCalculatorTest {

    @Test
    fun `portrait layout keeps map bottom-left and pills bottom-right`() {
        val rows = listOf(
            PhotoStampRow(PhotoStampLayoutCalculator.timeIcon, listOf("09:30  13/06/2026")),
            PhotoStampRow(PhotoStampLayoutCalculator.locationIcon, listOf("123 Street", "District 1")),
            PhotoStampRow(PhotoStampLayoutCalculator.noteIcon, listOf("Kiem tra cot dien"))
        )

        val layout = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = rows,
            textWidth = { it.length * 14f },
            iconWidth = { 26f },
            showMap = true
        )

        val mapRect = layout.mapRect
        assertNotNull(mapRect)
        assertTrue(mapRect!!.left < 120f)
        assertTrue(mapRect.bottom > 1750f)

        val firstRow = layout.rowLayouts.first().rect
        val lastRow = layout.rowLayouts.last().rect
        assertTrue(firstRow.left > mapRect.right)
        assertTrue(lastRow.bottom > 1750f)
    }

    @Test
    fun `landscape layout keeps same logical anchors`() {
        val rows = listOf(
            PhotoStampRow(PhotoStampLayoutCalculator.timeIcon, listOf("09:30  13/06/2026")),
            PhotoStampRow(PhotoStampLayoutCalculator.locationIcon, listOf("123 Street", "District 1"))
        )

        val layout = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1920f,
            frameHeight = 1080f,
            rows = rows,
            textWidth = { it.length * 16f },
            iconWidth = { 28f },
            showMap = true
        )

        val mapRect = layout.mapRect
        assertNotNull(mapRect)
        assertTrue(mapRect!!.left < 120f)
        assertTrue(mapRect.bottom > 950f)

        val bottomRow = layout.rowLayouts.last().rect
        assertTrue(bottomRow.right > 1800f)
        assertTrue(bottomRow.bottom > 950f)
    }

    @Test
    fun `note row increases pills stack height without moving map anchor`() {
        val baseRows = listOf(
            PhotoStampRow(PhotoStampLayoutCalculator.timeIcon, listOf("09:30  13/06/2026")),
            PhotoStampRow(PhotoStampLayoutCalculator.locationIcon, listOf("123 Street"))
        )
        val noteRows = baseRows + PhotoStampRow(
            PhotoStampLayoutCalculator.noteIcon,
            listOf("Ghi chu them")
        )

        val withoutNote = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = baseRows,
            textWidth = { it.length * 14f },
            iconWidth = { 26f },
            showMap = true
        )
        val withNote = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = noteRows,
            textWidth = { it.length * 14f },
            iconWidth = { 26f },
            showMap = true
        )

        assertEquals(withoutNote.mapRect!!.left, withNote.mapRect!!.left, 0.001f)
        assertEquals(withoutNote.mapRect.bottom, withNote.mapRect.bottom, 0.001f)
        assertTrue(withNote.rowLayouts.first().rect.top < withoutNote.rowLayouts.first().rect.top)
    }

    @Test
    fun `buildContent falls back to coordinates and wraps long address`() {
        val content = PhotoStampLayoutCalculator.buildContent(
            timestampMs = 0L,
            address = "",
            latitude = 10.12345,
            longitude = 106.98765,
            note = "Mot ghi chu rat dai de kiem tra wrap text trong stamp preview",
            missingLocationText = "Khong co vi tri"
        )

        assertEquals(PhotoStampLayoutCalculator.formatTime(0L), content.rows.first().lines.first())
        assertEquals("10.1235, 106.9877", content.coordinateText)
        assertTrue(content.rows[1].lines.first().contains("10.12345"))
        assertTrue(content.rows[2].lines.size >= 1)
    }

    @Test
    fun `same capture stamp builds identical preview and saved content`() {
        val stamp = CaptureStamp(
            timestampMs = 1710000000000L,
            latitude = 10.12345,
            longitude = 106.98765,
            address = "123 Street, District 1",
            note = "Kiem tra overlay",
            bearingDeg = 45f
        )

        val fromStamp = PhotoStampLayoutCalculator.buildContent(stamp, missingLocationText = "Khong co vi tri")
        val manual = PhotoStampLayoutCalculator.buildContent(
            timestampMs = stamp.timestampMs,
            address = stamp.address,
            latitude = stamp.latitude,
            longitude = stamp.longitude,
            note = stamp.note,
            missingLocationText = "Khong co vi tri"
        )

        assertEquals(fromStamp.rows, manual.rows)
        assertEquals(fromStamp.coordinateText, manual.coordinateText)
    }

    @Test
    fun `aspect crop helper matches expected bounds`() {
        assertEquals(
            AspectCropRect(240, 0, 1440, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_4_3)
        )
        assertEquals(
            AspectCropRect(0, 0, 1920, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_16_9)
        )
        assertEquals(
            AspectCropRect(420, 0, 1080, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_1_1)
        )
        assertEquals(
            AspectCropRect(0, 0, 1920, 1080),
            calculateAspectCropRect(1920, 1080, CameraAspectRatio.RATIO_FULL)
        )
    }

    @Test
    fun `layout scales text and minimap up for shared render`() {
        val rows = listOf(
            PhotoStampRow(PhotoStampLayoutCalculator.timeIcon, listOf("09:30  13/06/2026")),
            PhotoStampRow(PhotoStampLayoutCalculator.locationIcon, listOf("123 Street")),
        )

        val layout = PhotoStampLayoutCalculator.calculate(
            frameWidth = 1080f,
            frameHeight = 1920f,
            rows = rows,
            textWidth = { it.length * 16f },
            iconWidth = { 28f },
            showMap = true
        )

        assertEquals(22.68f, layout.textSize, 0.1f)
        assertEquals(24.192f, layout.iconSize, 0.1f)
        assertEquals(223.776f, layout.mapRect!!.width, 0.5f)
        assertEquals(223.776f, layout.mapRect.height, 0.5f)
    }
}
