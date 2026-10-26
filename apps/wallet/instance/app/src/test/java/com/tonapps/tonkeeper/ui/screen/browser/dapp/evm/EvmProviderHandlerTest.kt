package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Message
import com.tonapps.tonkeeper.ui.screen.browser.dapp.evm.EvmProviderProtocol.Response
import com.tonapps.tonkeeper.ui.screen.browser.dapp.silenceLog
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wc.WcRouteAdapter
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EvmProviderHandlerTest {

    private val providerRepository = EvmProviderRepository()

    private val confirmSheetQueue = ConfirmSheetQueue()

    private val adapter = ChainCapturingAdapter()

    private var nextRequestId = 0

    // Not backgroundScope: advanceUntilIdle does not wait for background work, and the queue under
    // test would never get to run.
    private val job = SupervisorJob()

    @After
    fun tearDown() {
        job.cancel()
    }

    @Test
    fun `a request sent together with the handshake waits for the account`() = runTest {
        val harness = harness()

        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(1, "eth_requestAccounts"))
        advanceUntilIdle()

        assertEquals(listOf(CONNECTED, Response.Result(1, listOf(ADDRESS))), harness.answers)
    }

    @Test
    fun `a read is answered while a signature waits for its sheet`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        harness.handler.onMessage(request(5, "eth_requestAccounts"))
        advanceUntilIdle()

        assertEquals(listOf(CONNECTED, Response.Result(5, listOf(ADDRESS))), harness.answers)
    }

    @Test
    fun `a chain switch behind a signature leaves the signature on its chain`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        harness.handler.onMessage(switchTo(3, BASE_HEX))
        advanceUntilIdle()

        assertEquals(listOf(CONNECTED, Response.ChainSwitched(3, BASE_HEX)), harness.answers)

        providerRepository.approve(harness.confirmations.single(), "0xsigned")
        advanceUntilIdle()

        assertEquals(
            listOf(CONNECTED, Response.ChainSwitched(3, BASE_HEX), Response.Result(2, "0xsigned")),
            harness.answers,
        )
        assertEquals(listOf<Chain>(Chain.Ethereum.Mainnet), adapter.chains)
    }

    @Test
    fun `signatures reach their sheets in the order they came`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        harness.handler.onMessage(request(3, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()
        val first = harness.confirmations.single()

        providerRepository.approve(first, "0xfirst")
        confirmSheetQueue.onSheetDestroyed(first)
        advanceUntilIdle()

        assertEquals(listOf("confirm-1", "confirm-2"), harness.confirmations)
    }

    @Test
    fun `a closed sheet answers with a rejection`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()

        confirmSheetQueue.onSheetDestroyed(harness.confirmations.single())
        advanceUntilIdle()

        assertEquals(Response.Failure(2, REJECTED, "User rejected the request"), harness.answers.last())
    }

    @Test
    fun `a failed request closed with its sheet answers with the failure, not a rejection`() = runTest {
        val outcomes = RecordingSignListener()
        val harness = harness(signListener = outcomes)
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()

        // The sheet answers from its teardown, before its destruction reaches the handler.
        val requestId = harness.confirmations.single()
        providerRepository.fail(requestId)
        confirmSheetQueue.onSheetDestroyed(requestId)
        advanceUntilIdle()

        assertEquals(Response.Failure(2, INTERNAL, "Failed to sign the request"), harness.answers.last())
        assertEquals(listOf("requested personal_sign", "failed personal_sign $INTERNAL"), outcomes.events)
    }

    @Test
    fun `detaching cancels everything still owed`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        harness.handler.onMessage(request(5, "eth_requestAccounts"))
        advanceUntilIdle()
        val requestId = harness.confirmations.single()

        val remaining = harness.handler.detach()
        advanceUntilIdle()

        assertEquals(listOf(Response.Failure(2, DISCONNECTED, "Request cancelled")), remaining)
        assertNull(providerRepository.pendingRequest(requestId))
        assertEquals(listOf(CONNECTED, Response.Result(5, listOf(ADDRESS))), harness.answers)
    }

    @Test
    fun `a session detached before it answered the handshake says it is unavailable`() = runTest {
        val harness = harness(accountsLatencyMs = 1_000)
        harness.handler.onMessage(Message.Handshake)
        // Well inside the account lookup: the handshake is being handled, not merely queued.
        advanceTimeBy(500)

        val remaining = harness.handler.detach()
        advanceUntilIdle()

        assertEquals(listOf(Response.Unavailable(DISCONNECTED, "Provider disconnected")), remaining)
        assertTrue(harness.answers.isEmpty())
    }

    @Test
    fun `a new session starts from the default chain`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(switchTo(1, BASE_HEX))
        advanceUntilIdle()
        assertEquals(Response.ChainSwitched(1, BASE_HEX), harness.answers.last())

        harness.handler.detach()
        harness.handler.attach()
        harness.handler.onMessage(Message.Handshake)
        advanceUntilIdle()

        assertEquals(CONNECTED, harness.answers.last())
    }

    @Test
    fun `a reloaded page keeps the chain it was on`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(switchTo(1, BASE_HEX))
        advanceUntilIdle()

        harness.handler.onMessage(Message.Handshake)
        advanceUntilIdle()

        assertEquals(
            listOf(CONNECTED, Response.ChainSwitched(1, BASE_HEX), Response.Connected(ADDRESS, BASE_HEX)),
            harness.answers,
        )
    }

    @Test
    fun `a chain switch needs a 0x-prefixed chain id`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(1, "wallet_switchEthereumChain"))
        harness.handler.onMessage(switchTo(2, "8453"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                CONNECTED,
                Response.Failure(1, INVALID_PARAMS, "Expected a 0x-prefixed chainId"),
                Response.Failure(2, INVALID_PARAMS, "Expected a 0x-prefixed chainId"),
            ),
            harness.answers,
        )
    }

    @Test
    fun `switching to the current chain succeeds without a chain change`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(switchTo(1, "0x1"))
        advanceUntilIdle()

        assertEquals(listOf(CONNECTED, Response.Result(1, null)), harness.answers)
    }

    @Test
    fun `answers nobody picked up are handed back by detach`() = runTest {
        val harness = harness(collectResponses = false)
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(1, "eth_requestAccounts"))
        advanceUntilIdle()

        assertEquals(listOf(CONNECTED, Response.Result(1, listOf(ADDRESS))), harness.handler.detach())
    }

    @Test
    fun `a cancellation from below fails the message, not the queue`() = runTest {
        val harness = harness(accounts = { throw CancellationException("timed out") })
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(1, "eth_requestAccounts"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                Response.Unavailable(DISCONNECTED, "EVM accounts could not be loaded"),
                Response.Failure(1, UNAUTHORIZED, "EVM account is not available"),
            ),
            harness.answers,
        )
    }

    @Test
    fun `requests of a reloaded page never reach a sheet`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        harness.handler.onMessage(request(3, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()
        val stale = harness.confirmations.single()

        harness.handler.onMessage(Message.Handshake)
        advanceUntilIdle()

        // The sheet handed out for request 2 finds no request anymore and closes itself; request 3
        // was still waiting for its turn and is dropped from the queue.
        assertNull(providerRepository.pendingRequest(stale))
        assertNull(providerRepository.pendingRequest("confirm-2"))
        confirmSheetQueue.onSheetDestroyed(stale)
        advanceUntilIdle()
        assertEquals(listOf(stale), harness.confirmations)
        assertEquals(listOf(CONNECTED, CONNECTED), harness.answers)
    }

    @Test
    fun `a request cancelled while it waits for its turn never gets a sheet`() = runTest {
        confirmSheetQueue.enqueue(OTHER_SHEET)
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()

        harness.handler.detach()
        advanceUntilIdle()
        confirmSheetQueue.onSheetDestroyed(OTHER_SHEET)
        advanceUntilIdle()

        assertEquals(listOf(OTHER_SHEET), harness.confirmations)
    }

    @Test
    fun `a reload while a request is being parsed keeps its sheet from opening`() = runTest {
        val harness = harness()
        harness.handler.onMessage(Message.Handshake)
        advanceUntilIdle()
        adapter.onConvert = { harness.handler.onMessage(Message.Handshake) }

        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()

        assertTrue(harness.confirmations.isEmpty())
        assertEquals(listOf(CONNECTED, CONNECTED), harness.answers)
    }

    @Test
    fun `only the latest handshake is answered`() = runTest {
        var loads = 0
        val harness = harness(
            accounts = {
                loads++
                listOf(account(Chain.Ethereum.Mainnet))
            },
        )

        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(Message.Handshake)
        advanceUntilIdle()

        // The first page is gone before its handshake is handled, so it is never resolved at all.
        assertEquals(listOf(CONNECTED), harness.answers)
        assertEquals(1, loads)
    }

    @Test
    fun `the funnel tells a declined signature from a failed one`() = runTest {
        val outcomes = RecordingSignListener()
        val harness = harness(signListener = outcomes)
        harness.handler.onMessage(Message.Handshake)
        harness.handler.onMessage(request(2, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()
        confirmSheetQueue.onSheetDestroyed(harness.confirmations.single())
        advanceUntilIdle()
        harness.handler.onMessage(request(3, "personal_sign", MESSAGE, ADDRESS))
        advanceUntilIdle()
        providerRepository.approve(harness.confirmations.last(), "0xsigned")
        advanceUntilIdle()
        harness.handler.onMessage(request(4, "eth_signTypedData_v4"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "requested personal_sign",
                "rejected personal_sign",
                "requested personal_sign",
                "signed personal_sign",
                "requested eth_signTypedData_v4",
                "failed eth_signTypedData_v4 $INVALID_PARAMS",
            ),
            outcomes.events,
        )
    }

    private class RecordingSignListener : EvmSignListener {

        val events = mutableListOf<String>()

        override fun onRequested(method: String, chain: Chain.Evm) {
            events += "requested $method"
        }

        override fun onSigned(method: String, chain: Chain.Evm) {
            events += "signed $method"
        }

        override fun onRejected(method: String, chain: Chain.Evm) {
            events += "rejected $method"
        }

        override fun onFailed(method: String, chain: Chain.Evm, code: Int, message: String) {
            events += "failed $method $code"
        }
    }

    private class Harness(
        val handler: EvmProviderHandler,
        val answers: List<Response>,
        // Requests whose sheet the queue put on screen, in order.
        val confirmations: List<String>,
    )

    private fun TestScope.harness(
        accountsLatencyMs: Long = 100,
        collectResponses: Boolean = true,
        signListener: EvmSignListener? = null,
        accounts: suspend () -> List<AccountEntity> = {
            listOf(account(Chain.Ethereum.Mainnet), account(Chain.Base.Mainnet))
        },
    ): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(job + dispatcher)
        val handler = EvmProviderHandler(
            scope = scope,
            walletId = WALLET,
            dappName = DAPP_NAME,
            providerRepository = providerRepository,
            confirmSheetQueue = confirmSheetQueue,
            resolveAccounts = {
                delay(accountsLatencyMs)
                accounts()
            },
            methodParser = EvmMethodParser(DAPP_NAME, adapter) { "confirm-${++nextRequestId}" },
            signListener = signListener,
            ioDispatcher = dispatcher,
        )
        val answers = mutableListOf<Response>()
        val confirmations = mutableListOf<String>()
        if (collectResponses) {
            scope.launch { handler.responses.toList(answers) }
        }
        scope.launch { confirmSheetQueue.current.filterNotNull().toList(confirmations) }
        handler.attach()
        return Harness(handler, answers, confirmations)
    }

    private fun request(id: Long, method: String, vararg params: Any?): Message.Request {
        return Message.Request(id, method, params.toList(), host = "earn.example")
    }

    private fun switchTo(id: Long, chainId: String): Message.Request {
        return request(id, "wallet_switchEthereumChain", mapOf("chainId" to chainId))
    }

    private fun account(chain: Chain) = AccountEntity(
        walletId = WALLET,
        network = chain.network.type.id,
        mode = chain.network.mode.id,
        displayAddress = ADDRESS,
        publicKey = "",
        segwitPublicKey = "",
    )

    private class ChainCapturingAdapter : WcRouteAdapter {

        val chains = mutableListOf<Chain>()

        var onConvert: () -> Unit = {}

        override fun <T : WcRequestData> convertRequest(
            requestId: String,
            walletId: String,
            chain: Chain,
            meta: WcDappConnection,
            rawRequest: T,
        ): ConfirmRequest {
            chains += chain
            onConvert()
            return ConfirmRequest(
                data = CommonTransactionData(assetId = "asset", walletId = walletId),
                type = ConfirmType.Message(MESSAGE, ConfirmType.Message.Type.Personal),
                context = ConfirmContext.Dapp(requestId, meta.topic, meta.app.name, meta.app.url),
            )
        }

        override fun handleMultiSession(
            requestId: String,
            proposal: WcSessionProposal,
            wcConnection: WcConnection,
            validation: WcValidation,
        ) = throw UnsupportedOperationException()
    }

    private companion object {

        const val WALLET = "wallet-id"
        const val OTHER_SHEET = "walletconnect-request"
        const val DAPP_NAME = "Earn"
        const val ADDRESS = "0x7ECebAF27c989adEF9EA3DD06D316F6F463a5109"
        const val MESSAGE = "0x68656c6c6f"
        const val BASE_HEX = "0x2105"
        const val REJECTED = 4001
        const val UNAUTHORIZED = 4100
        const val DISCONNECTED = 4900
        const val INVALID_PARAMS = -32602
        const val INTERNAL = -32603

        val CONNECTED = Response.Connected(ADDRESS, "0x1")

        @JvmStatic
        @BeforeClass
        fun setUpLog() {
            silenceLog()
        }
    }
}
