package com.tonapps.perps.screens.portfolio

import android.text.format.DateFormat
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsPortfolio
import com.tonapps.perps.data.PerpsPortfolioOrder
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatTrimmedUsdPrice
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.perpsMarketsPager
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.spendableBalance
import com.tonapps.perps.data.unrealizedPnlPercent
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsFundingRecovery
import com.tonapps.perps.funding.toDepositStatus
import com.tonapps.perps.region.PerpsRegionAccess
import com.tonapps.perps.screens.depositstatus.PerpsDepositBanner
import com.tonapps.perps.screens.depositstatus.PerpsDepositBannerState
import com.tonapps.perps.screens.depositstatus.PerpsDepositListItemUi
import com.tonapps.perps.screens.depositstatus.PerpsDepositRowUi
import com.tonapps.perps.screens.depositstatus.PerpsDepositStatusUi
import com.tonapps.perps.screens.depositstatus.PerpsDepositToast
import com.tonapps.perps.screens.depositstatus.nextRowChange
import com.tonapps.perps.screens.depositstatus.rowUi
import com.tonapps.perps.screens.depositstatus.toListItemUi
import com.tonapps.perps.screens.depositstatus.ticking
import com.tonapps.perps.screens.depositstatus.toStatusUi
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface PerpsPortfolioViewState : MviViewState {
    val portfolio: StateFlow<PerpsPortfolioUi>
    val portfolioLoading: StateFlow<Boolean>
    val portfolioError: StateFlow<PerpsError?>

    val sort: StateFlow<PerpsSort>
    val exploreMarkets: Flow<PagingData<PerpMarket>>
    val livePrices: StateFlow<Map<String, BigDecimal>>
    val fundingStatus: StateFlow<PerpsFundingStatus?>
    val depositRow: StateFlow<PerpsDepositRowUi?>
    val depositToast: StateFlow<PerpsDepositToast?>
    val depositStatuses: StateFlow<List<PerpsDepositListItemUi>?>
    val depositDetails: StateFlow<PerpsDepositStatusUi?>
    val regionRestricted: StateFlow<Boolean>
}

enum class PerpsFundingStatus {
    SENDING,
    WITHDRAW_SENT,
    CONFIRMED,
    NEEDS_AUTHORIZATION,
    WITHDRAW_FAILED,
}

sealed interface PerpsPortfolioAction : MviAction {
    data class SetSort(val value: PerpsSort) : PerpsPortfolioAction
    data class SetActive(val active: Boolean) : PerpsPortfolioAction
    data class RowVisible(val symbol: String, val visible: Boolean) : PerpsPortfolioAction
    data object Retry : PerpsPortfolioAction
    data object AuthorizeDeposit : PerpsPortfolioAction
    data object DismissFundingOutcome : PerpsPortfolioAction
    data object DepositSent : PerpsPortfolioAction
    data object DepositInteraction : PerpsPortfolioAction
    data object OpenDepositRow : PerpsPortfolioAction
    data class OpenDepositDetails(val depositId: String) : PerpsPortfolioAction
    data object CloseDepositDetails : PerpsPortfolioAction
    data object CloseDepositStatuses : PerpsPortfolioAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsPortfolioFeature(
    private val repository: PerpsRepository,
    private val livePriceStore: PerpsLivePrices,
    private val portfolioStore: PerpsPortfolioStore,
    private val fundingRecovery: PerpsFundingRecovery,
    private val depositService: PerpsDepositService,
    private val walletUnlocker: WalletUnlocker,
    private val settingsRepository: SettingsRepository,
    private val regionAccess: PerpsRegionAccess,
) : GraphViewModel<PerpsPortfolioViewState, PerpsPortfolioAction>(), PerpsPortfolioViewState {

    private val depositBanner = PerpsDepositBannerState(isVisible = false)
    private val depositBannerChanges = MutableStateFlow(0)
    private val depositStatusesOpen = MutableStateFlow(false)
    private val depositDetailsId = MutableStateFlow<String?>(null)
    private var depositToastJob: Job? = null
    private val depositDateFormat = Locale.getDefault().let { locale ->
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, DEPOSIT_DATE_SKELETON), locale)
    }

    private val portfolioLive = portfolioStore.liveSubscription()

    private val snapshot = portfolioStore.snapshot
        .cacheState(initialValue = null)

    private val refreshRequests = merge(
        actions.on<PerpsPortfolioAction.Retry>(),
        actions.on<PerpsPortfolioAction.SetActive>().filter { it.active },
    )
        .onEach { portfolioStore.refresh() }
        .cacheState(initialValue = null)

    private val accessReloads = actions
        .on<PerpsPortfolioAction.SetActive>()
        .filter { it.active }
        .mapLatest { regionAccess.refresh() }
        .cacheState(initialValue = null)

    private val active = actions
        .on<PerpsPortfolioAction.SetActive>()
        .map { it.active }
        .distinctUntilChanged()
        .onEach { portfolioLive.set(it) }
        .cacheState(initialValue = false)

    private val visibleSymbols = actions
        .on<PerpsPortfolioAction.RowVisible>()
        .runningFold(emptySet<String>()) { visible, action ->
            if (action.visible) {
                visible + action.symbol
            } else {
                visible - action.symbol
            }
        }
        .cacheState(initialValue = emptySet<String>())

    override val portfolio = combine(
        snapshot
            .map { it?.displayPortfolio() ?: PerpsPortfolio.EMPTY }
            .distinctUntilChanged(),
        settingsRepository.hiddenBalancesFlow,
        transform = { portfolio, _ -> portfolio.toUi(settingsRepository.hiddenBalances) },
    )
        .cacheState(initialValue = PerpsPortfolio.EMPTY.toUi(settingsRepository.hiddenBalances))

    override val portfolioLoading = snapshot
        .map { it == null || (it.loading && it.displayPortfolio() == null) }
        .cacheState(initialValue = true)

    override val portfolioError = snapshot
        .map { it?.displayError() }
        .cacheState(initialValue = null)

    override val sort = actions
        .on<PerpsPortfolioAction.SetSort>()
        .map { it.value }
        .cacheState(initialValue = PerpsSort.VOLUME)

    override val exploreMarkets = sort
        .flatMapLatest { sort -> perpsMarketsPager(repository, "", PerpsMarketFilter.ALL, sort).flow }
        .cachedIn(viewModelScope)

    private val tickers = combine(
        visibleSymbols,
        active,
        transform = { symbols, isActive ->
            if (isActive) {
                symbols.mapTo(mutableSetOf(), ::perpsTicker)
            } else {
                null
            }
        }
    )
        .runningFold(emptySet<String>()) { previous, next ->
            when {
                next == null -> emptySet()
                next.isEmpty() -> previous
                else -> next
            }
        }

    override val livePrices = livePriceStore
        .observe(tickers)
        .flowOn(Async.Io)
        .cacheState(initialValue = emptyMap<String, BigDecimal>())

    override val fundingStatus = snapshot
        .map { it?.walletId }
        .distinctUntilChanged()
        .flatMapLatest { walletId ->
            if (walletId == null) {
                flowOf(null)
            } else {
                fundingRecovery.observe(walletId).flatMapLatest { records ->
                    fundingStatus(records.filter { it.kind == PerpsFundingKind.WITHDRAW || it.stage == PerpsDepositStage.NEEDS_AUTHORIZATION })
                }
            }
        }
        .cacheState(initialValue = null)

    override val depositToast: StateFlow<PerpsDepositToast?> field = MutableStateFlow<PerpsDepositToast?>(null)

    private val depositUpdates = combine(
        depositBannerChanges,
        settingsRepository.hiddenBalancesFlow,
        transform = { _, _ -> Unit },
    )

    override val depositRow = depositUpdates
        .flatMapLatest {
            ticking(next = { now -> depositBanner.nextRowChange(now) }) { now ->
                depositBanner.rowUi(now, settingsRepository.hiddenBalances)
            }
        }
        .cacheState(initialValue = null)

    override val depositStatuses = combine(depositUpdates, depositStatusesOpen) { _, open -> open }
        .map { open ->
            if (open) {
                depositBanner.listedDeposits.map { deposit ->
                    deposit.toListItemUi(settingsRepository.hiddenBalances) { depositDateFormat.format(Date(it)) }
                }
            } else {
                null
            }
        }
        .cacheState(initialValue = null)

    override val depositDetails = combine(depositUpdates, depositDetailsId) { _, id -> id?.let(depositBanner::deposit) }
        .toStatusUi(hiddenBalance = { settingsRepository.hiddenBalances })
        .cacheState(initialValue = null)

    override val regionRestricted = regionAccess.restricted
        .cacheState(initialValue = regionAccess.isRestricted)

    init {
        mainScope.launch {
            actions.on<PerpsPortfolioAction.AuthorizeDeposit>().collect { authorizeDeposit() }
        }
        mainScope.launch {
            combine(
                snapshot.map { it?.walletId }.distinctUntilChanged().flatMapLatest { walletId ->
                    if (walletId == null) {
                        flowOf(emptyList())
                    } else {
                        fundingRecovery.observe(walletId).map { records -> records.mapNotNull { it.toDepositStatus() } }
                    }
                },
                snapshot.map { it?.loading == true }.distinctUntilChanged(),
                transform = { deposits, refreshing -> deposits to refreshing },
            ).collect { (deposits, refreshing) ->
                val arrived = depositBanner.apply(deposits, isBalanceRefreshing = refreshing)
                depositBannerChanges.update { it + 1 }
                if (arrived.isNotEmpty() && depositBanner.isVisible) {
                    showDepositToast(PerpsDepositToast.COMPLETED)
                }
            }
        }
        mainScope.launch {
            active.collect { isActive ->
                depositBanner.isVisible = isActive
                if (!isActive) {
                    depositBanner.didInteract()
                    depositBannerChanges.update { it + 1 }
                }
            }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.DepositSent>().collect { showDepositToast(PerpsDepositToast.IN_PROGRESS) }
        }
        mainScope.launch {
            actions
                .filter { it is PerpsPortfolioAction.OpenDepositRow || it is PerpsPortfolioAction.DepositInteraction }
                .collect { action ->
                    if (action is PerpsPortfolioAction.OpenDepositRow) {
                        openDepositRow()
                    } else {
                        dismissCompletedDeposit()
                    }
                }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.OpenDepositDetails>().collect { openDepositDetails(it.depositId) }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.CloseDepositDetails>().collect { depositDetailsId.value = null }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.CloseDepositStatuses>().collect { closeDepositStatuses() }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.DismissFundingOutcome>().collect { dismissFundingOutcome() }
        }
    }

    private fun openDepositRow() {
        when (val banner = depositBanner.banner) {
            is PerpsDepositBanner.Single -> openDepositDetails(banner.depositId)
            is PerpsDepositBanner.Completed -> {
                val single = banner.depositIds.singleOrNull()
                if (single != null) {
                    openDepositDetails(single)
                } else {
                    openDepositStatuses(announced = banner.depositIds)
                }
            }

            PerpsDepositBanner.Multiple,
            PerpsDepositBanner.Problems -> openDepositStatuses()
            null -> Unit
        }
    }

    private fun dismissCompletedDeposit() {
        if (depositBanner.banner is PerpsDepositBanner.Completed) {
            depositBanner.didInteract()
            depositBannerChanges.update { it + 1 }
        }
    }

    private fun openDepositStatuses(announced: Set<String> = emptySet()) {
        depositStatusesOpen.value = true
        acknowledgeDeposits(depositBanner.didOpenList(announced))
        depositBannerChanges.update { it + 1 }
    }

    private fun closeDepositStatuses() {
        depositStatusesOpen.value = false
        acknowledgeDeposits(depositBanner.didCloseList())
        depositBannerChanges.update { it + 1 }
    }

    private fun openDepositDetails(depositId: String) {
        depositDetailsId.value = depositId
        acknowledgeDeposits(depositBanner.didOpen(depositId))
        depositBannerChanges.update { it + 1 }
    }

    private fun acknowledgeDeposits(operationIds: Set<String>) {
        if (operationIds.isEmpty()) {
            return
        }
        mainScope.launch {
            try {
                depositService.acknowledgeDepositStatuses(operationIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e, "perps funding: acknowledging deposit statuses failed")
            }
        }
    }

    private fun showDepositToast(toast: PerpsDepositToast) {
        depositToastJob?.cancel()
        depositToast.value = toast
        depositToastJob = mainScope.launch {
            delay(DEPOSIT_TOAST_MS)
            depositToast.value = null
        }
    }

    private suspend fun dismissFundingOutcome() {
        val walletId = snapshot.value?.walletId ?: return
        try {
            withContext(Async.Io) { depositService.dismissOutcomes(walletId, PerpsFundingKind.WITHDRAW) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e, "perps funding: dismissing deposit outcomes failed for $walletId")
        }
    }

    private suspend fun authorizeDeposit() {
        val walletId = snapshot.value?.walletId ?: return
        try {
            val wallet = walletUnlocker.unlockWallet(walletId, PasscodeReason.PERPS)
            withContext(Async.Io) { depositService.relinkWallet(walletId, wallet) }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
        } catch (e: Throwable) {
            L.e(e, "perps funding: deposit authorization failed for $walletId")
        }
    }

    private fun fundingStatus(records: List<PerpsFundingRecord>): Flow<PerpsFundingStatus?> = flow {
        val submitting = records.any {
            it.stage.isUnresolved && it.stage != PerpsDepositStage.NEEDS_AUTHORIZATION && !it.stage.isTrackingInBackground
        }
        if (submitting) {
            emit(PerpsFundingStatus.SENDING)
            return@flow
        }
        if (records.any { it.stage == PerpsDepositStage.NEEDS_AUTHORIZATION }) {
            emit(PerpsFundingStatus.NEEDS_AUTHORIZATION)
            return@flow
        }
        val now = System.currentTimeMillis()
        val recent = recentWithdrawStatus(records, now)
        if (recent != null) {
            emit(recent.status)
            delay(recent.shownUntil - now)
        }
        emit(outcomeStatus(records))
    }

    private fun outcomeStatus(records: List<PerpsFundingRecord>): PerpsFundingStatus? {
        val lastSucceededAt = records
            .filter { it.stage.isSucceeded }
            .maxOfOrNull { it.updatedAt }
            ?: 0L
        val withdrawFailed = records.any {
            it.updatedAt > lastSucceededAt &&
                it.kind == PerpsFundingKind.WITHDRAW &&
                (it.stage == PerpsDepositStage.FAILED || it.stage == PerpsDepositStage.REFUNDED)
        }
        return if (withdrawFailed) {
            PerpsFundingStatus.WITHDRAW_FAILED
        } else {
            null
        }
    }

    override fun onCleared() {
        portfolioLive.close()
        super.onCleared()
    }

    private companion object {
        const val DEPOSIT_TOAST_MS = 2_000L
        const val DEPOSIT_DATE_SKELETON = "d MMM HH:mm"
    }
}

private fun PerpsPortfolio.toUi(hiddenBalance: Boolean): PerpsPortfolioUi {
    val collateral = balance?.collateral ?: BigDecimal.ZERO
    return PerpsPortfolioUi(
        balanceFormat = maskIf(hiddenBalance) { formatUsdAmount(balance?.spendableBalance() ?: BigDecimal.ZERO) },
        funded = collateral.signum() > 0,
        isEmpty = positions.isEmpty() && collateral.signum() == 0,
        positions = positions.map { it.toUi(hiddenBalance) },
        positionsTotal = positions.toTotalUi(hiddenBalance),
        orders = openOrders.orEmpty().map { it.toUi(hiddenBalance) },
    )
}

private fun List<PerpsPosition>.toTotalUi(hiddenBalance: Boolean): PerpsPositionsTotalUi {
    if (hiddenBalance) {
        return PerpsPositionsTotalUi(
            amountFormat = HIDDEN_BALANCE,
            pnl = null,
            pnlFormat = HIDDEN_BALANCE,
            pnlPercent = null,
            pnlPercentFormat = null,
        )
    }
    val pnl = sumOf { it.unrealizedPnl ?: BigDecimal.ZERO }
    val pnlPercent = unrealizedPnlPercent()
    return PerpsPositionsTotalUi(
        amountFormat = formatUsdAmount(sumOf { it.equity ?: BigDecimal.ZERO }),
        pnl = pnl,
        pnlFormat = formatSignedUsdAmount(pnl),
        pnlPercent = pnlPercent,
        pnlPercentFormat = pnlPercent?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
    )
}

private fun PerpsPortfolioOrder.toUi(hiddenBalance: Boolean): PerpsOrderUi {
    val limitPrice = limitPrice
    return PerpsOrderUi(
        order = this,
        amountFormat = margin?.let { maskIf(hiddenBalance) { formatUsdAmount(it) } } ?: PERPS_EMPTY_VALUE,
        limitPriceFormat = limitPrice?.let { formatUsdPrice(it.toDouble(), it.scale()) } ?: PERPS_EMPTY_VALUE,
        takeProfitFormat = takeProfit?.triggerPrice?.let(::formatTrimmedUsdPrice),
        stopLossFormat = stopLoss?.triggerPrice?.let(::formatTrimmedUsdPrice),
    )
}

/*
 *   PerpsPortfolioStore.snapshot ──► snapshot?
 *
 *                     Retry ──┐
 *   SetActive [active only] ──┴──► refreshRequests? ╌► store.refresh()
 *   SetActive [active only] ╌► PerpsRegionAccess.refresh()
 *
 *   snapshot? ──┐
 *   hiddenBalancesFlow ─┴──► portfolio
 *   snapshot? ──► portfolioLoading
 *   snapshot? ──► portfolioError?
 *
 *   SetSort ──► sort ──► [latest] exploreMarkets (paged)
 *
 *   RowVisible ──► [fold] visibleSymbols ──┐
 *                    SetActive ──► active ─┴──► [fold] tickers ──► livePrices
 *                                  active ╌► store live subscription
 *
 *   API.configFlow ──► regionRestricted
 */

internal class PerpsRecentWithdrawStatus(
    val status: PerpsFundingStatus,
    val shownUntil: Long,
)

internal fun recentWithdrawStatus(records: List<PerpsFundingRecord>, now: Long): PerpsRecentWithdrawStatus? {
    val latest = records
        .filter { it.stage.isSucceeded }
        .maxByOrNull { it.updatedAt }
        ?: return null
    val shownUntil = latest.updatedAt + STATUS_PILL_MS
    if (shownUntil <= now) {
        return null
    }
    val status = if (latest.stage == PerpsDepositStage.SETTLED) {
        PerpsFundingStatus.CONFIRMED
    } else {
        PerpsFundingStatus.WITHDRAW_SENT
    }
    return PerpsRecentWithdrawStatus(status = status, shownUntil = shownUntil)
}

private const val STATUS_PILL_MS = 6_000L
