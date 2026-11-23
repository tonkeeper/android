package com.tonapps.perps.screens.deposit

import androidx.lifecycle.SavedStateHandle
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.core.components.AmountToggle
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KState
import com.tonapps.mvi.graph.map
import com.tonapps.network.NetworkMonitor
import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.tokenInputDecimals
import com.tonapps.perps.funding.PerpsDepositException
import com.tonapps.perps.funding.PerpsDepositMinimum
import com.tonapps.perps.funding.PerpsDepositQuote
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsDepositSources
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.isRegionRestricted
import com.tonapps.perps.funding.retryable
import com.tonapps.perps.funding.toDepositQuoteError
import com.tonapps.perps.funding.toJava
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val KEY_DEPOSIT_STARTED_SENT = "deposit_started_sent"
private const val KEY_ADDRESS_BLOCKED_SENT = "address_blocked_sent"

private data class AmountInput(
    val text: String,
    val inFiat: Boolean,
    val toggle: AmountToggle? = null,
)

private data class SourceKey(
    val walletId: String?,
    val assetId: String,
)

private data class QuoteTarget(
    val walletId: String,
    val assetId: String,
    val units: BigInteger,
    val attempt: Int,
)

private class Refusal(
    val assetId: String,
    val units: BigInteger,
    val error: PerpsDepositQuoteError,
)

private val PerpsDepositSource.key: SourceKey
    get() = SourceKey(walletId = account.data.walletId, assetId = assetId)

internal val PerpsDepositSource.amountInputDecimals: Int
    get() = tokenInputDecimals(decimals)

data class PerpsDepositSummary(
    val amountUnits: BigInteger,
    val amount: BigDecimal,
    val fiat: BigDecimal?,
    val insufficient: Boolean,
    val minimum: PerpsDepositMinimum?,
    val senderBlocked: Boolean,
) {
    val addressBlocked: Boolean
        get() = senderBlocked && amountUnits.signum() > 0

    val belowMinimum: Boolean
        get() = minimum != null && amountUnits.signum() > 0 && amountUnits < minimum.units

    val canReview: Boolean
        get() = amountUnits.signum() > 0 && !insufficient && !belowMinimum && !senderBlocked
}

private class SourceMinimum(
    val key: SourceKey,
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
    val preview: StateFlow<PerpsDepositQuotePreview>
    val reviewEnabled: StateFlow<Boolean>
    val tradingKeyKept: StateFlow<Boolean>
    val anotherPerpsWalletAvailable: StateFlow<Boolean>
}

sealed interface PerpsDepositAction : MviAction {
    data class SetAmount(val value: String) : PerpsDepositAction
    data object ToggleInput : PerpsDepositAction
    data class SelectSource(val assetId: String) : PerpsDepositAction
    data object Max : PerpsDepositAction
    data object Reload : PerpsDepositAction
    data object Review : PerpsDepositAction
    data object Shown : PerpsDepositAction
}

sealed interface PerpsDepositEvent {
    data class Reviewed(val request: ConfirmRequest) : PerpsDepositEvent
    data object Closed : PerpsDepositEvent
    data object RegionRestricted : PerpsDepositEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsDepositFeature(
    private val sourceScreen: PerpsSourceScreen,
    private val service: PerpsDepositService,
    private val accountService: PerpsAccountService,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
) : GraphViewModel<PerpsDepositViewState, PerpsDepositAction>(), PerpsDepositViewState {

    private val relay = MviRelay<PerpsDepositEvent>()
    val events: Flow<PerpsDepositEvent> = relay.events

    private val maxState = MutableStateFlow(false)
    private var maxJob: Job? = null

    override val computingMax: StateFlow<Boolean> = maxState

    private val awaitingReview = MutableStateFlow(false)
    private var reviewedRequest: ConfirmRequest? = null
    private val requotes = MutableStateFlow(0)
    private val blockedSenders = MutableStateFlow<Set<SourceKey>>(emptySet())
    private var leftForRegion = false

    override val quoting: StateFlow<Boolean> = awaitingReview

    val walletId: StateFlow<String?> = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.takeIf { it.supportsPerps }?.id }
        .filterNotNull()
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    private val keyedWalletId = walletId
        .filterNotNull()
        .transformLatest<String, String?> { wallet ->
            emit(null)
            if (awaitTradingKey(wallet)) {
                emit(wallet)
            } else {
                relay.emit(PerpsDepositEvent.Closed)
            }
        }
        .cacheState(initialValue = null)

    override val tradingKeyKept = keyedWalletId
        .map { it != null }
        .cacheState(initialValue = false)

    override val anotherPerpsWalletAvailable = walletId
        .filterNotNull()
        .map(::hasAnotherPerpsWallet)
        .cacheState(initialValue = false)

    private val reloads = actions
        .on<PerpsDepositAction.Reload>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val loadContext = combine(
        walletId.filterNotNull(),
        reloads,
        mcAccountRepository.refreshTrigger,
    ) { wallet, attempt, _ -> wallet to attempt }

    private val sourcesLoaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val loadedListing = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.sources(wallet) } }
        .onEach { state ->
            if (state is KState.Data) {
                sourcesLoaded.tryEmit(Unit)
            }
        }
        .cacheStateWithLoading()

    private val loadedSources = loadedListing
        .map { state -> state.map { it?.items } }
        .cacheState(initialValue = KState.Loading())

    override val sources = combine(loadedSources, settingsRepository.hiddenBalancesFlow) { state, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        state.map { sources -> sources?.map { it.toUi(hiddenBalance) } }
    }
        .cacheState(initialValue = KState.Loading())

    private val loadedPaymentInFlight = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.pendingPayment(wallet) != null } }
        .cacheStateWithLoading()

    override val paymentInFlight = loadedPaymentInFlight
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
        keyedWalletId.filterNotNull(),
        selectedSource.distinctUntilChangedBy { it?.key },
        reloads,
    ) { wallet, source, _ -> wallet to source }
        .transformStateLatest { (wallet, source) ->
            source
                ?.takeIf { it.key.walletId == wallet }
                ?.let { SourceMinimum(it.key, probeMinimum(wallet, it)) }
        }
        .map { it.state }
        .cacheState(initialValue = null)

    private val input = MutableStateFlow(AmountInput(text = "", inFiat = false))

    override val amountInput = input.map { it.text }.cacheState(initialValue = "")

    override val inputInFiat = input.map { it.inFiat }.cacheState(initialValue = false)

    override val summary = combine(amountInput, inputInFiat, selectedSource, sourceMinimum, blockedSenders) { input, fiat, source, minimum, blocked ->
        summarize(
            input = input,
            inputInFiat = fiat,
            source = source,
            minimum = minimum?.takeIf { it.key == source?.key }?.minimum,
            senderBlocked = source?.key in blocked,
        )
    }
        .cacheState(initialValue = null)

    private val quoteTarget = combine(summary, selectedSource, keyedWalletId, paymentInFlight, maxState) { current, source, wallet, inFlight, computing ->
        if (current == null || source == null || wallet == null || inFlight || computing || !current.canReview) {
            null
        } else {
            QuoteTarget(walletId = wallet, assetId = source.assetId, units = current.amountUnits, attempt = 0)
        }
    }
        .combine(requotes) { target, attempt -> target?.copy(attempt = attempt) }
        .distinctUntilChanged()

    override val preview = quoteTarget
        .transformLatest { target ->
            if (target == null) {
                emit(PerpsDepositQuotePreview.None)
                return@transformLatest
            }
            emit(PerpsDepositQuotePreview.Loading)
            delay(QUOTE_DEBOUNCE_MS)
            val source = selectedSource.value?.takeIf { it.assetId == target.assetId }
            if (source == null) {
                emit(PerpsDepositQuotePreview.None)
                return@transformLatest
            }
            val quoted = try {
                withContext(Async.Io) { service.quote(target.walletId, source, target.units, sourceScreen) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                if (e.isRegionRestricted()) {
                    leaveForRegion()
                    emit(PerpsDepositQuotePreview.None)
                    return@transformLatest
                }
                val error = e.toDepositQuoteError()
                if (error == PerpsDepositQuoteError.AddressBlocked) {
                    blockedSenders.update { it + source.key }
                }
                emit(PerpsDepositQuotePreview.Failed(error))
                return@transformLatest
            }
            emit(quoted.toPreview())
        }
        .cacheState(initialValue = PerpsDepositQuotePreview.None)

    override val reviewEnabled = combine(selectedSource, summary, paymentInFlight, maxState, preview) { source, current, inFlight, computing, shown ->
        source != null &&
            current?.canReview == true &&
            !inFlight &&
            !computing &&
            (shown is PerpsDepositQuotePreview.Ready || shown == PerpsDepositQuotePreview.Loading || shown.retryableFailure)
    }
        .cacheState(initialValue = false)

    init {
        mainScope.launch {
            sendStarted(loadedListing.first { it is KState.Data }.state)
        }
        // MAX is to find the network fees in the cache rather than wait for them.
        bgScope.launch {
            service.loadNetworkFees()
        }
        stateScope.launch {
            if (loadedPaymentInFlight.first { it is KState.Data }.state == true) {
                PerpsAnalytics.depositAmountInFlight()
            }
        }
        stateScope.launch {
            combine(summary, selectedSource, paymentInFlight, ::refusalOf)
                .distinctUntilChangedBy { Triple(it?.assetId, it?.units, it?.error?.javaClass) }
                .collectLatest { refused ->
                    if (refused != null) {
                        delay(QUOTE_DEBOUNCE_MS)
                        PerpsAnalytics.depositAmountFailed(refused.error)
                    }
                }
        }
        stateScope.launch {
            walletId.collect {
                resetMax()
                awaitingReview.value = false
            }
        }
        stateScope.launch {
            networkMonitor.isOnlineFlow
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    if (preview.value is PerpsDepositQuotePreview.Failed) {
                        requotes.update { attempt -> attempt + 1 }
                    }
                }
        }
        stateScope.launch {
            sourcesLoaded.collect {
                if (preview.value.amountRefusal != null) {
                    requotes.update { attempt -> attempt + 1 }
                }
            }
        }
        stateScope.launch {
            preview.collect { shown ->
                if (shown is PerpsDepositQuotePreview.Failed && shown.error != PerpsDepositQuoteError.AddressBlocked) {
                    PerpsAnalytics.depositAmountFailed(shown.error)
                }
                if (awaitingReview.value) {
                    when (shown) {
                        is PerpsDepositQuotePreview.Ready -> {
                            awaitingReview.value = false
                            openConfirm(shown.request)
                        }
                        PerpsDepositQuotePreview.None,
                        is PerpsDepositQuotePreview.Failed -> awaitingReview.value = false
                        PerpsDepositQuotePreview.Loading -> Unit
                    }
                }
            }
        }
        stateScope.launch {
            actions.collect { action ->
                if (action !is PerpsDepositAction.Review && action !is PerpsDepositAction.Shown) {
                    resetMax()
                    awaitingReview.value = false
                }
                when (action) {
                    is PerpsDepositAction.SetAmount -> input.update { it.copy(text = action.value) }
                    PerpsDepositAction.ToggleInput -> input.update { it.toggled(selectedSource.value) }
                    PerpsDepositAction.Max -> applyMax()
                    PerpsDepositAction.Review -> review()
                    PerpsDepositAction.Shown -> requoteReviewedPreview()
                    is PerpsDepositAction.SelectSource,
                    PerpsDepositAction.Reload -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        sendStarted(null)
        super.onCleared()
    }

    private fun sendStarted(listing: PerpsDepositSources?) {
        if (savedStateHandle.get<Boolean>(KEY_DEPOSIT_STARTED_SENT) == true) {
            return
        }
        savedStateHandle[KEY_DEPOSIT_STARTED_SENT] = true
        PerpsAnalytics.depositStarted(listing)
    }

    private fun sendAddressBlockedInBackground() {
        if (savedStateHandle.get<Boolean>(KEY_ADDRESS_BLOCKED_SENT) == true) {
            return
        }
        savedStateHandle[KEY_ADDRESS_BLOCKED_SENT] = true
        PerpsAnalytics.depositAddressBlockedInBackground()
    }

    private fun refusalOf(current: PerpsDepositSummary?, source: PerpsDepositSource?, inFlight: Boolean): Refusal? {
        if (current == null || source == null || inFlight) {
            return null
        }
        val error = when {
            current.addressBlocked -> PerpsDepositQuoteError.AddressBlocked
            current.insufficient -> PerpsDepositQuoteError.InsufficientBalance
            current.belowMinimum -> PerpsDepositQuoteError.BelowMinimum(current.minimum?.usd)
            else -> return null
        }
        return Refusal(assetId = source.assetId, units = current.amountUnits, error = error)
    }

    private fun review() {
        if (awaitingReview.value) {
            return
        }
        val source = selectedSource.value
        val current = summary.value
        if (reviewEnabled.value && source != null && current != null) {
            val typedFiat = input.value.takeIf { it.inFiat }?.text?.toBigDecimalOrNull()
            PerpsAnalytics.depositContinueClicked(source, current.amount, typedFiat, loadedSources.value.state.orEmpty())
        }
        when (val shown = preview.value) {
            is PerpsDepositQuotePreview.Ready -> if (shown.request.isExpiring() || shown.request === reviewedRequest) {
                awaitingReview.value = true
                requotes.update { it + 1 }
            } else {
                openConfirm(shown.request)
            }
            PerpsDepositQuotePreview.Loading -> awaitingReview.value = true
            is PerpsDepositQuotePreview.Failed -> if (shown.error.retryable) {
                requotes.update { it + 1 }
            }
            PerpsDepositQuotePreview.None -> Unit
        }
    }

    private fun openConfirm(request: ConfirmRequest) {
        reviewedRequest = request
        relay.emit(PerpsDepositEvent.Reviewed(request))
    }

    private fun requoteReviewedPreview() {
        val shown = preview.value as? PerpsDepositQuotePreview.Ready ?: return
        if (shown.request === reviewedRequest) {
            requotes.update { it + 1 }
        }
    }

    private fun leaveForRegion() {
        if (leftForRegion) {
            return
        }
        leftForRegion = true
        relay.emit(PerpsDepositEvent.RegionRestricted)
    }

    // The deadline is on the server's clock, as the confirm screen reads it.
    private fun ConfirmRequest.isExpiring(): Boolean {
        val expiresAt = data.expiresAt ?: return false
        return expiresAt - QUOTE_EXPIRY_LEAD_MS <= timeProvider.currentTimeMillis()
    }

    private fun PerpsDepositQuote.toPreview(): PerpsDepositQuotePreview {
        val deposit = request.type as? ConfirmType.PerpetualDeposit
            ?: return PerpsDepositQuotePreview.Failed(PerpsDepositQuoteError.QuoteFailed)
        val providerFeeUsd = deposit.providerFees?.let { fees ->
            BigDecimal((fees.destinationGasUsd + fees.providerUsd).toStringExpanded())
        }
        val feeUsd = listOfNotNull(networkFeeUsd, providerFeeUsd)
            .takeIf { it.isNotEmpty() && !paysFeeInCharges }
            ?.reduce(BigDecimal::add)
        return PerpsDepositQuotePreview.Ready(
            request = request,
            youGetUsd = PerpsFundingAmount.toDecimal(deposit.quote.depositAmount.toJava(), PerpsFundingAmount.USDC_DECIMALS),
            feeUsd = feeUsd,
            activatesAccount = deposit.activatesAccount,
        )
    }

    private fun applyMax() {
        val wallet = keyedWalletId.value ?: return
        val source = selectedSource.value ?: return
        maxState.value = true
        // A stale or failed load is refreshed for the next MAX, which this one does not wait for.
        bgScope.launch {
            service.loadNetworkFees()
        }
        maxJob = stateScope.launch {
            val units = if (source.key in blockedSenders.value) {
                source.balanceUnits
            } else {
                withContext(Async.Io) { loadOrNull { service.maxAmount(wallet, source) } }
            }
            if (units != null) {
                val amount = PerpsFundingAmount.toDecimal(units, source.decimals)
                val price = source.price
                input.update { current ->
                    val text = if (current.inFiat && price != null) {
                        formatPlainDecimal(amount.multiply(price).setScale(FIAT_SCALE, RoundingMode.DOWN))
                    } else {
                        formatPlainDecimal(amount.setScale(source.amountInputDecimals, RoundingMode.DOWN))
                    }
                    current.copy(text = text, toggle = null)
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
        val toFiat = !inFiat
        val toggledBack = source?.let { toggle?.toggledBack(it.assetId, text, toFiat) }
        if (toggledBack != null) {
            return AmountInput(text = toggledBack.toggledText, inFiat = toFiat, toggle = toggledBack)
        }
        val price = source?.price
        val entered = text.toBigDecimalOrNull()
        val converted = when {
            price == null || price.signum() == 0 || entered == null || entered.signum() <= 0 -> ""
            inFiat -> formatPlainDecimal(entered.divide(price, source.amountInputDecimals, RoundingMode.DOWN))
            else -> formatPlainDecimal(entered.multiply(price).setScale(FIAT_SCALE, RoundingMode.DOWN))
        }
        val toggle = source?.let {
            AmountToggle(assetId = it.assetId, sourceText = text, toggledText = converted, toFiat = toFiat)
        }
        return AmountInput(text = converted, inFiat = toFiat, toggle = toggle)
    }

    private fun summarize(
        input: String,
        inputInFiat: Boolean,
        source: PerpsDepositSource?,
        minimum: PerpsDepositMinimum?,
        senderBlocked: Boolean,
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
            senderBlocked = senderBlocked,
        )
    }

    private suspend fun probeMinimum(wallet: String, source: PerpsDepositSource): PerpsDepositMinimum? {
        return try {
            service.minimum(wallet, source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PerpsDepositException.AddressBlocked) {
            L.e(e)
            if (source.key !in blockedSenders.value) {
                blockedSenders.update { it + source.key }
                sendAddressBlockedInBackground()
            }
            null
        } catch (e: Throwable) {
            L.e(e)
            if (e.isRegionRestricted()) {
                leaveForRegion()
            }
            null
        }
    }

    private suspend fun hasAnotherPerpsWallet(currentWalletId: String): Boolean {
        return try {
            unifiedAccountRepository.getTonWallets().any { it.supportsPerps && it.id != currentWalletId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    private suspend fun awaitTradingKey(wallet: String): Boolean {
        return try {
            accountService.keepTradingKey(wallet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            false
        }
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
        const val QUOTE_DEBOUNCE_MS = 500L
        const val QUOTE_EXPIRY_LEAD_MS = 45_000L
    }
}
