package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.facebook.drawee.backends.pipeline.Fresco
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.filterList
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.extensions.loadSquare
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

class DAppViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectManager: TonConnectManager,
    override val url: Uri,
    private val dAppsRepository: DAppsRepository,
    private val settingsRepository: SettingsRepository,
): InjectedTonConnectScreen.ViewModel(app, wallet, tonConnectManager) {

    val isDarkTheme: Boolean
        get() = settingsRepository.theme.resId == uikit.R.style.Theme_App_Dark || context.isDarkMode


    fun mute() {
        DAppPushToggleWorker.run(
            context = context,
            wallet = wallet,
            appUrl = url,
            enable = false
        )
    }

    suspend fun getApp(): AppEntity = withContext(Dispatchers.IO) {
        dAppsRepository.getApp(url)
    }
}