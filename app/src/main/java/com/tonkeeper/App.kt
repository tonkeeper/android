package com.tonkeeper

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.drawee.backends.pipeline.Fresco
import ton.WalletManager

class App: Application() {

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
}