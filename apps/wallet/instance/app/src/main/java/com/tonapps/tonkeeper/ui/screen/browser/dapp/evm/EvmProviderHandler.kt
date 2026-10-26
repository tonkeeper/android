package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.MessageHandler
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.evm.EvmProviderRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * `window.ethereum` of one in-app dApp: a [Session] per attached WebView.
 *
 * [attach] opens a [Session] and [detach] ends it. Page messages are handled one at a time, in the
 * order they arrived, so the handshake settles the account before any request reads it. A signature
 * takes the chain it was handled on and then waits for its sheet aside: the messages behind it,
 * reads and chain switches included, are answered meanwhile. A reloaded page handshakes again
 * within the same session and keeps its chain. A session answers through channels of its own,
 * closed by [detach], so nothing a stopped session still produces can reach the one that follows it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EvmProviderHandler(
    private val scope: CoroutineScope,
    private val walletId: String,
    dappName: String,
    private val providerRepository: EvmProviderRepository,
    private val confirmSheetQueue: ConfirmSheetQueue,
    private val resolveAccounts: suspend () -> List<AccountEntity>,
    private val methodParser: EvmMethodParser = EvmMethodParser(dappName),
    private val signListener: EvmSignListener? = null,
    private val ioDispatcher: CoroutineDispatcher = Async.Io,
) : MessageHandler<EvmProviderProtocol.Message, EvmProviderProtocol.Response> {

    private val current = MutableStateFlow<Session?>(null)

    override val responses: Flow<EvmProviderProtocol.Response> =
        current.flatMapLatest { it?.outgoing?.receiveAsFlow() ?: emptyFlow() }

    private val Chain.Evm.hexChainId: String?
        get() = id.toLongOrNull()?.let { "0x" + it.toString(16) }

    override fun attach() {
        val session = Session()
        if (!current.compareAndSet(null, session)) {
            session.cancel()
            return
        }
        session.start()
    }

    override fun onMessage(message: EvmProviderProtocol.Message) {
        val session = current.value
        if (session == null) {
            L.w("EVM ${message.describe()} dropped: no session is running")
            return
        }
        session.enqueue(message)
    }

    /**
     * Ends the running session and returns what the page must still receive: answers nobody picked
     * up, and a cancellation for everything that had no answer yet. An answer a collector has
     * already taken is not among them: if that collector is cancelled first, as a view's collectors
     * are before onDestroyView, the answer is lost, together with the page it was meant for.
     */
    override fun detach(): List<EvmProviderProtocol.Response> {
        return current.getAndUpdate { null }?.cancel().orEmpty()
    }

    private fun accountUnavailable(id: Long): EvmProviderProtocol.Response {
        return failure(id, ERROR_UNAUTHORIZED, "EVM account is not available")
    }

    private fun failure(id: Long, code: Int, message: String): EvmProviderProtocol.Response {
        return EvmProviderProtocol.Response.Failure(id, code, message)
    }

    // Method names and request ids only, as in Protocol.describeMessage: params carry addresses and
    // payloads.
    private fun EvmProviderProtocol.Message.describe(): String {
        return when (this) {
            is EvmProviderProtocol.Message.Handshake -> "handshake"
            is EvmProviderProtocol.Message.Request -> "$method #$id"
        }
    }

    private fun <T> Channel<T>.drain(): List<T> = buildList {
        while (true) {
            add(tryReceive().getOrNull() ?: break)
        }
    }

    private inner class Session {

        private inner class Queued(val message: EvmProviderProtocol.Message, val document: Document)

        /**
         * One page load. The handshake of the next one closes it, and what it is still owed goes
         * unanswered: the page that asked is gone, and so is its reply port.
         */
        private inner class Document {

            // Settled at most once: by the answer, or by [cancel] when the session ends first. A
            // replaced document's debts are abandoned with it.
            val unanswered: MutableSet<Long> = ConcurrentHashMap.newKeySet()

            val handshakeUnanswered = AtomicBoolean(false)

            val closed = CompletableDeferred<Unit>()
        }

        private val inbox = Channel<Queued>(Channel.UNLIMITED)

        val outgoing = Channel<EvmProviderProtocol.Response>(Channel.UNLIMITED)

        private val document = AtomicReference(Document())

        private var chain: Chain.Evm = Chain.Ethereum.Mainnet

        private var address: String? = null

        private var chains: Set<Chain.Evm> = emptySet()

        // Child of the handler's scope, ended by [cancel]: the message loop and the signatures waiting
        // for their sheets live in it. A supervisor, so one failed signature leaves the rest running.
        private val sessionScope = CoroutineScope(
            scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
        )

        private val loop = sessionScope.launch(start = CoroutineStart.LAZY) {
            for (queued in inbox) {
                // Messages already buffered keep coming after cancel; none of them may run.
                ensureActive()
                if (queued.document === document.get()) {
                    handle(queued)?.let { answer(queued, it) }
                }
            }
        }

        fun start() {
            loop.start()
        }

        fun enqueue(message: EvmProviderProtocol.Message) {
            val queued = when (message) {
                is EvmProviderProtocol.Message.Handshake -> {
                    val next = Document()
                    next.handshakeUnanswered.set(true)
                    document.getAndSet(next).closed.complete(Unit)
                    Queued(message, next)
                }
                is EvmProviderProtocol.Message.Request -> {
                    val current = document.get()
                    if (!current.unanswered.add(message.id)) {
                        L.w("EVM ${message.describe()} reuses the id of a request still in progress")
                    }
                    Queued(message, current)
                }
            }
            if (inbox.trySend(queued).isFailure) {
                settle(queued)
            }
        }

        /**
         * Closing the channels is what stops the session: from here on nothing it sends is
         * accepted. What it did send before is returned, together with a cancellation for every
         * debt left.
         */
        fun cancel(): List<EvmProviderProtocol.Response> {
            sessionScope.cancel()
            inbox.close()
            outgoing.close()
            val undelivered = outgoing.drain()
            val last = document.get()
            val cancelled = buildList {
                if (last.handshakeUnanswered.getAndSet(false)) {
                    add(EvmProviderProtocol.Response.Unavailable(ERROR_DISCONNECTED, "Provider disconnected"))
                }
                for (id in last.unanswered.toList()) {
                    if (last.unanswered.remove(id)) {
                        add(EvmProviderProtocol.Response.Failure(id, ERROR_DISCONNECTED, "Request cancelled"))
                    }
                }
            }
            return undelivered + cancelled
        }

        // A document closed while its message was being handled gets no answer: the reply would
        // reach the page that replaced it, which is how an old handshake's failure could kill it.
        private fun answer(queued: Queued, response: EvmProviderProtocol.Response) {
            if (queued.document === document.get() && settle(queued)) {
                outgoing.trySend(response)
            }
        }

        private fun settle(queued: Queued): Boolean {
            return when (val message = queued.message) {
                is EvmProviderProtocol.Message.Handshake -> queued.document.handshakeUnanswered.getAndSet(false)
                is EvmProviderProtocol.Message.Request -> queued.document.unanswered.remove(message.id)
            }
        }

        // Null: a signature answers later, once its sheet is done.
        private suspend fun handle(queued: Queued): EvmProviderProtocol.Response? {
            return guarded(queued.message) {
                when (val message = queued.message) {
                    is EvmProviderProtocol.Message.Handshake -> connect()
                    is EvmProviderProtocol.Message.Request -> call(message, queued)
                }
            }
        }

        private suspend fun guarded(
            message: EvmProviderProtocol.Message,
            block: suspend () -> EvmProviderProtocol.Response?,
        ): EvmProviderProtocol.Response? {
            return try {
                block()
            } catch (e: CancellationException) {
                // Only the session's own cancellation ends it: a timeout from below is a failure of
                // this one message.
                currentCoroutineContext().ensureActive()
                failed(message, e)
            } catch (e: Exception) {
                failed(message, e)
            }
        }

        // One broken message must not stop the queue: everything behind it would hang.
        private fun failed(message: EvmProviderProtocol.Message, e: Exception): EvmProviderProtocol.Response {
            L.e(e, "EVM ${message.describe()} failed")
            return when (message) {
                is EvmProviderProtocol.Message.Handshake ->
                    EvmProviderProtocol.Response.Unavailable(ERROR_DISCONNECTED, "Provider is unavailable")
                is EvmProviderProtocol.Message.Request -> failure(message.id, ERROR_INTERNAL, "Internal error")
            }
        }

        private suspend fun connect(): EvmProviderProtocol.Response {
            val accounts = try {
                withContext(ioDispatcher) { resolveAccounts() }.filter { it.chain is Chain.Evm }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                return accountsFailed(e)
            } catch (e: Exception) {
                return accountsFailed(e)
            }
            val resolved = accounts.firstOrNull()?.displayAddress
                ?: return EvmProviderProtocol.Response.Unavailable(
                    ERROR_UNAUTHORIZED,
                    "EVM account is not available",
                )
            chains = accounts.mapNotNull { it.chain as? Chain.Evm }.toSet()
            if (chain !in chains) {
                chain = chains.firstOrNull { it is Chain.Ethereum } ?: chains.first()
            }
            val chainId = chain.hexChainId
                ?: return EvmProviderProtocol.Response.Unavailable(
                    ERROR_DISCONNECTED,
                    "Unsupported chain: ${chain.id}",
                )
            address = resolved
            return EvmProviderProtocol.Response.Connected(resolved, chainId)
        }

        private fun accountsFailed(e: Exception): EvmProviderProtocol.Response {
            L.e(e, "EVM accounts could not be loaded")
            return EvmProviderProtocol.Response.Unavailable(ERROR_DISCONNECTED, "EVM accounts could not be loaded")
        }

        private suspend fun call(
            request: EvmProviderProtocol.Message.Request,
            queued: Queued,
        ): EvmProviderProtocol.Response? {
            return when (request.method) {
                METHOD_REQUEST_ACCOUNTS -> accounts(request.id)
                METHOD_REQUEST_PERMISSIONS -> permissions(request.id)
                METHOD_SWITCH_CHAIN -> switchChain(request)
                METHOD_SEND_TRANSACTION -> sign(request, queued, methodParser::transaction)
                METHOD_PERSONAL_SIGN -> sign(request, queued, methodParser::personalSign)
                METHOD_SIGN_TYPED_DATA_V4 -> sign(request, queued, methodParser::typedData)
                else -> failure(request.id, ERROR_UNSUPPORTED, "Unsupported method: ${request.method}")
            }
        }

        private fun accounts(id: Long): EvmProviderProtocol.Response {
            val address = address ?: return accountUnavailable(id)
            return EvmProviderProtocol.Response.Result(id, listOf(address))
        }

        private fun permissions(id: Long): EvmProviderProtocol.Response {
            val address = address ?: return accountUnavailable(id)
            val caveat = mapOf(
                "type" to "restrictReturnedAccounts",
                "value" to listOf(address),
            )
            val permission = mapOf(
                "parentCapability" to "eth_accounts",
                "caveats" to listOf(caveat),
            )
            return EvmProviderProtocol.Response.Result(id, listOf(permission))
        }

        // EIP-3326: the chain id is a 0x-prefixed hex string; switching to the current chain is a
        // success that changes nothing, so it must not make the page emit chainChanged.
        private fun switchChain(request: EvmProviderProtocol.Message.Request): EvmProviderProtocol.Response {
            val requested = (request.params.firstOrNull() as? Map<*, *>)?.get("chainId") as? String
            if (requested == null || !requested.startsWith("0x")) {
                return failure(request.id, ERROR_INVALID_PARAMS, "Expected a 0x-prefixed chainId")
            }
            val chain = evmChainByHex(requested)
            val chainId = chain?.hexChainId ?: return failure(
                request.id,
                ERROR_UNRECOGNIZED_CHAIN,
                "Unsupported chain: $requested",
            )
            if (chain == this.chain) {
                return EvmProviderProtocol.Response.Result(request.id, null)
            }
            this.chain = chain
            return EvmProviderProtocol.Response.ChainSwitched(request.id, chainId)
        }

        private suspend fun sign(
            request: EvmProviderProtocol.Message.Request,
            queued: Queued,
            parse: (EvmProviderProtocol.Message.Request, String, Chain.Evm, String) -> EvmMethodParser.Result,
        ): EvmProviderProtocol.Response? {
            val chain = chain
            signListener?.onRequested(request.method, chain)
            val response = try {
                signed(request, queued, chain, parse)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                signListener?.onFailed(request.method, chain, ERROR_INTERNAL, e.javaClass.simpleName)
                throw e
            }
            response?.let { report(request.method, chain, it) }
            return response
        }

        private fun report(method: String, chain: Chain.Evm, response: EvmProviderProtocol.Response) {
            val listener = signListener ?: return
            when (response) {
                is EvmProviderProtocol.Response.Result -> listener.onSigned(method, chain)
                is EvmProviderProtocol.Response.Failure -> if (response.code == ERROR_REJECTED) {
                    listener.onRejected(method, chain)
                } else {
                    listener.onFailed(method, chain, response.code, response.message)
                }
                else -> Unit
            }
        }

        private suspend fun signed(
            request: EvmProviderProtocol.Message.Request,
            queued: Queued,
            chain: Chain.Evm,
            parse: (EvmProviderProtocol.Message.Request, String, Chain.Evm, String) -> EvmMethodParser.Result,
        ): EvmProviderProtocol.Response? {
            val address = address ?: return accountUnavailable(request.id)
            // Parsing decodes ABI through chainkit and reads the typed data payload.
            val result = withContext(ioDispatcher) { parse(request, walletId, chain, address) }
            return when (result) {
                is EvmMethodParser.Result.Error -> failure(request.id, result.code, result.message)
                is EvmMethodParser.Result.Confirm -> {
                    // Undispatched: it joins the sheet queue before the next message is handled, so
                    // signatures keep their order there.
                    sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        guarded(request) { awaitConfirmation(request.id, queued.document, result) }
                            ?.let {
                                report(request.method, chain, it)
                                answer(queued, it)
                            }
                    }
                    null
                }
            }
        }

        private suspend fun awaitConfirmation(
            id: Long,
            document: Document,
            confirm: EvmMethodParser.Result.Confirm,
        ): EvmProviderProtocol.Response {
            val pending = providerRepository.expect(confirm.requestId, confirm.request)
            val outcome = coroutineScope {
                // A sheet gone without an answer rejects the request: it was dismissed, or it found
                // nothing to show.
                val sheetGone = launch(start = CoroutineStart.UNDISPATCHED) {
                    confirmSheetQueue.awaitSheetDestroyed(confirm.requestId)
                    providerRepository.reject(confirm.requestId)
                }
                // A reload rejects the request, whether its sheet is up or still about to open:
                // without its request the sheet closes itself.
                val reload = launch {
                    document.closed.await()
                    providerRepository.reject(confirm.requestId)
                }
                if (!document.closed.isCompleted) {
                    confirmSheetQueue.enqueue(confirm.requestId)
                }
                try {
                    pending.await()
                } finally {
                    reload.cancel()
                    sheetGone.cancel()
                    // Answered or cancelled while still waiting for its turn: its sheet never opens.
                    confirmSheetQueue.discard(confirm.requestId)
                }
            }
            return when (outcome) {
                is EvmProviderRepository.Outcome.Signed ->
                    EvmProviderProtocol.Response.Result(id, outcome.result)
                is EvmProviderRepository.Outcome.Rejected ->
                    failure(id, ERROR_REJECTED, "User rejected the request")
                is EvmProviderRepository.Outcome.Failed ->
                    failure(id, ERROR_INTERNAL, "Failed to sign the request")
            }
        }

        private fun evmChainByHex(value: String): Chain.Evm? {
            val chainId = value.removePrefix("0x").toLongOrNull(16)?.toString() ?: return null
            return chains.firstOrNull { it.id == chainId }
        }
    }

    private companion object {

        private const val METHOD_REQUEST_ACCOUNTS = "eth_requestAccounts"
        private const val METHOD_REQUEST_PERMISSIONS = "wallet_requestPermissions"
        private const val METHOD_SWITCH_CHAIN = "wallet_switchEthereumChain"
        private const val METHOD_SEND_TRANSACTION = "eth_sendTransaction"
        private const val METHOD_PERSONAL_SIGN = "personal_sign"
        private const val METHOD_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"

        private const val ERROR_REJECTED = 4001

        // 4100 is "method or account not authorized", which is what a missing EVM account is;
        // 4900 would claim the provider is disconnected from every chain. iOS answers the same.
        private const val ERROR_UNAUTHORIZED = 4100
        private const val ERROR_UNSUPPORTED = 4200
        private const val ERROR_DISCONNECTED = 4900
        private const val ERROR_UNRECOGNIZED_CHAIN = 4902
        private const val ERROR_INVALID_PARAMS = -32602
        private const val ERROR_INTERNAL = -32603
    }
}
