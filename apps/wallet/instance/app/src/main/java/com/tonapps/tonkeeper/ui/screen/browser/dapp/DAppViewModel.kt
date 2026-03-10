package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.app.Application
import android.net.Uri
import com.tonapps.tonkeeper.extensions.getAppFixIcon
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DAppViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectManager: TonConnectManager,
    override val url: Uri,
    private val dAppsRepository: DAppsRepository,
    private val settingsRepository: SettingsRepository,
    private val browserRepository: BrowserRepository
): InjectedTonConnectScreen.ViewModel(app, wallet, tonConnectManager) {

    val isDarkTheme: Boolean
        get() = settingsRepository.theme.resId == uikit.R.style.Theme_App_Dark || context.isDarkMode

    val installId: String
        get() = settingsRepository.installId

    val country: String
        get() = settingsRepository.country

    fun mute() {
        DAppPushToggleWorker.run(
            context = context,
            wallet = wallet,
            appUrl = url,
            enable = false
        )
    }

    suspend fun getApp(url: Uri): AppEntity = withContext(Dispatchers.IO) {
        dAppsRepository.getAppFixIcon(url, wallet, browserRepository, settingsRepository)
    }
}