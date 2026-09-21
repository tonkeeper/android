package com.tonapps.tonkeeper.manager.walletkit

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.core.net.toUri
import com.tonapps.base64.decodeBase64
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.hasQuery
import com.tonapps.extensions.isEmptyQuery
import com.tonapps.log.L
import com.tonapps.security.Security
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.manager.tonconnect.TonConnect
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager.Companion.normalizeUri
import com.tonapps.tonkeeper.manager.tonconnect.cleanupDisconnectedOrigin
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.manager.tonconnect.exceptions.ManifestException
import com.tonapps.tonkeeper.ui.component.SnackBarView
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.localization.Localization
import io.ton.walletkit.ITONWalletKit
import io.ton.walletkit.api.MAINNET
import io.ton.walletkit.api.TESTNET
import io.ton.walletkit.api.generated.TONConnectEventErrorCodes
import io.ton.walletkit.api.generated.TONConnectionApprovalProof
import io.ton.walletkit.api.generated.TONConnectionApprovalProofDomain
import io.ton.walletkit.api.generated.TONConnectionApprovalResponse
import io.ton.walletkit.api.generated.TONManifestFetchResult
import io.ton.walletkit.api.generated.TONNetwork
import io.ton.walletkit.config.SignDataType
import io.ton.walletkit.config.TONWalletKitConfiguration
import io.ton.walletkit.event.TONWalletKitEvent
import io.ton.walletkit.listener.TONBridgeEventsHandler
import io.ton.walletkit.model.TONBase64
import io.ton.walletkit.request.TONWalletConnectionRequest
import io.ton.walletkit.storage.TONWalletKitStorageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

class WalletKitTonConnect(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: UnifiedAccountRepository,
    private val dAppsRepository: DAppsRepository,
    private val pushManager: PushManager,
    private val chainKitProvider: ChainKitProvider,
    private val manager: TonConnectManager,
) : ITonConnectBridge by manager {

    @Volatile
    private var walletKit: ITONWalletKit? = null
    private val _initialized = MutableStateFlow(false)

    private val _walletKitEventFlow = MutableSharedFlow<WalletKitEvent>()
    override val walletKitEventFlow: Flow<WalletKitEvent> =
        _walletKitEventFlow.asSharedFlow()

    private val initMutex = Mutex()
    private val syncMutex = Mutex()
    private var walletObserverJob: Job? = null

    suspend fun initialize() {
        if (walletKit != null) {
            return
        }

        initMutex.withLock {
            if (walletKit != null) {
                return
            }

            val sessionManager = TonkeeperSessionManager(accountRepository, dAppsRepository)
            val config = TONWalletKitConfiguration(
                networkConfigurations = setOf(
                    TONWalletKitConfiguration.NetworkConfiguration(
                        network = TONNetwork.MAINNET,
                        apiClientConfiguration = TONWalletKitConfiguration.APIClientConfiguration(key = ""),
                    ),
                    TONWalletKitConfiguration.NetworkConfiguration(
                        network = TONNetwork.TESTNET,
                        apiClientConfiguration = TONWalletKitConfiguration.APIClientConfiguration(key = ""),
                    ),
                    TONWalletKitConfiguration.NetworkConfiguration(
                        network = TONNetwork(TonNetwork.TETRA.value.toString()),
                        apiClientConfiguration = TONWalletKitConfiguration.APIClientConfiguration(key = ""),
                    ),
                ),
                walletManifest = TONWalletKitConfiguration.Manifest(
                    name = MANIFEST_NAME,
                    appName = MANIFEST_APP_NAME,
                    imageUrl = MANIFEST_IMAGE_URL,
                    aboutUrl = MANIFEST_ABOUT_URL,
                    universalLink = MANIFEST_UNIVERSAL_LINK,
                    bridgeUrl = BRIDGE_URL,
                    jsBridgeKey = MANIFEST_JS_BRIDGE_KEY,
                ),
                bridge = TONWalletKitConfiguration.Bridge(bridgeUrl = BRIDGE_URL),
                features = listOf(
                    TONWalletKitConfiguration.SendTransactionFeature(maxMessages = 255),
                    TONWalletKitConfiguration.SignDataFeature(
                        types = listOf(SignDataType.TEXT, SignDataType.BINARY, SignDataType.CELL),
                    ),
                ),
                storageType = TONWalletKitStorageType.Encrypted,
                sessionManager = sessionManager,
                eventsConfiguration = TONWalletKitConfiguration.EventsConfiguration(
                    disableTransactionEmulation = true,
                ),
                fetchManifest = ::fetchManifest,
            )

            val walletKit = ITONWalletKit.initialize(context, config)
            walletKit.addEventsHandler(object : TONBridgeEventsHandler {
                override fun handle(event: TONWalletKitEvent) {
                    handleWalletKitEvent(event)
                }
            })

            this.walletKit = walletKit
            syncWalletList(walletKit)
            observeWalletChanges()
            _initialized.value = true
        }
    }

    private suspend fun fetchManifest(url: String): TONManifestFetchResult {
        return try {
            val body = manager.fetchManifestRaw(url)
            TONManifestFetchResult(manifest = Json.parseToJsonElement(body))
        } catch (e: ManifestException.NotFound) {
            TONManifestFetchResult(
                manifest = null,
                manifestFetchErrorCode = TONConnectEventErrorCodes.manifestNotFoundError,
            )
        } catch (e: Throwable) {
            L.w(e, "fetchManifest failed for $url")
            TONManifestFetchResult(
                manifest = null,
                manifestFetchErrorCode = TONConnectEventErrorCodes.manifestContentError,
            )
        }
    }

    private fun observeWalletChanges() {
        walletObserverJob?.cancel()
        walletObserverJob = accountRepository.selectedTonWalletFlow
            .onEach {
                val kit = walletKit ?: return@onEach
                syncWalletList(kit)
            }
            .launchIn(scope)
    }

    private fun handleWalletKitEvent(event: TONWalletKitEvent) {
        scope.launch(Dispatchers.IO) {
            try {
                when (event) {
                    is TONWalletKitEvent.ConnectRequest -> {
                        // Handled only in case connection event from web view
                        // Deeplink is handled directly to process additional parameters
                        // For example - needed to handle fromPackageNameParameter
                        _walletKitEventFlow.emit(WalletKitEvent.ConnectionRequest(request = event.request, fromPackageName = null))
                    }
                    is TONWalletKitEvent.SendTransactionRequest -> {
                        val request = event.request
                        val wallet = request.event.walletId?.let { accountRepository.getTonWalletById(it) }
                        if (wallet == null) {
                            L.w("Wallet not found for id: ${request.event.walletId}")
                            request.reject("Wallet not found", UNKNOWN_APP_ERROR)
                            return@launch
                        }
                        _walletKitEventFlow.emit(WalletKitEvent.SendTransactionRequest(request = request, wallet = wallet))
                    }
                    is TONWalletKitEvent.SignDataRequest -> {
                        val request = event.request
                        val wallet = request.event.walletId?.let { accountRepository.getTonWalletById(it) }
                        if (wallet == null) {
                            L.w("Wallet not found for id: ${request.event.walletId}")
                            request.reject("Wallet not found", UNKNOWN_APP_ERROR)
                            return@launch
                        }
                        _walletKitEventFlow.emit(WalletKitEvent.SignDataRequest(request = request, wallet = wallet))
                    }
                    is TONWalletKitEvent.SignMessageRequest -> {
                        // Sign-message (sign-only) isn't wired into the Tonkeeper flow yet
                        // (TonkeeperWalletAdapter.signedSignMessage is unsupported), so decline
                        // cleanly instead of silently dropping the request.
                        L.w("SignMessageRequest received but not supported")
                        event.request.reject("Sign message is not supported")
                    }
                    is TONWalletKitEvent.Disconnect -> {
                        // No need to additionally handle this event, because
                        // connection will be automatically deleted
                        // in session manager and dapps list will be
                        // notified and updated using connections flow
                    }
                    is TONWalletKitEvent.RequestError -> {
                        L.e("Request error: ${event.event}")
                    }
                }
            } catch (e: Exception) {
                L.e(e, "Error handling WalletKit event")
            }
        }
    }

    override fun connectBridge() {
        scope.launch(Dispatchers.IO) {
            try {
                initialize()
            } catch (e: Exception) {
                L.e(e, "Failed to initialize WalletKit on connectBridge")
            }
        }
    }

    override fun disconnectBridge() {}

    override fun reconnectBridge() {
        connectBridge()
    }

    override fun disconnect(wallet: WalletEntity, appUrl: Uri, type: AppConnectEntity.Type?) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.getMatchingConnections(
                accountId = wallet.accountId,
                network = wallet.network,
                appUrl = appUrl,
                type = type,
            )

            val kit = walletKit
            // Internal sessions live in the natively injected browser WebView, unknown to the kit.
            val bridgeConnections = connections.filter { it.type == AppConnectEntity.Type.External }
            if (kit != null && bridgeConnections.isNotEmpty()) {
                // Notifying the kit is best-effort: a session that fails, times out or cancels
                // itself must not cancel this scope and take the row delete below with it.
                coroutineScope {
                    bridgeConnections.map { connection ->
                        async {
                            try {
                                withTimeout(DISCONNECT_SESSION_TIMEOUT_MS) {
                                    kit.disconnectSession(connection.clientId)
                                }
                            } catch (e: Exception) {
                                L.w(e, "Failed to disconnect session ${connection.clientId}")
                            }
                        }
                    }.awaitAll()
                }
            }

            val removedRows = dAppsRepository.deleteApp(wallet.accountId, wallet.network, appUrl, type)

            if (connections.isEmpty() && removedRows.isEmpty()) {
                DevSettings.tonConnectLog("disconnect matched no connections for $appUrl", error = true)
            }

            dAppsRepository.cleanupDisconnectedOrigin(wallet, appUrl)

            if (connections.isNotEmpty()) {
                pushManager.dAppUnsubscribe(wallet, connections)
            }
        }
    }

    override suspend fun showLogoutAppBar(
        wallet: WalletEntity,
        context: Context,
        url: Uri,
        type: AppConnectEntity.Type?
    ) = withContext(Dispatchers.Main.immediate) {
        val host = url.host ?: run {
            DevSettings.tonConnectLog("skip disconnect prompt for a host-less url: $url", error = false)
            return@withContext
        }

        if (dAppsRepository.hasConnections(wallet.accountId, wallet.network, url, type)) {
            val text = context.getString(Localization.disconnect_dapp_confirm, host)
            SnackBarView.show(
                context = context,
                text = text,
                buttonText = context.getString(Localization.disconnect),
                onClickListener = View.OnClickListener {
                    disconnect(wallet, url, type)
                }
            )
        }
    }

    private suspend fun syncWalletList(kit: ITONWalletKit) {
        syncMutex.withLock {
            try {
                val wallets = accountRepository.getTonWallets().associateBy { it.id }
                val loadedIds = kit.getWallets().map { it.id }.toSet()

                for (id in loadedIds) {
                    if (id !in wallets) {
                        try {
                            kit.removeWallet(id)
                        } catch (e: Exception) {
                            L.w(e, "Failed to remove stale wallet $id from kit")
                        }
                    }
                }

                for (wallet in wallets.values) {
                    if (wallet.id in loadedIds) {
                        continue
                    }

                    try {
                        val adapter = if (wallet.type == WalletType.Multichain) {
                            val account = accountRepository.getMultichainTonAccount(wallet.id)
                            if (account == null) {
                                L.w("Skip MC wallet ${wallet.id}: no TON account")
                                continue
                            }

                            L.d("[WalletKitSync] MC wallet ${wallet.id} legacy.addr=${wallet.address} mc.addr=${account.displayAddress} pubKey=${account.publicKey}")
                            MultichainWalletAdapter(wallet, account, chainKitProvider)
                        } else {
                            TonkeeperWalletAdapter(wallet)
                        }
                        if (wallet.type == WalletType.Multichain && wallet.id in loadedIds) {
                            try {
                                L.d("[WalletKitSync] removing stale MC wallet ${wallet.id} before re-add")
                                kit.removeWallet(wallet.id)
                            } catch (e: Exception) {
                                L.w(e, "Failed to drop stale MC wallet ${wallet.id} before re-add")
                            }
                        }
                        kit.addWallet(adapter)
                        L.d("[WalletKitSync] added wallet ${wallet.id} (type=${wallet.type}) to kit")
                    } catch (e: Exception) {
                        L.e(e, "Failed to load wallet ${wallet.address}")
                    }
                }
            } catch (e: Exception) {
                L.e(e, "Failed to sync wallets")
            }
        }
    }

    override fun processDeeplink(
        context: Context,
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?,
        fromPackageName: String?
    ): Uri? {
        val returnUri = TonConnect.parseReturn(uri.getQueryParameter("ret"), refSource)

        try {
            val route = DeepLinkRoute.resolve(uri)
            if (route !is DeepLinkRoute.TonConnect) {
                return null
            }

            val normalizedUri = normalizeUri(uri)

            scope.launch {
                initialize()

                val tonConnectionRequest = walletKit?.connectionEventFromUrl(normalizedUri.toString())
                val dApp = tonConnectionRequest?.event?.dAppInfo
                val manifestUrl = dApp?.manifestUrl

                if (dApp == null || manifestUrl == null) {
                    return@launch
                }

                if (!isScam(
                        context,
                        WalletEntity.EMPTY,
                        uri,
                        normalizedUri,
                        manifestUrl.toUri()
                    )
                ) {
                    val url = dApp.url
                    val name = dApp.name
                    val iconUrl = dApp.iconUrl

                    if (url != null && name != null && iconUrl != null) {
                        try {
                            dAppsRepository.insertApp(AppEntity(url = url.toUri(), name = name, iconUrl = iconUrl, empty = false))
                            initialize()
                            // TODO: fix the production bug with fromPackageName = null
                            _walletKitEventFlow.emit(WalletKitEvent.ConnectionRequest(request = tonConnectionRequest, fromPackageName = fromPackageName))
                        } catch (e: Exception) {
                            L.w(e, "Failed to insert app before connect approval")
                        }
                    }
                }
            }
        }  catch (e: Exception) {
            if (uri.isEmptyQuery || uri.hasQuery("open") || uri.hasQuery("ret")) {
                return returnUri
            } else {
                context.showToast(Localization.invalid_link)
                return null
            }
        }
        return returnUri
    }

    override suspend fun sendTransactionResponseSuccess(connection: AppConnectEntity, boc: String, id: Long) {}

    override suspend fun sendBridgeError(connection: AppConnectEntity, error: BridgeError, id: Long) {}

    override suspend fun sendSignDataResponseSuccess(
        connection: AppConnectEntity,
        proof: TONProof.Result,
        address: String,
        payload: SignDataRequestPayload,
        id: Long
    ) {}

    override suspend fun approveConnectionRequest(
        request: TONWalletConnectionRequest,
        wallet: WalletEntity,
        proof: TONProof.Result?,
        pushEnabled: Boolean
    ): Boolean {
        return try {
            val kitWallet = walletKit?.getWallet(wallet.id) ?: return false
            val response = proof?.let { convertProofToResponse(it) }
            val from = request.event.from?.takeIf { it.isNotBlank() } ?: Security.randomBytes(32).toHexString()
            request.event.from = from
            request.approve(kitWallet, response)

            var connection = dAppsRepository
                .getConnections()
                .first { it.clientId == from }

            connection = connection.copy(
                    timestamp = proof?.timestamp ?: connection.timestamp,
                    pushEnabled = pushEnabled
                )

            dAppsRepository.newConnect(connection)
            dAppsRepository.setPushEnabled(connection.accountId, connection.network, connection.appUrl, pushEnabled)
            true
        } catch (e: Exception) {
            L.e(e, "Failed to approve connection")
            false
        }
    }
    
    override suspend fun rejectConnectionRequest(
        request: TONWalletConnectionRequest,
        reason: String?
    ): Boolean {
        return try {
            request.reject(reason)
            true
        } catch (e: Exception) {
            L.e(e, "Failed to reject connection")
            false
        }
    }

    private fun convertProofToResponse(proof: TONProof.Result): TONConnectionApprovalResponse {
        val signatureBytes = proof.signature.decodeBase64()
        return TONConnectionApprovalResponse(
            proof = TONConnectionApprovalProof(
                signature = TONBase64.fromData(signatureBytes),
                timestamp = proof.timestamp.toDouble(),
                domain = TONConnectionApprovalProofDomain(
                    lengthBytes = proof.domain.value.length,
                    value = proof.domain.value
                ),
                payload = proof.payload ?: ""
            )
        )
    }

    companion object {
        private const val UNKNOWN_APP_ERROR = 100

        private const val MANIFEST_NAME = "tonkeeper"
        private const val MANIFEST_APP_NAME = "Tonkeeper"
        private const val MANIFEST_JS_BRIDGE_KEY = "tonkeeper"
        private const val MANIFEST_IMAGE_URL = "https://tonkeeper.com/assets/tonkeeper-logo.png"
        private const val MANIFEST_ABOUT_URL = "https://tonkeeper.com"
        private const val MANIFEST_UNIVERSAL_LINK = "https://app.tonkeeper.com/ton-connect"
        private const val BRIDGE_URL = "https://bridge.tonapi.io/bridge"
        private const val DISCONNECT_SESSION_TIMEOUT_MS = 5_000L

        fun isEnabled(api: API): Boolean {
            return WalletFeature.WalletKitEnabled.isEnabled || !api.getConfig(TonNetwork.MAINNET).flags.disableWalletKit
        }
    }
}
