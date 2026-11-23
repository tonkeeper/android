package com.tonapps.signer

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.tonapps.signer.screen.crash.CrashActivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File

class App : Application(), CameraXConfig.Provider {

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!isCrashProcess()) {
            val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, e ->
                try {
                    CrashActivity.open(e)
                } finally {
                    systemHandler?.uncaughtException(thread, e)
                    Process.killProcess(Process.myPid())
                }
            }
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        startKoin {
            androidContext(this@App)
            modules(koinModel)
        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
    }

    private fun isCrashProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            File("/proc/self/cmdline").readText().substringBefore(Char.MIN_VALUE)
        }
        return processName.endsWith(CRASH_PROCESS_SUFFIX)
    }

    companion object {
        lateinit var instance: App
    }
}

private const val CRASH_PROCESS_SUFFIX = ":crash_activity"
