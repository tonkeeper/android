package com.tonapps.deposit.multicoin.screens.buy

import androidx.lifecycle.SavedStateHandle
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.BuyCryptoAnalytics
import com.tonapps.deposit.multicoin.analytics.BuyCryptoTrack
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.domain.MoonPayAccess
import com.tonapps.deposit.multicoin.domain.MoonPayRamp
import com.tonapps.deposit.multicoin.domain.MoonPaySignIn
import com.tonapps.deposit.multicoin.domain.RampAmountRange
import com.tonapps.deposit.multicoin.domain.RampCheckout
import com.tonapps.deposit.multicoin.domain.RampLimitViolation
import com.tonapps.deposit.multicoin.domain.RampOffer
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampPayments
import com.tonapps.deposit.multicoin.domain.RampQuoteRequest
import com.tonapps.deposit.multicoin.domain.RampSource
import com.tonapps.deposit.multicoin.domain.RampTicket
import com.tonapps.deposit.multicoin.domain.merchantOptions
import com.tonapps.deposit.multicoin.domain.merchantSlug
import com.tonapps.deposit.multicoin.domain.moonPayMethodNames
import com.tonapps.deposit.multicoin.domain.offersMoonPay
import com.tonapps.deposit.multicoin.domain.preferred
import com.tonapps.deposit.multicoin.domain.soldBy
import com.tonapps.deposit.multicoin.fiatOrUsd
import com.tonapps.deposit.multicoin.fiatScale
import com.tonapps.deposit.multicoin.formatRate
import com.tonapps.deposit.multicoin.moonpay.MoonPayDisclosureText
import com.tonapps.deposit.multicoin.moonpay.MoonPayPaymentDisclosures
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentFailure
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentState
import com.tonapps.deposit.multicoin.moonpay.domain.isFinal
import com.tonapps.deposit.multicoin.quickAmounts
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.AmountState
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.ButtonPhase
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.ButtonState
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Command
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.LoadStatus
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Phase
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.QuoteKey
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Session
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Setup
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.SetupRequest
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.ShownMethod
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoReducer.Step
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentFee
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayQuoteSummary
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentRetry
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.network.NetworkMonitor
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.countdown
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.ExchangeMerchantSlug
import java.math.BigDecimal
import java.math.MathContext
import java.net.SocketException
import java.net.UnknownHostException
import java.math.RoundingMode
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class BuyCryptoData(
    val walletId: String,
    val assetId: String?,
    val preferredCurrency: String?,
    val analyticsFrom: DepositFlowFrom,
    val utm: UtmParameters,
)

// [card] is a saved card's icon, shown instead of [iconUrl].
data class BuyCryptoPayment(
    val title: String,
    val subtitle: String?,
    val iconUrl: String?,
    val badge: BuyCryptoBadge?,
    val card: BuyCryptoCard?,
)

// The provider on a payment method: MoonPay's own, as on iOS, or the merchant that quoted.
sealed interface BuyCryptoBadge {
    data object MoonPay : BuyCryptoBadge
    data class Image(val url: String) : BuyCryptoBadge
}

sealed interface BuyCryptoPaymentRow {
    data class Method(val payment: BuyCryptoPayment, val loading: Boolean) : BuyCryptoPaymentRow
    data object Failed : BuyCryptoPaymentRow
}

data class BuyCryptoQuickAmount(
    val title: String,
    val amount: String,
)

// [max] is the amount MAX puts in the field, when the merchants say how much they take.
data class BuyCryptoQuickAmounts(
    val items: List<BuyCryptoQuickAmount>,
    val max: String?,
    val loading: Boolean,
)

// [revision] grows with every input the view model takes, a rejected one included: the field follows the
// text only when it changes, so a frame between a keystroke and its answer never rewinds the field.
data class BuyCryptoAmount(
    val text: String,
    val revision: Int,
)

data class BuyCryptoSetup(
    val fiat: String,
    val assetId: String?,
    val method: String?,
    val canPickMethod: Boolean,
)

// What the method screen lists: the methods of the setup on «Buy crypto», [selected] among them.
data class BuyCryptoOfferState(
    val assetId: String?,
    val fiat: String?,
    val loading: Boolean,
    val failed: Boolean,
    val methods: List<RampPaymentMethod>,
    val selected: String?,
)

data class BuyCryptoProviderRow(
    val id: ExchangeMerchantSlug,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val selected: Boolean,
) {

    val isMoonPay: Boolean
        get() = id == ExchangeMerchantSlug.moonpay
}

data class BuyCryptoProviderLine(val name: String?, val switchable: Boolean, val failed: Boolean)

// «Buy crypto» decides who sells and with what; the method screen shows it and asks it for changes.
interface BuyCryptoOffer {
    val offer: StateFlow<BuyCryptoOfferState>
    val providers: StateFlow<List<BuyCryptoProviderRow>>
    val providerLine: StateFlow<BuyCryptoProviderLine?>

    // Grows with each customer's retry of the providers that failed.
    val providerFailures: StateFlow<Int>
    fun sendAction(action: BuyCryptoAction)
}

data class BuyCryptoAmountField(
    val fiat: String,
    val editable: Boolean,
    val outOfLimits: Boolean,
    val grouping: String,
)

data class BuyCryptoReceive(
    val amount: String?,
    val muted: Boolean,
    val asset: String?,
    val iconUrl: String?,
    // For the network badge on the asset's icon.
    val assetId: String?,
)

sealed interface BuyCryptoRate {
    data object Loading : BuyCryptoRate
    data object Failed : BuyCryptoRate
    data class Quoted(val from: String, val to: String, val refreshing: Boolean) : BuyCryptoRate
}

sealed interface BuyCryptoLimit {
    data class BelowMin(val amount: String) : BuyCryptoLimit
    data class AboveMax(val amount: String) : BuyCryptoLimit
}

interface BuyCryptoViewState : MviViewState {
    val loading: StateFlow<Boolean>
    val failed: StateFlow<Boolean>

    // The fiat's name while it has nothing to buy with, null otherwise.
    val nothingToBuy: StateFlow<String?>
    val fiat: StateFlow<String?>
    val fiatFlag: StateFlow<String?>
    val amount: StateFlow<BuyCryptoAmount>
    val amountField: StateFlow<BuyCryptoAmountField?>
    val quickAmounts: StateFlow<BuyCryptoQuickAmounts?>
    val receive: StateFlow<BuyCryptoReceive?>
    val quoting: StateFlow<Boolean>
    val rate: StateFlow<BuyCryptoRate?>
    val limit: StateFlow<BuyCryptoLimit?>
    val rateTimer: StateFlow<Float?>
    val payment: StateFlow<BuyCryptoPaymentRow?>
    val currentSetup: StateFlow<BuyCryptoSetup?>
    val canContinue: StateFlow<Boolean>
    val continuing: StateFlow<Boolean>
    val payButton: StateFlow<BuyCryptoPayButton?>

    // MoonPay's legal texts for this payment, shown in full under the payment method.
    val disclosures: StateFlow<List<MoonPayDisclosureText>>

    // Null while there is no MoonPay quote on screen to break down.
    val priceDetails: StateFlow<BuyCryptoPriceDetails?>

    // Off once Continue or the pay button starts paying: the sheet then neither opens nor stays over MoonPay's
    // sign-in or status sheet.
    val priceDetailsOpenable: StateFlow<Boolean>
}

// What MoonPay's quote on screen is made of, behind "incl. fees" next to the rate.
data class BuyCryptoPriceDetails(
    val youPay: String,
    val rate: BuyCryptoRate.Quoted,
    val fees: List<MoonPayPaymentFee>,
    val youGet: String,
)

// MoonPay's Google Pay button in place of Continue: its frame is found by [externalTransactionId].
data class BuyCryptoPayButton(val externalTransactionId: String, val ready: Boolean, val enabled: Boolean)

sealed interface BuyCryptoAction : MviAction {
    data class SetAmount(val text: String) : BuyCryptoAction
    data object Continue : BuyCryptoAction
    data object Reload : BuyCryptoAction
    data object Requote : BuyCryptoAction
    data object RetryRate : BuyCryptoAction
    data object RetryProviders : BuyCryptoAction
    data object ToggleRateDirection : BuyCryptoAction
    data class SetVisible(val visible: Boolean) : BuyCryptoAction

    // Back from MoonPay's status sheet; [retry] is what the customer chose, null when the frame was closed.
    data class PaymentEnded(val retry: MoonPayPaymentRetry?) : BuyCryptoAction

    // Saved cards the customer removed in the method picker, by MoonPay's card id.
    data class CardsRemoved(val ids: Set<String>) : BuyCryptoAction

    data class SelectFiat(val code: String) : BuyCryptoAction
    data class SelectAsset(
        val assetId: String,
        val fiat: String,
        val symbol: String,
        val networkName: String?,
        val iconUrl: String?,
    ) : BuyCryptoAction

    data class SelectMethod(
        val id: String,
        val fiat: String,
        val title: String,
        val subtitle: String?,
        val iconUrl: String?,
        val card: BuyCryptoCard?,
        val moonPay: Boolean,
    ) : BuyCryptoAction

    data class SelectProvider(val provider: ExchangeMerchantSlug) : BuyCryptoAction
}

sealed interface BuyCryptoEvent {
    data class OpenUrl(val url: String) : BuyCryptoEvent
    data class OpenMoonPay(val data: MoonPayPaymentData) : BuyCryptoEvent
    data object PickMethod : BuyCryptoEvent
    data object Failed : BuyCryptoEvent
    data object Offline : BuyCryptoEvent
}

class BuyCryptoFeature(
    private val data: BuyCryptoData,
    private val source: RampSource,
    private val merchants: RampPayments,
    private val moonPay: MoonPayRamp?,
    private val exchange: ExchangeRepository,
    private val settingsRepository: SettingsRepository,
    private val appLifecycle: AppLifecycleProvider,
    private val savedStateHandle: SavedStateHandle,
    private val networkMonitor: NetworkMonitor,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : GraphViewModel<BuyCryptoViewState, BuyCryptoAction>(), BuyCryptoViewState, BuyCryptoOffer {

    private val relay = MviRelay<BuyCryptoEvent>()
    private val started = timeSource.markNow()
    val events: Flow<BuyCryptoEvent> = relay.events

    private val reducer = BuyCryptoReducer(moonPay?.payments, data.analyticsFrom, data.utm) { UUID.randomUUID().toString() }

    private val analytics = BuyCryptoAnalytics(data.analyticsFrom, data.utm)

    // Results of loads, quotes and checkouts on their way to the session; buffered, so none is lost before it listens.
    private val results = Channel<Command>(Channel.UNLIMITED)

    private val restored = Session(
        request = SetupRequest(
            fiat = savedStateHandle.get<String>(KEY_FIAT),
            assetId = savedStateHandle.get<String>(KEY_ASSET),
            method = savedStateHandle.get<String>(KEY_METHOD),
            provider = merchantSlug(savedStateHandle.get<String>(KEY_PROVIDER)),
        ),
        amount = AmountState(
            text = savedStateHandle.get<String>(KEY_AMOUNT).orEmpty(),
            suggested = if (savedStateHandle.contains(KEY_AMOUNT)) {
                savedStateHandle.get<String>(KEY_SUGGESTED_AMOUNT)
            } else {
                ""
            },
            revision = 0,
        ),
    )

    private val session: StateFlow<Session> = merge(actions.map { Command.Act(it) }, results.receiveAsFlow())
        .runningFold(Step(restored)) { step, command -> reducer.reduce(step.session, command) }
        .onEach { step ->
            // A failure with no network says so instead of an unknown error.
            val offline = !networkMonitor.isOnlineFlow.first()
            step.events.forEach { event ->
                relay.emit(BuyCryptoEvent.Offline.takeIf { offline && event is BuyCryptoEvent.Failed } ?: event)
            }
            step.tracks.forEach(::track)
            save(step.session)
        }
        .map { it.session }
        .cacheState(initialValue = restored)

    init {
        // Once per opening: a rotation or a process death restores the screen without opening it again.
        if (savedStateHandle.get<Boolean>(KEY_STARTED_SENT) != true) {
            savedStateHandle[KEY_STARTED_SENT] = true
            analytics.started()
        }
    }

    // Effects: each runs what the session asks for and hands the result back to it.

    private val connection = flow { emit(moonPay?.check?.invoke() ?: MoonPayAccess.Unavailable) }
        .onEach { results.send(Command.Connected(it)) }
        .cacheState(initialValue = null)

    private val setupLoad = session
        .map { it.setupKey }
        .distinctUntilChanged()
        .mapLatest { key ->
            coroutineScope {
                val access = async {
                    if (key.recheck) {
                        recheck()
                    } else {
                        session.mapNotNull { it.moonPay }.first()
                    }
                }
                // A load that failed before the methods does not wait for the check.
                Command.SetupLoaded(key, load(key.request, access).also { access.cancel() })
            }
        }
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    private val providersLoad = session
        .map { it.providersKey }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { Command.ProvidersLoaded(it, loadProviders(it)) } }
        .filterNotNull()
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    private val rangeLoad = session
        .map { it.setup?.takeIf { setup -> setup.method?.isP2P == false } }
        .distinctUntilChanged { old, new -> old === new }
        .mapLatest { setup -> setup?.let { Command.RangeLoaded(it, loadAmountRange(it)) } }
        .filterNotNull()
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    private val quoteLoad = session
        .map { it.quoteKey?.takeIf { key -> key.quotable } }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { quote(it) } }
        .filterNotNull()
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    private val checkout = session
        .map { it.phase as? Phase.Continuing }
        .distinctUntilChanged()
        .mapLatest { phase ->
            when {
                phase == null -> null
                phase.signIn -> Command.SignInEnded(phase.id, signIn())
                else -> proceed(phase)
            }
        }
        .filterNotNull()
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    // Back online, a quote that failed for the network asks again at once: with no rate yet, no timer would.
    private val onlineRequote = networkMonitor.isOnlineFlow
        .distinctUntilChanged()
        .filter { it }
        .withLatestFrom(session) { _, session -> session }
        .filter { it.quote.offline && it.phase is Phase.Idle }
        .onEach { results.send(Command.Act(BuyCryptoAction.Requote)) }
        .cacheState(initialValue = null)

    private val rateRetry = actions
        .on<BuyCryptoAction.RetryRate>()
        .withLatestFrom(session) { _, session -> session }
        .filter { it.phase is Phase.Idle }
        .map(reducer::onMoonPay)
        .onEach { onMoonPay ->
            if (onMoonPay && recheck() != MoonPayAccess.Connected) {
                results.send(Command.Act(BuyCryptoAction.Reload))
            } else {
                results.send(Command.Act(BuyCryptoAction.Requote))
            }
        }
        .cacheState(initialValue = null)

    // Runs only while the screen is idle and seen; paused, the time still runs, so a rate that ran out is refreshed on return.
    private val rateCountdown = session
        .map { session ->
            val view = session.quote
            val ready = (view.ready as? RampOffer.Available)?.takeIf { it.rate != null || it.lifetime != null }
            when {
                ready != null -> session.quotesLanded to rateTimer(ready)
                // Offline the timer keeps asking, a rate on screen or not: the rate comes back by itself with the network,
                // even when the network the monitor reports does not work yet.
                view.offline && !view.loading -> session.quotesLanded to RATE_LIFETIME
                else -> null
            }
        }
        .distinctUntilChanged()
        .countdown(
            running = combine(session, appLifecycle.observeWithoutGrace) { session, foreground ->
                session.phase is Phase.Idle && session.visible && foreground
            },
            timeSource = timeSource,
        ) { landed -> landed?.second }
        .onEach { countdown ->
            if (countdown != null && countdown.finished) {
                results.send(Command.Act(BuyCryptoAction.Requote))
            }
        }
        .cacheState(initialValue = null)

    // What the screen shows: read from the session only.

    override val loading = session
        .map { it.status == LoadStatus.Loading && it.lastLoaded == null }
        .cacheState(initialValue = true)

    override val failed = session
        .map { it.status == LoadStatus.Failed && it.lastLoaded == null }
        .cacheState(initialValue = false)

    override val nothingToBuy = session
        .map { session -> session.setup?.takeIf { it.method == null }?.let { fiatName(it.fiat) } }
        .cacheState(initialValue = null)

    private val grouping = walletGroupingSeparator

    override val fiat = session
        .map { it.shown?.fiat }
        .cacheState(initialValue = null)

    override val fiatFlag = session
        .map { session -> session.shown?.fiat?.let { WalletCurrency.fiat(it).iconUri?.toString() } }
        .cacheState(initialValue = null)

    override val amount = session
        .map { BuyCryptoAmount(it.amount.text, it.amount.revision) }
        .cacheState(initialValue = BuyCryptoAmount(restored.amount.text, restored.amount.revision))

    override val amountField = session
        .map { session ->
            session.shown?.let {
                // Open only over a way to pay; until a new setup lands (a load, a failed one), the last loaded decides.
                val payable = (session.setup ?: session.lastLoaded)?.method != null
                BuyCryptoAmountField(
                    fiat = it.fiat,
                    editable = session.phase is Phase.Idle && payable,
                    outOfLimits = limit(session) != null,
                    grouping = grouping,
                )
            }
        }
        .cacheState(initialValue = null)

    override val quickAmounts = session
        .map(::quickAmounts)
        .cacheState(initialValue = null)

    override val receive = session
        .map { session ->
            val shown = session.shown
            val asset = shown?.asset
            when {
                shown == null -> null
                asset == null -> BuyCryptoReceive(amount = null, muted = false, asset = null, iconUrl = null, assetId = null)
                else -> {
                    val offer = session.quote.ready.takeIf { session.status == LoadStatus.Ready }
                    // Offline the amount stays worked out at the last rate, greyed, as the design shows.
                    val offlineRate = offlineRate(session)?.takeIf { offer == null }
                    BuyCryptoReceive(
                        amount = when (offer) {
                            is RampOffer.Available -> shownAmount(CurrencyFormatter.format(value = offer.receive))
                            is RampOffer.OutOfLimits -> "0"
                            null -> offlineRate?.let { perFiat ->
                                parseAmount(session.amount.text, fiatScale(shown.fiat))
                                    ?.let { shownAmount(CurrencyFormatter.format(value = it.multiply(perFiat, MathContext.DECIMAL64))) }
                            }
                        },
                        muted = offer is RampOffer.OutOfLimits || offlineRate != null,
                        asset = asset.label,
                        iconUrl = asset.iconUrl,
                        assetId = asset.id,
                    )
                }
            }
        }
        .cacheState(initialValue = null)

    override val quoting = session
        .map { it.quote.loading || it.status == LoadStatus.Loading }
        .cacheState(initialValue = false)

    override val rate = session
        .map(::rate)
        .cacheState(initialValue = null)

    override val limit = session
        .map(::limit)
        .cacheState(initialValue = null)

    override val priceDetails = session
        .map(::priceDetails)
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    override val priceDetailsOpenable = session
        .map { it.phase is Phase.Idle }
        .distinctUntilChanged()
        .cacheState(initialValue = true)

    override val rateTimer = rateCountdown
        .map { it?.progress }
        .cacheState(initialValue = null)

    override val payment = session
        .map { session ->
            val shown = session.shown
            val method = shown?.method
            when {
                session.status == LoadStatus.Failed -> BuyCryptoPaymentRow.Failed.takeIf { shown != null }
                method == null -> null
                else -> BuyCryptoPaymentRow.Method(
                    payment = payment(method, session.quote.offer.takeIf { session.status == LoadStatus.Ready }),
                    loading = session.status == LoadStatus.Loading,
                )
            }
        }
        .cacheState(initialValue = null)

    override val currentSetup = session
        .map { session ->
            session.shown?.takeIf { session.phase is Phase.Idle }?.let {
                BuyCryptoSetup(
                    fiat = it.fiat,
                    assetId = it.asset?.id,
                    method = it.method?.id,
                    canPickMethod = session.setup?.let(reducer::canPickMethod) == true,
                )
            }
        }
        .cacheState(initialValue = null)

    override val offer = session
        .map(reducer::offer)
        .distinctUntilChanged()
        .cacheState(initialValue = reducer.offer(restored))

    override val providerFailures = session
        .map { it.providerFailures }
        .distinctUntilChanged()
        .cacheState(initialValue = 0)

    private val providerSheet = session
        .map(reducer::providerSheet)
        .cacheState(initialValue = reducer.providerSheet(restored))

    override val providers = providerSheet
        .map { it.rows }
        .distinctUntilChanged()
        .cacheState(initialValue = emptyList())

    override val providerLine = providerSheet
        .map { it.line }
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    override val canContinue: StateFlow<Boolean> = session
        .map { it.canContinue }
        .cacheState(initialValue = false)

    override val continuing = session
        .map { it.phase is Phase.Continuing }
        .cacheState(initialValue = false)

    override val disclosures = session
        .map { it.paymentDisclosures }
        .distinctUntilChanged()
        .map(MoonPayPaymentDisclosures::texts)
        .cacheState(initialValue = emptyList())

    // MoonPay's buy button the session asks for runs while its id stays; its events go back to the session. Leaving it
    // drops an untapped one; a tapped one is the status sheet's until that sheet stops it.
    private val buttonRun = session
        .map { it.button }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { button -> button?.let(::runButton) ?: emptyFlow() }
        .onEach { results.send(it) }
        .cacheState(initialValue = null)

    // Tappable when Continue would be, for the quote the frame holds while it still has the lifetime a payment needs;
    // the countdown re-reads that lifetime as it runs out. An ended button gives its place back to a disabled Continue.
    override val payButton: StateFlow<BuyCryptoPayButton?> = combine(session, rateCountdown) { session, _ ->
        session.button?.takeUnless { it.phase == ButtonPhase.Ended }?.let { button ->
            BuyCryptoPayButton(
                externalTransactionId = button.id,
                ready = button.phase != ButtonPhase.Loading,
                enabled = button.phase == ButtonPhase.Ready && payable(session, button.inFrame),
            )
        }
    }
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    // Starts the button with a quote that still has the lifetime a payment needs, hands it every newer quote of its
    // amount, and tells the session what the button does. A button that does not draw within [PAY_BUTTON_READY_TIMEOUT]
    // ends as a failure.
    private fun runButton(state: ButtonState): Flow<Command> = channelFlow {
        val buttons = moonPay?.buttons
        val data = state.setup.payments.buttonPayment(state.loaded, state.setup.method?.title.orEmpty(), state.id)
        if (buttons == null || data == null) {
            send(Command.ButtonEnded(state.id, quiet = false, signInRequired = false))
            return@channelFlow
        }
        if (!state.loaded.isFresh(PAY_BUTTON_MIN_LIFETIME)) {
            send(Command.ButtonEnded(state.id, quiet = true, signInRequired = false))
            return@channelFlow
        }
        val button = buttons.start(data)
        try {
            launch {
                session.mapNotNull { it.button?.takeIf { shown -> shown.id == state.id }?.inFrame }
                    .distinctUntilChanged()
                    .collect { buttons.setQuote(state.id, it) }
            }
            val drawn = withTimeoutOrNull(PAY_BUTTON_READY_TIMEOUT) {
                button.states.first { it is MoonPayPaymentState.ButtonReady || it is MoonPayPaymentState.ButtonPressed || it.isFinal }
            }
            if (drawn == null) {
                // In the background the page may not draw at all: that is no failure to tell, a newer quote retries.
                val foreground = appLifecycle.observeWithoutGrace.first()
                L.w("MoonPay buy button ${state.id} did not draw, foreground $foreground")
                buttons.discard(button)
                send(Command.ButtonEnded(state.id, quiet = !foreground, signInRequired = false))
                awaitCancellation()
            }
            button.states.transformWhile { update ->
                when {
                    update is MoonPayPaymentState.ButtonReady -> emit(Command.ButtonReady(state.id))
                    update is MoonPayPaymentState.ButtonPressed ->
                        emit(Command.ButtonPressed(state.id, data.copy(quote = button.pressedWith ?: button.quote.value)))
                    update.isFinal -> {
                        val failure = (update as? MoonPayPaymentState.Failed)?.failure
                        emit(
                            Command.ButtonEnded(
                                id = state.id,
                                quiet = failure == MoonPayPaymentFailure.QuoteExpired,
                                signInRequired = failure == MoonPayPaymentFailure.SignInRequired,
                            ),
                        )
                    }
                }
                update !is MoonPayPaymentState.ButtonPressed && !update.isFinal
            }.collect { send(it) }
            awaitCancellation()
        } finally {
            buttons.discard(button)
        }
    }

    private fun payable(session: Session, inFrame: MoonPayBuyQuote): Boolean {
        val shown = ((session.quote.ready as? RampOffer.Available)?.ticket as? RampTicket.MoonPay)?.quote
        return session.phase is Phase.Idle && shown?.signature == inFrame.signature && inFrame.isFresh(PAY_BUTTON_MIN_LIFETIME)
    }

    // The amount screen is seen once per opening.
    private fun track(track: BuyCryptoTrack) {
        if (track is BuyCryptoTrack.AmountShown) {
            if (savedStateHandle.get<Boolean>(KEY_AMOUNT_SHOWN_SENT) == true) {
                return
            }
            savedStateHandle[KEY_AMOUNT_SHOWN_SENT] = true
        }
        analytics.track(track)
    }

    private fun save(session: Session) {
        savedStateHandle[KEY_FIAT] = session.request.fiat
        savedStateHandle[KEY_ASSET] = session.request.assetId
        savedStateHandle[KEY_METHOD] = session.request.method
        savedStateHandle[KEY_PROVIDER] = session.request.provider?.value
        savedStateHandle[KEY_AMOUNT] = session.amount.text
        savedStateHandle[KEY_SUGGESTED_AMOUNT] = session.amount.suggested
    }

    // Unreachable keeps what was known: a connected session is not dropped for a network failure.
    private suspend fun recheck(): MoonPayAccess {
        val checked = moonPay?.check?.invoke() ?: return MoonPayAccess.Unavailable
        val access = checked.takeIf { it != MoonPayAccess.Unreachable } ?: session.value.moonPay ?: checked
        results.send(Command.Connected(access))
        return access
    }

    private suspend fun signIn(): MoonPaySignIn {
        val signIn = moonPay?.connect?.invoke() ?: MoonPaySignIn.Closed
        if (signIn is MoonPaySignIn.Failed) {
            verifyError(signIn.error)
        }
        return signIn
    }

    private suspend fun load(request: SetupRequest, access: Deferred<MoonPayAccess>): Setup? {
        val preferred = request.fiat ?: data.preferredCurrency ?: settingsRepository.currency.code
        return try {
            val placeholder = Setup(asset = null, fiatOrUsd(preferred), method = null, merchants)
            val assetId = request.assetId ?: data.assetId
                ?: return defaultSetup(request, preferred, access) ?: placeholder
            // A token the user came for stays, as on iOS: nothing to buy it with shows its stub, not another token.
            // One the onramp does not know is described by the app's own data, so MoonPay may still sell it.
            val asset = source.loadAssetDetail(assetId).getOrThrow() ?: source.appAssetDetail(assetId).getOrThrow() ?: return placeholder
            assetSetup(asset, request, preferred, access)
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e)
            null
        }
    }

    // MoonPay's row lists its catalog of methods once signed in, else what the onramp lists for it; the sheet
    // names its methods for the pair instead while it sells here.
    private suspend fun loadProviders(key: BuyCryptoReducer.ProvidersKey): BuyCryptoReducer.ProviderOptions? {
        return try {
            val listed = key.asset.moonPayMethodNames(key.fiat)
            val moonPayMethods = if (key.signedIn) {
                runCatching { exchange.getMoonPayPaymentMethods() }
                    .onFailure { verifyError(it) }
                    .map { all -> all.filter { moonPay?.methodSupport?.supports(it.type) == true }.map { it.title } }
                    .getOrDefault(listed)
            } else {
                listed
            }
            val merchants = key.asset.merchantOptions(key.fiat, exchange.getMerchants(data.walletId), withMoonPayWidget = true)
            BuyCryptoReducer.ProviderOptions(merchants, moonPayMethods)
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e)
            null
        }
    }

    private suspend fun loadAmountRange(setup: Setup): RampAmountRange? {
        val method = setup.method?.takeIf { !it.isP2P } ?: return null
        return setup.payments.loadAmountRange(method, setup.fiat)
            .onFailure { verifyError(it); L.e(it) }
            .getOrNull()
    }

    // The preferred currency may be one the onramp has no default for (TON, an unsupported fiat): USD is next,
    // unless a pick fixed the currency. The first default with a payment method wins, else the first one found.
    private suspend fun defaultSetup(request: SetupRequest, preferred: String, access: Deferred<MoonPayAccess>): Setup? {
        val tried = mutableSetOf<String>()
        var found: Setup? = null
        val fiats = if (request.fiat != null) {
            listOf(preferred)
        } else {
            listOf(preferred, WalletCurrency.USD.code).distinct()
        }
        for (fiat in fiats) {
            val assetId = source.findDefaultAsset(fiat).getOrThrow()?.asset?.id ?: continue
            if (!tried.add(assetId)) {
                continue
            }
            val asset = source.loadAssetDetail(assetId).getOrThrow() ?: continue
            val setup = assetSetup(asset, request, preferred, access)
            if (setup.method != null) {
                return setup
            }
            found = found ?: setup
        }
        return found
    }

    // P2P is never the requested method: picking it opens the market at once.
    // Before a sign-in MoonPay quotes through its widget, as on iOS.
    private suspend fun assetSetup(asset: RampAssetDetail, request: SetupRequest, preferred: String, access: Deferred<MoonPayAccess>): Setup {
        val fiat = request.fiat ?: source.resolveFiat(asset, preferred)
        val pickedMerchant = request.provider?.takeIf { it != ExchangeMerchantSlug.moonpay }
        val merchantMethods = if (pickedMerchant != null) {
            merchants.loadMethods(asset.assetId, asset.symbol, fiat).getOrThrow().orEmpty()
        } else {
            null
        }
        val picked = pickedMerchant?.takeIf { merchant -> merchantMethods.orEmpty().soldBy(merchant, fiat).any { !it.isP2P } }
        // Awaited even for a merchant pick: the provider sheet offers MoonPay by the check.
        val checked = access.await()
        val notSold = pairKey(asset.assetId, fiat) in session.value.moonPayNotSold
        val triesMoonPay = moonPay != null && picked == null && !notSold && checked == MoonPayAccess.Connected
        val moonPayLoad = if (moonPay != null && triesMoonPay) {
            moonPay.payments.loadMethods(asset.assetId, asset.symbol, fiat).also { load ->
                load.exceptionOrNull()?.let { verifyError(it) }
            }
        } else {
            null
        }
        val moonPayMethods = moonPayLoad?.getOrNull()?.takeIf { moonPay != null && quotesDefi(moonPay, it, fiat) }
        if (moonPay != null && moonPayMethods != null) {
            val pickedMoonPay = request.provider?.takeIf { it == ExchangeMerchantSlug.moonpay }
            return Setup(
                asset,
                fiat,
                moonPayMethods.preferred(request.method),
                moonPay.payments,
                ExchangeMerchantSlug.moonpay,
                pickedMoonPay,
                moonPaySold = true,
                methods = moonPayMethods,
            )
        }
        // Signed in, MoonPay not selling the pair leaves every merchant, as on iOS; MoonPay picked then sells through its widget.
        val all = merchantMethods ?: merchants.loadMethods(asset.assetId, asset.symbol, fiat).getOrThrow().orEmpty()
        val widgetSells = all.any { it.offersMoonPay(fiat) }
        val provider = picked ?: ExchangeMerchantSlug.moonpay.takeIf {
            widgetSells && (request.provider == ExchangeMerchantSlug.moonpay || checked == MoonPayAccess.SignInRequired)
        }
        val methods = provider?.let { all.soldBy(it, fiat) } ?: all
        val honoured = picked ?: provider?.takeIf { request.provider == it }
        // MoonPay failing for a pair the merchants do not sell either is a failed load to retry, not a pair nothing sells;
        // only its refusal leaves the stub.
        moonPayLoad?.exceptionOrNull()?.takeIf { methods.isEmpty() && !it.isMoonPayRefusal }?.let { throw it }
        // A failed MoonPay load leaves the merchants this time without marking the pair not sold, as on iOS.
        val sold = false.takeIf { moonPayLoad?.isSuccess == true }
        return Setup(asset, fiat, methods.preferred(request.method), merchants, provider, honoured, moonPaySold = sold, methods = methods)
    }

    // MoonPay lists DeFi tokens (AAVE, PEPE) it then refuses to quote with a 503, as iOS found: one is asked for its limits
    // before the screen shows MoonPay, and a 503 leaves it to the merchants. Any other answer lets MoonPay sell it.
    private suspend fun quotesDefi(moonPay: MoonPayRamp, methods: List<RampPaymentMethod>, fiat: String): Boolean {
        val defi = methods.firstOrNull { (it.details as? RampPaymentMethod.Details.MoonPay)?.destination?.caip19 != null } ?: return true
        val probe = moonPay.payments.loadAmountRange(defi, fiat)
        probe.exceptionOrNull()?.let { verifyError(it) }
        return probe.exceptionOrNull()?.isServiceUnavailable != true
    }

    private fun elapsedMs(): Long = started.elapsedNow().inWholeMilliseconds

    // As on iOS: the device reports no network, or the request found no host. The monitor may learn of a lost network
    // after a request already failed for it.
    private suspend fun isOffline(error: Throwable): Boolean {
        return !networkMonitor.isOnlineFlow.first() ||
            generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any { it.isNoNetwork }
    }

    // The last rate of this setup while the quote fails offline: asset per unit of fiat.
    private fun offlineRate(session: Session): BigDecimal? {
        val view = session.quote.takeIf { it.offline } ?: return null
        return view.rated?.takeIf { it.first === session.setup }?.second?.rate
    }

    private suspend fun quote(key: QuoteKey): Command.Quoted {
        val method = requireNotNull(key.setup.method)
        val asset = requireNotNull(key.setup.asset)
        val amount = requireNotNull(key.amount)
        if (!key.refresh) {
            delay(QUOTE_DEBOUNCE)
        }
        val request = RampQuoteRequest(asset.assetId, key.setup.fiat, method, amount)
        return try {
            Command.Quoted(key, key.setup.payments.requestOffer(request).getOrThrow(), failed = false, offline = false, atMs = elapsedMs())
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e)
            Command.Quoted(key, offer = null, failed = true, offline = isOffline(e), atMs = elapsedMs())
        }
    }

    private suspend fun proceed(continuing: Phase.Continuing): Command {
        val setup = continuing.setup
        val method = setup.method ?: return Command.ContinueEnded(continuing.id, RampCheckout.Cancelled)
        val asset = setup.asset ?: return Command.ContinueEnded(continuing.id, RampCheckout.Cancelled)
        val offer = continuing.offer
        val checkout = try {
            when {
                method.isP2P -> RampCheckout.OpenUrl(source.createP2PSessionUrl(asset.assetId, setup.fiat).getOrThrow())
                offer == null && !method.isNewCard -> RampCheckout.Cancelled
                continuing.onMoonPay -> when (val signIn = signIn()) {
                    is MoonPaySignIn.SignedIn -> {
                        results.send(Command.Connected(MoonPayAccess.Connected))
                        setup.payments.checkout(method, offer).getOrThrow()
                    }
                    else -> return Command.SignInEnded(continuing.id, signIn)
                }
                else -> setup.payments.checkout(method, offer).getOrThrow()
            }
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e)
            null
        }
        return Command.ContinueEnded(continuing.id, checkout)
    }

    // While a pick loads: null, or whether placeholders stand where chips were, so P2P or nothing to buy does not flash them.
    private fun quickAmounts(session: Session): BuyCryptoQuickAmounts? {
        val fiat = session.shown?.fiat ?: return null
        if (session.status == LoadStatus.Loading) {
            val placeholders = session.lastLoaded?.method?.isP2P == false
            return BuyCryptoQuickAmounts(items = placeholderQuickAmounts(fiat), max = null, loading = true)
                .takeIf { placeholders }
        }
        // P2P opens the market without an amount.
        session.setup?.method?.takeIf { !it.isP2P } ?: return null
        val range = session.range
        // MAX fills in what the field takes: a merchant's maximum over a billion (VND, IDR) is the field's cap.
        val max = range?.max?.min(MAX_AMOUNT)
        return BuyCryptoQuickAmounts(
            items = quickAmounts(range?.suggested).filter { quick -> max == null || BigDecimal(quick) <= max }.map { quick ->
                BuyCryptoQuickAmount(
                    title = shownAmount(CurrencyFormatter.format(currency = fiat, value = BigDecimal(quick))),
                    amount = quick.toString(),
                )
            },
            max = max?.stripTrailingZeros()?.toPlainString(),
            loading = session.rangeLoading,
        )
    }

    private fun rate(session: Session): BuyCryptoRate? {
        val asset = session.setup?.asset
        val view = session.quote
        return when {
            session.status == LoadStatus.Loading && session.shown?.asset != null -> BuyCryptoRate.Loading
            asset == null -> null
            // Offline the last rate stays with the spinner, as the design shows; with none yet, it is still loading.
            view.offline -> view.rated?.takeIf { it.first === session.setup }?.second?.let { last ->
                last.rate?.let { rate(asset, last.request.fiat, it, session.inverted, refreshing = true) }
            } ?: BuyCryptoRate.Loading
            view.loading -> BuyCryptoRate.Loading
            view.failed -> BuyCryptoRate.Failed
            // In the currency the offer was quoted in, not the one a pick may have just put on screen.
            else -> when (val offer = view.offer) {
                is RampOffer.Available -> offer.rate?.let { rate(asset, offer.request.fiat, it, session.inverted, view.refreshing) }
                is RampOffer.OutOfLimits -> view.rated?.takeIf { it.first === session.setup }?.second?.let { last ->
                    last.rate?.let { rate(asset, last.request.fiat, it, session.inverted, refreshing = false) }
                }
                null -> null
            }
        }
    }

    // The quote the rate row shows, refreshing included; none offline, out of limits or from another provider.
    private fun priceDetails(session: Session): BuyCryptoPriceDetails? {
        if (session.status != LoadStatus.Ready || session.quote.offline) {
            return null
        }
        val rate = rate(session) as? BuyCryptoRate.Quoted ?: return null
        val offer = session.quote.ready as? RampOffer.Available ?: return null
        val quote = (offer.ticket as? RampTicket.MoonPay)?.quote ?: return null
        val asset = session.setup?.asset ?: return null
        if (quote.source.amount?.toBigDecimalOrNull() == null || quote.destination.amount?.toBigDecimalOrNull() == null) {
            return null
        }
        val summary = MoonPayQuoteSummary(quote, assetLabel(asset.symbol, asset.networkName)).summary
        return BuyCryptoPriceDetails(youPay = summary.paid, rate = rate, fees = summary.fees, youGet = summary.receive)
    }

    private fun placeholderQuickAmounts(fiat: String): List<BuyCryptoQuickAmount> {
        return quickAmounts(minimum = null).map { quick ->
            BuyCryptoQuickAmount(
                title = shownAmount(CurrencyFormatter.format(currency = fiat, value = BigDecimal(quick))),
                amount = quick.toString(),
            )
        }
    }

    private fun rate(asset: RampAssetDetail, fiat: String, perFiat: BigDecimal, inverted: Boolean, refreshing: Boolean): BuyCryptoRate {
        val label = assetLabel(asset.symbol, asset.networkName)
        return if (inverted) {
            val perAsset = BigDecimal.ONE.divide(perFiat, MathContext.DECIMAL64)
            BuyCryptoRate.Quoted(from = label, to = shownAmount(formatFiatPrice(perAsset, currency = fiat)), refreshing = refreshing)
        } else {
            BuyCryptoRate.Quoted(from = fiat, to = "${shownAmount(formatRate(perFiat, currency = ""))} $label", refreshing = refreshing)
        }
    }

    private fun shownAmount(formatted: CharSequence): String {
        return walletGrouping(tightFiat(formatted))
    }

    // Kept while the next amount is quoted, so typing out of limits does not flash «Continue»; display only.
    private fun limit(session: Session): BuyCryptoLimit? {
        if (session.signsIn) {
            return null
        }
        val offer = session.quote.offer as? RampOffer.OutOfLimits ?: return null
        val quoted = session.quote.input?.first ?: return null
        return limit(offer.violation, quoted.fiat).takeIf { session.status == LoadStatus.Ready }
    }

    // At the fiat's precision, on the side RampLimits compares for the merchants: the minimum down, the maximum up.
    // MoonPay's limits come already rounded to the side it accepts.
    private fun limit(violation: RampLimitViolation, fiat: String): BuyCryptoLimit {
        val scale = fiatScale(fiat)
        return when (violation) {
            is RampLimitViolation.BelowMin -> BuyCryptoLimit.BelowMin(formatLimit(violation.min.setScale(scale, RoundingMode.DOWN), fiat))
            is RampLimitViolation.AboveMax -> BuyCryptoLimit.AboveMax(formatLimit(violation.max.setScale(scale, RoundingMode.UP), fiat))
        }
    }

    private fun formatLimit(value: BigDecimal, fiat: String): String {
        val exact = value.stripTrailingZeros()
        return shownAmount(CurrencyFormatter.formatFull(fiat, Coins.of(exact), exact.scale().coerceAtLeast(0)))
    }

    private fun payment(method: ShownMethod, offer: RampOffer?): BuyCryptoPayment {
        val quotedBy = when (offer) {
            is RampOffer.Available -> offer.badgeUrl
            is RampOffer.OutOfLimits -> offer.badgeUrl
            null -> null
        }
        return BuyCryptoPayment(
            title = method.title,
            subtitle = method.subtitle,
            iconUrl = method.iconUrl,
            badge = when {
                method.moonPay -> BuyCryptoBadge.MoonPay
                else -> quotedBy?.let(BuyCryptoBadge::Image)
            },
            card = method.card,
        )
    }

    private fun rateTimer(offer: RampOffer.Available): Duration {
        val lifetime = offer.lifetime ?: return RATE_LIFETIME
        return (lifetime - 1.seconds).coerceIn(MIN_RATE_LIFETIME, MAX_RATE_LIFETIME)
    }

    private companion object {
        val QUOTE_DEBOUNCE = 400.milliseconds
        val RATE_LIFETIME = 12.seconds
        val MIN_RATE_LIFETIME = 3.seconds
        val MAX_RATE_LIFETIME = 60.seconds
        const val KEY_AMOUNT = "buy_crypto_amount"
        const val KEY_SUGGESTED_AMOUNT = "buy_crypto_suggested_amount"
        const val KEY_FIAT = "buy_crypto_fiat"
        const val KEY_ASSET = "buy_crypto_asset"
        const val KEY_METHOD = "buy_crypto_method"
        const val KEY_PROVIDER = "buy_crypto_provider"
        const val KEY_STARTED_SENT = "buy_crypto_started_sent"
        const val KEY_AMOUNT_SHOWN_SENT = "buy_crypto_amount_shown_sent"
    }
}

/*
 *   SetAmount, Select*, Continue, Reload, Requote, RetryProviders, ToggleRateDirection, SetVisible, PaymentEnded, CardsRemoved ──┐
 *   results: Connected, SetupLoaded, ProvidersLoaded, RangeLoaded, Quoted, SignInEnded, ContinueEnded, Button* ───────────────────────┴──►
 *       [fold, BuyCryptoReducer.reduce] session ──► events, analytics, SavedStateHandle
 *
 *   MoonPay check [once, no sign-in] ──► Connected ⋯ [awaited by the methods] setupLoad
 *   session.setupKey ──► [latest, a MoonPay check on a reload or pick] setupLoad ⋯ session ──► Connected, SetupLoaded
 *   session.providersKey [pair, signed in, retry] ──► [latest] providersLoad ──► ProvidersLoaded
 *   session.setup [not P2P] ──► [latest] rangeLoad ──► RangeLoaded
 *   session.quoteKey [quotable] ──► [latest, debounced unless a refresh] quoteLoad ──► Quoted
 *   session.phase [continuing] ──► [a MoonPay sign-in, then a checkout] checkout ──► Connected, SignInEnded, ContinueEnded
 *   RetryRate ⋯ session ──► [a MoonPay check on MoonPay] rateRetry ⋯ session ──► Connected, then Reload or Requote
 *   session.quote [landed, ready; offline, rated], session [idle, seen], foreground ──► [countdown] rateCountdown ──► rateTimer, Requote
 *   NetworkMonitor [back online] ⋯ session [quote offline, idle] ──► onlineRequote ──► Requote
 *   NetworkMonitor [now] ⋯ quoteLoad [a failure] ──► Quoted.offline; ⋯ session events [Failed] ──► Offline
 *   session.button [id] ──► [latest: start in MoonPayButtonPayments, setQuote on each inFrame ⋯ session, 10 s for ready,
 *       discard an untapped one on leaving] buttonRun ──► ButtonReady, ButtonPressed, ButtonEnded
 *   session, rateCountdown ──► payButton
 *
 *   session ──► loading, failed, nothingToBuy, fiat, fiatFlag, amount, amountField, quickAmounts, receive, quoting,
 *               rate, limit, payment, currentSetup, canContinue, continuing
 *   session.paymentDisclosures [last MoonPay quote of the setup] ──► [MoonPayPaymentDisclosures] disclosures
 *   session ──► [BuyCryptoReducer.offer] offer; providerFailures; [BuyCryptoReducer.providerSheet] providerSheet ──► providers, providerLine
 */

// The system's name with the code, «Chilean Peso (CLP)»; the code alone when the system knows no name for it.
private fun fiatName(code: String): String {
    val title = WalletCurrency.fiat(code).title
    return if (title.equals(code, ignoreCase = true)) {
        code
    } else {
        "$title ($code)"
    }
}

// The lifetime a quote needs left for MoonPay to take it on a tap, as on iOS.
private val PAY_BUTTON_MIN_LIFETIME = 3.seconds

// As long as MoonPay's modal frames are given to draw.
private val PAY_BUTTON_READY_TIMEOUT = 10.seconds

private val Throwable.isMoonPayRefusal: Boolean
    get() = this is MoonPayApiException.Http && status in MOONPAY_REFUSALS

private val Throwable.isNoNetwork: Boolean
    // No DNS answer or an unreachable network is what a device without network gets; a refused connection happens on a
    // working one too.
    get() = this is UnknownHostException || this is SocketException && message.orEmpty().contains("ENETUNREACH")

private const val MAX_CAUSE_DEPTH = 8

private val Throwable.isServiceUnavailable: Boolean
    get() = this is MoonPayApiException.Http && status == HTTP_SERVICE_UNAVAILABLE

// What MoonPay answers a pair it does not take with; an expired session (401, 403) or a timeout (408) is a failure.
private val MOONPAY_REFUSALS = setOf(400, 404, 422)
private const val HTTP_SERVICE_UNAVAILABLE = 503
