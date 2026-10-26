package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import androidx.annotation.MainThread
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.transport.InAppTransport
import com.tonapps.tonkeeperx.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One bridge for a single attach: [protocol] over [transport], with every answer delivered to the
 * document that asked for it. Main thread only: a send or an incoming message off it fails in debug
 * builds and is dropped in release.
 */
internal class InAppBridgeChannel<In : Any, Out : Any>(
    private val protocol: Protocol<In, Out>,
    private val transport: InAppTransport,
    private val isMainThread: () -> Boolean = Async::isMainThread,
) {

    private val inbox = Channel<In>(Channel.UNLIMITED)

    private val replies = HashMap<String, InAppTransport.Reply>()

    private var sessionReply: InAppTransport.Reply? = null

    val messages: Flow<In> = inbox.receiveAsFlow()

    /**
     * Must complete before the page loads: a script added later misses the document that is
     * already there.
     */
    @MainThread
    suspend fun attach(): Boolean {
        val script = try {
            protocol.script.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e, "${protocol.name} script could not be loaded")
            return false
        }
        val endpoint = InAppTransport.Endpoint(protocol.name, script)
        return transport.attach(endpoint, ::onMessage)
    }

    @MainThread
    fun detach() {
        transport.detach()
        replies.clear()
        sessionReply = null
        inbox.cancel()
    }

    @MainThread
    fun send(response: Out) {
        if (!ensureMainThread { "answer ${protocol.describeResponse(response)}" }) {
            return
        }
        // An answer to a request goes to that request's port or nowhere: falling back to the
        // session port would deliver a duplicate after the first answer took the port.
        val reply = when (val id = protocol.replyId(response)) {
            null -> sessionReply
            else -> replies.remove(id)
        }
        if (reply == null) {
            L.w("${protocol.name}: no reply port for ${protocol.describeResponse(response)}")
            return
        }
        L.d("${protocol.name}: answer ${protocol.describeResponse(response)}")
        reply.post(protocol.encode(response))
    }

    private fun onMessage(data: String, host: String, reply: InAppTransport.Reply) {
        if (!ensureMainThread { "message" }) {
            return
        }
        val message = protocol.parse(data, host) ?: return
        when (val route = protocol.route(message)) {
            is Protocol.Route.Handshake -> {
                replies.clear()
                sessionReply = reply
            }
            is Protocol.Route.Request -> replies[route.id] = reply
        }
        val result = inbox.trySend(message)
        L.d("${protocol.name}: ${protocol.describeMessage(message)}, delivered=${result.isSuccess}")
    }

    private fun ensureMainThread(call: () -> String): Boolean {
        if (isMainThread()) {
            return true
        }
        val message = "${protocol.name}: ${call()} off the main thread, dropped"
        if (BuildConfig.DEBUG) {
            error(message)
        }
        L.w(message)
        return false
    }
}