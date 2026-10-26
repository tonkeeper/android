package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.MessageHandler
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.transport.InAppTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** `h` is a handshake, `r:<id>` a request; anything else does not parse. */
internal data class FakeMessage(val id: String?)

/** [id] null answers the session. */
internal data class FakeAnswer(val id: String?, val text: String)

internal class FakeProtocol(
    override val script: Script = Script { "script" },
) : Protocol<FakeMessage, FakeAnswer> {

    override val name: String = "Fake"

    override fun parse(data: String, host: String): FakeMessage? {
        return when {
            data == "h" -> FakeMessage(null)
            data.startsWith("r:") -> FakeMessage(data.removePrefix("r:"))
            else -> null
        }
    }

    override fun encode(response: FakeAnswer): String = "${response.id}:${response.text}"

    override fun route(message: FakeMessage): Protocol.Route {
        return message.id?.let { Protocol.Route.Request(it) } ?: Protocol.Route.Handshake
    }

    override fun replyId(response: FakeAnswer): String? = response.id
}

/** Every reply post and transport call, in the order they happened. */
internal class EventLog {

    val events = mutableListOf<String>()

    fun reply(name: String): InAppTransport.Reply = InAppTransport.Reply { events += "$name <- $it" }
}

internal class FakeTransport(
    private val log: EventLog,
    private val attachSucceeds: Boolean = true,
) : InAppTransport {

    private var receiver: InAppTransport.Receiver? = null

    var attachedScript: String? = null
        private set

    override fun attach(
        endpoint: InAppTransport.Endpoint,
        receiver: InAppTransport.Receiver,
    ): Boolean {
        log.events += "transport.attach"
        if (!attachSucceeds) {
            return false
        }
        attachedScript = endpoint.script
        this.receiver = receiver
        return true
    }

    override fun detach() {
        log.events += "transport.detach"
        receiver = null
    }

    fun deliver(data: String, reply: InAppTransport.Reply) {
        receiver?.onMessage(data, "dapp.example", reply)
    }
}

internal class FakeHandler(
    private val owed: List<FakeAnswer> = emptyList(),
    private val echo: Boolean = false,
) : MessageHandler<FakeMessage, FakeAnswer> {

    private val outgoing = Channel<FakeAnswer>(Channel.UNLIMITED)

    val received = mutableListOf<FakeMessage>()

    var attachCalls = 0
        private set

    var detachCalls = 0
        private set

    override val responses: Flow<FakeAnswer> = outgoing.receiveAsFlow()

    override fun attach() {
        attachCalls++
    }

    override fun onMessage(message: FakeMessage) {
        received += message
        if (echo) {
            outgoing.trySend(FakeAnswer(message.id, "echo"))
        }
    }

    override fun detach(): List<FakeAnswer> {
        detachCalls++
        return owed
    }
}