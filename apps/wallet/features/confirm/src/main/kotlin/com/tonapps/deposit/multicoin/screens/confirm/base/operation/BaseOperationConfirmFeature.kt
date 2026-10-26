package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import android.content.Context
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.RedOperation
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext

interface OperationConfirmState : MviViewState {
    val wallet: StateFlow<McWalletEntity?>

    /** The sender's position in the request's asset. */
    val account: StateFlow<AccountWithDetails?>

    /** What the operation's payloads cost together. */
    val fee: StateFlow<Fee?>

    /** The fiat rate of the asset [fee] is paid in, where the wallet has one. */
    val feeRate: StateFlow<FiatRate?>
    val confirmationError: StateFlow<ConfirmationError?>
    val sendingState: StateFlow<SendingState>
}

sealed interface OperationConfirmAction : MviAction {
    data class Confirm(val context: Context) : OperationConfirmAction
    data class Retry(val context: Context) : OperationConfirmAction

    sealed interface Internal : OperationConfirmAction {
        data class Signed(val signed: SignedData) : Internal
        data object Sent : Internal
    }
}

/** What a send signs: [request]'s operation as priced for [account]. */
class SendData(
    val request: ConfirmRequest,
    val wallet: McWalletEntity,
    val position: AccountWithDetails,
    val fees: List<Fee>,
    val nonce: BigInteger,
    val fee: Fee,
    val feeRate: FiatRate?,
) {
    val account: Account
        get() = position.data.chainAccount

    val operation: Operation
        get() = request.operation
}

/** [data] signed as [operation]: [data] is what [OperationAuthorizer.authorize] made of the one on screen. */
class SignedData(
    val data: SendData,
    val operation: SignedOperation,
)

/**
 * Turns a request whose operation is not yet the one sent — it is what the wallet signs to get it —
 * into the request to sign. Handed to the feature's constructor rather than overridden: the send
 * is prepared while the feature is constructed, before a subclass's own fields exist.
 */
fun interface OperationAuthorizer {

    /** With the wallet unlocked, before anything is signed: [request] as it is, or the one it makes. */
    suspend fun authorize(request: ConfirmRequest, wallet: CryptoWallet): ConfirmRequest

    companion object {
        /** The request's own operation, signed as it is. */
        val Original = OperationAuthorizer { request, _ -> request }
    }
}

/**
 * Confirms a request of a [ConfirmType.Operational], as [authorizer] makes it.
 */
abstract class BaseOperationConfirmFeature(
    request: ConfirmRequest,
    private val provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    passcodeManager: PasscodeManager,
    private val postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    private val tracker: OperationTracker? = null,
    private val authorizer: OperationAuthorizer = OperationAuthorizer.Original,
) : BaseConfirmFeature<OperationConfirmState, OperationConfirmAction>(request, accountRepo, passcodeManager),
    OperationConfirmState {

    private class SendAttempt(
        val context: Context,
        val data: SendData,
        val signed: SignedData?,
    )

    // An error of a send stays with the data it was prepared with: preparing again leaves it behind.
    private class SendFailure(
        val data: SendData,
        val error: ConfirmationError,
    )

    // Only a closed passcode prompt; any other cancellation while the screen is alive is a failed send.
    private class PasscodeDismissed(cause: CancellationException) : CancellationException("passcode dismissed") {
        init {
            initCause(cause)
        }
    }

    private class ConfirmationException(
        val error: ConfirmationError,
        cause: Throwable? = null,
    ) : Exception(error.toString(), cause)

    init {
        require(request.type is ConfirmType.Operational) { "${request.type::class.simpleName} carries no operation" }
    }

    private val sender: OperationSender = DefaultOperationSender(provider)
    private val walletId = request.data.walletId
    private val analytics = ConfirmAnalytics(request)

    // Signed once: a retry sends the same bytes again, so a send that did land is never doubled.
    // The signature is immutable per operation, so a payload that lands late is redundant, never stale.
    private val signed = actions
        .on<OperationConfirmAction.Internal.Signed>()
        .map { it.signed }
        .cacheState(initialValue = null)

    private val sent = actions
        .on<OperationConfirmAction.Internal.Sent>()
        .map { true }
        .cacheState(initialValue = false)

    // Until something is signed a retry prepares again; after that it sends what was signed.
    private val retries = actions
        .on<OperationConfirmAction.Retry>()
        .withLatestFrom(signed) { retry, signed -> retry to signed }

    // Prepare
    private val prepareAttempt = retries
        .filter { (_, signed) -> signed == null }
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val prepareState = prepareAttempt
        .transformStateLatest { prepare() }
        .cacheStateWithLoading()

    private val sendData = prepareState
        .mapStateDataValueOrNull()
        .mapResultValueOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    override val wallet = sendData
        .map { it?.wallet }
        .cacheState(initialValue = null)

    override val account = sendData
        .map { it?.position }
        .cacheState(initialValue = null)

    override val fee = sendData
        .map { it?.fee }
        .cacheState(initialValue = null)

    override val feeRate = sendData
        .map { it?.feeRate }
        .cacheState(initialValue = null)

    // Send
    private val sendState = merge(
        actions
            .on<OperationConfirmAction.Confirm>()
            .map { it.context },
        retries
            .filter { (_, signed) -> signed != null }
            .map { (retry, _) -> retry.context },
    )
        .withLatestFrom(sendData, signed, sent) { context, data, signed, sent ->
            data?.takeUnless { sent }?.let { SendAttempt(context, it, signed) }
        }
        .filterNotNull()
        .transformStateFirst { attempt -> send(attempt) }
        .cacheStateWithEmpty()

    override val sendingState = combine(
        sendState.isLoading(),
        sent,
        transform = { loading, sent ->
            when {
                sent -> SendingState.Done
                loading -> SendingState.Loading
                else -> SendingState.None
            }
        }
    )
        .cacheState(initialValue = SendingState.None)

    // TODO TK-4608: count the deadline down on the screen. Until then only the swipe checks it.
    override val confirmationError: StateFlow<ConfirmationError?> = combine(
        prepareState,
        sendState,
        sent,
        transform = { prepared, send, sent ->
            val prepareResult = (prepared as? KState.Data)?.state
            when {
                // A send in flight was checked when it started and reports its own outcome.
                sent || send is KState.Loading -> null
                prepareResult is KResult.Err -> prepareResult.value
                else -> send.state?.takeIf { it.data === prepareResult?.getOrNull() }?.error
            }
        }
    )
        .cacheState(initialValue = null)

    private suspend fun prepare(): KResult<SendData, ConfirmationError> {
        return analytics.redOperation(RedOperationsOperation.Emulate) {
            try {
                val data = withContext(Async.Io) { prepareSend(request) }
                success()
                KResult.Ok(data)
            } catch (e: Throwable) {
                verifyError(e)
                L.e(e)
                report(e)
                KResult.Err(e.toConfirmationError())
            }
        }
    }

    // What sending [quoted] takes: the chain checked, every payload's fee, the nonce it starts at, the balance.
    private suspend fun prepareSend(quoted: ConfirmRequest): SendData {
        val wallet = accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")
        val position = accountRepo.findAccount(walletId, request.data.assetId)
            ?: throw IllegalStateException("Can't find account")

        val chainAccount = position.data.chainAccount
        val operation = quoted.operation

        // The chain signs with the sender's chain id, whatever chain the payload names. A preview's
        // payloads are only priced here: the authorizer signs them, each on its own chain.
        val foreign = !quoted.data.preview && operation.payloads.any { it.chain != chainAccount.chain }
        if (operation.payloads.isEmpty() || foreign) {
            throw ConfirmationException(ConfirmationError.UnsupportedTransaction)
        }

        val fees = operation.payloads.map { payload ->
            val result = provider.estimateFee(payload, chainAccount)
            result.getOrNull() ?: throw ConfirmationException(
                error = result.error?.let(::parseChainError) ?: ConfirmationError.Unknown,
                cause = result.error,
            )
        }

        val feeAsset = fees.first().asset
        if (fees.any { it.asset.id != feeAsset.id }) {
            throw ConfirmationException(ConfirmationError.UnsupportedTransaction)
        }

        val nonceResult = provider.getNonce(chainAccount)
        val nonce = nonceResult.getOrNull() ?: throw ConfirmationException(
            error = nonceResult.error?.let(::parseNodeError) ?: ConfirmationError.Unknown,
            cause = nonceResult.error,
        )

        val total = fees.fold(BigInteger.ZERO) { sum, item -> sum + item.amount }
        val coin = if (feeAsset.id == chainAccount.asset.id) {
            accountRepo.findAccount(walletId, chainAccount.asset.id, forceRefresh = true)
        } else {
            null
        }

        if (coin != null) {
            val spent = operation.payloads.fold(total) { sum, payload -> sum + payload.meta.coinValue() }
            if (spent > coin.unitBalance.value) {
                throw ConfirmationException(ConfirmationError.InsufficientBalance)
            }
        }

        return SendData(
            request = quoted,
            wallet = wallet,
            position = position,
            fees = fees,
            nonce = nonce,
            fee = Fee.Value(feeAsset, total),
            feeRate = coin?.rate?.value,
        )
    }

    private suspend fun send(attempt: SendAttempt): SendFailure? {
        val data = attempt.data
        if (isExpired(attempt.signed?.data?.request ?: data.request)) {
            return SendFailure(data, ConfirmationError.Expired)
        }

        val (signed, sent) = analytics.redOperation(analytics.sendOperation) {
            try {
                val signed = attempt.signed ?: sign(attempt.context, data).also {
                    sendAction(OperationConfirmAction.Internal.Signed(it))
                }
                val sent = withContext(Async.Io) {
                    val signing = signed.data
                    val send = suspend { sender.send(walletId, signing.account, signed.operation) }
                    tracker?.track(signing.request, signing.account, signed.operation, send) ?: send()
                }
                success()
                signed to sent
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                report(e)
                // Nothing was signed or sent and the swipe is ready again.
                if (e is PasscodeDismissed) {
                    return null
                }
                L.e(e)
                return SendFailure(data, e.toConfirmationError())
            }
        }

        // Past this point the operation is out: what follows must not turn it into a failed send.
        try {
            reportSent(signed.data, sent)
            postTransactionRefreshSchedule.notifyTransactionSent(walletId, signed.data.account.chain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
        }

        sendAction(OperationConfirmAction.Internal.Sent)
        delay(SUCCESS_HOLD_MS)
        finish()
        return null
    }

    private suspend fun sign(context: Context, data: SendData): SignedData {
        val cryptoWallet = try {
            unlockCryptoWallet(context, walletId)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw PasscodeDismissed(e)
        }

        // The passcode prompt can outlast the deadline.
        if (isExpired(data.request)) {
            throw ConfirmationException(ConfirmationError.Expired)
        }

        return withContext(Async.Io) {
            val authorized = if (data.request.data.preview) {
                authorizer.authorize(data.request, cryptoWallet)
            } else {
                data.request
            }

            // An equal operation, its service-minted id included, is the one on screen. A new one: what it
            // costs and the nonce it starts at are its own.
            val signing = if (authorized.operation == data.operation) {
                data
            } else {
                prepareSend(authorized)
            }

            val operation = sign(signing, cryptoWallet)

            tracker?.onSigned(signing.request, signing.account, operation, cryptoWallet)

            SignedData(signing, operation)
        }
    }

    private suspend fun reportSent(signed: SendData, sent: List<SentPayload>) {
        for (item in sent) {
            val payload = signed.operation.payloads.first { it.id == item.payload.payloadId }
            provider.reportPayloadSent(
                walletId = walletId,
                account = signed.account,
                payload = payload,
                txHash = item.txHash,
                swap = tracker?.pendingSwap(signed.request, payload),
            )
        }
    }

    private suspend fun sign(data: SendData, cryptoWallet: CryptoWallet): SignedOperation {
        val operation = data.operation
        var nonce = data.nonce

        val payloads = operation.payloads.mapIndexed { index, payload ->
            val result = provider.signPayload(payload, data.account, data.fees[index], nonce, cryptoWallet)
            if (payload.type.isTransaction()) {
                nonce += BigInteger.ONE
            }

            result.getOrNull() ?: throw ConfirmationException(
                error = result.error?.let(::parseSignError) ?: ConfirmationError.Unknown,
                cause = result.error,
            )
        }

        return SignedOperation(operation.id, payloads)
    }

    private fun isExpired(quoted: ConfirmRequest): Boolean {
        val expiresAt = quoted.data.expiresAt
        return expiresAt != null && expiresAt - EXPIRY_LEAD_MS <= System.currentTimeMillis()
    }

    private fun RedOperation.report(e: Throwable) {
        when (e) {
            is ConfirmationException -> failure(e.error)
            else -> failure(e)
        }
    }

    private fun Throwable.toConfirmationError(): ConfirmationError {
        return when (this) {
            is ConfirmationException -> error
            is ConfirmationErrorCause -> confirmationError
            is NodeError -> parseNodeError(this)
            else -> ConfirmationError.Unknown
        }
    }

    // A document a relayer submits rides on the relayer's transaction and takes no nonce of the sender's.
    private fun Payload.Type.isTransaction(): Boolean {
        return this == Payload.Type.Main || this == Payload.Type.Prebuild || this == Payload.Type.Batch
    }

    private companion object {
        const val EXPIRY_LEAD_MS = 2_000L
    }
}

/*
 *    Signed ──► signed?
 *      Sent ──► sent
 *
 *                Retry ──┐
 *                        ├──► retries
 *             signed? ⋯──┘
 *
 *   retries [unsigned] ──► [fold] prepareAttempt ──► [latest] prepareState
 *
 *   prepareState [dataOrNull, okOrNull] ──► sendData? ──┬──► wallet?
 *                                                       ├──► account?
 *                                                       ├──► fee?
 *                                                       └──► feeRate?
 *
 *   Confirm / retries [signed] ──┐
 *                   sendData? ⋯──┤
 *                     signed? ⋯──┼──► [first] sendState? ──► events
 *                        sent ⋯──┘            ╎
 *                                             ╎
 *                     Signed / Sent ◄╌╌╌╌╌╌╌╌╌┘
 *
 *   sendState? [loading] ──┐
 *                          ├──► sendingState
 *                    sent ─┘
 *
 *         prepareState ──┐
 *           sendState? ──┼──► confirmationError?
 *                 sent ──┘
 */

private val ConfirmRequest.operation
    get() = (type as? ConfirmType.Operational)?.operation
        ?: throw IllegalArgumentException("${type::class.simpleName} carries no operation")

fun PayloadMeta.coinValue(): BigInteger {
    return when (this) {
        is PayloadMeta.Ton.Main -> value
        is PayloadMeta.Ton.Batch -> calls.fold(BigInteger.ZERO) { sum, call -> sum + call.coinValue() }
        is PayloadMeta.Evm.Main -> value
        is PayloadMeta.Tron.Main -> value
        is PayloadMeta.Bitcoin.Main -> value
        else -> BigInteger.ZERO
    }
}
