package com.tonkeeper

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.facebook.drawee.backends.pipeline.Fresco
import ton.WalletManager

class App: Application(), CameraXConfig.Provider {

    companion object {

        lateinit var walletManager: WalletManager
        lateinit var settings: AppSettings
        lateinit var passcode: PasscodeManager
        lateinit var db: AppDatabase
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        instance = this
        db = AppDatabase.getInstance(this)
        walletManager = WalletManager(this)
        settings = AppSettings(this)
        passcode = PasscodeManager(this)

        initFresco()
    }

    private fun initFresco() {
        Fresco.initialize(this)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }
}