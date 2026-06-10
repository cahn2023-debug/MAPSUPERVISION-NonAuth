package com.mapsupervision.app

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mapsupervision.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class MapSupervisionApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        val isDebugBuild = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppLogger.init(this, isDebugBuild)
        if (isDebugBuild) {
            Timber.plant(Timber.DebugTree())
        }
        installOptionalMapBridge()
    }

    private fun installOptionalMapBridge() {
        runCatching {
            val clazz = Class.forName("com.mapsupervision.gis.maplibre.MapBridgeInstaller")
            val method = clazz.getMethod("install", android.content.Context::class.java)
            method.invoke(null, this)
            AppLogger.d("MapBridge installed successfully")
        }.onFailure { e ->
            AppLogger.e(e, "Failed to install MapBridge")
        }
    }

    override fun newImageLoader(): ImageLoader {
        val isLowRam = isLowRamDevice(this)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.08 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .bitmapConfig(if (isLowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .allowHardware(true)
            .crossfade(true)
            .build()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.isLowRamDevice || activityManager.memoryClass <= 192
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching {
                coil.Coil.imageLoader(this).memoryCache?.clear()
                System.gc()
            }
        }
    }
}
