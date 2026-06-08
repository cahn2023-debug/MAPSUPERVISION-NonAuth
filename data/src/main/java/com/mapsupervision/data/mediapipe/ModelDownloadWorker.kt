package com.mapsupervision.data.mediapipe

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mapsupervision.storage.ProjectStorageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * WorkManager worker for downloading MediaPipe LLM models
 * Downloads model files in the background when on Wi-Fi
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val projectStorageManager: ProjectStorageManager
) : CoroutineWorker(context, params) {

    companion object {
        const val MODEL_URL_KEY = "model_url"
        const val PROJECT_SLUG_KEY = "project_slug"
        const val MODEL_NAME_KEY = "model_name"
        
        // Gemma 2B model URL (example - replace with actual URL)
        const val GEMMA_2B_URL = "https://storage.googleapis.com/mediapipe-models/gemma-2b-it-cpu-int8.task"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val modelUrl = inputData.getString(MODEL_URL_KEY) ?: return@withContext Result.failure()
            val projectSlug = inputData.getString(PROJECT_SLUG_KEY) ?: return@withContext Result.failure()
            val modelName = inputData.getString(MODEL_NAME_KEY) ?: "gemma-2b.task"
            
            // Get model storage directory
            val modelDir = File(projectStorageManager.projectRoot(projectSlug), "models").apply { mkdirs() }
            val modelFile = File(modelDir, modelName)
            
            // Download model
            downloadModel(Uri.parse(modelUrl), modelFile)
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private fun downloadModel(uri: Uri, outputFile: File) {
        val inputStream = applicationContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Failed to open input stream")
        
        FileOutputStream(outputFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
    }
}
