package com.tonkeeper

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.tonkeeper.core.fiat.Fiat
import com.tonkeeper.event.ChangeWalletNameEvent
import com.tonkeeper.event.WalletRemovedEvent
import com.tonkeeper.settings.AppSettings
import core.EventBus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.libsodium.jni.NaCl
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
        NaCl.sodium()

        instance = this
        db = AppDatabase.getInstance(this)
        walletManager = WalletManager(this)
        fiat = Fiat(this)
        settings = AppSettings(this)
        passcode = PasscodeManager(this)

        if (settings.experimental.lightTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        initFresco()
    }

    fun setWalletName(address: String, name: String) {
        GlobalScope.launch {
            walletManager.setWalletName(address, name)
            EventBus.post(ChangeWalletNameEvent(address, name))
        }
    }

    fun deleteWallet(address: String?) {
        GlobalScope.launch {
            walletManager.logout(address)
            address?.let {
                EventBus.post(WalletRemovedEvent(address))
            }
        }
    }

    private fun initFresco() {
        val configBuilder = ImagePipelineConfig.newBuilder(this)
        configBuilder.experiment().setNativeCodeDisabled(true)
        configBuilder.setDownsampleEnabled(true)

        Fresco.initialize(this, configBuilder.build())
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }

    fun isOriginalAppInstalled(): Boolean {
        val pm = packageManager
        return try {
            pm.getPackageInfo("com.ton_keeper", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}