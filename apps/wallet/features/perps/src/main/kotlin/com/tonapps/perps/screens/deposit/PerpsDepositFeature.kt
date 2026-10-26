package com.tonapps.perps.screens.deposit

import androidx.lifecycle.SavedStateHandle
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KState
import com.tonapps.mvi.graph.map
import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.funding.PerpsDepositMinimum
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.toDepositQuoteError
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val KEY_DEPOSIT_STARTED_SENT = "deposit_started_sent"

private data class AmountInput(
    val text: String,
    val inFiat: Boolean,
)

private data class SourceKey(
    val walletId: String?,
    val assetId: String,
)

private val PerpsDepositSource.key: SourceKey
    get() = SourceKey(walletId = account.data.walletId, assetId = assetId)

data class PerpsDepositSummary(
    val amountUnits: BigInteger,
    val amount: BigDecimal,
    val fiat: BigDecimal?,
    val insufficient: Boolean,
    val minimum: PerpsDepositMinimum?,
) {
    val belowMinimum: Boolean
        get() = minimum != null && amountUnits.signum() > 0 && amountUnits < minimum.units

    val canReview: Boolean
        get() = amountUnits.signum() > 0 && !insufficient && !belowMinimum
}

private class SourceMinimum(
    val assetId: String,
    val minimum: PerpsDepositMinimum?,
)

interface PerpsDepositViewState : MviViewState {
    val sources: StateFlow<KState<List<PerpsDepositSourceUi>?>>
    val selected: StateFlow<PerpsDepositSourceUi?>
    val amountInput: StateFlow<String>
    val inputInFiat: StateFlow<Boolean>
    val summary: StateFlow<PerpsDepositSummary?>
    val paymentInFlight: StateFlow<Boolean>
    val computingMax: StateFlow<Boolean>
    val quoting: StateFlow<Boolean>
    val quoteError: StateFlow<PerpsDepositQuoteError?>
}

sealed interface PerpsDepositAction : MviAction {
    data class SetAmount(val value: String) : PerpsDepositAction
    data object ToggleInput : PerpsDepositAction
    data class SelectSource(val assetId: String) : PerpsDepositAction
    data object Max : PerpsDepositAction
    data object Reload : PerpsDepositAction
    data object Review : PerpsDepositAction
}

sealed interface PerpsDepositEvent {
    data class Reviewed(val request: ConfirmRequest) : PerpsDepositEvent
}

class PerpsDepositFeature(
    private val service: PerpsDepositService,
    unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : GraphViewModel<PerpsDepositViewState, PerpsDepositAction>(), PerpsDepositViewState {

    private val relay = MviRelay<PerpsDepositEvent>()
    val events: Flow<PerpsDepositEvent> = relay.events

    private val maxState = MutableStateFlow(false)
    private var maxJob: Job? = null

    override val computingMax: StateFlow<Boolean> = maxState

    private val quotingState = MutableStateFlow(false)
    private val quoteFailure = MutableStateFlow<PerpsDepositQuoteError?>(null)
    private var quoteJob: Job? = null

    override val quoting: StateFlow<Boolean> = quotingState
    override val quoteError: StateFlow<PerpsDepositQuoteError?> = quoteFailure

    private val walletId = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
        .filterNotNull()
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    private val reloads = actions
        .on<PerpsDepositAction.Reload>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val loadContext = combine(
        walletId.filterNotNull(),
        reloads,
        mcAccountRepository.refreshTrigger,
    ) { wallet, attempt, _ -> wallet to attempt }

    private val loadedSources = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.sources(wallet) } }
        .cacheStateWithLoading()

    override val sources = combine(loadedSources, settingsRepository.hiddenBalancesFlow) { state, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        state.map { sources -> sources?.map { it.toUi(hiddenBalance) } }
    }
        .cacheState(initialValue = KState.Loading())

    override val paymentInFlight = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.pendingPayment(wallet) != null } }
        .map { it.state == true }
        .cacheState(initialValue = false)

    private val pickedSource = actions
        .on<PerpsDepositAction.SelectSource>()
        .map { SourceKey(walletId = walletId.value, assetId = it.assetId) }
        .cacheState(initialValue = null)

    private val selectedSource = combine(loadedSources, pickedSource, ::Pair)
        .runningFold<_, PerpsDepositSource?>(null) { shown, (state, picked) ->
            val loaded = state.state.orEmpty()
            loaded.firstOrNull { it.key == picked }
                ?: loaded.firstOrNull { it.key == shown?.key && it.balanceUnits.signum() > 0 }
                ?: loaded.maxWithOrNull(compareBy<PerpsDepositSource> { it.fiatValue }.thenBy { it.balanceUnits.signum() })
        }
        .cacheState(initialValue = null)

    override val selected = combine(selectedSource, settingsRepository.hiddenBalancesFlow) { source, _ ->
        source?.toUi(settingsRepository.hiddenBalances)
    }
        .cacheState(initialValue = null)

    private val sourceMinimum = combine(
        walletId.filterNotNull(),
        selectedSource.distinctUntilChangedBy { it?.assetId },
        reloads,
    ) { wallet, source, _ -> wallet to source }
        .transformStateLatest { (wallet, source) ->
            source?.let { SourceMinimum(it.assetId, loadOrNull { service.minimum(wallet, it) }) }
        }
        .map { it.state }
        .cacheState(initialValue = null)

    private val input = MutableStateFlow(AmountInput(text = "", inFiat = false))

    override val amountInput = input.map { it.text }.cacheState(initialValue = "")

    override val inputInFiat = input.map { it.inFiat }.cacheState(initialValue = false)

    override val summary = combine(amountInput, inputInFiat, selectedSource, sourceMinimum) { input, fiat, source, minimum ->
        summarize(input, fiat, source, minimum?.takeIf { it.assetId == source?.assetId }?.minimum)
    }
        .cacheState(initialValue = null)

    init {
        if (savedStateHandle.get<Boolean>(KEY_DEPOSIT_STARTED_SENT) != true) {
            savedStateHandle[KEY_DEPOSIT_STARTED_SENT] = true
            PerpsAnalytics.depositStarted()
        }
        stateScope.launch {
            walletId.collect {
                resetMax()
                resetQuote()
            }
        }
        stateScope.launch {
            actions.collect { action ->
                if (action !is PerpsDepositAction.Review) {
                    resetMax()
                    resetQuote()
                }
                when (action) {
                    is PerpsDepositAction.SetAmount -> input.update { it.copy(text = action.value) }
                    PerpsDepositAction.ToggleInput -> input.update { it.toggled(selectedSource.value) }
                    PerpsDepositAction.Max -> applyMax()
                    PerpsDepositAction.Review -> review()
                    is PerpsDepositAction.SelectSource,
                    PerpsDepositAction.Reload -> Unit
                }
            }
        }
    }

    // The confirm screen signs the quote's operation as it is, so the quote is taken before it opens;
    // an edit of the amount or the source while it loads drops it.
    private fun review() {
        val wallet = walletId.value ?: return
        val source = selectedSource.value ?: return
        val entered = input.value
        val current = summarize(entered.text, entered.inFiat, source, minimumOf(source)) ?: return
        if (!current.canReview || paymentInFlight.value || maxState.value || quotingState.value) {
            return
        }
        quotingState.value = true
        quoteFailure.value = null
        quoteJob = stateScope.launch {
            val request = try {
                withContext(Async.Io) {
                    service.confirmRequest(wallet, source, current.amountUnits)
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                quotingState.value = false
                return@launch
            } catch (e: Throwable) {
                L.e(e)
                quoteFailure.value = e.toDepositQuoteError()
                quotingState.value = false
                return@launch
            }
            quotingState.value = false
            relay.emit(PerpsDepositEvent.Reviewed(request))
        }
    }

    private fun resetQuote() {
        quoteJob?.cancel()
        quoteJob = null
        quotingState.value = false
        quoteFailure.value = null
    }

    private fun applyMax() {
        val wallet = walletId.value ?: return
        val source = selectedSource.value ?: return
        maxState.value = true
        maxJob = stateScope.launch {
            val units = withContext(Async.Io) { loadOrNull { service.maxAmount(wallet, source) } }
            if (units != null) {
                val amount = PerpsFundingAmount.toDecimal(units, source.decimals)
                val price = source.price
                input.update { current ->
                    val text = if (current.inFiat && price != null) {
                        formatPlainDecimal(amount.multiply(price).setScale(FIAT_SCALE, RoundingMode.DOWN))
                    } else {
                        formatPlainDecimal(amount)
                    }
                    current.copy(text = text)
                }
            }
            maxState.value = false
        }
    }

    private fun resetMax() {
        maxJob?.cancel()
        maxJob = null
        maxState.value = false
    }

    private fun AmountInput.toggled(source: PerpsDepositSource?): AmountInput {
        val price = source?.price
        val entered = text.toBigDecimalOrNull()
        val converted = when {
            price == null || price.signum() == 0 || entered == null || entered.signum() <= 0 -> ""
            inFiat -> formatPlainDecimal(entered.divide(price, source.decimals, RoundingMode.DOWN))
            else -> formatPlainDecimal(entered.multiply(price).setScale(FIAT_SCALE, RoundingMode.DOWN))
        }
        return AmountInput(text = converted, inFiat = !inFiat)
    }

    private fun minimumOf(source: PerpsDepositSource): PerpsDepositMinimum? {
        return sourceMinimum.value?.takeIf { it.assetId == source.assetId }?.minimum
    }

    private fun summarize(
        input: String,
        inputInFiat: Boolean,
        source: PerpsDepositSource?,
        minimum: PerpsDepositMinimum?,
    ): PerpsDepositSummary? {
        source ?: return null
        val entered = input.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val price = source.price
        val amount = when {
            !inputInFiat -> entered
            price == null || price.signum() == 0 -> BigDecimal.ZERO
            else -> entered.divide(price, source.decimals, RoundingMode.DOWN)
        }
        val units = amount.movePointRight(source.decimals).setScale(0, RoundingMode.DOWN).toBigIntegerExact()
        val exact = PerpsFundingAmount.toDecimal(units, source.decimals)
        return PerpsDepositSummary(
            amountUnits = units,
            amount = exact,
            fiat = price?.multiply(exact),
            insufficient = units > source.balanceUnits,
            minimum = minimum,
        )
    }

    private suspend fun <T> loadOrNull(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            null
        }
    }

    private companion object {
        const val FIAT_SCALE = 2
    }
}
