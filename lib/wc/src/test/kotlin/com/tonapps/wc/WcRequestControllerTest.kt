package com.tonapps.wc

import com.reown.walletkit.client.Wallet
import com.tonapps.wc.models.WcEvent
import com.tonapps.wc.models.WcEventData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WcRequestControllerTest {

    private val delegate = WcEventDelegate()

    private val controller = WcRequestController(delegate)

    private val collectors = CoroutineScope(Dispatchers.Default)

    @After
    fun tearDown() {
        collectors.cancel()
    }

    @Test
    fun `a request WalletKit emits again while it is unanswered comes through once`() = runBlocking {
        val requests = collectRequests()

        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())
        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())
        delegate.onSessionRequest(sessionRequest(id = 2), verifyContext())

        val first = withTimeout(TIMEOUT_MS) { requests.receive() }
        val second = withTimeout(TIMEOUT_MS) { requests.receive() }
        assertNotEquals(first.requestId, second.requestId)
        assertNull(withTimeoutOrNull(SETTLE_MS) { requests.receive() })
    }

    @Test
    fun `an answered request emitted again comes through as a new one`() = runBlocking {
        val requests = collectRequests()

        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())
        val answered = withTimeout(TIMEOUT_MS) { requests.receive() }
        controller.cleanup(answered.requestId)
        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())

        assertNotEquals(answered.requestId, withTimeout(TIMEOUT_MS) { requests.receive() }.requestId)
    }

    private suspend fun collectRequests(): Channel<WcEvent.Request> {
        awaitControllerSubscribed()
        val requests = Channel<WcEvent.Request>(Channel.UNLIMITED)
        collectors.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.events.filterIsInstance<WcEvent.Request>().collect { requests.send(it) }
        }
        return requests
    }

    // The controller subscribes to the delegate on a background scope; events sent before that are dropped.
    private suspend fun awaitControllerSubscribed() {
        @Suppress("UNCHECKED_CAST")
        (delegate.events as MutableSharedFlow<WcEventData>).subscriptionCount.first { it > 0 }
    }

    private fun sessionRequest(id: Long): Wallet.Model.SessionRequest {
        return Wallet.Model.SessionRequest(
            topic = TOPIC,
            chainId = "eip155:1",
            peerMetaData = null,
            request = Wallet.Model.SessionRequest.JSONRPCRequest(id, "personal_sign", "[\"0x00\",\"0x00\"]"),
        )
    }

    private fun verifyContext(): Wallet.Model.VerifyContext {
        return Wallet.Model.VerifyContext(1, "https://example.org", Wallet.Model.Validation.VALID, "", false)
    }

    private companion object {
        const val TOPIC = "topic"
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
    }
}