package com.tonapps.signer

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.tonapps.signer.screen.crash.CrashActivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import security.Security
import java.io.PrintWriter
import java.io.StringWriter


class App: Application(), CameraXConfig.Provider {

    companion object {
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            CrashActivity.open(e)
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        startKoin{
            androidContext(this@App)
            modules(koinModel)
        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .setMinimumLoggingLevel(Log.ERROR).build()
    }
}