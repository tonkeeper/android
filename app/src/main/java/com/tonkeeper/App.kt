package com.tonkeeper

import android.app.Application
import android.os.SystemClock
import com.tonkeeper.ton.WalletManager

class App: Application() {

    companion object {

        lateinit var walletManager: WalletManager
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        walletManager = WalletManager(this)
    }
}