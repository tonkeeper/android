package com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.MessageHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol.Message
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow

/** Answers every call at once: `window.native` methods do not wait for anything. */
internal class NativeBridgeHandler(
    private val methods: NativeMethods,
) : MessageHandler<Message, NativeBridgeProtocol.Response> {

    private var outgoing = emptyOutgoing()

    // Unlike the answers, kept across attaches: an answer belongs to the document that asked, a
    // Close to the screen, which a recreated view still has to close.
    private val pendingEffects = Channel<Effect>(Channel.UNLIMITED)

    // Read per collection: each attach starts a new channel, and the connection collects it after.
    override val responses: Flow<NativeBridgeProtocol.Response>
        get() = merge(
            outgoing.receiveAsFlow(),
            methods.events.map { NativeBridgeProtocol.Response.Event(it.name, it.payload) },
        )

    override val effects: Flow<Effect> = pendingEffects.receiveAsFlow()

    override fun attach() {
        outgoing = emptyOutgoing()
    }

    override fun onMessage(message: Message) {
        when (message) {
            is Message.Ready -> methods.onDocumentReady().forEach { event ->
                outgoing.trySend(NativeBridgeProtocol.Response.Event(event.name, event.payload))
            }
            is Message.Request -> call(message)
        }
    }

    override fun detach(): List<NativeBridgeProtocol.Response> {
        outgoing.close()
        return buildList {
            while (true) {
                add(outgoing.tryReceive().getOrNull() ?: break)
            }
        }
    }

    private fun call(request: Message.Request) {
        val result = methods.handle(request.method, request.params)
        outgoing.trySend(result.toResponse(request.id))
        // After the answer: an effect such as Close takes the page away.
        if (result is NativeMethodResult.Success) {
            result.effect?.let(pendingEffects::trySend)
        }
    }

    private fun emptyOutgoing(): Channel<NativeBridgeProtocol.Response> = Channel(Channel.UNLIMITED)

    private fun NativeMethodResult.toResponse(id: String): NativeBridgeProtocol.Response {
        return when (this) {
            is NativeMethodResult.Success -> NativeBridgeProtocol.Response.Success(id, value)
            is NativeMethodResult.Failure -> NativeBridgeProtocol.Response.Failure(id, code, message)
        }
    }
}