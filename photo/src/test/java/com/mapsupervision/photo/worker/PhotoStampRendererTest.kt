package com.mapsupervision.photo.worker

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoStampRendererTest {

    @Test
    fun `loadMutableNormalizedBitmap rotates image and writeBitmap resets exif`() {
        val tempFile = File.createTempFile("photo-orientation", ".jpg")
        tempFile.deleteOnExit()

        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        tempFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        bitmap.recycle()

        ExifInterface(tempFile.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val normalized = PhotoStampRenderer.loadMutableNormalizedBitmap(tempFile)
        assertNotNull(normalized)
        assertEquals(20, normalized!!.width)
        assertEquals(40, normalized.height)

        PhotoStampRenderer.writeBitmap(tempFile, normalized, 90)

        val savedOrientation = ExifInterface(tempFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        assertEquals(ExifInterface.ORIENTATION_NORMAL, savedOrientation)
    }
}
