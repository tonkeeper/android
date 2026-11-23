package com.tonapps.wc

import com.reown.walletkit.client.Wallet
import com.tonapps.wc.models.WcEvent
import com.tonapps.wc.models.WcEventData
import com.tonapps.wc.models.WcValidation
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WcRequestControllerTest {

    private val delegate = WcEventDelegate()

    @Volatile
    private var unansweredRequests = emptyList<WcEventData.SessionRequest>()

    @Volatile
    private var unansweredRequestsFailure: Exception? = null

    private val controller = WcRequestController(delegate) {
        unansweredRequestsFailure?.let { throw it }
        unansweredRequests
    }

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

    @Test
    fun `a request carries the verdict Verify gives that request`() = runBlocking {
        val requests = collectRequests()

        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext(Wallet.Model.Validation.VALID))
        delegate.onSessionRequest(sessionRequest(id = 2), verifyContext(Wallet.Model.Validation.INVALID))
        delegate.onSessionRequest(sessionRequest(id = 3), verifyContext(Wallet.Model.Validation.UNKNOWN))

        assertEquals(WcValidation.Valid, withTimeout(TIMEOUT_MS) { requests.receive() }.request.dapp.validation)
        assertEquals(WcValidation.Invalid, withTimeout(TIMEOUT_MS) { requests.receive() }.request.dapp.validation)
        assertEquals(WcValidation.Unknown, withTimeout(TIMEOUT_MS) { requests.receive() }.request.dapp.validation)
    }

    @Test
    fun `a request from a domain flagged as scam is a scam whatever the domain check says`() = runBlocking {
        val requests = collectRequests()

        delegate.onSessionRequest(
            sessionRequest(id = 1),
            verifyContext(Wallet.Model.Validation.VALID, isScam = true),
        )

        assertEquals(WcValidation.Scam, withTimeout(TIMEOUT_MS) { requests.receive() }.request.dapp.validation)
    }

    @Test
    fun `a request left unanswered by a previous process comes through once the relay connects`() = runBlocking {
        val requests = collectRequests()
        unansweredRequests = listOf(unansweredRequest(id = 1), unansweredRequest(id = 2))

        delegate.onConnectionStateChange(Wallet.Model.ConnectionState(isAvailable = true))

        val first = withTimeout(TIMEOUT_MS) { requests.receive() }
        val second = withTimeout(TIMEOUT_MS) { requests.receive() }
        assertEquals(1L, wcRequestId(first))
        assertEquals(2L, wcRequestId(second))
    }

    @Test
    fun `an unanswered request already shown does not come through again when the relay reconnects`() = runBlocking {
        val requests = collectRequests()
        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())
        withTimeout(TIMEOUT_MS) { requests.receive() }
        unansweredRequests = listOf(unansweredRequest(id = 1))

        delegate.onConnectionStateChange(Wallet.Model.ConnectionState(isAvailable = true))

        assertNull(withTimeoutOrNull(SETTLE_MS) { requests.receive() })
    }

    @Test
    fun `losing the relay does not restore unanswered requests`() = runBlocking {
        val requests = collectRequests()
        unansweredRequests = listOf(unansweredRequest(id = 1))

        delegate.onConnectionStateChange(
            Wallet.Model.ConnectionState(
                isAvailable = false,
                reason = Wallet.Model.ConnectionState.Reason.ConnectionClosed("closed"),
            ),
        )

        assertNull(withTimeoutOrNull(SETTLE_MS) { requests.receive() })
    }

    @Test
    fun `requests keep coming through after unanswered ones fail to load`() = runBlocking {
        val requests = collectRequests()
        unansweredRequestsFailure = IllegalStateException("WalletKit is not initialized")

        delegate.onConnectionStateChange(Wallet.Model.ConnectionState(isAvailable = true))
        delegate.onSessionRequest(sessionRequest(id = 1), verifyContext())

        assertEquals(1L, wcRequestId(withTimeout(TIMEOUT_MS) { requests.receive() }))
    }

    private fun wcRequestId(event: WcEvent.Request): Long? {
        return (controller.request(event.requestId) as? WcEventData.SessionRequest)?.data?.request?.id
    }

    private fun unansweredRequest(id: Long): WcEventData.SessionRequest {
        return WcEventData.SessionRequest(sessionRequest(id), verifyContext())
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

    private fun verifyContext(
        validation: Wallet.Model.Validation = Wallet.Model.Validation.VALID,
        isScam: Boolean = false,
    ): Wallet.Model.VerifyContext {
        return Wallet.Model.VerifyContext(1, "https://example.org", validation, "", isScam)
    }

    private companion object {
        const val TOPIC = "topic"
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
    }
}