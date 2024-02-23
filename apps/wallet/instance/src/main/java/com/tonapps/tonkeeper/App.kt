package com.tonapps.tonkeeper

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.tonapps.tonkeeper.core.fiat.Fiat
import com.tonapps.tonkeeper.event.WalletRemovedEvent
import core.EventBus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.tonapps.network.Network
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.libsodium.jni.NaCl
import ton.wallet.WalletManager

class App: Application(), CameraXConfig.Provider {

    companion object {

        @Deprecated("Use injection")
        lateinit var walletManager: WalletManager

        lateinit var fiat: Fiat
        lateinit var settings: SettingsRepository
        lateinit var passcode: PasscodeManager
        lateinit var db: AppDatabase
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        NaCl.sodium()
        Network.init(instance.applicationContext)

        db = AppDatabase.getInstance(this)
        walletManager = WalletManager(this)
        fiat = Fiat(this)
        settings = SettingsRepository(this)
        passcode = PasscodeManager(this)

        startKoin {
            androidContext(this@App)
            modules(koinModel)
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        initFresco()
    }

    fun getThemeRes(): Int {
        val themeName = settings.theme
        if (themeName == "dark") {
            return uikit.R.style.Theme_App_Dark
        } else if (themeName == "light") {
            return uikit.R.style.Theme_App_Light
        }
        return uikit.R.style.Theme_App_Blue
    }

    fun setThemeRes(newTheme: Int): Boolean {
        val currentTheme = getThemeRes()
        if (newTheme == uikit.R.style.Theme_App_Dark && newTheme != currentTheme) {
            settings.theme = "dark"
            return true
        } else if (newTheme == uikit.R.style.Theme_App_Light && newTheme != currentTheme) {
            settings.theme = "light"
            return true
        } else if (newTheme != currentTheme) {
            settings.theme = "blue"
            return true
        }
        return false
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
        configBuilder.setDownsampleEnabled(false)

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