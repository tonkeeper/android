package com.tonapps.wc

import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.wc.models.WcAppData
import com.tonapps.wc.models.WcError
import com.tonapps.wc.models.WcEvent
import com.tonapps.wc.models.WcEventData
import com.tonapps.wc.models.WcPendingEvent
import com.tonapps.wc.models.WcSessionData
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WalletConnectManager {

    val isAvailable: Boolean
        get() = WcInitializer.isInitialized

    private val walletDelegate = WcEventDelegate()
    private val requestController = WcRequestController(walletDelegate) 

    private val wcEvents = MutableSharedFlow<WcEvent>(0, Int.MAX_VALUE, BufferOverflow.DROP_OLDEST)
    val events: Flow<WcEvent> = merge(wcEvents, requestController.events)

    init {
        if (WcInitializer.isInitialized) {
            WalletKit.setWalletDelegate(walletDelegate)
        }
    }

    fun createPair(uri: String) {
        L.d("WalletConnect", "createPair $uri")
        if (!WcInitializer.isInitialized) {
            L.e("WalletConnect", "createPair is unavailable: WalletKit is not initialized")
            return
        }
        val params = Wallet.Params.Pair(uri)
        WalletKit.pair(
            params = params,
            onError = {
                L.e("WalletConnect", it)
            },
            onSuccess = {
                L.e("WalletConnect", it)
            }
        )
    }

    fun activeSessions(): List<WcSessionData> {
        if (!WcInitializer.isInitialized) {
            return emptyList()
        }
        return WalletKit.getListOfActiveSessions()
            .mapNotNull {
                it.metaData?.let { app ->
                    WcSessionData(
                        topic = it.topic,
                        app = WcAppData(
                            name = app.name,
                            url = app.url,
                            description = app.description,
                            icon = app.icons.firstOrNull(),
                        ),
                        chains = it.namespaces.values.flatMap { ns ->
                            ns.chains.orEmpty().mapNotNull { caip2 -> Chain.fromCaip2(caip2)?.network?.type }
                        }.toSet(),
                    )
                }
            }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun approveSession(
        requestId: String,
        accounts: List<Account>,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        L.d("WalletConnect", "approveSession")
        val event = requestController.request(requestId) as WcEventData.SessionProposal

        val namespaces = WalletKit.generateApprovedNamespaces(
            event.data,
            buildSessionNamespaces(event.data, accounts)
        )

        val approveParams = Wallet.Params.SessionApprove(
            proposerPublicKey = event.data.proposerPublicKey,
            namespaces = namespaces,
            relayProtocol = event.data.relayProtocol,
        )

        WalletKit.approveSession(
            params = approveParams,
            onError = { e ->
                L.e("WalletConnect", e)
                cont.resume(Result.failure(e.throwable))
            },
            onSuccess = {
                L.d("WalletConnect", "session accepted")
                requestController.cleanup(requestId)
                cont.resume(Result.success(event.data.pairingTopic))
            },
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun rejectSession(
        requestId: String,
        error: WcError,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        L.d("WalletConnect", "rejectSession")
        val event = requestController.request(requestId) as WcEventData.SessionProposal

        val rejectParam = Wallet.Params.SessionReject(
            proposerPublicKey = event.data.proposerPublicKey,
            reason = error.message,
        )

        WalletKit.rejectSession(
            params = rejectParam,
            onError = { e ->
                L.e("WalletConnect", e)
                cont.resume(Result.failure(e.throwable))
            },
            onSuccess = {
                L.e("WalletConnect", "session rejected $error")
                requestController.cleanup(requestId)
                cont.resume(Result.success(event.data.pairingTopic))
            },
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun approveRequest(
        requestId: String,
        data: String,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        L.d("WalletConnect", "approveRequest", data)
        val request = requestController.request(requestId) as WcEventData.SessionRequest

        val result = Wallet.Params.SessionRequestResponse(
            sessionTopic = request.data.topic,
            jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcResult(
                id = request.data.request.id,
                result = data,
            ),
        )

        WalletKit.respondSessionRequest(
            params = result,
            onError = { e ->
                L.e("WalletConnect", e)
                cont.resume(Result.failure(e.throwable))
            },
            onSuccess = {
                L.d("WalletConnect", "request accepted")
                cont.resume(Result.success(request.data.topic))
            },
        )
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun rejectRequest(
        requestId: String,
        error: WcError = WcError.UNKNOWN,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        L.d("WalletConnect", "rejectRequest")
        val request = requestController.request(requestId) as WcEventData.SessionRequest

        val result = Wallet.Params.SessionRequestResponse(
            sessionTopic = request.data.topic,
            jsonRpcResponse = Wallet.Model.JsonRpcResponse.JsonRpcError(
                id = request.data.request.id,
                code = error.code,
                message = error.message,
            ),
        )

        WalletKit.respondSessionRequest(
            params = result,
            onError = { e ->
                L.e("WalletConnect", e)
                cont.resume(Result.failure(e.throwable))
            },
            onSuccess = {
                L.e("WalletConnect", "request rejected $error")
                cont.resume(Result.success(request.data.topic))
            },
        )
    }
    
    @OptIn(InternalCoroutinesApi::class)
    suspend fun disconnectSession(topic: String) = suspendCancellableCoroutine { cont ->
        val disconnectParams = Wallet.Params.SessionDisconnect(topic)

        WalletKit.disconnectSession(
            params = disconnectParams,
            onError = { e ->
                L.e("WalletConnect", e)
                cont.resume(Result.failure(e.throwable))
            },

            onSuccess = {
                wcEvents.tryEmit(
                    WcEvent.StateChanged(
                        WcPendingEvent.Disconnected(topic),
                    ),
                )
                cont.resume(Result.success(it.sessionTopic))
            },
        )
    }

    private fun buildSessionNamespaces(
        proposal: Wallet.Model.SessionProposal,
        accounts: List<Account>,
    ): Map<String, Wallet.Model.Namespace.Session> {
        L.d("WalletConnect", "buildSessionNamespaces")
        val grouped = accounts.groupBy { it.asset.chain.network.group.caip2 }

        return grouped.mapValues { (key, namespaceAccounts) ->
            val required = proposal.requiredNamespaces[key]
            val optional = proposal.optionalNamespaces[key]

            Wallet.Model.Namespace.Session(
                chains = namespaceAccounts.map { it.asset.chain.caip2 }.distinct(),
                accounts = namespaceAccounts.map { it.address.caip2 }.distinct(),
                methods = ((required?.methods.orEmpty()) + (optional?.methods.orEmpty())).distinct(),
                events = ((required?.events.orEmpty()) + (optional?.events.orEmpty())).distinct(),
            )
        }
    }
}
