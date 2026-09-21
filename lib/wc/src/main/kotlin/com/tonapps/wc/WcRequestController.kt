package com.tonapps.wc

import com.reown.walletkit.client.Wallet
import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.wallet.ChainConfig
import com.tonapps.wc.exceptions.WalletConnectException
import com.tonapps.wc.models.WcAppData
import com.tonapps.wc.models.WcChain
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcError
import com.tonapps.wc.models.WcEvent
import com.tonapps.wc.models.WcEventData
import com.tonapps.wc.models.WcMethod
import com.tonapps.wc.models.WcPendingEvent
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcSessionRequest
import com.tonapps.wc.models.WcValidation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class WcRequestController(
    walletDelegate: WcEventDelegate,
) {
    private val eventsStore = ConcurrentHashMap<String, WcEventData>()

    private val wcEvents = MutableSharedFlow<WcEvent>(0, Int.MAX_VALUE, BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<WcEvent> = wcEvents

    private val wcConnection = MutableStateFlow(false)
    val connection: SharedFlow<Boolean> = wcConnection

    init {
        walletDelegate.events.onEach { model ->
            L.d("WC", "New event: $model")
            when (model) {
                is WcEventData.Error -> wcEvents.emit(WcEvent.Error(model.data.throwable))
                is WcEventData.SessionUpdateResponse -> {}

                is WcEventData.ConnectionState -> wcConnection.tryEmit(model.data.isAvailable)
                is WcEventData.SettledSessionResponse -> if (model.data is Wallet.Model.SettledSessionResponse.Result) {
                    wcEvents.emit(
                        WcEvent.StateChanged(
                            WcPendingEvent.Created(model.data.session.topic, model.data.session.pairingTopic),
                        ),
                    )
                }
                is WcEventData.SessionDelete -> if (model.data is Wallet.Model.SessionDelete.Success) {
                    wcEvents.emit(WcEvent.StateChanged(WcPendingEvent.Disconnected(model.data.topic)))
                }

                is WcEventData.SessionProposal -> {
                    val id = generateId()
                    eventsStore[id] = model

                    val proposal = model.data.mapProposal()
                    if (proposal == null) {
                        wcEvents.emit(WcEvent.SessionError(id, model.data.pairingTopic, WcError.UNSUPPORTED_CHAIN))
                    } else {
                        val validation = when (model.verifyContext.isScam) {
                            true -> WcValidation.Scam
                            else -> when (model.verifyContext.validation) {
                                Wallet.Model.Validation.VALID -> WcValidation.Valid
                                Wallet.Model.Validation.INVALID -> WcValidation.Invalid
                                Wallet.Model.Validation.UNKNOWN -> WcValidation.Unknown
                            }
                        }

                        wcEvents.emit(WcEvent.Session(id, proposal, validation))
                    }
                }
                is WcEventData.SessionRequest -> {
                    val id = generateId()
                    eventsStore[id] = model

                    val data = model.data
                    val method = WcMethod.findByName(data.request.method)
                        ?: return@onEach wcEvents.emit(
                            WcEvent.RequestError(
                                id,
                                data.topic,
                                WcError.UNSUPPORTED_REQUEST_METHOD,
                            ),
                        )

                    val chain = data.chainId?.let { Chain.fromCaip2(it) }
                        ?: return@onEach wcEvents.emit(
                            WcEvent.RequestError(
                                id,
                                data.topic,
                                WcError.UNSUPPORTED_REQUEST_CHAIN,
                            ),
                        )

                    val meta = data.peerMetaData.let { meta ->
                        WcDappConnection(
                            data.topic,
                            WcAppData(
                                meta?.name.orEmpty(),
                                meta?.url.orEmpty(),
                                meta?.description.orEmpty(),
                                meta?.icons?.firstOrNull(),
                            ),
                        )
                    }

                    val request = WcSessionRequest(
                        method = method,
                        data = data.request.params,
                        topic = data.topic,
                        chain = chain,
                        dapp = meta,
                    )

                    wcEvents.emit(WcEvent.Request(id, request))
                }
            }
        }.launchIn(Async.globalScope())
    }

    fun request(requestId: String): WcEventData? {
        return eventsStore[requestId]
    }

    fun cleanup(requestId: String) {
        eventsStore.remove(requestId)
    }

    private fun Wallet.Model.SessionProposal.mapProposal(): WcSessionProposal? {
        return try {
            val required = requiredNamespaces.mapPermissions(isRequired = true)
            val optional = optionalNamespaces.mapPermissions(isRequired = false)
            val chains = (required + optional).distinctBy { it.value }
            if (chains.isEmpty()) {
                return null
            }

            WcSessionProposal(
                topic = pairingTopic,
                app = WcAppData(
                    name = name,
                    url = url,
                    description = description,
                    icon = icons.firstOrNull()?.toASCIIString(),
                ),
                url = url,
                chains = chains,
            )
        } catch (e: Throwable) {
            null
        }
    }

    private fun Map<String, Wallet.Model.Namespace.Proposal>.mapPermissions(
        isRequired: Boolean = false
    ): List<WcChain> {
        return flatMap { (_, data) ->
            data.chains.orEmpty().mapNotNull { caip2 ->
                val chain = Chain.fromCaip2(caip2)?.takeIf { ChainConfig.isWcSupported(it) }
                if (chain == null && isRequired) {
                    throw WalletConnectException(WcError.UNSUPPORTED_CHAIN)
                }
                chain?.let { WcChain(it, isRequired = isRequired) }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateId(): String {
        return Uuid.generateV4().toString()
    }
}

