package com.tonapps.tonkeeper.manager.tonconnect

import android.content.Context
import android.net.Uri
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.manager.walletkit.WalletKitEvent
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppBridge
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import io.ton.walletkit.request.TONWalletConnectionRequest
import com.tonapps.security.CryptoBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import uikit.navigation.NavigationActivity

/** Common interface for TonConnect implementations (legacy vs WalletKit). */
interface ITonConnectBridge {

    val transactionRequestFlow: Flow<Pair<AppConnectEntity, BridgeEvent.Message>>

    val signDataRequestFlow: Flow<BridgeEvent>

    val walletKitEventFlow: Flow<WalletKitEvent>
        get() = emptyFlow()

    fun connectBridge() {}

    fun reconnectBridge() {}

    fun disconnectBridge() {}

    fun processDeeplink(
        context: Context,
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?,
        fromPackageName: String?
    ): Uri?

    suspend fun sendTransactionResponseSuccess(
        connection: AppConnectEntity,
        boc: String,
        id: Long
    )

    suspend fun sendBridgeError(
        connection: AppConnectEntity,
        error: BridgeError,
        id: Long
    )

    suspend fun sendSignDataResponseSuccess(
        connection: AppConnectEntity,
        proof: TONProof.Result,
        address: String,
        payload: SignDataRequestPayload,
        id: Long
    )

    fun walletConnectionsFlow(wallet: WalletEntity): Flow<List<AppConnectEntity>>

    fun walletAppsFlow(wallet: WalletEntity): Flow<List<AppEntity>>

    fun disconnect(wallet: WalletEntity, appUrl: Uri, type: AppConnectEntity.Type? = null)

    suspend fun clear(wallet: WalletEntity)

    fun setLastAppRequestId(clientId: String, messageId: Long)

    fun isPushEnabled(wallet: WalletEntity, appUrl: Uri): Boolean

    suspend fun showLogoutAppBar(wallet: WalletEntity, context: Context, url: Uri)

    fun createInjector(
        bridge: DAppBridge,
        wallet: WalletEntity? = null
    ): ITonConnectWebViewInjector

    suspend fun isScam(context: Context, wallet: WalletEntity, vararg uris: Uri): Boolean

    suspend fun launchConnectFlow(
        activity: NavigationActivity,
        tonConnect: TonConnect,
        keyPair: CryptoBox.KeyPair = CryptoBox.keyPair(),
        wallet: WalletEntity?,
        forceConnect: Boolean = false
    ): JSONObject

    suspend fun getConnection(
        accountId: String,
        network: TonNetwork,
        appUrl: Uri,
        type: AppConnectEntity.Type
    ): AppConnectEntity?

    suspend fun approveConnectionRequest(
        request: TONWalletConnectionRequest,
        wallet: WalletEntity,
        proof: TONProof.Result? = null,
        pushEnabled: Boolean = false
    ): Boolean = throw UnsupportedOperationException("Only supported by WalletKit")

    suspend fun rejectConnectionRequest(
        request: TONWalletConnectionRequest,
        reason: String? = null
    ): Boolean = throw UnsupportedOperationException("Only supported by WalletKit")
}
