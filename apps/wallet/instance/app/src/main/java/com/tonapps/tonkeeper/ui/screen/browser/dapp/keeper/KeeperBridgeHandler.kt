package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.log.L
import com.tonapps.webview.bridge.InAppBridgeContract.Effect
import com.tonapps.webview.bridge.InAppBridgeContract.MessageHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppPageEvent
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.AppSettings
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Credentials
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.ErrorCode
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Request
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperBridgeProtocol.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class KeeperBridgeHandler(
    private val scope: CoroutineScope,
    private val loadCredentials: suspend (expiredAccessToken: String?) -> Credentials?,
    private val appSettings: () -> AppSettings,
    private val track: (event: String, params: MutableMap<String, Any>) -> Unit,
    private val referralCode: suspend (host: String) -> String?,
    walletDeactivated: Flow<Unit>,
) : MessageHandler<Request, Response> {

    private var session: Job? = null

    private var outgoing = emptyOutgoing()

    private val pendingEffects = Channel<Effect>(Channel.UNLIMITED)

    override val responses: Flow<Response>
        get() = outgoing.receiveAsFlow()

    override val effects: Flow<Effect> = pendingEffects.receiveAsFlow()

    init {
        scope.launch {
            walletDeactivated.collect { pendingEffects.trySend(Effect.Close) }
        }
    }

    override fun attach() {
        session?.cancel()
        session = SupervisorJob(scope.coroutineContext[Job])
        outgoing = emptyOutgoing()
    }

    override fun onMessage(message: Request) {
        val session = session ?: return
        val outgoing = outgoing
        when (message) {
            is Request.Rejected -> outgoing.trySend(Response.Failure(message.queryId, message.error))
            is Request.Close -> acknowledge(outgoing, message.queryId, Effect.Close)
            is Request.Haptic -> acknowledge(outgoing, message.queryId, Effect.Haptic(message.style))
            is Request.SetCloseButton -> acknowledge(outgoing, message.queryId, Effect.CloseButtonVisibility(message.visible))
            is Request.Share -> acknowledge(outgoing, message.queryId, Effect.Share(message.text))
            is Request.AnalyticsTrack -> {
                track(message.event, DAppPageEvent.params(message.params, message.provider))
                outgoing.trySend(Response.Done(message.queryId))
            }
            is Request.GetData -> {
                val settings = appSettings()
                scope.launch(session) {
                    val answer = answer(message.queryId, expiredAccessToken = null, settings) {
                        referralCode(message.host)
                    }
                    outgoing.trySend(answer)
                }
            }
            is Request.RefreshData -> scope.launch(session) {
                outgoing.trySend(answer(message.queryId, message.expiredAccessToken, appSettings = null))
            }
        }
    }

    override fun detach(): List<Response> {
        session?.cancel()
        session = null
        outgoing.close()
        return buildList {
            while (true) {
                add(outgoing.tryReceive().getOrNull() ?: break)
            }
        }
    }

    private fun acknowledge(outgoing: Channel<Response>, queryId: String, effect: Effect) {
        outgoing.trySend(Response.Done(queryId))
        pendingEffects.trySend(effect)
    }

    private suspend fun answer(
        queryId: String,
        expiredAccessToken: String?,
        appSettings: AppSettings?,
        referralCode: suspend () -> String? = { null },
    ): Response {
        val credentials = try {
            loadCredentials(expiredAccessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w("Keeper dApp bridge: credentials failed for #$queryId: ${e.javaClass.simpleName}")
            null
        }
        if (credentials == null) {
            return Response.Failure(queryId, ErrorCode.Unauthorized)
        }
        return Response.Data(queryId, credentials, appSettings, referralCode())
    }

    private fun emptyOutgoing(): Channel<Response> = Channel(Channel.UNLIMITED)
}
