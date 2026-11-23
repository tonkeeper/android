package com.tonapps.swap.screens.swap

import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.settings.SettingsRepository
import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeType
import com.tonapps.bus.generated.Events.SwapsNative.SwapsNativeWalletMode
import com.tonapps.wallet.api.API
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.core.components.AmountInputConverter
import com.tonapps.core.components.AmountToggle
import com.tonapps.core.components.amountText
import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.core.components.toAssetEntity
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeature
import com.tonapps.deposit.multicoin.screens.confirm.PriceImpact
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.deposit.multicoin.screens.confirm.PriceImpactSeverity
import com.tonapps.deposit.multicoin.screens.confirm.formatMinReceived
import com.tonapps.deposit.multicoin.screens.confirm.priceImpactOf
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.anyOfFlows
import com.tonapps.mvi.flow.countdown
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.mvi.flow.flatMapPerKey
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.mvi.flow.noneOfFlows
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.network.NetworkMonitor
import com.tonapps.swap.SwapRoutes
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapConfig
import com.tonapps.wallet.data.multichain.exchange.SwapDefaultPair
import com.tonapps.wallet.data.multichain.exchange.SwapNoRouteReason
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteDirection
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface SwapEvent {
    data class ShowError(val message: String) : SwapEvent

    /**
     * MAX found nothing above the TON coin's 1 TON, its minimum too: the amount stays as it was and the user is
     * told to enter more than [reserve] [symbol] (e.g. "1" GRAM), as iOS does.
     */
    data class MaxBelowReserve(val reserve: String, val symbol: String) : SwapEvent

    /**
     * MAX found nothing above the `/network-fees` safe fee of a coin other than TON: the amount stays as it was and
     * the user is told the balance does not cover the gas. That fee is no minimum, so no amount is asked for.
     */
    data class MaxBelowFee(val symbol: String) : SwapEvent

    data class Continue(val request: ConfirmRequest) : SwapEvent
    data class Completed(val tracked: Boolean) : SwapEvent
    data object Offline : SwapEvent
}

sealed interface SwapInitState {
    data object Loading : SwapInitState
    data object Error : SwapInitState
    data object Ready : SwapInitState
}

data class DisplayQuote(
    val quote: SwapQuote,
    val sellAmount: String,
    val buyAmount: String,
    val rateLabel: String,
    val receivedAt: Long,
    val minReceived: Pair<String, String?>,
)

sealed interface SwapQuoteError {
    // The aggregator answered with no route for this pair/amount — shown to the user.
    data class NotFound(val reason: SwapNoRouteReason) : SwapQuoteError

    // The request itself failed (network, backend); shown like NotFound and retried sooner.
    data class Failed(val isOffline: Boolean) : SwapQuoteError
}

@get:StringRes
internal val SwapQuoteError.messageRes: Int
    get() = when (this) {
        is SwapQuoteError.Failed -> when (isOffline) {
            true -> Localization.no_internet_connection
            false -> Localization.swap_error_quote_failed
        }
        is SwapQuoteError.NotFound -> when (reason) {
            SwapNoRouteReason.RegionBlocked -> Localization.swap_error_region_unavailable
            SwapNoRouteReason.AmountTooSmall -> Localization.swap_error_amount_too_small
            SwapNoRouteReason.PairUnavailable -> Localization.swap_error_pair_unavailable
            SwapNoRouteReason.AssetUnavailable -> Localization.swap_error_asset_unavailable
            SwapNoRouteReason.ProviderUnavailable -> Localization.swap_error_provider_unavailable
            SwapNoRouteReason.WalletScreened -> Localization.swap_error_wallet_screened
            SwapNoRouteReason.Unknown -> Localization.swap_error_quote_failed
        }
    }

enum class AmountInputSide {
    Sell,
    Buy,
    ;

    val flipped: AmountInputSide
        get() = when (this) {
            Sell -> Buy
            Buy -> Sell
        }
}

/**
 * How long the screen waits before spending a quote, and how long that quote stays good for. The
 * defaults are the product's; a caller passes its own only to test them.
 */
data class SwapTimings(
    val quoteDebounce: Duration = 500.milliseconds,
    val quoteTtl: Duration = 30.seconds,
    val failedQuoteRetryDelay: Duration = 5.seconds,
    val quoteTick: Duration = 250.milliseconds,
    val noticeDuration: Duration = 4.seconds,
)

data class AmountInput(
    val text: String = "",
    val inFiat: Boolean = false,
    val isMax: Boolean = false,
    val side: AmountInputSide = AmountInputSide.Sell,
    val maxUnits: BigInteger? = null,
    val toggle: AmountToggle? = null,
)

sealed interface SwapButton {
    data object Sending : SwapButton
    data object Done : SwapButton
    data class Swap(val enabled: Boolean) : SwapButton
    data object FeeUnpaid : SwapButton
    data object EnterAmount : SwapButton
    data class Minimum(val amount: String) : SwapButton
    data object InsufficientBalance : SwapButton
    data object StatusUnknown : SwapButton

    val isEnabled: Boolean
        get() = this is Swap && enabled

    val isTappable: Boolean
        get() = isEnabled || this == FeeUnpaid
}

interface SwapState : MviViewState {
    val isReceiveAmountEditable: Boolean
    val sendsInPlace: Boolean

    // A legacy wallet swaps one fixed pair: the assets can trade places but not be changed.
    val isPairLocked: StateFlow<Boolean>

    val initState: StateFlow<SwapInitState>
    val zeroFeeRaffleId: StateFlow<String?>

    val sellAsset: StateFlow<AssetEntity?>
    val buyAsset: StateFlow<AssetEntity?>
    val sellAccount: StateFlow<AccountWithDetails?>
    val buyAccount: StateFlow<AccountWithDetails?>
    val sellBalance: StateFlow<String?>
    val buyBalance: StateFlow<String?>

    val amountInput: StateFlow<AmountInput>
    val sellValidatedAmount: StateFlow<BaseUnit?>
    val buyValidatedAmount: StateFlow<BaseUnit?>
    val insufficientFunds: StateFlow<Boolean>

    val slippage: StateFlow<SwapSlippage?>
    val selectedSlippageBps: StateFlow<Int?>

    val quote: StateFlow<DisplayQuote?>
    val quoteErrorMessageRes: StateFlow<Int?>
    val quoteTimer: StateFlow<Float?>
    val isLoading: StateFlow<Boolean>

    val continueEnabled: StateFlow<Boolean>
    val continueLoading: StateFlow<Boolean>
    val send: StateFlow<SwapSend?>
    val sendHoldsQuote: StateFlow<Boolean>

    val feeLine: StateFlow<SwapFeeLine>
    val receiveImpact: StateFlow<PriceImpact?>
    val swapButton: StateFlow<SwapButton>
    val notice: StateFlow<String?>
    val lastQuote: StateFlow<DisplayQuote?>
    val shownQuote: StateFlow<DisplayQuote?>
}

sealed interface SwapAction : MviAction {

    sealed interface Amount : SwapAction {
        data class SetSell(val value: String) : Amount
        data class SetBuy(val value: String) : Amount
        data object Max : Amount
        data class Percent(val percent: Int) : Amount
        data object ToggleCurrency : Amount
    }

    /**
     * What a max sale sells instead of the max asked for: what its fee, paid in the asset sold, leaves.
     * From the send attempt [id], and dropped once another has taken its place.
     */
    data class FitMax(val id: Int, val units: BigInteger) : SwapAction

    sealed interface Asset : SwapAction {
        data class SelectSell(val value: AssetEntity) : Asset
        data class SelectBuy(val value: AssetEntity) : Asset
        data object Swap : Asset
    }

    data class SelectSlippage(val bps: Int) : SwapAction
    data object Requote : SwapAction
    data object Retry : SwapAction
    data object Continue : SwapAction
    data object CloseDetails : SwapAction
    data class SendProgress(val id: Int, val phase: SwapSendPhase, val message: String?) : SwapAction
    data class Prepared(val id: Int, val ready: Boolean) : SwapAction
    data class SendDisposed(val id: Int) : SwapAction
    data class FeePlanned(val id: Int, val plan: SwapFeePlan) : SwapAction
    data class FeePicked(val id: Int, val feeId: String?) : SwapAction
    data object CloseShortage : SwapAction
    data object FeeRowTapped : SwapAction
    data object PromoShown : SwapAction
}

private val AmountInputSide.quoteDirection: SwapQuoteDirection
    get() = when (this) {
        AmountInputSide.Sell -> SwapQuoteDirection.ExactInput
        AmountInputSide.Buy -> SwapQuoteDirection.ExactOutput
    }

class SwapFeature(
    private val initialRoute: SwapRoutes.Swap,
    private val context: Context,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val swapRepo: SwapRepository,
    private val raffleRepository: RaffleRepository,
    private val appLifecycle: AppLifecycleProvider,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val legacyAccounts: LegacySwapAccounts,
    private val api: API,
    private val batteryRepository: BatteryRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val timings: SwapTimings = SwapTimings(),
    // The quote TTL is the one thing here that measures real elapsed time; injecting the clock is
    // what lets a test run those seconds without spending them.
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : GraphViewModel<SwapState, SwapAction>(), SwapState {

    private val relay = MviRelay<SwapEvent>()
    val events = relay.events

    val utm: UtmParameters
        get() = initialRoute.utm

    init {
        bgScope.launch {
            swapRepo.swapCompleted.collect { relay.emit(SwapEvent.Completed(it)) }
        }

        // MAX reads the network fees already loaded and never waits for them: they load as the screen
        // opens, and each MAX refreshes them (a request only once they are older than their TTL) for the next.
        bgScope.launch {
            swapRepo.loadNetworkFees()
            actions
                .filter { it is SwapAction.Amount.Max || it is SwapAction.Amount.Percent }
                .collect { swapRepo.loadNetworkFees() }
        }
    }

    override val isReceiveAmountEditable: Boolean = WalletFeature.SwapExactOutput.isEnabled

    // One-click swap needs both: the remote rollout flag and the boot config, which can turn it off on its own.
    override val sendsInPlace: Boolean = WalletFeature.SwapRedesign.isEnabled && BootFlags.isOneClickSwapEnabled

    // The wallet the screen swaps for, and which swap it makes: a multichain wallet the backend
    // knows, or a legacy one it never registered, which swaps GRAM ↔ USDT TRC20 on its own addresses.
    private sealed interface SwapWallet {
        val id: String

        // The id the swap API knows the wallet by; null for a wallet it never registered.
        val registeredId: String?

        data class Multichain(override val id: String) : SwapWallet {
            override val registeredId: String get() = id
        }

        data class Legacy(val wallet: LegacySwapWallet) : SwapWallet {
            override val id: String get() = wallet.id
            override val registeredId: String? get() = null
        }
    }

    // Wallet: Retry re-runs the lookup, a null payload marks it as failed. Everything that reads the
    // backend or the balances hangs off this node, so a legacy wallet never makes a multichain call.
    private val retryAttempt = actions
        .on<SwapAction.Retry>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val wallet = retryAttempt
        .transformStateLatest { loadWallet() }
        .cacheStateWithLoading()

    // The wallet a lookup settled on. A Retry that finds the same wallet again is no change: nothing
    // that hangs off it reloads for it.
    private val lookedUpWallet = wallet
        .filterAndMapStateData()
        .distinctUntilChanged()

    // Locked until the wallet is known to be multichain: an unknown wallet may be a legacy one.
    override val isPairLocked = wallet
        .map { it !is KState.Data || it.state !is SwapWallet.Multichain }
        .cacheState(initialValue = true)

    // Raffle perk line under the top-bar title while the offer's zero-fee
    // window is active; tapping the title opens that raffle. Hidden once the
    // user has migrated (joined), per the design annotation.
    override val zeroFeeRaffleId = lookedUpWallet
        .flatMapLatestCatching { wallet ->
            // The raffle perk is a multichain swap's; a legacy swap does not take part.
            if (wallet !is SwapWallet.Multichain) {
                return@flatMapLatestCatching flowOf(null)
            }

            raffleRepository.getRafflesFlow(wallet.id, forced = false)
                .map { raffles ->
                    raffles.firstOrNull { raffle ->
                        raffle.status == RaffleEntity.Status.NotJoined &&
                            raffle.progress?.zeroFeeEndsAt?.isAfter(OffsetDateTime.now()) == true
                    }?.id
                }
        }
        .cacheState(initialValue = null)

    // Config: read for the wallet the lookup settled on, a null payload marks it as failed. Retry is
    // an input of its own: a config that failed for a wallet that did not is read again, and the
    // lookup finding that same wallet does not read it a second time.
    private data class ReadyConfig(
        val sell: AssetEntity,
        val buy: AssetEntity,
        val config: SwapConfig,
    )

    private val configState = combine(lookedUpWallet, retryAttempt) { wallet, _ -> wallet }
        .transformStateLatest { wallet -> wallet?.let { loadConfig(it) } }
        .cacheStateWithLoading()

    override val initState = configState
        .map { state ->
            when {
                state is KState.Loading -> SwapInitState.Loading
                state.state == null -> SwapInitState.Error
                else -> SwapInitState.Ready
            }
        }
        .cacheState(initialValue = SwapInitState.Loading)

    // Pair: sell/buy are one node because selections interact — picking the asset that already sits
    // on the other side swaps them, so the two never collide. It holds what was picked and nothing
    // read for it: an asset is identified by its id, and the accounts are a node of their own.
    // Config only fills the sides that have no user selection yet. [AssetPair.flipped] marks the
    // transitions that traded the sides, which the amount node needs: the entered amount follows its
    // token.
    private data class AssetPair(
        val sell: AssetEntity? = null,
        val buy: AssetEntity? = null,
        val flipped: Boolean = false,
    ) {
        val assets: List<AssetEntity> get() = listOfNotNull(sell, buy)
    }

    // Commands of the pair node: UI actions arrive wrapped in [Action]; [Defaults] is an internal
    // edge — config fills the sides that have no user selection yet.
    private sealed interface AssetCmd {
        data class Action(val value: SwapAction.Asset) : AssetCmd
        data class Defaults(val sell: AssetEntity, val buy: AssetEntity) : AssetCmd
    }

    private val assetPair = merge(
        configState
            .filterAndMapStateData()
            .filterNotNull()
            .map { AssetCmd.Defaults(sell = it.sell, buy = it.buy) },
        // A locked pair only trades places.
        actions
            .on<SwapAction.Asset>()
            .withLatestFrom(isPairLocked) { action, locked ->
                action.takeIf { it is SwapAction.Asset.Swap || !locked }
            }
            .filterNotNull()
            .map { AssetCmd.Action(it) },
    )
        .runningFold(AssetPair(), ::reduceAssets)
        .cacheState(initialValue = AssetPair())

    override val sellAsset = assetPair
        .map { it.sell }
        .cacheState(initialValue = null)

    override val buyAsset = assetPair
        .map { it.buy }
        .cacheState(initialValue = null)

    // Every balance refresh moves the tick on; the account nodes re-read on it like on any other
    // input. Counting rather than relaying the trigger makes its replayed value harmless: it is
    // already counted by the time anything reads.
    private val refreshTick = accountRepo.refreshTrigger
        .runningFold(0) { tick, _ -> tick + 1 }
        .cacheState(initialValue = 0)

    // Accounts: one per asset of the pair, not per side. Trading places changes no asset, so it
    // re-reads nothing and each balance moves with its token; an asset that joins the pair is read,
    // one that leaves is dropped along with any read still in flight.
    private val accountsByAsset = lookedUpWallet
        .flatMapLatestCatching { wallet ->
            if (wallet == null) {
                return@flatMapLatestCatching flowOf(emptyMap())
            }

            assetPair
                .map { it.assets }
                .flatMapPerKey(key = { it.id }) { asset -> accountState(wallet, asset) }
        }
        .cacheState(initialValue = emptyMap())

    // A side is Loading until its asset is read, never showing the account of the asset it replaced;
    // afterwards a refresh replaces the account in place.
    private val sellAccountState = combine(sellAsset, accountsByAsset, ::sideState)
        .cacheStateWithLoading()

    private val sellAccountLoading = sellAccountState.isLoading()

    override val sellAccount = sellAccountState
        .map { it.state }
        .cacheState(initialValue = null)

    private val buyAccountState = combine(buyAsset, accountsByAsset, ::sideState)
        .cacheStateWithLoading()

    private val buyAccountLoading = buyAccountState.isLoading()

    override val buyAccount = buyAccountState
        .map { it.state }
        .cacheState(initialValue = null)

    override val sellBalance: StateFlow<String?> = sellAccount
        .map(::formatBalance)
        .cacheState(initialValue = null)

    override val buyBalance: StateFlow<String?> = buyAccount
        .map(::formatBalance)
        .cacheState(initialValue = null)

    // Both sides and the wallet they belong to, in one node so the reducers and the quote sample
    // them together. A side's account is only ever one that was read for its asset; a side still
    // reading its asset says so, so a missing account is not taken for one without a rate.
    private data class Accounts(
        val wallet: SwapWallet?,
        val sell: AccountWithDetails?,
        val buy: AccountWithDetails?,
        val sellLoading: Boolean = false,
        val buyLoading: Boolean = false,
    ) {
        fun of(side: AmountInputSide): AccountWithDetails? = when (side) {
            AmountInputSide.Sell -> sell
            AmountInputSide.Buy -> buy
        }

        fun isLoading(side: AmountInputSide): Boolean = when (side) {
            AmountInputSide.Sell -> sellLoading
            AmountInputSide.Buy -> buyLoading
        }
    }

    private val accounts = combine(lookedUpWallet, sellAccountState, buyAccountState) { wallet, sell, buy ->
        Accounts(
            wallet = wallet,
            sell = sell.state,
            buy = buy.state,
            sellLoading = sell is KState.Loading,
            buyLoading = buy is KState.Loading,
        )
    }
        .cacheState(initialValue = Accounts(wallet = null, sell = null, buy = null))

    // Amount: isMax is sticky only while the amount MAX produced stays untouched — a manual edit,
    // a sell-asset change or a flip makes it stale, so every such edge clears it. The sampled
    // accounts feed the token/fiat converter.

    // Commands of the amount node: UI actions arrive wrapped in [Action], the rest are internal
    // edges merged from repo/graph flows, never sent by the UI.
    private sealed interface AmountCmd {
        data class Action(val value: SwapAction.Amount) : AmountCmd
        data object Reset : AmountCmd
        data object ClearMax : AmountCmd
        data object FlipSide : AmountCmd
        data object SyncRate : AmountCmd
        data class Fit(val units: BigInteger) : AmountCmd
    }

    override val amountInput = merge(
        actions
            .on<SwapAction.Amount>()
            .map { AmountCmd.Action(it) },
        swapRepo.swapCompleted
            .map { AmountCmd.Reset },
        sellAsset
            .distinctUntilChangedBy { it?.id }
            .map { AmountCmd.ClearMax },
        assetPair
            .filter { it.flipped }
            .map { AmountCmd.FlipSide },
        accounts
            .map { AmountCmd.SyncRate },
        // Only the attempt on screen fits the max, or the one that just gave way to be quoted again:
        // one already replaced was priced for another amount.
        actions
            .on<SwapAction.FitMax>()
            .filter { it.id == send.value?.id || it.id == sendNode.value.requoted?.id }
            .map { AmountCmd.Fit(it.units) },
    )
        .withLatestFrom(accounts) { cmd, accounts -> cmd to accounts }
        .runningFold(AmountInput()) { input, (cmd, accounts) -> reduceAmount(input, cmd, accounts) }
        .cacheState(initialValue = AmountInput())

    // A MAX the fee reserve leaves nothing for changes no input (see [maxAmount]); without a word
    // it looks like a dead button on a wallet holding less than the reserve, so the user is told
    // what to enter instead. A side-effect node, like the requote timer: the reducer stays pure.
    private val maxBelowReserve = actions
        .on<SwapAction.Amount>()
        .filter { it is SwapAction.Amount.Max || it is SwapAction.Amount.Percent }
        .withLatestFrom(accounts) { _, accounts -> accounts.of(AmountInputSide.Sell) }
        .filterNotNull()
        .onEach { account ->
            val asset = account.asset.value
            val reserve = swapRepo.getMaxReserve(asset)
            if (!reserve.isPositive || maxSellAmount(account, reserve) != null) {
                return@onEach
            }

            // Only the TON coin's reserve is a minimum to ask for; another coin's is the gas it lacks.
            if (swapRepo.getMinimumAmount(asset).isPositive) {
                val reserveText = Formatter.formatNumber(
                    value = reserve.toDisplayUnit().value,
                    options = Formatter.defaultOptions,
                )
                relay.emit(SwapEvent.MaxBelowReserve(reserve = reserveText, symbol = account.asset.symbol))
            } else {
                relay.emit(SwapEvent.MaxBelowFee(symbol = account.asset.symbol))
            }
        }
        .cacheState()

    // Slippage (per sell-chain config; the selected value feeds the quote request). null means
    // "use the server default"; a slippage change (chain switch) resets the choice back to it. It
    // reads the config in effect, the previous one while a Retry reloads it, so a reload that
    // brings the same options back changes nothing.
    override val slippage = combine(
        configState
            .map { it.state },
        sellAsset,
        transform = { config, asset ->
            val ready = config ?: return@combine null
            asset?.valueOrNull?.let { ready.config.slippage(it.chain) }
        }
    )
        .cacheState(initialValue = null)

    override val selectedSlippageBps = merge(
        slippage
            .map { it?.defaultBps },
        actions
            .on<SwapAction.SelectSlippage>()
            .map { it.bps },
    )
        .cacheState(initialValue = null)

    // Quote: any input change re-enters the loader, whose leading delay doubles as the debounce
    // (Loading is emitted before it, so the spinner covers the debounce window too). Requote
    // arrives from the countdown below through the action bus.
    private data class QuoteContext(
        val sell: AccountWithDetails,
        val buy: AccountWithDetails,
        val amount: BaseUnit,
        val direction: SwapQuoteDirection,
        val slippageBps: Int?,
        val walletId: String?,
        val attempt: Int,
    ) {
        // BaseUnit and the account rate have no value equality, so the node dedupes on this key:
        // everything the request is built from and nothing else — a balance or rate refresh that
        // leaves the request identical must not spend a quote.
        val inputKey: String = listOf(
            sell.asset.id,
            sell.data.displayAddress,
            buy.asset.id,
            buy.data.displayAddress,
            amount.value.toString(),
            direction,
            slippageBps,
            walletId,
        ).joinToString("|")

        val key: String = "$inputKey|$attempt"
    }

    private data class QuoteOutcome(
        val inputKey: String,
        val result: KResult<DisplayQuote, SwapQuoteError>,
    )

    private enum class QuoteFetch { Idle, Loading, Refreshing }

    private val requoteAttempt = actions
        .on<SwapAction.Requote>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    // Read accounts only: while a side reads its new asset the context drops to null, so a swap can
    // never be quoted against the pair it just left. [isLoading] keeps covering the screen meanwhile.
    private val quoteContext = combine(
        accounts,
        amountInput,
        selectedSlippageBps,
        requoteAttempt,
        transform = { accounts, input, slippageBps, attempt ->
            val wallet = accounts.wallet ?: return@combine null
            val sell = accounts.of(AmountInputSide.Sell) ?: return@combine null
            val buy = accounts.of(AmountInputSide.Buy) ?: return@combine null
            val amount = parsePositiveAmount(accounts.of(input.side), input.text, input.inFiat)
                ?: return@combine null

            QuoteContext(
                sell = sell,
                buy = buy,
                amount = amount,
                direction = input.side.quoteDirection,
                slippageBps = slippageBps,
                walletId = wallet.registeredId,
                attempt = attempt,
            )
        }
    )
        .cacheState(initialValue = null)

    private val quoteState = quoteContext
        .distinctUntilChangedBy { it?.key }
        .transformStateLatest { ctx ->
            if (ctx == null) {
                return@transformStateLatest null
            }

            delay(timings.quoteDebounce)
            QuoteOutcome(inputKey = ctx.inputKey, result = fetchQuote(ctx))
        }
        .cacheStateWithLoading()

    private val quoteLoading = quoteState.isLoading()

    override val quote = quoteState
        .mapStateDataValueOrNull()
        .map { it?.result?.getOrNull() }
        .cacheState(initialValue = null)

    private data class KeptQuote(val inputKey: String? = null, val quote: DisplayQuote? = null)

    override val lastQuote = quoteState
        .runningFold(KeptQuote()) { kept, state ->
            val outcome = state.state ?: return@runningFold KeptQuote()
            val error = (outcome.result as? KResult.Err)?.value
            when {
                sendsInPlace && error is SwapQuoteError.Failed && error.isOffline && outcome.inputKey == kept.inputKey -> kept
                else -> KeptQuote(outcome.inputKey, outcome.result.getOrNull())
            }
        }
        .map { it.quote }
        .cacheState(initialValue = null)

    private val quoteFetch = combine(quoteState, quoteContext) { state, ctx ->
        when {
            state !is KState.Loading || ctx == null -> QuoteFetch.Idle
            state.state?.inputKey == ctx.inputKey -> QuoteFetch.Refreshing
            else -> QuoteFetch.Loading
        }
    }
        .cacheState(initialValue = QuoteFetch.Idle)

    private val quoteError = combine(quoteState, quoteFetch) { state, fetch ->
        (state.state?.result as? KResult.Err)?.value
            ?.takeIf { state is KState.Data || fetch == QuoteFetch.Refreshing }
    }
        .cacheState(initialValue = null)

    override val quoteErrorMessageRes = quoteError
        .map { it?.messageRes }
        .cacheState(initialValue = null)

    private val offlineToast = quoteError
        .map { sendsInPlace && it is SwapQuoteError.Failed && it.isOffline }
        .distinctUntilChanged()
        .filter { it }
        .onEach { relay.emit(SwapEvent.Offline) }
        .cacheState()

    override val shownQuote = combine(quote, lastQuote, quoteFetch, quoteError) { quote, last, fetch, error ->
        val offline = error is SwapQuoteError.Failed && error.isOffline
        quote ?: last.takeIf { sendsInPlace && (fetch == QuoteFetch.Idle || offline) }
    }
        .cacheState(initialValue = null)

    override val receiveImpact = shownQuote
        .map { quote -> priceImpactOf(quote?.quote?.priceImpactBps)?.takeIf { it.severity != PriceImpactSeverity.Normal } }
        .cacheState(initialValue = null)

    override val isLoading = anyOfFlows(quoteLoading, sellAccountLoading, buyAccountLoading)
        .cacheState(initialValue = false)

    // Validated amounts (for fiat formatting): the side the user did not fix is the quoted one.
    override val sellValidatedAmount = combine(sellAccount, amountInput, quote)
        { account, input, quote ->
            when (input.side) {
                AmountInputSide.Sell -> parsePositiveAmount(account, input.text, input.inFiat)
                AmountInputSide.Buy -> quote?.let { account?.asset?.value?.decimals?.baseUnit(it.quote.sourceBaseAmount) }
            }
        }
        .cacheState()

    override val buyValidatedAmount = combine(buyAccount, amountInput, quote)
        { account, input, quote ->
            when (input.side) {
                AmountInputSide.Sell -> parsePositiveAmount(account, quote?.buyAmount.orEmpty())
                AmountInputSide.Buy -> parsePositiveAmount(account, input.text, input.inFiat)
            }
        }
        .cacheState()

    override val insufficientFunds = combine(sellAccount, sellValidatedAmount)
        { account, amount -> account != null && amount != null && amount > account.unitBalance }
        .cacheState(initialValue = false)

    // Continue: samples everything the confirm screen needs; transformStateFirst drops re-taps
    // while the request is being prepared.
    private data class ContinueContext(
        val walletId: String,
        val quote: SwapQuote,
        val quoteReceivedAt: Long,
        val sell: AccountWithDetails,
        val buy: AccountWithDetails,
        val isMax: Boolean,
        val slippageBps: Int?,
    )

    private val continueContext = combine(
        quote,
        accounts,
        amountInput,
        selectedSlippageBps,
        transform = { quote, accounts, input, slippageBps ->
            val display = quote ?: return@combine null
            val wallet = accounts.wallet ?: return@combine null
            val sell = accounts.of(AmountInputSide.Sell) ?: return@combine null
            val buy = accounts.of(AmountInputSide.Buy) ?: return@combine null

            ContinueContext(
                walletId = wallet.id,
                quote = display.quote,
                quoteReceivedAt = display.receivedAt,
                sell = sell,
                buy = buy,
                isMax = input.isMax,
                slippageBps = slippageBps,
            )
        }
    )
        .cacheState(initialValue = null)

    private val continueState = actions
        .on<SwapAction.Continue>()
        .filter { !sendsInPlace }
        .withLatestFrom(continueContext) { _, ctx -> ctx }
        .transformStateFirst { ctx ->
            if (ctx == null) {
                return@transformStateFirst null
            }

            runCatching { buildConfirmRequest(ctx) }
                .fold(
                    onSuccess = { relay.emit(SwapEvent.Continue(it)) },
                    onFailure = { reportPrepareFailure(it) },
                )
        }
        .cacheStateWithEmpty()

    private val amountBlocker = combine(accounts, amountInput, sellValidatedAmount, insufficientFunds)
    { accounts, input, sellAmount, insufficient ->
        val minimum = accounts.of(AmountInputSide.Sell)?.let { sell ->
            swapRepo.getMinimumAmount(sell.asset.value)
                .takeIf { it.isPositive && sellAmount != null && sellAmount < it }
                ?.let { Formatter.formatShort(value = it.toDisplayUnit(), asset = sell.asset.value) }
        }
        when {
            parsePositiveAmount(accounts.of(input.side), input.text, input.inFiat) == null -> SwapButton.EnterAmount
            minimum != null -> SwapButton.Minimum(minimum)
            insufficient -> SwapButton.InsufficientBalance
            else -> null
        }
    }
        .cacheState(initialValue = SwapButton.EnterAmount)

    val amountBlocked = amountBlocker
        .map { it is SwapButton.Minimum || it is SwapButton.InsufficientBalance }
        .cacheState(initialValue = false)

    private val sendInputs = combine(sellAsset, buyAsset, amountInput, selectedSlippageBps) { sell, buy, input, bps ->
        SwapInputs(sell?.id, buy?.id, input, bps)
    }.cacheState()

    private val sendNode: StateFlow<SendNode> = merge(
        actions.on<SwapAction.Continue>()
            .filter { sendsInPlace }
            .withLatestFrom(continueContext, amountBlocker, quoteError) { _, ctx, blocker, error ->
                SendCmd.Continue(blocked = blocker != null || error != null, quoteReceivedAt = ctx?.quoteReceivedAt)
            },
        actions.on<SwapAction.CloseDetails>()
            .map { SendCmd.CloseDetails },
        continueContext
            .filterNotNull()
            .filter { sendsInPlace }
            .distinctUntilChangedBy { it.quoteReceivedAt }
            .withLatestFrom(sendInputs) { ctx, inputs -> SendCmd.Quoted(buildConfirmRequest(ctx), inputs) },
        actions.on<SwapAction.SendProgress>()
            .withLatestFrom(quote) { progress, quote -> SendCmd.Progress(progress, quote?.receivedAt) },
        quoteContext
            .map { it?.inputKey }
            .distinctUntilChanged()
            .map { SendCmd.InputKeyChanged },
        actions.on<SwapAction.Prepared>()
            .map { SendCmd.Prepared(it) },
        actions.on<SwapAction.SendDisposed>()
            .map { SendCmd.Disposed(it.id) },
        actions.on<SwapAction.FeePlanned>()
            .map { SendCmd.FeePlanned(it) },
        actions.on<SwapAction.FeePicked>()
            .map { SendCmd.FeePicked(it) },
        actions.on<SwapAction.CloseShortage>()
            .map { SendCmd.CloseShortage },
        actions.on<SwapAction.FeeRowTapped>()
            .map { SendCmd.FeeRowTapped },
        sendInputs
            .filterNotNull()
            .map { SendCmd.InputsChanged(it) },
    )
        .runningFold(SendNode(), ::reduceSend)
        .cacheState(initialValue = SendNode())

    override val send: StateFlow<SwapSend?> = sendNode
        .map { it.attempt }
        .cacheState(initialValue = null)

    override val sendHoldsQuote = send
        .map { it != null && (it.phase != SwapSendPhase.Review || it.shortagePopup) }
        .cacheState(initialValue = false)

    override val swapButton = combine(send, amountBlocker, quoteFetch, quoteError, quote) { send, blocker, fetch, error, quote ->
        when {
            send?.phase == SwapSendPhase.Working -> SwapButton.Sending
            send?.phase == SwapSendPhase.Done -> SwapButton.Done
            send?.phase == SwapSendPhase.Blocked -> SwapButton.StatusUnknown
            blocker != null -> blocker
            error != null || fetch != QuoteFetch.Idle || quote == null -> SwapButton.Swap(enabled = false)
            send?.phase == SwapSendPhase.Retryable -> SwapButton.Swap(enabled = true)
            send == null || send.quoteReceivedAt != quote.receivedAt -> SwapButton.Swap(enabled = false)
            else -> when (val plan = send.plan) {
                SwapFeePlan.Preparing -> SwapButton.Swap(enabled = false)
                SwapFeePlan.Failed -> SwapButton.Swap(enabled = true)
                is SwapFeePlan.Ready -> when {
                    plan.summary.isUnpaid -> SwapButton.FeeUnpaid
                    else -> SwapButton.Swap(enabled = send.prepared || plan.summary == SwapFeeSummary.Unknown)
                }
            }
        }
    }
        .cacheState(initialValue = SwapButton.EnterAmount)

    override val notice = sendNode
        .map { it.noticeSeq to it.notice }
        .distinctUntilChanged()
        .transformLatest { (_, notice) ->
            emit(notice)
            if (notice != null) {
                delay(timings.noticeDuration)
                emit(null)
            }
        }
        .cacheState(initialValue = null)

    private val promoShownBefore = settingsRepository.swapBatteryPromoShown

    private val battery = combine(lookedUpWallet, refreshTick) { wallet, _ -> wallet.takeIf { sendsInPlace && !promoShownBefore } }
        .mapLatestCatching(onError = { L.e(it) }) { wallet ->
            (wallet as? SwapWallet.Multichain)?.let { loadBattery(it.id) }
        }
        .cacheState(initialValue = null)

    private val showsPromo = combine(battery, sellAsset, amountBlocker, quoteFetch) { battery, sell, blocker, fetch ->
        showsBatteryPromo(
            shownBefore = promoShownBefore,
            battery = battery,
            sellAsset = sell?.valueOrNull,
            amountEmpty = blocker == SwapButton.EnterAmount,
            quoteIdle = fetch == QuoteFetch.Idle,
        )
    }
        .cacheState(initialValue = false)

    private val promoShown = actions
        .on<SwapAction.PromoShown>()
        .onEach { settingsRepository.swapBatteryPromoShown = true }
        .cacheState()

    private val swapClicked = send
        .map { it?.takeIf { attempt -> attempt.clicked && attempt.phase == SwapSendPhase.Working }?.id }
        .distinctUntilChanged()
        .filterNotNull()
        .withLatestFrom(assetPair, amountInput) { _, pair, input -> pair to input.isMax }
        .onEach { (pair, isMax) ->
            val sell = pair.sell ?: return@onEach
            val buy = pair.buy ?: return@onEach
            AnalyticsHelper.Default.eventsTaggedWith(utm).swapsNative.swapClick(
                type = SwapsNativeType.Native,
                walletMode = SwapsNativeWalletMode.Multi,
                assetFrom = sell.id,
                assetTo = buy.id,
                isMax = isMax,
            )
        }
        .cacheState()

    private val shownPlan = combine(send, shownQuote) { send, quote ->
        send?.plan?.takeIf { quote != null && send.quoteReceivedAt == quote.receivedAt }
    }
        .cacheState(initialValue = null)

    private val feeLineTappable = combine(swapButton, notice) { button, notice -> notice == null && button.isTappable }
        .cacheState(initialValue = false)

    override val feeLine = combine(
        combine(quoteFetch, quoteError, shownQuote, ::Triple),
        shownPlan,
        showsPromo,
        amountBlocker,
        feeLineTappable,
    ) { (fetch, error, quote), plan, promo, blocker, tappable ->
        feeLine(
            fetching = fetch != QuoteFetch.Idle,
            quoteError = error,
            quote = quote,
            plan = plan,
            promo = promo,
            amountEmpty = blocker == SwapButton.EnterAmount,
            tappable = tappable,
        )
    }
        .cacheState(initialValue = SwapFeeLine.Hidden)

    override val continueLoading = continueState
        .isLoading()
        .cacheState(initialValue = false)

    override val continueEnabled = combine(
        noneOfFlows(isLoading, continueLoading, insufficientFunds),
        quote,
        send,
        transform = { idle, quote, attempt ->
            idle && quote != null && (attempt == null || attempt.phase == SwapSendPhase.Retryable)
        }
    )
        .cacheState(initialValue = false)

    private val appInBackground = appLifecycle.observeWithoutGrace
        .map { foreground -> !foreground }
        .cacheState(initialValue = false)

    // Timer: runs for every completed request (a failed one is retried sooner); paused while the
    // confirm request is being prepared, so a requote can't race the continue.
    private val quoteCountdown = quoteState
        .filterStateData()
        .countdown(
            tick = timings.quoteTick,
            running = noneOfFlows(continueLoading, sendHoldsQuote, appInBackground),
            timeSource = timeSource,
        ) { data ->
            when (val result = data.state?.result) {
                null -> null
                is KResult.Ok -> when (sendsInPlace) {
                    true -> quoteLifetime(arrivalLifetimeMillis = result.value.quote.lifetimeMillis)
                    false -> timings.quoteTtl
                }
                is KResult.Err -> when (result.value) {
                    is SwapQuoteError.NotFound -> timings.quoteTtl
                    is SwapQuoteError.Failed -> timings.failedQuoteRetryDelay
                }
            }
        }
        .onEach { countdown ->
            if (countdown != null && countdown.finished) {
                sendAction(SwapAction.Requote)
            }
        }
        .cacheState(initialValue = null)

    override val quoteTimer = quoteCountdown
        .map { it?.progress }
        .cacheState(initialValue = null)

    // Loaders
    private suspend fun loadWallet(): SwapWallet? {
        val walletId = selectedWalletId()
            ?: return null

        // Not a legacy wallet the swap signs for is not yet a multichain one: a legacy wallet it cannot
        // sign for has no swap at all, and fails the screen instead of asking the backend for a wallet
        // it never registered.
        return try {
            legacyAccounts.find(walletId)?.let { SwapWallet.Legacy(it) }
                ?: accountRepo.getWallet(walletId)?.let { SwapWallet.Multichain(walletId) }
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            null
        }
    }

    private suspend fun loadBattery(walletId: String): SwapBattery? {
        val wallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return null
        val config = api.getConfig(wallet.network)
        val allowed = !api.getConfig(TonNetwork.MAINNET).flags.disableBattery &&
            !config.batterySendDisabled
        if (!allowed) {
            return null
        }
        return SwapBattery(
            charges = batteryRepository.fetchCharges(wallet) ?: return null,
            relayedEvmChains = batteryRepository.getGaslessChains(wallet.network),
        )
    }

    private suspend fun loadConfig(wallet: SwapWallet): ReadyConfig? {
        val config = when (wallet) {
            is SwapWallet.Multichain -> loadSwapConfig(wallet.id)
            is SwapWallet.Legacy -> legacyConfig()
        }

        val sell = config.defaultPair.source
            ?: return null

        val buy = config.defaultPair.destination
            ?: return null

        return ReadyConfig(sell = sell, buy = buy, config = config)
    }

    private suspend fun loadSwapConfig(walletId: String): SwapConfig {
        return try {
            swapRepo.getConfig(
                walletId = walletId,
                fromAssetId = initialRoute.sellAssetId,
                toAssetId = initialRoute.buyAssetId,
            )
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            FallbackConfig
        }
    }

    // An asset's account, read past the cache when it joins the pair and again on every refresh.
    // Only that first read is a Loading; a refresh that cannot read the account keeps what it had,
    // and one that finds an account the first read missed fills it in.
    private fun accountState(wallet: SwapWallet, asset: AssetEntity): Flow<KState<AccountWithDetails?>> = refreshTick
        .mapLatestCatching(onError = { L.e(it) }) { resolveAccount(wallet, asset) }
        .runningFold<AccountWithDetails?, KState<AccountWithDetails?>>(KState.Loading()) { previous, account ->
            KState.Data(account ?: previous.state)
        }

    private suspend fun resolveAccount(wallet: SwapWallet, asset: AssetEntity): AccountWithDetails? {
        val resolved = when (wallet) {
            is SwapWallet.Multichain -> accountRepo.findAccount(wallet.id, asset.id, forceRefresh = true)
            is SwapWallet.Legacy -> legacyAccounts.findAccount(wallet.wallet, asset.id, refresh = true)
        } ?: return null

        // The side shows the asset the swap backend described: the wallet's own icon for it may be
        // blank or a different one.
        val image = asset.imageUrl
        if (image.isBlank() || resolved.asset.imageUrl == image) {
            return resolved
        }

        return resolved.copy(asset = resolved.asset.copy(imageUrl = image))
    }

    private suspend fun fetchQuote(ctx: QuoteContext): KResult<DisplayQuote, SwapQuoteError> {
        val found = try {
            val result = swapRepo.fetchQuote(
                sell = ctx.sell,
                buy = ctx.buy,
                amount = ctx.amount.value,
                slippageBps = ctx.slippageBps,
                direction = ctx.direction,
                walletId = ctx.walletId,
            )
            when (result) {
                is SwapQuoteResult.Found -> result.quote
                is SwapQuoteResult.NoRoute -> return KResult.Err(SwapQuoteError.NotFound(result.reason))
            }
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            return KResult.Err(SwapQuoteError.Failed(isOffline = !networkMonitor.isOnlineFlow.first()))
        }
        val receivedAt = SystemClock.elapsedRealtime()
        val quote = found.copy(
            receivedAt = receivedAt,
            lifetimeMillis = found.expiresAtMillis?.let { it - System.currentTimeMillis() },
        )

        val sellDisplay = ctx.sell.asset.value.decimals.baseUnit(quote.sourceBaseAmount).toDisplayUnit()
        val buyDisplay = ctx.buy.asset.value.decimals.baseUnit(quote.buyBaseAmount).toDisplayUnit()

        return KResult.Ok(
            DisplayQuote(
                quote = quote,
                sellAmount = sellDisplay.amountText(MAX_DISPLAY_SCALE),
                buyAmount = buyDisplay.amountText(MAX_DISPLAY_SCALE),
                rateLabel = swapRepo.formatRateLabel(quote, ctx.sell.asset.value, ctx.buy.asset.value).orEmpty(),
                receivedAt = receivedAt,
                minReceived = formatMinReceived(ctx.buy.asset.value, ctx.buy.rate?.value, quote.minimumBuyBaseAmount),
            )
        )
    }

    // Built off the main thread like every other loader here, so a second tap that lands while the
    // request is being prepared is dropped.
    private suspend fun buildConfirmRequest(ctx: ContinueContext): ConfirmRequest = withContext(bgDispatcher) {
        ConfirmRequest(
            data = CommonTransactionData(
                assetId = ctx.sell.asset.value.id,
                walletId = ctx.walletId,
            ),
            type = ConfirmType.Swap(
                sourceAmount = ctx.quote.sourceBaseAmount,
                destinationAssetId = ctx.buy.asset.value.id,
                isMax = ctx.isMax,
                slippageBps = ctx.slippageBps,
                // Hand the confirm screen the quote we already have so it can render
                // immediately and only re-request once its countdown expires.
                quote = ConfirmType.Swap.Quote(
                    routeId = ctx.quote.routeId,
                    buyAmount = ctx.quote.buyBaseAmount,
                    minBuyAmount = ctx.quote.minimumBuyBaseAmount,
                    providerTxId = ctx.quote.providerTxId,
                    slippageBps = ctx.quote.slippageBps,
                    priceImpactBps = ctx.quote.priceImpactBps,
                    provider = ctx.quote.provider.id,
                    receivedAt = ctx.quoteReceivedAt,
                    lifetimeMillis = ctx.quote.lifetimeMillis,
                    genericPayload = ctx.quote.genericPayload,
                ),
                oneClick = sendsInPlace,
            ),
            initiator = initialRoute.initiator,
            utm = initialRoute.utm,
        )
    }

    private suspend fun reportPrepareFailure(e: Throwable) {
        verifyError(e)
        L.e(e)
        relay.emit(SwapEvent.ShowError(e.message ?: context.getString(Localization.swap_error_prepare_failed)))
    }

    private suspend fun selectedWalletId(): String? = withContext(bgDispatcher) {
        oldAccount.getSelectedWalletId()
    }

    // The pair is fixed and has no config to read: the server picks the slippage.
    private fun legacyConfig(): SwapConfig {
        val pair = legacyAccounts.pair(initialRoute.sellAssetId, initialRoute.buyAssetId)
        return SwapConfig(
            defaultPair = SwapDefaultPair(source = pair.sell, destination = pair.buy),
            slippagePerChain = emptyMap(),
        )
    }

    // Reducers
    private fun reduceAssets(pair: AssetPair, cmd: AssetCmd): AssetPair = when (cmd) {
        is AssetCmd.Defaults -> AssetPair(
            sell = pair.sell ?: cmd.sell,
            buy = pair.buy ?: cmd.buy,
        )

        is AssetCmd.Action -> when (val action = cmd.value) {
            is SwapAction.Asset.SelectSell -> when (action.value.id) {
                pair.buy?.id -> AssetPair(sell = action.value, buy = pair.sell, flipped = true)
                else -> AssetPair(sell = action.value, buy = pair.buy)
            }

            is SwapAction.Asset.SelectBuy -> when (action.value.id) {
                pair.sell?.id -> AssetPair(sell = pair.buy, buy = action.value, flipped = true)
                else -> AssetPair(sell = pair.sell, buy = action.value)
            }

            is SwapAction.Asset.Swap -> AssetPair(sell = pair.buy, buy = pair.sell, flipped = true)
        }
    }

    private fun sideState(
        asset: AssetEntity?,
        accounts: Map<String, KState<AccountWithDetails?>>,
    ): KState<AccountWithDetails?> {
        return asset?.let { accounts[it.id] } ?: KState.Loading()
    }

    private fun reduceAmount(
        input: AmountInput,
        cmd: AmountCmd,
        accounts: Accounts,
    ): AmountInput = when (cmd) {
        is AmountCmd.Action -> when (val action = cmd.value) {
            is SwapAction.Amount.SetSell -> input.copy(
                text = action.value.sanitizeAmountInput(),
                isMax = false,
                side = AmountInputSide.Sell,
            )

            is SwapAction.Amount.SetBuy -> when {
                !isReceiveAmountEditable -> input
                else -> input.copy(
                    text = action.value.sanitizeAmountInput(),
                    isMax = false,
                    side = AmountInputSide.Buy,
                )
            }

            is SwapAction.Amount.ToggleCurrency -> toggleCurrency(input, accounts.of(input.side))
            is SwapAction.Amount.Max -> maxAmount(input, accounts.of(AmountInputSide.Sell), percent = 100)
            is SwapAction.Amount.Percent -> maxAmount(input, accounts.of(AmountInputSide.Sell), action.percent)
        }

        is AmountCmd.Fit -> fitAmount(input, accounts.of(AmountInputSide.Sell), cmd.units)

        is AmountCmd.Reset -> input.copy(text = "", isMax = false, side = AmountInputSide.Sell, toggle = null)

        is AmountCmd.ClearMax -> input.copy(isMax = false, toggle = null)

        // The pair traded places: the amount stays with its token, so the side it is fixed on
        // flips with it.
        is AmountCmd.FlipSide -> input.copy(isMax = false, side = flipSide(input.side), toggle = null)

        // Fiat input needs a rate: fall back to token input when the account loses it. A side still
        // reading a newly picked asset has no account yet, which is not a missing rate.
        is AmountCmd.SyncRate -> when {
            input.inFiat && !accounts.isLoading(input.side) && accounts.of(input.side)?.rate == null -> {
                input.copy(inFiat = false, isMax = false)
            }

            else -> input
        }
    }

    // Without the exact-output flag the receive side can never be the fixed one, so a flip keeps
    // the amount on the sell side.
    private fun flipSide(side: AmountInputSide): AmountInputSide = when {
        isReceiveAmountEditable -> side.flipped
        else -> AmountInputSide.Sell
    }

    private fun toggleCurrency(input: AmountInput, account: AccountWithDetails?): AmountInput {
        val converter = AmountInputConverter(account ?: return input, FORMAT_DECIMALS)
        if (!converter.hasRate) {
            return input
        }

        val toggle = converter.toggle(input.text, input.isMax, !input.inFiat, input.toggle)
        return AmountInput(
            text = toggle.toggledText,
            inFiat = toggle.toFiat,
            isMax = toggle.toggledIsMax,
            side = input.side,
            maxUnits = input.maxUnits,
            toggle = toggle,
        )
    }

    private fun maxAmount(input: AmountInput, account: AccountWithDetails?, percent: Int): AmountInput {
        account ?: return input

        // Nothing above the reserve: the input stays as it was, and [maxBelowReserve] says why.
        val max = maxSellAmount(account, swapRepo.getMaxReserve(account.asset.value))
            ?.let { account.asset.value.decimals.baseUnit(it.value * BigInteger.fromInt(percent) / BigInteger.fromInt(100)) }
            ?: return input

        return maxInput(input, account, max, isMax = percent == 100)
    }

    // A max its fee, paid in the asset sold, leaves no room for: what the send says is left to sell is
    // the max from here on. Only a max still on screen is fitted, and only down.
    private fun fitAmount(input: AmountInput, account: AccountWithDetails?, units: BigInteger): AmountInput {
        val max = input.maxUnits
        if (account == null || !input.isMax || max == null || units >= max) {
            return input
        }

        return maxInput(input, account, account.asset.value.decimals.baseUnit(units), isMax = true)
    }

    private fun maxInput(input: AmountInput, account: AccountWithDetails, max: BaseUnit, isMax: Boolean): AmountInput {
        val converter = AmountInputConverter(account, FORMAT_DECIMALS)
        var inFiat = input.inFiat
        var text = converter.maxText(max, inFiat) ?: return input

        if (inFiat && converter.positiveTokenUnit(text, inFiat = true) == null) {
            inFiat = false
            text = converter.maxText(max, false) ?: return input
        }

        return AmountInput(text = text, inFiat = inFiat, isMax = isMax, maxUnits = max.value.takeIf { isMax })
    }

    // What MAX sells: the balance less the reserve [SwapRepository.getMaxReserve] keeps for the swap's
    // network fee. Null when nothing is left.
    private fun maxSellAmount(account: AccountWithDetails, reserve: BaseUnit): BaseUnit? {
        return (account.unitBalance - reserve).takeIf { it.isPositive }
    }

    private fun parsePositiveAmount(
        account: AccountWithDetails?,
        input: String,
        inFiat: Boolean = false,
    ): BaseUnit? {
        if (account == null || input.isBlank()) {
            return null
        }

        return AmountInputConverter(account, FORMAT_DECIMALS)
            .positiveTokenUnit(input, inFiat)
    }

    private fun formatBalance(account: AccountWithDetails?): String? {
        val balance = account?.displayBalance ?: return null
        return if (settingsRepository.hiddenBalances) {
            HIDDEN_BALANCE
        } else {
            Formatter.formatShort(value = balance)
        }
    }

    private companion object {
        const val FORMAT_DECIMALS = 8
        const val MAX_DISPLAY_SCALE = 8

        // Both sides are native coins, so they carry their assets without the `/asset` endpoint.
        private val FallbackConfig = SwapConfig(
            defaultPair = SwapDefaultPair(
                source = Chain.Ethereum.Mainnet.toAssetEntity(),
                destination = Chain.Ton.Mainnet.toAssetEntity(),
            ),
            slippagePerChain = emptyMap(),
        )
    }

}

/*
 *   Retry ──► [fold] retryAttempt ──► [latest] wallet ──┬──► isPairLocked
 *                                                       └──► [dataOnly, distinct] lookedUpWallet?
 *
 *                          lookedUpWallet? ──► [latest] zeroFeeRaffleId?
 *
 *                          lookedUpWallet? ──┐
 *                             retryAttempt ──┴──► [latest] configState ──► initState
 *
 *                   configState [dataOnly] ──┐
 *   Asset.SelectSell / SelectBuy / Swap ─────┤
 *                         isPairLocked ⋯─────┴──► [fold] assetPair ──┬──► sellAsset?
 *                                                                    └──► buyAsset?
 *
 *   refreshTrigger ──► [fold] refreshTick ─────────────────────┐
 *   lookedUpWallet? ──► [latest] ──┐                           │
 *          assetPair [assets] ─────┴──► [per asset id] ────────┴──► accountsByAsset
 *
 *        sellAsset? ──┐
 *   accountsByAsset ──┴──► sellAccountState ──┬──► [loading] sellAccountLoading
 *                                             └──► sellAccount? ──► sellBalance?
 *
 *         buyAsset? ──┐
 *   accountsByAsset ──┴──► buyAccountState ──┬──► [loading] buyAccountLoading
 *                                            └──► buyAccount? ──► buyBalance?
 *
 *    lookedUpWallet? ──┐
 *   sellAccountState ──┼──► accounts
 *    buyAccountState ──┘
 *
 *   Amount.SetSell / SetBuy / Max / Percent / ToggleCurrency ──┐
 *                                    swapRepo.swapCompleted ───┤
 *                            sellAsset? [distinct by id] ──────┼──► [fold] amountInput
 *                               assetPair [flipped only] ──────┤
 *                                                  accounts ───┤
 *                             FitMax [the attempt on screen] ──┤
 *                                                  accounts ⋯──┘
 *
 *   Amount.Max / Percent ──┐
 *             accounts ⋯───┴──► maxBelowReserve ╌╌► MaxBelowReserve / MaxBelowFee event
 *
 *   configState [in effect] ──┐
 *                 sellAsset? ──┴──► slippage? ──┐
 *                                               ├──► selectedSlippageBps?
 *                              SelectSlippage ──┘
 *
 *                     accounts ──┐
 *                  amountInput ──┤
 *         selectedSlippageBps? ──┼──► quoteContext? ──► [distinct by key, debounce, latest] quoteState
 *   Requote ──► [fold] requoteAttempt ─┘
 *                                    quoteState [loading] ──► quoteLoading
 *                           quoteState [dataOrNull, okOrNull] ──► quote?
 *                  quoteState [fold, okOrNull, kept offline] ──► lastQuote?
 *
 *      quoteState ──┐
 *   quoteContext? ──┴──► quoteFetch ──► [data or refreshing, err] quoteError? ──┬──► quoteErrorMessageRes?
 *                                                                               └──► offlineToast ╌╌► Offline event
 *
 *       quote? ──┐
 *   lastQuote? ──┼──► shownQuote? ──► receiveImpact?
 *   quoteFetch ──┤
 *  quoteError? ──┘
 *
 *         quoteLoading ──┐
 *   sellAccountLoading ──┼── [any] ──► isLoading
 *    buyAccountLoading ──┘
 *
 *   sellAccount? ──┐
 *    amountInput ──┼──► sellValidatedAmount? ──┐
 *         quote? ──┘                           ├──► insufficientFunds
 *                            sellAccount? ─────┘
 *
 *    buyAccount? ──┐
 *    amountInput ──┼──► buyValidatedAmount?
 *         quote? ──┘
 *
 *   quote? / accounts / amountInput / selectedSlippageBps? ──► continueContext?
 *
 *            Continue [flag off] ──┐
 *               continueContext? ⋯─┴──► [first] continueState?
 *                                       continueState? [loading] ──► continueLoading
 *                                                  continueState? ──► events
 *                                          swapRepo.swapCompleted ──► events
 *
 *   sellAsset? / buyAsset? / amountInput / selectedSlippageBps? ──► sendInputs?
 *
 *   accounts / amountInput / sellValidatedAmount? / insufficientFunds ──► amountBlocker? ──► amountBlocked
 *
 *                       Continue [flag on] ──┐
 *   continueContext? / amountBlocker? /      │
 *               quoteError? ⋯────────────────┴──► [blocked] ──┐
 *   continueContext? [flag on, new quote] ──┐                 │
 *                         sendInputs? ⋯─────┴──► [request] ───┤
 *                   SendProgress ──┐                          │
 *                         quote? ⋯─┴──► [quoted at] ──────────┤
 *   quoteContext? [inputKey, distinct] ──► [input key] ───────┤
 *   CloseDetails / FeeRowTapped / Prepared / FeePlanned /     │
 *   FeePicked / CloseShortage / SendDisposed ─────────────────┤
 *                                         sendInputs? ────────┴──► [fold] sendNode ──┬──► send?
 *                                                                                    └──► [latest, delay] notice?
 *
 *   [request] failure ╌╌► ShowError event
 *
 *   send? [non-null, not Review or popup shown] ──► sendHoldsQuote
 *
 *   send? [clicked, Working, distinct id] ──┐
 *                assetPair / amountInput ⋯──┴──► swapClicked ╌╌► swap_click analytics
 *
 *   send? / amountBlocker? / quoteFetch / quoteError? / quote? ──► swapButton
 *
 *   lookedUpWallet? ──┐
 *       refreshTick ──┴──► [latest, flag on] battery?
 *   battery? / sellAsset? / amountBlocker? / quoteFetch ──► showsPromo
 *   PromoShown ──► promoShown ╌╌► settingsRepository.swapBatteryPromoShown
 *
 *              send? / shownQuote? ──► shownPlan?
 *                swapButton / notice? ──► feeLineTappable
 *   quoteFetch / quoteError? / shownQuote? ──┐
 *      shownPlan? / showsPromo / amountBlocker? ┼──► feeLine
 *                          feeLineTappable ──┘
 *
 *   isLoading / continueLoading / insufficientFunds ── [none] ──┐
 *                                                      quote? ──┼──► continueEnabled
 *                                                       send? ──┘
 *
 *   continueLoading ──┐
 *    sendHoldsQuote ──┤
 *   appInBackground ──┴─ [none] ──┐
 *                                 ├──► [countdown] quoteCountdown? ──► quoteTimer?
 *     quoteState [dataOnly] ──────┘                      ╎
 *                                                        ╎
 *                                 Requote ◄╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘
 */