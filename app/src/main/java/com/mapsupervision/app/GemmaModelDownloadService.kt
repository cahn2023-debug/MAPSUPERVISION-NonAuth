package com.mapsupervision.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mapsupervision.data.mediapipe.GemmaDownloadFailure
import com.mapsupervision.data.mediapipe.GemmaDownloadProgress
import com.mapsupervision.data.mediapipe.GemmaDownloadState
import com.mapsupervision.data.mediapipe.GemmaDownloadStateStore
import com.mapsupervision.data.mediapipe.GemmaModelDownloader
import com.mapsupervision.data.mediapipe.GemmaModelManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@AndroidEntryPoint
class GemmaModelDownloadService : Service() {
    @Inject lateinit var modelManager: GemmaModelManager
    @Inject lateinit var downloadStateStore: GemmaDownloadStateStore
    @Inject lateinit var downloader: GemmaModelDownloader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelDownload(startId)
            return START_NOT_STICKY
        }
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val model = modelManager.supportedModels().firstOrNull { it.downloadFileName == modelId }
        if (model == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) {
            return START_REDELIVER_INTENT
        }
        beginDownload(model.downloadFileName, startId)
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun beginDownload(modelId: String, startId: Int) {
        val model = modelManager.supportedModels().firstOrNull { it.downloadFileName == modelId } ?: return
        val downloadUrl = model.url
        if (downloadUrl.isNullOrBlank()) {
            downloadStateStore.update(
                GemmaDownloadState.Failed(
                    workName = "gemma-service-${model.family.name.lowercase()}",
                    modelId = model.downloadFileName,
                    fileName = model.downloadFileName,
                    errorCode = "CONFIG_ERROR",
                    message = "Thieu URL tai model.",
                    httpCode = 0,
                    bytesDownloaded = 0L,
                    totalBytes = modelManager.expectedBytes(model),
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            stopSelf(startId)
            return
        }
        val workName = "gemma-service-${model.family.name.lowercase()}"
        val targetFile = modelManager.modelFile(model)
        val expectedBytes = modelManager.expectedBytes(model)
        val baseRunningState = GemmaDownloadState.Running(
            workName = workName,
            modelId = model.downloadFileName,
            fileName = model.downloadFileName,
            bytesDownloaded = targetFile.takeIf { it.exists() }?.length() ?: 0L,
            totalBytes = expectedBytes,
            lastUpdatedAt = System.currentTimeMillis()
        )
        downloadStateStore.update(baseRunningState)
        startForegroundCompat(baseRunningState)
        acquireWakeLock()

        downloadJob = serviceScope.launch {
            var lastProgress = baseRunningState
            try {
                while (true) {
                    try {
                        downloader.download(
                            url = downloadUrl,
                            targetFile = targetFile,
                            expectedBytes = expectedBytes
                        ) { progress ->
                            lastProgress = GemmaDownloadState.Running(
                                workName = workName,
                                modelId = model.downloadFileName,
                                fileName = model.downloadFileName,
                                bytesDownloaded = progress.bytesDownloaded,
                                totalBytes = progress.totalBytes,
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                            publishRunning(lastProgress)
                        }
                        val completed = GemmaDownloadState.Completed(
                            workName = workName,
                            modelId = model.downloadFileName,
                            fileName = model.downloadFileName,
                            totalBytes = expectedBytes,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                        downloadStateStore.update(completed)
                        showTerminalNotification(buildCompletedNotification(model.displayName))
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: GemmaDownloadFailure) {
                        if (failure.code == "NETWORK_ERROR") {
                            val waitingState = lastProgress.copy(
                                warningCode = failure.code,
                                warningMessage = "Dang cho mang de tiep tuc tai",
                                httpCode = failure.httpCode,
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                            publishRunning(waitingState)
                            waitForNetwork()
                            continue
                        }
                        val failed = GemmaDownloadState.Failed(
                            workName = workName,
                            modelId = model.downloadFileName,
                            fileName = model.downloadFileName,
                            errorCode = failure.code,
                            message = failure.userMessage,
                            httpCode = failure.httpCode,
                            bytesDownloaded = lastProgress.bytesDownloaded,
                            totalBytes = expectedBytes,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                        downloadStateStore.update(failed)
                        showTerminalNotification(buildFailedNotification(model.displayName, failure.userMessage))
                        break
                    }
                }
            } finally {
                releaseWakeLock()
                downloadJob = null
                stopSelf(startId)
            }
        }
    }

    private fun publishRunning(state: GemmaDownloadState.Running) {
        downloadStateStore.update(state)
        notificationManager.notify(NOTIFICATION_ID, buildRunningNotification(state))
    }

    private fun cancelDownload(startId: Int) {
        downloadJob?.cancel(CancellationException("User cancelled model download"))
        downloadStateStore.update(GemmaDownloadState.Idle)
        removeForegroundNotification()
        releaseWakeLock()
        stopSelf(startId)
    }

    private suspend fun waitForNetwork() {
        if (hasNetwork()) return
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
            ?: return
        suspendCancellableCoroutine { continuation ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.unregisterNetworkCallback(this)
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
            continuation.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }

    private fun hasNetwork(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startForegroundCompat(state: GemmaDownloadState.Running) {
        val notification = buildRunningNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfoCompat.DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildRunningNotification(state: GemmaDownloadState.Running): Notification {
        val message = if (state.warningMessage.isNotBlank()) {
            state.warningMessage
        } else {
            "${formatFileSize(state.bytesDownloaded)} / ${formatFileSize(state.totalBytes)}"
        }
        val builder = baseNotificationBuilder()
            .setContentTitle("Dang tai ${state.fileName}")
            .setContentText(message)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Huy",
                cancelPendingIntent()
            )
        val progress = if (state.totalBytes > 0L) {
            ((state.bytesDownloaded * 1000L) / state.totalBytes).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        builder.setProgress(1000, progress, state.totalBytes <= 0L)
        return builder.build()
    }

    private fun buildCompletedNotification(modelName: String): Notification {
        return baseNotificationBuilder()
            .setContentTitle("Da tai xong model")
            .setContentText(modelName)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun buildFailedNotification(modelName: String, message: String): Notification {
        return baseNotificationBuilder()
            .setContentTitle("Tai model that bai")
            .setContentText(modelName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun baseNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(this, GemmaModelDownloadService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showTerminalNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gemma downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:GemmaDownload").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun formatFileSize(sizeBytes: Long): String = Formatter.formatFileSize(this, sizeBytes)

    companion object {
        private const val ACTION_CANCEL = "com.mapsupervision.app.action.CANCEL_GEMMA_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "extra_model_id"
        private const val CHANNEL_ID = "gemma_model_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, GemmaModelDownloadService::class.java)
                .putExtra(EXTRA_MODEL_ID, modelId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, GemmaModelDownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}

private object ServiceInfoCompat {
    const val DATA_SYNC = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
}
