package com.tonkeeper

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.tonkeeper.core.fiat.Fiat
import com.tonkeeper.event.ChangeWalletNameEvent
import com.tonkeeper.event.WalletRemovedEvent
import core.EventBus
import ton.wallet.WalletManager

class App: Application(), CameraXConfig.Provider {

    companion object {

        lateinit var walletManager: WalletManager
        lateinit var fiat: Fiat
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
        fiat = Fiat(this)
        settings = AppSettings(this)
        passcode = PasscodeManager(this)

        initFresco()
    }

    fun setWalletName(address: String, name: String) {
        walletManager.setWalletName(address, name)
        EventBus.post(ChangeWalletNameEvent(address, name))
    }

    fun deleteWallet(address: String?) {
        walletManager.logout(address)
        address?.let {
            EventBus.post(WalletRemovedEvent(address))
        }
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