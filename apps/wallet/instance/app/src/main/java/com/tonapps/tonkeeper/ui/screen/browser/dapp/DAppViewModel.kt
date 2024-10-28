package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.app.Application
import android.net.Uri
import android.util.Log
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.filterList
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

class DAppViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val url: Uri,
    private val tonConnectManager: TonConnectManager
): BaseWalletVM(app) {

    val connectionFlow = tonConnectManager.walletConnectionsFlow(wallet).filterList { connection ->
        connection.type == AppConnectEntity.Type.Internal && connection.appUrl == url
    }.map { it.firstOrNull() }

    fun mute() {
        DAppPushToggleWorker.run(
            context = context,
            wallet = wallet,
            appUrl = url,
            enable = false
        )
    }

    fun disconnect() {
        tonConnectManager.disconnect(wallet, url, AppConnectEntity.Type.Internal)
    }

    suspend fun restoreConnection(): JSONObject {
        val connection = loadConnection()
        return if (connection == null) {
            JsonBuilder.connectEventError(BridgeError.unknownApp())
        } else {
            JsonBuilder.connectEventSuccess(wallet, null, null, context.appVersionName)
        }
    }

    private suspend fun loadConnection(): AppConnectEntity? = withTimeoutOrNull(2.seconds) {
        while (isActive) {
            val connection = connectionFlow.first()
            if (connection != null) {
                return@withTimeoutOrNull connection
            }
            delay(100)
        }
        return@withTimeoutOrNull null
    }
}