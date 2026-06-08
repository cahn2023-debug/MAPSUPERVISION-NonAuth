package com.mapsupervision.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the public project root under Downloads/MapSupervision/Projects/<slug>.
     * Falls back to internal filesDir if external storage is not available.
     */
    fun projectRoot(projectSlug: String): File {
        val base = publicBaseDir()
        val root = File(base, "Projects/$projectSlug")
        listOf("photos/Nodes", "thumbs", "reports", "exports", "imports", "imports/pending", "imports/processed", "imports/failed", "db").forEach { child ->
            File(root, child).mkdirs()
        }
        return root
    }

    /**
     * Returns Downloads/MapSupervision, creating it if needed.
     * Falls back to internal filesDir/MapSupervision on failure.
     */
    fun publicBaseDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "MapSupervision")
        return if (dir.mkdirs() || dir.exists()) dir
        else File(context.filesDir, "MapSupervision").also { it.mkdirs() }
    }

    /**
     * Notifies the system MediaScanner so the file appears in gallery/file manager immediately.
     */
    fun scanFile(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }
}
