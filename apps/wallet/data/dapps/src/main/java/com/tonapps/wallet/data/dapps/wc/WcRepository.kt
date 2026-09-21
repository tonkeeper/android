package com.tonapps.wallet.data.dapps.wc

import androidx.core.net.toUri
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.net.provider.NetworkProvider
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.log.L
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wallet.data.dapps.source.db.ConnectDao
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity
import com.tonapps.wallet.data.dapps.wc.WcRequestResult.Executed.Type
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wc.WalletConnectManager
import com.tonapps.wc.WcMethodParser
import com.tonapps.wc.exceptions.WalletConnectException
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcMethod
import com.tonapps.wc.models.WcError
import com.tonapps.wc.models.WcEvent
import com.tonapps.wc.models.WcPendingEvent
import com.tonapps.wc.models.WcSessionData
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

sealed interface WcRequestResult {
    val connection: WcConnection

    data class Connecting(
        override val connection: WcConnection
    ) : WcRequestResult

    data class Proposal(
        val data: DappConnectRequest,
        val requestId: String,
        override val connection: WcConnection,
    ) : WcRequestResult

    data class Request(
        val data: ConfirmRequest,
        val requestId: String,
        override val connection: WcConnection,
    ) : WcRequestResult

    data class Error(
        val cause: Throwable,
        override val connection: WcConnection,
    ) : WcRequestResult

    data class NoInternet(
        override val connection: WcConnection,
    ) : WcRequestResult

    data class Executed(
        override val connection: WcConnection,
        val isApproved: Boolean,
        val type: Type,
    ) : WcRequestResult {
        enum class Type {
            Session, Request
        }
    }
}

class WcRepository internal constructor(
    private val wcManager: WalletConnectManager,
    private val accountRepo: McAccountRepository,
    private val connectDao: ConnectDao,
    private val networkProvider: NetworkProvider,
) {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val requestParser = WcRequestParser()
    private val methodParser = WcMethodParser(json, requestParser)

    // Session + Common
    private val sources = ConcurrentHashMap<String, WcConnection>() // topic -> source cache
    private val pendingProposals = ConcurrentHashMap<String, DappConnectRequest>() // requestId -> proposal
    private val pendingRequests = ConcurrentHashMap<String, ConfirmRequest>() // requestId -> request

    private val requestFlow = MutableSharedFlow<WcRequestResult>()
    private val sessionFlow = MutableSharedFlow<WcPendingEvent>()

    val request: SharedFlow<WcRequestResult> = requestFlow.asSharedFlow()
    val sessions: SharedFlow<WcPendingEvent> = sessionFlow.asSharedFlow()

    init {
        wcManager.events.onEach { event ->
            when (event) {
                is WcEvent.Session -> handleSession(event)
                is WcEvent.Request -> handleRequest(event)
                is WcEvent.StateChanged -> handleStateChanged(event)
                is WcEvent.SessionError -> handleSessionError(event)
                is WcEvent.RequestError -> handleRequestError(event)
                is WcEvent.Error -> handleConnectError(event)
            }
        }.launchIn(Async.globalScope())
    }

    fun getActiveSessions(): List<WcSessionData> {
        return wcManager.activeSessions()
    }

    suspend fun pair(uri: String, connection: WcConnection = WcConnection.DApp) {
        if (!wcManager.isAvailable) {
            requestFlow.emit(WcRequestResult.Error(WalletConnectException(WcError.UNKNOWN), connection))
            return
        }

        parsePairingTopic(uri)?.let {
            L.d("WalletConnect", "Save session topic $it over $connection")
            sources[it] = connection
        }

        requestFlow.emit(WcRequestResult.Connecting(connection))
        wcManager.createPair(uri)
    }

    suspend fun approveProposal(
        requestId: String,
        walletId: String,
        chains: List<Chain>,
    ): Result<String> {
        return withContext(NonCancellable) {
            // TODO analytics: legacy emitted WCRequestResultEvent on approval
            val accounts = accountRepo.getCoinAccounts(walletId)
                .filter { it.chain in chains }
                .map { it.chainAccount }

            wcManager.approveSession(requestId, accounts)
                // TODO return on Error as well
                .onSuccess { pairingTopic ->
                    terminationEvent(requestId, pairingTopic, isSuccessful = true, Type.Session)

                    // TODO can success session event come faster than this one?
                    connectDao.insert(
                        ConnectEntity(
                            id = pairingTopic,
                            provider = DappProvider.WalletConnect,
                            walletId = walletId,
                            createdAt = currentTimeSeconds(),
                            source = sources[pairingTopic] ?: WcConnection.DApp,
                            appUrl = null,
                            accountId = null,
                            mode = null,
                            type = null,
                            topic = null,
                            keyPair = null,
                        )
                    )
                }
        }
    }

    suspend fun rejectProposal(requestId: String): Result<String> {
        return withContext(NonCancellable) {
            wcManager.rejectSession(requestId, WcError.UNKNOWN)
                .onSuccess { terminationEvent(requestId, it, false, Type.Session) }
        }
    }

    suspend fun approveRequest(requestId: String, signedResult: String): Result<String> {
        return withContext(NonCancellable) {
            wcManager.approveRequest(requestId, signedResult)
                .onSuccess { terminationEvent(requestId, it, true, Type.Request) }
        }
    }

    suspend fun rejectRequest(requestId: String, topic: String) {
        return withContext(NonCancellable) {
            wcManager.rejectRequest(requestId, WcError.UNKNOWN)
                // TODO maybe return before get successful state
                .also { terminationEvent(requestId, topic, false, Type.Request)  }
        }
    }

   private suspend fun terminationEvent(requestId: String, topic: String, isSuccessful: Boolean, type: Type) {
        pendingRequests.remove(requestId)
        sources[topic]?.let {
            requestFlow.emit(WcRequestResult.Executed(it, isSuccessful, type))
        }
    }

    suspend fun disconnect(topic: String): Result<String> {
        return wcManager.disconnectSession(topic)
    }

    fun findConnection(topic: String): WcConnection? {
        sources[topic]
            ?.let { return it }

        val source = connectDao.getWalletConnectByTopic(topic)
            ?.source
            ?.also { sources[topic] = it }

        return source
    }

    fun pendingProposal(requestId: String): DappConnectRequest? {
        return pendingProposals[requestId]
    }

    fun pendingRequest(requestId: String): ConfirmRequest? {
        return pendingRequests[requestId]
    }

    private suspend fun handleSession(event: WcEvent.Session) {
        val source = sources[event.proposal.topic] ?: WcConnection.DApp
        val dappRequest = methodParser.handleMultiSession(
            requestId = event.requestId,
            multiSession = event.proposal,
            wcConnection = source,
            validation = event.verifyContext,
        )

        pendingProposals[event.requestId] = dappRequest
        requestFlow.emit(WcRequestResult.Proposal(dappRequest, event.requestId, source))
    }

    private suspend fun handleRequest(event: WcEvent.Request) {
        val topic = event.request.topic
        val connection = findConnection(topic)
        if (connection == null) {
            handleRequestError(WcEvent.RequestError(event.requestId, topic, WcError.UNKNOWN))
            return
        }

        if (event.request.method == WcMethod.WALLET_GET_CAPABILITIES) {
            // EIP-5792: no advanced capabilities supported; respond silently.
            wcManager.approveRequest(event.requestId, "{}")
            return
        }

        if (event.request.method == WcMethod.WALLET_WATCH_ASSET) {
            wcManager.approveRequest(event.requestId, "{}")
            return
        }

        val walletId = connectDao.getWalletConnectByTopic(topic)?.walletId
        if (walletId == null) {
            handleRequestError(WcEvent.RequestError(event.requestId, topic, WcError.UNSUPPORTED_REQUEST_INVALID))
            return
        }

        // TODO watch-only gating: legacy rejected Wallet.Type.WATCH here
        val result = methodParser.handleRequest(
            requestId = event.requestId,
            walletId = walletId,
            chain = event.request.chain,
            params = event.request.data,
            method = event.request.method,
            meta = event.request.dapp,
        )

        when (result) {
            is WcMethodParser.Result.Request -> {
                pendingRequests[event.requestId] = result.data
                requestFlow.emit(WcRequestResult.Request(result.data, event.requestId, connection))
            }
            is WcMethodParser.Result.Error -> {
                result.throwable?.let { L.e(it) }
                handleRequestError(WcEvent.RequestError(event.requestId, topic, WcError.UNSUPPORTED_REQUEST_INVALID))
            }
            null -> {
                handleRequestError(WcEvent.RequestError(event.requestId, topic, WcError.UNSUPPORTED_REQUEST_INVALID))
            }
        }
    }

    private suspend fun handleStateChanged(event: WcEvent.StateChanged) {
        when (val state = event.state) {
            is WcPendingEvent.Created -> {
                connectDao.updateWalletConnectTopic(state.pairingTopic, state.topic)
            }
            is WcPendingEvent.Disconnected -> {
                connectDao.deleteWalletConnectByTopic(state.topic)
                sources.remove(state.topic)
            }
        }

        sessionFlow.emit(event.state)
    }

    private suspend fun handleSessionError(event: WcEvent.SessionError) {
        wcManager.rejectSession(event.requestId, event.error)
        sendError(event.topic, event.error)
    }

    private suspend fun handleConnectError(event: WcEvent.Error) {
        // Relay errors carry no topic. Surface one only when it's actually a connectivity problem,
        // so the user gets a clear "no internet" message instead of an opaque relay timeout.
        // Reconnection itself is the relay client's job: it re-subscribes and delivers the proposal
        // once connectivity returns, so there is nothing to cache or retry here.
        if (networkProvider.isConnected()) {
            return
        }
        L.e(event.cause, "WalletConnect", "relay error while offline")
        requestFlow.emit(WcRequestResult.NoInternet(WcConnection.DApp))
    }

    private suspend fun handleRequestError(event: WcEvent.RequestError): String? {
        val topic = when (event.topic) {
            null -> wcManager.rejectRequest(event.requestId, event.error)
                .onFailure { L.e(it, "WC", event) }
                .onSuccess { L.d("WC", "Rejected request", event) }
                .getOrNull()
            else -> event.topic
        }

        topic?.let { sendError(it, event.error) }

        return topic
    }

    private suspend fun sendError(topic: String, error: WcError) {
        findConnection(topic)?.let {
            requestFlow.emit(
                WcRequestResult.Error(
                    WalletConnectException(error),
                    it
                )
            )
        }
    }

    private fun parsePairingTopic(uri: String): String? {
        val wcUri = extractWcUri(uri) ?: return null
        val normalized = wcUri.replace("wc:", "wc://")
        return normalized.toUri().userInfo
    }

    private fun extractWcUri(uri: String): String? {
        if (uri.startsWith("wc:")) {
            return uri
        }

        val inner = runCatching { uri.toUri().getQueryParameter("uri") }.getOrNull()
        return inner?.takeIf { it.startsWith("wc:") }
    }
}
