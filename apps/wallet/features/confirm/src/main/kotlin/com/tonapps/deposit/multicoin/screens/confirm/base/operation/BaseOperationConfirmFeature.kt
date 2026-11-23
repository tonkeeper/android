package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.RedOperations.RedOperationsOperation
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.core.flags.BootFlags
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.deposit.multicoin.screens.confirm.RedOperation
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.endsSend
import com.tonapps.deposit.multicoin.screens.confirm.base.isShortage
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSend
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSigner
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.fee.mayHaveRelayed
import com.tonapps.deposit.multicoin.screens.confirm.fee.relayedHash
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.WalletUnlocker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

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
    val quoted: StateFlow<ConfirmRequest>
    val quoteChanged: StateFlow<Boolean>
    val feeMethods: StateFlow<TxFeeState?>
    val feeMethod: StateFlow<TxFee?>
    val feeQuoting: StateFlow<Boolean>

    /** What the operation does past what the screen shows: the user reads it and may still confirm. */
    val findings: StateFlow<List<OperationFinding>>

    /** Whether [findings] are still being read for what the swipe signs: the swipe waits for them. */
    val findingsReading: StateFlow<Boolean>

    /** Whether the prepare failed for want of a fee: the chain could not price the operation. */
    val feeUnavailable: StateFlow<Boolean>

    /** What the balance falls short of when that is what stops the send; null otherwise. */
    val shortfall: StateFlow<OperationShortfall?>
}

sealed interface OperationConfirmAction : MviAction {
    data object Confirm : OperationConfirmAction
    data object Retry : OperationConfirmAction
    data class PickFee(val id: String) : OperationConfirmAction
    data object RefreshFees : OperationConfirmAction

    sealed interface Internal : OperationConfirmAction {
        data class Signed(val signed: SignedData) : Internal
        data object Sent : Internal
        data class QuoteChanged(val data: SendData) : Internal
        data class Requoted(val request: ConfirmRequest) : Internal
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
    val findings: List<OperationFinding> = emptyList(),
    val feeMethods: TxFeeState = TxFeeState.Empty,
    /** What the operation is priced and signed as: the chain's own account unless [OperationQuote.signer] says otherwise. */
    val account: Account = position.data.chainAccount,
    /** Whether [request] is what [OperationQuote.refit] made of the screen's, to leave room for the fee. */
    val refitted: Boolean = false,
) {

    val operation: Operation
        get() = request.operation

    /** [request], what the authorizer filled in, priced as this one and read as [findings]. */
    fun authorizedAs(request: ConfirmRequest, findings: List<OperationFinding>): SendData = SendData(
        request = request,
        wallet = wallet,
        position = position,
        fees = fees,
        nonce = nonce,
        fee = fee,
        feeRate = feeRate,
        findings = findings,
        feeMethods = feeMethods,
        account = account,
        refitted = refitted,
    )
}

/** [data] signed as [operation]: [data] is what [OperationAuthorizer.authorize] made of the one on screen. */
class SignedData(
    val data: SendData,
    val operation: SignedOperation,
)

/**
 * Turns a request whose operation is not yet the one sent — it is what the wallet signs to get it, or
 * holds a stand-in for what only the keys make, as an encrypted comment — into the request to sign.
 * Asked of every request; the one it makes is held to the intent again, and priced again only when it
 * quotes a preview for real: a stand-in costs what it stands in for. Handed to the
 * feature's constructor rather than overridden: the send is prepared while the feature is
 * constructed, before a subclass's own fields exist.
 */
fun interface OperationAuthorizer {

    /** With the wallet unlocked, before anything is signed: [request] as it is, or the one it makes. */
    suspend fun authorize(request: ConfirmRequest, wallet: MnemonicWallet): ConfirmRequest

    companion object {
        /** The request's own operation, signed as it is. */
        val Original = OperationAuthorizer { request, _ -> request }
    }
}

fun interface OperationRequote {

    suspend fun requote(request: ConfirmRequest): ConfirmRequest
}

/**
 * What the user agreed to, which the operation is held to before it is signed: what the screen shows
 * leaving [account], and where. Handed to the feature's constructor, as [OperationAuthorizer] is.
 */
fun interface OperationIntent {

    fun intent(request: ConfirmRequest, account: Account): Intent
}

/**
 * Makes the operation of a request that holds what the user asked for rather than what is signed: a
 * transfer's recipient and amount, built as its chain signs them. Handed to the feature's
 * constructor, as [OperationAuthorizer] is.
 */
interface OperationQuote {

    /** The request whose operation is priced, validated and signed for [request] as the screen holds it. */
    suspend fun build(request: ConfirmRequest): ConfirmRequest

    /**
     * [built] once its [fee] is known: what it sends past the fee, or [built] itself when that does not
     * change. [relayable] when a relayer may pay the fee, so the wallet need not cover it.
     */
    suspend fun fit(built: ConfirmRequest, fee: Fee, relayable: Boolean): ConfirmRequest

    /**
     * [built] made anew to send less, when what it sends out of [payer], the account [fee] is paid from,
     * leaves the balance short of [fee], the fee as the method picked charges it. Null when [built] is
     * sent as it is. What it makes is priced as [built] was, and not fitted to its own fee again.
     */
    suspend fun refit(built: ConfirmRequest, payer: AccountWithDetails, fee: Fee): ConfirmRequest? = null

    /**
     * The account the built operation is priced and signed as: the chain's own, or [position]'s own
     * for an operation that sends its asset, as an SPL transfer that names its mint is signed.
     */
    fun signer(position: AccountWithDetails): Account = position.data.chainAccount

    companion object {
        /** The request's own operation, signed as it is. */
        val Original = object : OperationQuote {
            override suspend fun build(request: ConfirmRequest): ConfirmRequest = request

            override suspend fun fit(built: ConfirmRequest, fee: Fee, relayable: Boolean): ConfirmRequest = built
        }
    }
}

/**
 * What the screen says the user pays of the sender's own asset. With it a balance that falls short is
 * told as the transaction confirm told it: what is to be paid against the balance, with what was priced
 * kept on screen. Handed to the feature's constructor, as [OperationAuthorizer] is; without it a
 * shortfall is the prepare's error alone.
 */
fun interface OperationSpending {

    /** What [request] takes of [account]'s asset past the fee; null when the screen does not say. */
    fun spent(request: ConfirmRequest, account: Account): BigInteger?

    /** What [data] sends of its position's asset with [method] paying the fee: what the send reports. */
    fun sent(data: SendData, method: TxFee?): BigInteger? = spent(data.request, data.position.value)
}

class QuoteChangedException(
    val request: ConfirmRequest,
    field: String,
) : Exception("quote changed: $field")

/**
 * Confirms a request of a [ConfirmType.Operational], as [quote] builds it and [authorizer] makes it. A
 * dapp's request is answered with the hash its operation was sent under, or, asked only to sign, with
 * the signature.
 */
abstract class BaseOperationConfirmFeature(
    request: ConfirmRequest,
    private val provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    private val postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    private val intent: OperationIntent,
    private val timeProvider: TimeProvider,
    private val tracker: OperationTracker? = null,
    private val authorizer: OperationAuthorizer = OperationAuthorizer.Original,
    private val relayedFeeMethods: OperationFeeMethods? = null,
    private val quote: OperationQuote = OperationQuote.Original,
    private val requote: OperationRequote? = null,
    protected val spending: OperationSpending? = null,
    confirmResults: ConfirmResultListener? = null,
    private val isValidationEnabled: () -> Boolean = { BootFlags.isTxValidationEnabled },
    // The fee method picked before this screen opened, which the first prepare already prices by.
    pickedFee: String? = null,
) : BaseConfirmFeature<OperationConfirmState, OperationConfirmAction>(request, accountRepo, walletUnlocker, signatureGate, confirmResults),
    OperationConfirmState {

    private class SendAttempt(
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
        val feeUnavailable: Boolean = false,
        val priced: Priced? = null,
    ) : Exception(error.toString(), cause)

    // What a prepare priced before the balance fell short of it: the fee, its rate, the ways to pay it,
    // and the request a refit made of the screen's, where it priced that one.
    private class Priced(
        val fee: Fee,
        val feeRate: FiatRate?,
        val methods: TxFeeState,
        val refitted: ConfirmRequest? = null,
        val coinShort: CoinShort? = null,
    )

    protected class CoinShort(
        val sent: BigInteger,
        val coin: AccountWithDetails,
        val wallet: McWalletEntity,
        val position: AccountWithDetails,
    )

    // A failed prepare: its error, whether it is the fee the chain could not price, and what it priced
    // before it fell short.
    private class PrepareFailure(
        val error: ConfirmationError,
        val feeUnavailable: Boolean = false,
        val priced: Priced? = null,
    )

    // [data] is null for what a prepare that fell short priced: the screen shows it, nothing sends it.
    private class PricedFees(
        val data: SendData?,
        val methods: TxFeeState,
    )

    init {
        require(request.type is ConfirmType.Operational) { "${request.type::class.simpleName} carries no operation" }
    }

    /**
     * How the screen is laid out: a feature that says what the user pays takes over the transaction
     * confirm, and is laid out and tells a shortfall as it did.
     */
    val layout: OperationLayout = if (spending != null) {
        OperationLayout.Transaction
    } else {
        OperationLayout.Deposit
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

    private val sent: StateFlow<Boolean> = actions
        .on<OperationConfirmAction.Internal.Sent>()
        .map { true }
        .cacheState(initialValue = false)

    // Until something is signed a retry prepares again; after that it sends what was signed.
    private val retries = actions
        .on<OperationConfirmAction.Retry>()
        .filter { !confirmationError.value.endsSend }
        .withLatestFrom(signed) { _, signed -> signed }

    private val quoteChanges = actions
        .on<OperationConfirmAction.Internal.QuoteChanged>()
        .map { it.data }

    override val quoted = merge(
        quoteChanges.map { it.request },
        actions.on<OperationConfirmAction.Internal.Requoted>().map { it.request },
    )
        .cacheState(initialValue = request)

    override val quoteChanged = merge(
        quoteChanges.map { true },
        actions.on<OperationConfirmAction.Confirm>().map { false },
    )
        .cacheState(initialValue = false)

    // Read by the prepare, which may start while the feature is constructed: declared ahead of it.
    private val pickedFeeId = actions
        .on<OperationConfirmAction.PickFee>()
        .map { it.id }
        .cacheState(initialValue = pickedFee)

    // Prepare
    private val feeRefreshes = actions
        .on<OperationConfirmAction.RefreshFees>()
        .filter { signed.value == null && !sent.value && sendingState.value == SendingState.None }
        .filter { confirmationError.value != ConfirmationError.OutcomeUnknown }

    private val requotePending = AtomicBoolean(false)

    private val prepareAttempt = merge(
        retries
            .filter { signed -> signed == null }
            .map { requotePending.set(requote != null) },
        feeRefreshes.map { },
    )
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val prepareState = prepareAttempt
        .transformStateLatest { prepare() }
        .cacheStateWithLoading()

    /** Whether a prepare is in flight. */
    protected val preparing: StateFlow<Boolean> = prepareState
        .isLoading()
        .cacheState(initialValue = true)

    /** Whether something is signed: a retry then sends it again rather than preparing anew. */
    protected val isSigned: Boolean
        get() = signed.value != null

    /** What a send signs: [quoted] as prepared, built by [quote] and priced. */
    protected val sendData = merge(
        prepareState
            .mapStateDataValueOrNull()
            .mapResultValueOrNull()
            .map { it?.value },
        quoteChanges,
    )
        .cacheState(initialValue = null)

    private val prepareFailure = prepareState
        .map { prepared -> ((prepared as? KState.Data)?.state as? KResult.Err)?.value }
        .cacheState(initialValue = null)

    // What a prepare that fell short priced stays on screen, as the transaction confirm kept it, where
    // the feature tells a shortfall that way.
    private val shortPriced = prepareFailure
        .map { failure -> failure?.priced?.takeIf { spending != null } }
        .cacheState(initialValue = null)

    protected val coinShort: StateFlow<CoinShort?> = shortPriced
        .map { it?.coinShort }
        .cacheState(initialValue = null)

    protected val preparedCoinShort: Boolean
        get() = spending != null && ((prepareState.value as? KState.Data)?.state as? KResult.Err)?.value?.priced?.coinShort != null

    private val pricedFees = combine(sendData, shortPriced, pickedFeeId) { data, short, picked -> Triple(data, short, picked) }
        .transformStateLatest { (data, short, picked) ->
            val methods = data?.feeMethods ?: short?.methods ?: return@transformStateLatest null
            PricedFees(data, quoteFeeMethod(methods, picked))
        }
        .cacheStateWithEmpty()

    override val feeMethods = pricedFees
        .map { priced -> priced.state?.methods?.takeIf { it.options.isNotEmpty() } }
        .cacheState(initialValue = null)

    override val feeQuoting = pricedFees
        .isLoading()
        .cacheState(initialValue = false)

    override val feeMethod = combine(feeMethods, pickedFeeId) { methods, picked -> methods?.resolve(picked) }
        .cacheState(initialValue = null)

    // The wallet and the sender's position as stored, until a prepare reads them fresh: a screen whose
    // prepare fails still shows what is sent, and from where.
    private val stored = flow { emit(storedPosition()) }
        .cacheState(initialValue = null)

    override val wallet = combine(sendData, stored) { data, stored -> data?.wallet ?: stored?.first }
        .cacheState(initialValue = null)

    override val account = combine(sendData, stored) { data, stored -> data?.position ?: stored?.second }
        .cacheState(initialValue = null)

    override val fee = combine(sendData, shortPriced, feeMethod) { data, short, method -> method?.fee ?: data?.fee ?: short?.fee }
        .cacheState(initialValue = null)

    override val feeRate = combine(sendData, shortPriced, feeMethod) { data, short, method ->
        method?.account?.rate ?: data?.feeRate ?: short?.feeRate
    }
        .cacheState(initialValue = null)

    // What the swipe signs is what the user reads: the relayer's operation while a relayed method is picked
    // and priced, the screen's own otherwise. The pick changes what is signed, so the findings are read anew.
    // A preview's relayed operation is a stand-in: the authorized one is quoted and relayed in its place.
    private val readFindings = combine(sendData, feeMethods, feeMethod) { data, methods, method -> Triple(data, methods, method) }
        .transformStateLatest { (data, methods, method) ->
            data ?: return@transformStateLatest null
            val relayed = methods?.relayedOperation
                ?.takeIf { method != null && method.viaRelayer && methods.quotedId == method.id && !data.request.data.preview }
                ?: return@transformStateLatest data.findings

            // The swipe waits on these findings, so a check that throws still gives one: it read nothing, read as
            // a validator answering Unknown would be. Signing checks again, and there a throw still stops the send.
            try {
                withContext(Async.Io) { validate(data.request, data.position.value, relayed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e(e)
                val unread = ValidationError.Unknown(e)
                val source = intent.intent(data.request, data.position.value).source
                listOf(OperationFinding(unread, asset = null, severity = unread.severity.within(source)))
            }
        }
        .cacheStateWithEmpty()

    override val findings = readFindings
        .map { it.state.orEmpty() }
        .cacheState(initialValue = emptyList())

    override val findingsReading = readFindings
        .isLoading()
        .cacheState(initialValue = false)

    // Send
    private val sendState = merge(
        actions
            .on<OperationConfirmAction.Confirm>()
            .filter { !confirmationError.value.endsSend },
        retries.filter { signed -> signed != null },
    )
        .withLatestFrom(sendData, signed, sent) { _, data, signed, sent ->
            data?.takeUnless { sent }?.let { SendAttempt(it, signed) }
        }
        .filterNotNull()
        .transformStateFirst { attempt -> send(attempt) }
        .cacheStateWithEmpty()

    override val sendingState: StateFlow<SendingState> = combine(
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
        sendData,
        sendState,
        sent,
        feeMethod,
        transform = { prepared, data, send, sent, method ->
            val prepareResult = (prepared as? KState.Data)?.state
            when {
                // A send in flight was checked when it started and reports its own outcome.
                sent || send is KState.Loading -> null
                prepareResult is KResult.Err -> prepareResult.value.error
                else -> send.state?.takeIf { it.data === data }?.error
                    ?: method?.takeIf { data != null && !TxFeeLogic.isSufficient(it) }?.shortfall()
            }
        }
    )
        .cacheState(initialValue = null)

    override val feeUnavailable = prepareFailure
        .map { spending != null && it?.feeUnavailable == true }
        .cacheState(initialValue = false)

    override val shortfall = combine(confirmationError, shortPriced, pricedFees, account) { error, short, priced, position ->
        val methods = priced.state?.methods
        Shortage(
            error = error,
            methods = methods,
            estimated = methods?.estimated ?: priced.state?.data?.fee ?: short?.fee,
            position = position,
            sent = short?.coinShort?.sent,
            request = priced.state?.data?.takeIf { it.refitted }?.request ?: short?.refitted ?: quoted.value,
        )
    }
        .map { shortfallOf(it) }
        .cacheState(initialValue = null)

    final override val isCommitted: Boolean
        get() = signed.value != null || sent.value

    init {
        watchRequestGone()
    }

    override fun onStoppedAsGone() {
        analytics.markRequestGone()
    }

    fun pickFee(option: TxFee) {
        if (sendingState.value != SendingState.None || signed.value != null) {
            return
        }
        relayedFeeMethods?.remember(walletId, option)
        sendAction(OperationConfirmAction.PickFee(option.id))
    }

    /** Picks [option] as one kept from an earlier screen, not chosen on this one: the wallet's preference stays. */
    protected fun keepFee(option: TxFee) {
        if (sendingState.value != SendingState.None || signed.value != null) {
            return
        }
        sendAction(OperationConfirmAction.PickFee(option.id))
    }

    private suspend fun quoteFeeMethod(methods: TxFeeState, pickedId: String?): TxFeeState {
        if (methods.options.isEmpty()) {
            return methods
        }
        return TxFeeLogic.quote(methods, pickedId)
    }

    private suspend fun prepare(): KResult<SendData, PrepareFailure> {
        val shown = try {
            requoteIfPending() ?: quoted.value
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e)
            return KResult.Err(e.toPrepareFailure())
        }
        return analytics.redOperation(RedOperationsOperation.Emulate, otherMetadata = analytics.emulateMetadata(shown)) {
            // The terminal names the provider the route was prepared with, once the build quoted it.
            var prepared = shown
            try {
                val data = withContext(Async.Io) {
                    prepared = quote.build(prepared)
                    prepareSend(prepared)
                }
                success(analytics.emulateMetadata(data.request))
                KResult.Ok(data)
            } catch (e: Throwable) {
                verifyError(e)
                L.e(e)
                report(e, otherMetadata = analytics.emulateMetadata(prepared))
                KResult.Err(e.toPrepareFailure())
            }
        }
    }

    private suspend fun requoteIfPending(): ConfirmRequest? {
        val pendingRequote = requote?.takeIf { requotePending.get() } ?: return null
        val shown = quoted.value
        return analytics.redOperation(RedOperationsOperation.Quote, otherMetadata = analytics.emulateMetadata(shown)) {
            try {
                val requoted = withContext(Async.Io) { pendingRequote.requote(shown) }
                requotePending.set(false)
                sendAction(OperationConfirmAction.Internal.Requoted(requoted))
                success(analytics.emulateMetadata(requoted))
                requoted
            } catch (e: Throwable) {
                verifyError(e)
                report(e, otherMetadata = analytics.emulateMetadata(shown))
                throw e
            }
        }
    }

    private fun Throwable.toPrepareFailure(): PrepareFailure = when (this) {
        is ConfirmationException -> PrepareFailure(error, feeUnavailable, priced)
        else -> PrepareFailure(toConfirmationError())
    }

    // [estimated] is what the chain charges, whichever method pays it. [request] is what the screen asks
    // for: what a refit made of it, where the route was fitted to its fee.
    private class Shortage(
        val error: ConfirmationError?,
        val methods: TxFeeState?,
        val estimated: Fee?,
        val position: AccountWithDetails?,
        val sent: BigInteger?,
        val request: ConfirmRequest,
    )

    // What the balance falls short of, told as the transaction confirm told it. Not while another fee
    // method can pay: the user picks it instead.
    private suspend fun shortfallOf(shortage: Shortage): OperationShortfall? {
        val spending = spending ?: return null
        val error = shortage.error
        if (!error.isShortage) {
            return null
        }

        val position = shortage.position ?: return null
        val methods = shortage.methods
        if (methods != null && methods.options.any(TxFeeLogic::isSufficient)) {
            return null
        }

        val balance = position.unitBalance.value
        val isCoin = position.asset.value is Asset.Coin
        val spent = listOfNotNull(spending.spent(shortage.request, position.value), shortage.sent?.takeIf { isCoin }).maxOrNull()

        // A recharged battery pays the fee, never what is sent: a coin that cannot cover its own amount
        // is short on balance whatever pays the fee.
        val rechargeable = methods != null && methods.batteryRechargeable && methods.estimated != null
        if (rechargeable && (spent == null || !isCoin || spent <= balance)) {
            return OperationShortfall.Fee(
                coin = methods.energy ?: position,
                network = position.asset.value.chain.name,
                fee = methods.requiredEnergy,
                transfer = request.type is ConfirmType.Transfer,
            )
        }

        // The account the fee is paid from: the coin, which a token send buys when it is short of the fee.
        val energy = methods?.energy ?: if (isCoin) {
            position
        } else {
            accountRepo.findAccount(walletId, position.data.chainAccount.asset.id)
        }
        val generic = OperationShortfall.Balance(energy ?: position)
        val estimated = shortage.estimated

        if (!isCoin) {
            if (spent != null && spent > balance) {
                return OperationShortfall.Balance(position, required = spent)
            }
            energy ?: return generic
            estimated ?: return generic
            // The gas reserve that fell short checks the main fee only, so an approval's stays out.
            val attached = methods?.attachedEnergy?.takeUnless { it.isZero() } ?: shortage.sent ?: BigInteger.ZERO
            val required = TxFeeLogic.requiredEnergy(estimated, attached)
            return OperationShortfall.Balance(energy, required = required, forFees = true)
        }

        // With no fee known and the amount covered, "to be paid: amount" would read as if nothing is
        // wrong: the shortfall is in the unknown fee.
        spent ?: return generic
        if (estimated == null && spent <= balance) {
            return generic
        }
        return OperationShortfall.Balance(position, required = spent + (estimated?.amount ?: BigInteger.ZERO))
    }

    private suspend fun storedPosition(): Pair<McWalletEntity?, AccountWithDetails?> {
        return try {
            accountRepo.getWallet(walletId) to accountRepo.findAccount(walletId, request.data.assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null to null
        }
    }

    // What sending [quoted] takes: the chain checked, every payload's fee, the nonce it starts at, the balance.
    // [refitted] when [quoted] is what a refit made of the screen's request: it is not fitted again.
    private suspend fun prepareSend(quoted: ConfirmRequest, paidInCoin: Boolean = false, refitted: Boolean = false): SendData {
        val wallet = accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")
        val position = accountRepo.findAccount(walletId, request.data.assetId)
            ?: throw IllegalStateException("Can't find account")

        val chainAccount = position.data.chainAccount
        val signer = quote.signer(position)
        val operation = quoted.operation

        // The chain signs with the sender's chain id, whatever chain the payload names. A preview's
        // payloads are only priced here: the authorizer signs them, each on its own chain.
        val foreign = !quoted.data.preview && operation.payloads.any { it.chain != chainAccount.chain }
        // A TON batch with no message is no operation, whoever would pay for it.
        val empty = operation.payloads.any { (it.meta as? PayloadMeta.Ton.Batch)?.calls?.isEmpty() == true }
        if (operation.payloads.isEmpty() || foreign || empty) {
            throw ConfirmationException(ConfirmationError.UnsupportedTransaction)
        }

        // What the chain cannot price is priced at its default fee where the fee methods take that: a
        // relayer prices what it carries itself.
        val lenient = !paidInCoin && !quoted.data.preview && relayedFeeMethods?.buildsUnpriced == true
        val pricing = price(operation, signer, position, lenient)
        val quotedFees = pricing.fees
        val feeAsset = quotedFees.first().asset

        val quotedFee = Fee.Value(feeAsset, quotedFees.fold(BigInteger.ZERO) { sum, item -> sum + item.amount })
        val coin = if (feeAsset.id == chainAccount.asset.id) {
            accountRepo.findAccount(walletId, chainAccount.asset.id, forceRefresh = true)
        } else {
            null
        }

        val built = when {
            paidInCoin -> TxFeeState.Empty
            quoted.data.preview -> paymentFeeMethods(quoted, position)
            else -> coin?.let { relayedFeeMethods?.build(quoted, position, it, quotedFee) } ?: TxFeeState.Empty
        }

        // A default fee stands in only where the coin is short of it and a relayer pays instead: the coin
        // is never signed with a fee the chain did not price, so it is no option next to the relayer.
        val failure = pricing.failure
        val feeMethods = if (failure == null) {
            built
        } else {
            val sentCoins = operation.payloads.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() }
            val coinPays = coin == null || sentCoins + quotedFee.amount <= coin.unitBalance.value
            if (coinPays || !built.hasRelayedOption) {
                throw failure
            }
            built.relayedOnly()
        }

        // What the fee leaves no room for may be built again to send less: that is what is sent, priced anew.
        if (!refitted && !paidInCoin && !quoted.data.preview) {
            val paid = feeMethods.resolve(pickedFeeId.value)?.fee ?: quotedFee
            val payer = coin?.takeIf { it.asset.id == position.asset.id } ?: position
            val smaller = quote.refit(quoted, payer, paid)
            if (smaller != null) {
                return prepareSend(smaller, refitted = true)
            }
        }

        // A nonce the node does not give leaves the fee priced: the screen still shows it.
        val nonceResult = provider.getNonce(signer)
        val nonce = nonceResult.getOrNull() ?: throw ConfirmationException(
            error = nonceResult.error?.let(::parseNodeError) ?: ConfirmationError.Unknown,
            cause = nonceResult.error,
            priced = Priced(quotedFee, coin?.rate?.value, feeMethods, quoted.takeIf { refitted }),
        ).namingCoin(position)

        // What is left past the fee is signed with that fee, as the transaction confirm signed it: a coin
        // payment's fee does not follow its amount. One already authorized is signed as it is.
        val fitted = if (paidInCoin || quoted.data.preview) {
            quoted
        } else {
            try {
                quote.fit(quoted, quotedFee, feeMethods.hasRelayedOption)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ConfirmationException(
                    error = e.toConfirmationError(),
                    cause = e,
                    priced = Priced(quotedFee, coin?.rate?.value, feeMethods, quoted.takeIf { refitted }),
                )
            }
        }

        // What a relayer attaches beside a token, a jetton transfer's forward TON, is the relayer's to
        // cover, as the fee is. The coin itself sent is the wallet's own, whoever pays the fee.
        if (coin != null) {
            val sentCoins = fitted.operation.payloads.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() }
            val balance = coin.unitBalance.value
            val relayed = if (feeMethods.hasRelayedOption && position.asset.value is Asset.Token) {
                feeMethods.attachedEnergy
            } else {
                BigInteger.ZERO
            }
            val required = if (feeMethods.hasRelayedOption) {
                sentCoins - relayed
            } else {
                sentCoins + quotedFee.amount
            }
            if (required > balance) {
                throw ConfirmationException(
                    error = ConfirmationError.InsufficientNetworkFee(
                        symbol = feeAsset.symbol,
                        required = BigDecimal(feeAsset.decimals.toDisplayUnit(required).value.toStringExpanded()),
                    ),
                    priced = Priced(
                        fee = quotedFee,
                        feeRate = coin.rate?.value,
                        methods = feeMethods,
                        refitted = quoted.takeIf { refitted },
                        coinShort = CoinShort(sentCoins, coin, wallet, position),
                    ),
                )
            }
        }

        // A preview is not what is sent: what it makes is validated once it is quoted again. The
        // position's own account, not the chain's: what the user spends is its asset, a token or the coin.
        val findings = if (fitted.data.preview) {
            emptyList()
        } else {
            validate(fitted, position.value)
        }

        return SendData(
            request = fitted,
            wallet = wallet,
            position = position,
            fees = quotedFees,
            nonce = nonce,
            fee = quotedFee,
            feeRate = coin?.rate?.value,
            findings = findings,
            feeMethods = feeMethods,
            account = signer,
            refitted = refitted,
        )
    }

    // The fees as priced, and what kept the chain from pricing a payload that stands at its default fee.
    private class Pricing(val fees: List<Fee>, val failure: Throwable?)

    // Every payload's fee, all paid in one asset. A [lenient] pricing takes the chain's default fee for a
    // payload the chain could not price and keeps the failure for the caller to weigh; a payload with no
    // default fee fails it all the same.
    private suspend fun price(
        operation: Operation,
        signer: Account,
        position: AccountWithDetails,
        lenient: Boolean = false,
    ): Pricing {
        var failure: Throwable? = null
        val fees = operation.payloads.map { payload ->
            val result = provider.estimateFee(payload, signer)
            result.getOrNull() ?: run {
                val unpriced = ConfirmationException(
                    error = result.error?.let(::parseChainError) ?: ConfirmationError.Unknown,
                    cause = result.error,
                    feeUnavailable = true,
                ).namingCoin(position)
                val fallback = if (lenient) {
                    defaultFee(payload, signer)
                } else {
                    null
                }
                failure = failure ?: unpriced
                fallback ?: throw unpriced
            }
        }

        val asset = fees.first().asset
        if (fees.any { it.asset.id != asset.id }) {
            throw ConfirmationException(ConfirmationError.UnsupportedTransaction)
        }
        return Pricing(fees, failure)
    }

    private suspend fun defaultFee(payload: Payload, signer: Account): Fee? {
        val transaction = payload.toTransaction(signer).getOrElse { return null }
        return provider.defaultFee(transaction).getOrNull()
    }

    // The user reads a finding and may go on, so a finding never fails the prepare.
    private suspend fun validate(
        quoted: ConfirmRequest,
        account: Account,
        operation: Operation = quoted.operation,
    ): List<OperationFinding> {
        if (!isValidationEnabled()) {
            return emptyList()
        }

        val agreed = intent.intent(quoted, account)
        val result = provider.validateOperation(operation, account, agreed)

        // A validator that threw, or a chain with none, checked nothing: in a Wallet flow it reaches the user
        // as Info, so the log is all that tells a broken validator from a clean operation.
        // TODO: in a Wallet flow, don't show these to the user at all: there is nothing for them to act on.
        result.findings
            .filter { it is ValidationError.Unknown || it is ValidationError.UnsupportedChain }
            .forEach { L.e(it) }

        return result.findings.map { error ->
            OperationFinding(error, error.amountAsset(agreed), severity = error.severity.within(agreed.source))
        }
    }

    // A flow of the wallet's own is the backend's: what the validator cannot read or finds odd there
    // is not the user's to judge, so only what is dangerous stops the swipe.
    private fun FindingSeverity.within(source: Intent.Source): FindingSeverity = when {
        source == Intent.Source.Wallet && this == FindingSeverity.Warning -> FindingSeverity.Info
        else -> this
    }

    private suspend fun paymentFeeMethods(preview: ConfirmRequest, position: AccountWithDetails): TxFeeState {
        val methods = relayedFeeMethods ?: return TxFeeState.Empty
        val chainAccount = position.data.chainAccount
        return try {
            val payment = methods.payment(preview) ?: return TxFeeState.Empty
            val fees = payment.operation.payloads.map { payload ->
                provider.estimateFee(payload, chainAccount).getOrNull() ?: return TxFeeState.Empty
            }
            val asset = fees.firstOrNull()?.asset ?: return TxFeeState.Empty
            if (fees.any { it.asset.id != asset.id }) {
                return TxFeeState.Empty
            }
            val coin = accountRepo.findAccount(walletId, asset.id, forceRefresh = true) ?: return TxFeeState.Empty
            val estimated = Fee.Value(asset, fees.fold(BigInteger.ZERO) { sum, fee -> sum + fee.amount })
            methods.build(payment, position, coin, estimated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            TxFeeState.Empty
        }
    }

    private suspend fun send(attempt: SendAttempt): SendFailure? {
        val data = attempt.data
        if (attempt.signed == null && data.feeMethods.options.isNotEmpty()) {
            val methods = pricedFees.first { it is KState.Data }.state
                ?.takeIf { it.data === data }
                ?.methods
                ?: return SendFailure(data, ConfirmationError.Unknown)
            val method = methods.resolve(pickedFeeId.value)
                ?: return SendFailure(data, ConfirmationError.Unknown)
            if (!TxFeeLogic.isSufficient(method)) {
                return SendFailure(data, method.shortfall())
            }
            if (method.viaRelayer) {
                return sendRelayed(attempt, methods, method)
            }
        }

        if (isExpired(attempt.signed?.data?.request ?: data.request)) {
            return SendFailure(data, ConfirmationError.Expired)
        }

        var out: Pair<SignedData, List<SentPayload>>? = null
        val (signed, sent) = analytics.redOperation(
            operation = analytics.sendOperation,
            attemptSource = analytics.sendAttemptSource,
            otherMetadata = analytics.sendMetadata(data.account.asset, sentAmount(data, feeMethod.value), data.fee.asset),
        ) {
            try {
                val signed = attempt.signed ?: sign(data).also {
                    stopIfGone()
                    sendAction(OperationConfirmAction.Internal.Signed(it))
                }
                // A request to sign only is answered with the signature: nothing is broadcast.
                val sent = if (request.action.isSignOnly) {
                    emptyList()
                } else {
                    withContext(Async.Io) {
                        val signing = signed.data
                        // A broadcast once started is let finish: what it sent is reported though the screen closes.
                        val send = suspend { withContext(NonCancellable) { sender.send(walletId, signing.account, signed.operation) } }
                        val broadcast = if (tracker != null) {
                            tracker.track(signing.request, signing.account, signed.operation, send)
                        } else {
                            send()
                        }
                        out = signed to broadcast
                        broadcast
                    }
                }
                success()
                signed to sent
            } catch (thrown: Throwable) {
                // The screen closed once the operation was out: it is reported, and counted, as sent all the same.
                out?.let { (outSigned, outSent) ->
                    afterSent(outSigned, outSent)
                    success()
                }
                currentCoroutineContext().ensureActive()
                val e = thrown.namingCoin(data.position)
                if (e is QuoteChangedException) {
                    L.e(e)
                    return showChangedQuote(data, e)
                }
                report(e)
                // Nothing was signed or sent and the swipe is ready again.
                if (e is PasscodeDismissed) {
                    return null
                }
                L.e(e)
                val error = e.toConfirmationError()
                // It may be out: the history the user is told to check picks it up.
                if (error == ConfirmationError.NonceTaken) {
                    postTransactionRefreshSchedule.notifyTransactionSent(walletId, data.account.chain)
                }
                return SendFailure(data, error)
            }
        }

        afterSent(signed, sent)

        sendAction(OperationConfirmAction.Internal.Sent)
        delay(SUCCESS_HOLD_MS)
        finish()
        return null
    }

    // What a dapp asked for: the signed transaction, or the hash it went out under.
    private fun dappResult(signed: SignedData, sent: List<SentPayload>): String? {
        if (request.action.isSignOnly) {
            return (signed.operation.payloads.lastOrNull()?.data as? JsonPrimitive)?.content
        }
        return sent.lastOrNull()?.txHash
    }

    private suspend fun relayAuthorized(preview: ConfirmRequest, picked: TxFee, cryptoWallet: MnemonicWallet): RelayTarget {
        val prepared = prepareSend(authorizer.authorize(preview, cryptoWallet))
        val priced = TxFeeLogic.quote(prepared.feeMethods, picked.id)
        val option = priced.resolve(picked.id)
            ?.takeIf { it.id == picked.id && TxFeeLogic.isSufficient(it) }
            ?: throw ConfirmationException(picked.shortfall())
        val relayer = priced.relayer ?: throw ConfirmationException(ConfirmationError.Unknown)
        if (priced.quote != null && priced.quotedId != option.id) {
            throw ConfirmationException(ConfirmationError.Unknown)
        }
        if (option.fee.amount > picked.fee.amount) {
            throw QuoteChangedException(prepared.request, "fee")
        }
        return RelayTarget(prepared.request, prepared.account, relayer, option)
    }

    private class RelayTarget(
        val request: ConfirmRequest,
        val account: Account,
        val relayer: RelayerSend,
        val option: TxFee,
    )

    /** Once the operation is out: the dapp's answer, the pending report, the refresh, [onOperationSent]. */
    private suspend fun afterSent(signed: SignedData, sent: List<SentPayload>) {
        // What the feature tracks does not hang on the reports: a report that fails skips nothing of it.
        reportAfterSend { onOperationSent() }
        reportAfterSend {
            dappResult(signed, sent)?.let { approveDappRequest(it) }
            if (!request.action.isSignOnly) {
                reportSent(signed.data, sent)
                postTransactionRefreshSchedule.notifyTransactionSent(walletId, signed.data.account.chain)
            }
        }
    }

    /** Once the relayer has sent the operation: the pending report, the refresh, [onOperationSent]. */
    private suspend fun afterRelayed(relayed: Relayed) {
        reportAfterSend { onOperationSent() }
        reportAfterSend {
            reportRelayed(relayed)
            postTransactionRefreshSchedule.notifyTransactionSent(walletId, relayed.account.chain)
        }
    }

    /**
     * A relayed send whose outcome is not known: what it may have sent is reported, and tracked, under the
     * transaction the relayer named, its status telling what became of it, and the balances are refreshed.
     */
    private suspend fun afterUnknownOutcome(target: RelayTarget?, e: Throwable, chain: Chain) {
        val txHash = e.relayedHash
        if (target != null && txHash != null) {
            reportAfterSend { onOperationSent() }
            reportAfterSend { reportRelayed(Relayed(target.request, target.account, txHash)) }
        }
        reportAfterSend { postTransactionRefreshSchedule.notifyTransactionSent(walletId, chain) }
    }

    /**
     * Once the operation is out, whether or not the screen is still there: what a feature keeps track
     * of from here has to outlive the screen.
     */
    protected open fun onOperationSent() = Unit

    /**
     * Whether what a relayer sends is the [request] the authorizer makes once the wallet is unlocked, priced
     * by the relayer again: a dapp's preview, signed as the operation it authorizes, or a payload whose body
     * the wallet only writes then.
     */
    protected open fun relaysAuthorized(request: ConfirmRequest): Boolean = request.data.preview

    /**
     * What follows a send that went out: it fails nothing, and the screen closing does not stop it short,
     * so the swap is reported and `Sent` dispatched all the same.
     */
    private suspend fun reportAfterSend(block: suspend () -> Unit) {
        withContext(NonCancellable) {
            try {
                block()
            } catch (e: Throwable) {
                L.e(e)
            }
        }
    }

    /** What a relayer sent; [txHash] is null where the relayer names no transaction. */
    private class Relayed(
        val request: ConfirmRequest,
        val account: Account,
        val txHash: String?,
    )

    // The relayer sends the operation in a transaction of its own: a swap is reported as the swap in it, as
    // the wallet's own send reports it; a send as its last payload, what it is for.
    private suspend fun reportRelayed(relayed: Relayed) {
        val txHash = relayed.txHash ?: return
        val payloads = relayed.request.operation.payloads

        val swaps = payloads.mapNotNull { payload -> tracker?.pendingSwap(relayed.request, payload)?.let { payload to it } }
        // A send is reported as what it sends; any other operation with no swap in it, as before, not at all.
        val sends = if (relayed.request.type is ConfirmType.Transfer) {
            listOfNotNull(payloads.lastOrNull()?.let { it to null })
        } else {
            emptyList()
        }
        val reported = swaps.ifEmpty { sends }
        for ((payload, swap) in reported) {
            provider.reportPayloadSent(
                walletId = walletId,
                account = relayed.account,
                payload = payload,
                txHash = txHash,
                swap = swap,
            )
        }
    }

    private suspend fun sendRelayed(attempt: SendAttempt, methods: TxFeeState, method: TxFee): SendFailure? {
        val data = attempt.data
        val relayer = methods.relayer ?: return SendFailure(data, ConfirmationError.Unknown)
        if (methods.quote != null && methods.quotedId != method.id) {
            return SendFailure(data, ConfirmationError.Unknown)
        }
        if (isExpired(data.request)) {
            return SendFailure(data, ConfirmationError.Expired)
        }

        var relayed: Relayed? = null
        var target: RelayTarget? = null
        // What the relayer's send itself threw: a closed screen's cancellation replaces it on the way out.
        var sendFailure: Throwable? = null
        analytics.redOperation(
            operation = analytics.sendOperation,
            attemptSource = analytics.sendAttemptSource,
            otherMetadata = analytics.sendMetadata(data.account.asset, sentAmount(data, method), method.fee.asset),
        ) {
            try {
                requireSignatureEnabled(data.account)
                val cryptoWallet = try {
                    unlockCryptoWallet(walletId)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw PasscodeDismissed(e)
                }
                if (isExpired(data.request)) {
                    throw ConfirmationException(ConfirmationError.Expired)
                }
                withContext(Async.Io) {
                    val relaying = if (relaysAuthorized(data.request)) {
                        relayAuthorized(data.request, method, cryptoWallet)
                    } else {
                        RelayTarget(data.request, data.account, relayer, method)
                    }
                    target = relaying
                    tracker?.onRelayed(relaying.request, relaying.account, cryptoWallet)
                    // A send once started is let finish: what the relayer sent is reported though the screen closes.
                    val send = suspend {
                        withContext(NonCancellable) {
                            relaying.relayer.send(RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)), relaying.option)
                        }
                    }
                    val txHash = try {
                        if (tracker != null) {
                            tracker.trackRelayed(relaying.request, relaying.account, send)
                        } else {
                            send()
                        }
                    } catch (failure: Throwable) {
                        sendFailure = failure
                        throw failure
                    }
                    relayed = Relayed(relaying.request, relaying.account, txHash)
                }
                success()
            } catch (e: Throwable) {
                // The screen closed once the relayer had sent it: it is reported, and counted, as sent all the same.
                relayed?.let {
                    afterRelayed(it)
                    success()
                }
                // A send that may have gone out is handled before a closed screen's cancellation is rethrown.
                val outcome = sendFailure ?: e
                if (relayed == null && outcome.mayHaveRelayed) {
                    afterUnknownOutcome(target, outcome, data.account.chain)
                }
                currentCoroutineContext().ensureActive()
                if (e is QuoteChangedException) {
                    L.e(e)
                    return showChangedQuote(data, e)
                }
                report(e, e.toRelayedError())
                if (e is PasscodeDismissed) {
                    return null
                }
                L.e(e)
                return SendFailure(data, e.toRelayedError())
            }
        }

        relayed?.let { afterRelayed(it) }

        sendAction(OperationConfirmAction.Internal.Sent)
        delay(SUCCESS_HOLD_MS)
        finish()
        return null
    }

    private fun Throwable.toRelayedError(): ConfirmationError {
        return when {
            mayHaveRelayed -> ConfirmationError.OutcomeUnknown
            this is RelayerQuoteExpiredException -> ConfirmationError.Expired
            this is RelayerRejectedException -> ConfirmationError.RelayerRejected(reason.orEmpty())
            else -> toConfirmationError()
        }
    }

    // The send is given up for the new quote once that is ready to show; a re-prepare that fails is
    // the send's failure, as the user sees it.
    private suspend fun RedOperation.showChangedQuote(data: SendData, changed: QuoteChangedException): SendFailure? {
        val prepared = try {
            withContext(Async.Io) { prepareSend(changed.request) }
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            L.e(e)
            report(e)
            return SendFailure(data, e.toConfirmationError())
        }
        cancel(QUOTE_CHANGED_ERROR_TYPE, changed.message)
        sendAction(OperationConfirmAction.Internal.QuoteChanged(prepared))
        return null
    }

    private suspend fun sign(data: SendData): SignedData {
        if (data.operation.payloads.any { it.type.isTransaction() }) {
            requireSignatureEnabled(data.account)
        }
        val cryptoWallet = try {
            unlockCryptoWallet(walletId)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw PasscodeDismissed(e)
        }

        // The passcode prompt can outlast the deadline.
        if (isExpired(data.request)) {
            throw ConfirmationException(ConfirmationError.Expired)
        }

        return withContext(Async.Io) {
            val authorized = authorizer.authorize(data.request, cryptoWallet)

            // An equal operation, its service-minted id included, is the one on screen. A preview quoted for real
            // is a new one: what it costs and the nonce it starts at are its own. Any other only fills in what the
            // keys make, an encrypted comment the size of the stand-in it was priced as: it costs what the screen's
            // did, as the transaction confirm signed it, and only what it says is checked again.
            val signing = when {
                authorized.operation == data.operation -> data
                data.request.data.preview -> prepareSend(authorized, paidInCoin = true)
                else -> data.authorizedAs(authorized, validate(authorized, data.position.value))
            }

            // What the authorizer made is held to the intent as the screen's was: a danger the user
            // never read stops the send.
            if (signing !== data && signing.findings.addsDangerTo(data.findings)) {
                throw ConfirmationException(ConfirmationError.VerificationFailed)
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

    private suspend fun sign(data: SendData, cryptoWallet: MnemonicWallet): SignedOperation {
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

    private fun List<OperationFinding>.addsDangerTo(read: List<OperationFinding>): Boolean = any { finding ->
        finding.severity == FindingSeverity.Danger && read.none { it.error::class == finding.error::class }
    }

    private fun isExpired(quoted: ConfirmRequest): Boolean {
        val expiresAt = quoted.data.expiresAt
        return expiresAt != null && expiresAt - EXPIRY_LEAD_MS <= timeProvider.currentTimeMillis()
    }

    // Reports what the screen shows for [e]: a closed passcode prompt or a cancellation ends the
    // operation as cancelled, anything else as [error]. [otherMetadata] null keeps the attempt's.
    private fun RedOperation.report(
        e: Throwable,
        error: ConfirmationError = e.toConfirmationError(),
        otherMetadata: String? = null,
    ) {
        // A ConfirmationException's own text only repeats the error, and [namingCoin] wraps one in
        // another: tell what threw the innermost, if anything did.
        val cause = if (e is ConfirmationException) {
            generateSequence(e.cause) { it.cause }.firstOrNull { it !is ConfirmationException }
        } else {
            e
        }
        when {
            e is CancellationException -> failure(e)
            otherMetadata != null -> failure(error, otherMetadata, cause)
            else -> failure(error, cause = cause)
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

    // What a send reports it moves, in its asset's display unit; nothing where the feature does not say.
    private fun sentAmount(data: SendData, method: TxFee?): Double? {
        val sent = spending?.sent(data, method) ?: return null
        return data.account.asset.toDisplayUnit(sent).value.doubleValue(exactRequired = false)
    }

    private fun TxFee.shortfall(): ConfirmationError {
        if (isBattery) {
            return ConfirmationError.InsufficientBalance
        }
        return ConfirmationError.InsufficientNetworkFee(fee.asset.symbol)
    }

    private fun Throwable.namingCoin(position: AccountWithDetails): Throwable {
        val coin = position.data.chainAccount.asset
        if (position.asset.id != coin.id || toConfirmationError() != ConfirmationError.InsufficientBalance) {
            return this
        }
        val named = this as? ConfirmationException
        return ConfirmationException(
            error = ConfirmationError.InsufficientNetworkFee(coin.symbol),
            cause = this,
            feeUnavailable = named?.feeUnavailable == true,
            priced = named?.priced,
        )
    }

    // A document a relayer submits rides on the relayer's transaction and takes no nonce of the sender's.
    private fun Payload.Type.isTransaction(): Boolean {
        return this == Payload.Type.Main || this == Payload.Type.Prebuild || this == Payload.Type.Batch
    }

    private companion object {
        const val EXPIRY_LEAD_MS = 2_000L

        // `error_type` of a send given up because its quote changed: no ConfirmationError, the screen
        // shows the new quote instead.
        const val QUOTE_CHANGED_ERROR_TYPE = "quote_changed"
    }
}

/*
 *    Signed ──► signed?
 *      Sent ──► sent
 *   QuoteChanged ──► quoteChanges ──► quoted
 *
 *   quoteChanges ──┐
 *        Confirm ──┴──► quoteChanged
 *
 *                Retry ──┐
 *                        ├──► retries
 *             signed? ⋯──┘
 *
 *   retries [unsigned] ──► [fold] prepareAttempt ──► [latest] prepareState
 *
 *   prepareState [dataOrNull, okOrNull] ──┐
 *                          quoteChanges ──┴──► sendData? ──┬──► wallet?
 *                                                          ├──► account?
 *                                                          ├──► fee?
 *                                                          ├──► feeRate?
 *                                                          └──► findings
 *
 *   Confirm / retries [signed] ──┐
 *                   sendData? ⋯──┤
 *                     signed? ⋯──┼──► [first] sendState? ──► events
 *                        sent ⋯──┘            ╎
 *                                             ╎
 *        Signed / Sent / QuoteChanged ◄╌╌╌╌╌╌╌┘
 *
 *   sendState? [loading] ──┐
 *                          ├──► sendingState
 *                    sent ─┘
 *
 *         prepareState ──┐
 *            sendData? ──┤
 *           sendState? ──┼──► confirmationError?
 *                 sent ──┘
 */

private val ConfirmRequest.operation
    get() = (type as? ConfirmType.Operational)?.operation
        ?: throw IllegalArgumentException("${type::class.simpleName} carries no operation")

fun PayloadMeta.coinValue(): BigInteger {
    return when (this) {
        // A jetton deposit's value is the jetton's; the TON its transfer carries is chain-kit's to attach.
        is PayloadMeta.Ton.Main -> if (token == null) {
            value
        } else {
            BigInteger.ZERO
        }
        is PayloadMeta.Ton.Batch -> calls.fold(BigInteger.ZERO) { sum, call -> sum + call.coinValue() }
        is PayloadMeta.Evm.Main -> value
        is PayloadMeta.Tron.Main -> value
        is PayloadMeta.Bitcoin.Main -> value
        // With a mint, the value is the token's, not lamports.
        is PayloadMeta.Solana.Main -> if (token == null) {
            value
        } else {
            BigInteger.ZERO
        }
        else -> BigInteger.ZERO
    }
}
