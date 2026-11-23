package com.tonapps.deposit.multicoin.screens.confirm

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.isShortage
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// How long the swap screen has to quote the amount a max sale is fitted to before the attempt reads as short.
private const val REFIT_GRACE_MS = 5_000L

sealed interface HeadlessSwapPhase {
    val error: ConfirmationError? get() = null

    data object Working : HeadlessSwapPhase
    data class Impact(val impact: PriceImpact) : HeadlessSwapPhase
    data object Done : HeadlessSwapPhase
    data class Retryable(override val error: ConfirmationError?) : HeadlessSwapPhase
    data class Blocked(override val error: ConfirmationError) : HeadlessSwapPhase
    data class Ended(override val error: ConfirmationError?) : HeadlessSwapPhase
    data object Review : HeadlessSwapPhase
    data object Requoted : HeadlessSwapPhase

    /** The max asked for is being fitted to its fee: the swap screen quotes [amount], and this attempt gives way to it. */
    data class Refit(val amount: BigInteger) : HeadlessSwapPhase
    data class PriceChanged(val change: SwapPriceChange) : HeadlessSwapPhase
}

/** What a max sale that cannot pay its fee next to what it sells is to do instead. */
sealed interface SwapMaxFit {

    /** Sell [amount]: what the balance leaves past the fee. */
    data class Sell(val amount: BigInteger) : SwapMaxFit

    /** The fee leaves nothing to sell: the sale is short of it. */
    data object Short : SwapMaxFit
}

data class HeadlessSwapUpdate(val seq: Int, val phase: HeadlessSwapPhase)

/**
 * The confirm feature a headless swap runs on: the transaction swap confirm for a `/v2` route, the
 * operation swap confirm for a `/v3` one. Either shows what it prepared as a [PendingTransaction].
 */
interface HeadlessSwapSource {

    /** The swap as prepared, to show and to send; null while none is. */
    val prepared: Flow<PendingTransaction?>
    val pendingLoader: StateFlow<Boolean>
    val feeQuoting: StateFlow<Boolean>
    val confirmationError: StateFlow<ConfirmationError?>
    val pickedFeeId: MutableStateFlow<String?>

    /**
     * What a max sale is to do when the fee, paid in what it sells, leaves no room for what was asked:
     * the swap screen quotes the amount that fits. Null while what was asked can be paid.
     */
    val maxFit: StateFlow<SwapMaxFit?>

    /** Whether a send is under way or done: nothing is prepared anew under it. */
    val txCommitted: StateFlow<Boolean>
    val sendingState: StateFlow<SendingState>

    val shortageHasTx: Boolean
        get() = false

    /** Whether something is signed, which a retry sends again. */
    val hasSendData: Boolean

    /** What is prepared, while nothing of it is signed. */
    val unsignedSend: PendingTransaction?

    /** The screen is the swap screen's: the confirm's own quote timer stays off. */
    fun runHeadless()

    /** Sends what is signed again, or prepares anew; the job, when there is one, is the send. */
    fun retry(): Job?

    fun sendTransaction(pendingTx: PendingTransaction): Job?

    fun selectedFee(tx: PendingTransaction): TxFee?

    fun selectFee(fee: TxFee)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HeadlessSwapConfirm internal constructor(
    val feature: HeadlessSwapSource,
    private val scope: CoroutineScope,
    review: Boolean,
    feeId: String?,
) {
    init {
        feature.runHeadless()
        feeId?.let { feature.pickedFeeId.value = it }
    }

    private val _updates = MutableStateFlow(
        HeadlessSwapUpdate(0, HeadlessSwapPhase.Review.takeIf { review } ?: HeadlessSwapPhase.Working),
    )
    val updates: StateFlow<HeadlessSwapUpdate> = _updates.asStateFlow()

    val prepared: StateFlow<PendingTransaction?> = feature.prepared
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** What the swap screen is to sell instead of the max it asked for; see [HeadlessSwapSource.maxFit]. */
    val refitAmount: StateFlow<BigInteger?> = feature.maxFit
        .map { (it as? SwapMaxFit.Sell)?.amount }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val amountBlocked = MutableStateFlow(false)

    private val sendShortage = MutableStateFlow(false)

    // The swap screen is given a moment to quote the amount that fits, and replaces this attempt with
    // that quote's. One it has not replaced by then (the quote failed under an open fee sheet, or the
    // amount is no longer the max the fit was for) is short of its fee, not preparing.
    private val fitting: StateFlow<Boolean> = refitAmount
        .transformLatest { amount ->
            emit(amount != null)
            if (amount != null) {
                delay(REFIT_GRACE_MS)
                emit(false)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val busy = combine(feature.pendingLoader, feature.feeQuoting, fitting) { loading, quoting, fitting ->
        loading || quoting || fitting
    }

    val canConfirm: StateFlow<Boolean> = combine(
        prepared,
        busy,
        feature.confirmationError,
        feature.pickedFeeId,
        feature.maxFit,
    ) { tx, busy, error, _, fit ->
        tx != null && !busy && error == null && fit == null && !needsReview(tx, error)
    }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val feePlan: StateFlow<SwapFeePlan> = combine(
        prepared,
        busy,
        combine(feature.confirmationError, feature.txCommitted, ::Pair),
        feature.pickedFeeId,
        combine(amountBlocked, sendShortage, feature.maxFit) { blocked, shortage, fit -> blocked to (shortage || fit != null) },
    ) { tx, busy, (error, committed), picked, (blocked, shortage) ->
        val receivedAt = (tx?.request?.type as? ConfirmType.Swap)?.quote?.receivedAt
        val prepareFailed = error != null && !committed && !error.isShortage
        when {
            busy -> SwapFeePlan.Preparing
            tx == null && error != null -> SwapFeePlan.Failed
            tx == null -> SwapFeePlan.Preparing
            prepareFailed -> SwapFeePlan.Failed
            else -> SwapFeePlan.Ready(
                feeSummary(
                    summary = summarize(tx, picked, blocked),
                    noFunds = SwapFeeSummary.NoFunds((tx.fee.energy ?: tx.account).asset.symbol),
                    sendShortage = shortage,
                    balanceShort = error.isShortage && !blocked,
                ),
                receivedAt,
            )
        }
    }
        .stateIn(scope, SharingStarted.Eagerly, SwapFeePlan.Preparing)

    private val phase: HeadlessSwapPhase
        get() = _updates.value.phase

    private var impactTx: PendingTransaction? = null
    private var priceChangedTx: PendingTransaction? = null
    private var quoteAt = 0L

    init {
        scope.launch {
            feature.pickedFeeId.drop(1).collect { sendShortage.value = false }
        }
    }

    fun setAmountBlocked(blocked: Boolean) {
        amountBlocked.value = blocked
    }

    fun retryFees() {
        sendShortage.value = false
        feature.retry()
    }

    fun start() {
        if (phase == HeadlessSwapPhase.Working) {
            scope.launch { proceedPrepared() }
        }
    }

    fun confirmReview() {
        if (phase != HeadlessSwapPhase.Review) {
            return
        }
        setPhase(HeadlessSwapPhase.Working)
        scope.launch { proceedPrepared() }
    }

    fun retry() {
        if (phase !is HeadlessSwapPhase.Retryable) {
            return
        }
        if (feature.unsignedSend?.isRouteStale(quotedAt = SystemClock.elapsedRealtime()) == true) {
            setPhase(HeadlessSwapPhase.Requoted)
            return
        }
        sendShortage.value = false
        setPhase(HeadlessSwapPhase.Working)
        scope.launch {
            track(feature.retry())
        }
    }

    fun acceptImpact() {
        val pinned = impactTx
        if (phase !is HeadlessSwapPhase.Impact || pinned == null) {
            return
        }
        if (pinned.isRouteStale(quotedAt = quoteAt)) {
            setPhase(HeadlessSwapPhase.Requoted)
            return
        }
        setPhase(HeadlessSwapPhase.Working)
        scope.launch {
            proceed(pinned, error = null, checkImpact = false, checkPrice = false)
        }
    }

    fun declineImpact() {
        if (phase is HeadlessSwapPhase.Impact) {
            setPhase(HeadlessSwapPhase.Ended(null))
        }
    }

    fun acceptPriceChange() {
        val pinned = priceChangedTx
        if (phase !is HeadlessSwapPhase.PriceChanged || pinned == null) {
            return
        }
        if (pinned.isRouteStale(quotedAt = quoteAt)) {
            setPhase(HeadlessSwapPhase.Requoted)
            return
        }
        setPhase(HeadlessSwapPhase.Working)
        scope.launch {
            proceed(pinned, error = null, checkImpact = true, checkPrice = false)
        }
    }

    fun declinePriceChange() {
        if (phase is HeadlessSwapPhase.PriceChanged) {
            setPhase(HeadlessSwapPhase.Ended(null))
        }
    }

    private fun setPhase(value: HeadlessSwapPhase) {
        _updates.value = HeadlessSwapUpdate(_updates.value.seq + 1, value)
    }

    private suspend fun proceedPrepared() {
        val (tx, error) = combine(prepared, feature.confirmationError, ::Pair).first { (tx, error) ->
            tx != null || error != null && !(error.isShortage && feature.shortageHasTx)
        }
        proceed(tx, error, checkImpact = true, checkPrice = true)
    }

    private suspend fun proceed(
        tx: PendingTransaction?,
        error: ConfirmationError?,
        checkImpact: Boolean,
        checkPrice: Boolean,
    ) {
        val impact = tx?.priceImpact()?.takeIf { it.severity != PriceImpactSeverity.Normal }
        val trade = tx?.request?.type as? ConfirmType.Swap
        val snapshot = trade?.quote
        val quote = tx?.quote
        val change = if (checkPrice && trade != null && snapshot != null && quote != null) {
            priceChange(snapshot, trade.sourceAmount, quote)
        } else {
            null
        }
        val fit = feature.maxFit.value as? SwapMaxFit.Sell
        when {
            // A max being fitted to its fee is not a shortage: the attempt gives way to the quote of
            // what fits, which the swap screen could not start under an attempt that is being sent.
            // A prepare that failed for another reason ends the attempt with that reason instead.
            fit != null && (error == null || error.isShortage) -> setPhase(HeadlessSwapPhase.Refit(fit.amount))
            tx != null && needsReview(tx, error) -> setPhase(HeadlessSwapPhase.Review)
            tx == null || error != null -> setPhase(HeadlessSwapPhase.Ended(error ?: ConfirmationError.Unknown))
            tx.isRouteStale(quotedAt = quote?.receivedAt ?: SystemClock.elapsedRealtime()) ->
                setPhase(HeadlessSwapPhase.Requoted)
            change != null -> {
                priceChangedTx = tx
                quoteAt = SystemClock.elapsedRealtime()
                setPhase(HeadlessSwapPhase.PriceChanged(change))
            }
            checkImpact && impact != null -> {
                impactTx = tx
                quoteAt = snapshot?.receivedAt?.takeIf { quote?.routeId == snapshot.routeId } ?: SystemClock.elapsedRealtime()
                setPhase(HeadlessSwapPhase.Impact(impact))
            }
            else -> track(feature.sendTransaction(tx))
        }
    }

    // A max its fee leaves no room for is a shortage to review, unless the attempt failed for a reason
    // of its own: that reason is what the user is told.
    private fun needsReview(tx: PendingTransaction, error: ConfirmationError?): Boolean {
        return error.isShortage ||
            tx.isFeeUnavailable ||
            feature.maxFit.value != null && error == null ||
            feature.selectedFee(tx)?.let { !TxFeeLogic.isSufficient(it) } == true
    }

    private suspend fun track(job: Job?) {
        val sent = job != null && merge(
            feature.sendingState.filter { it == SendingState.Done }.map { true },
            flow {
                job.join()
                emit(false)
            },
        ).first()
        if (sent) {
            setPhase(HeadlessSwapPhase.Done)
            return
        }
        val error = feature.confirmationError.value
        setPhase(
            when {
                // It may already be out: nothing is sent again until the history says.
                error == ConfirmationError.OutcomeUnknown ||
                    error == ConfirmationError.PartiallySent ||
                    error == ConfirmationError.NonceTaken -> HeadlessSwapPhase.Blocked(error)
                feature.hasSendData -> HeadlessSwapPhase.Retryable(error)
                error.isShortage -> {
                    sendShortage.value = true
                    HeadlessSwapPhase.Review
                }
                error == ConfirmationError.Expired -> HeadlessSwapPhase.Requoted
                else -> HeadlessSwapPhase.Ended(error)
            }
        )
    }
}

// A route quoted on `/v3` is prepared into an operation and sent as one, unless a Ledger signs it, as on the confirm screen.
@Composable
fun rememberHeadlessSwapConfirm(request: ConfirmRequest, review: Boolean, feeId: String?): HeadlessSwapConfirm? {
    val signsOnHardware = if (request.preparesSwapOperation()) {
        rememberIsHardwareWallet(request.data.walletId) ?: return null
    } else {
        false
    }
    val feature: HeadlessSwapSource = if (!signsOnHardware && request.preparesSwapOperation()) {
        // The fee kept from an earlier attempt goes in with the request: the first prepare prices by it.
        koinViewModel<SwapOperationConfirmFeature> {
            if (feeId != null) {
                parametersOf(request, feeId)
            } else {
                parametersOf(request)
            }
        }
    } else {
        koinViewModel<SwapConfirmFeature> { parametersOf(request) }
    }
    val scope = rememberCoroutineScope()
    return remember(feature, scope) { HeadlessSwapConfirm(feature, scope, review, feeId) }
}

internal fun PendingTransaction.isRouteStale(quotedAt: Long): Boolean {
    val now = SystemClock.elapsedRealtime()
    return isRouteStale(
        elapsedNowMillis = now,
        receivedAt = quote?.receivedAt,
        lifetimeMillis = quote?.lifetimeMillis,
        quoteAgeMillis = now - quotedAt,
    )
}

@Composable
fun HeadlessSwapImpactDialog(confirm: HeadlessSwapConfirm) {
    val update by confirm.updates.collectAsState()
    val phase = update.phase as? HeadlessSwapPhase.Impact ?: return
    ValueDifferenceDialog(
        severity = phase.impact.severity,
        onConfirm = { confirm.acceptImpact() },
        onClose = { confirm.declineImpact() },
    )
}

@Composable
fun HeadlessSwapPriceChangedDialog(confirm: HeadlessSwapConfirm) {
    val update by confirm.updates.collectAsState()
    val tx by confirm.prepared.collectAsState()
    val phase = update.phase as? HeadlessSwapPhase.PriceChanged ?: return
    val prepared = tx ?: return
    val change = phase.change
    val asset = when (change.side) {
        SwapPriceChange.Side.Receive -> prepared.destination?.asset ?: return
        SwapPriceChange.Side.Send -> prepared.account.asset
    }.value
    val (current, previous) = remember(change, asset) {
        val current = asset.decimals.toDisplayUnit(change.current)
        val previous = asset.decimals.toDisplayUnit(change.previous)
        val shortCurrent = Formatter.formatShort(value = current, asset = asset)
        val shortPrevious = Formatter.formatShort(value = previous, asset = asset)
        if (shortCurrent != shortPrevious) {
            shortCurrent to shortPrevious
        } else {
            Formatter.formatAsset(value = current, asset = asset) to Formatter.formatAsset(value = previous, asset = asset)
        }
    }
    PriceChangedDialog(
        text = when (change.side) {
            SwapPriceChange.Side.Receive -> stringResource(Localization.swap_price_changed_receive, current, previous)
            SwapPriceChange.Side.Send -> stringResource(Localization.swap_price_changed_send, current, previous)
        },
        onConfirm = { confirm.acceptPriceChange() },
        onClose = { confirm.declinePriceChange() },
    )
}
