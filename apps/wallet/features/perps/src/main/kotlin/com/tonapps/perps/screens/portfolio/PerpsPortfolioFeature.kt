package com.tonapps.perps.screens.portfolio

import android.content.Context
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
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.perpsMarketsPager
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.spendableBalance
import com.tonapps.perps.data.unrealizedPnlPercent
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsFundingRecovery
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

interface PerpsPortfolioViewState : MviViewState {
    val portfolio: StateFlow<PerpsPortfolioUi>
    val portfolioLoading: StateFlow<Boolean>
    val portfolioError: StateFlow<PerpsError?>

    val sort: StateFlow<PerpsSort>
    val exploreMarkets: Flow<PagingData<PerpMarket>>
    val livePrices: StateFlow<Map<String, BigDecimal>>
    val fundingStatus: StateFlow<PerpsFundingStatus?>
}

enum class PerpsFundingStatus(val actionable: Boolean) {
    SENDING(actionable = false),
    CONFIRMED(actionable = false),
    NEEDS_AUTHORIZATION(actionable = true),
    FAILED(actionable = true),
    REFUNDED(actionable = true),
    WITHDRAW_FAILED(actionable = true),
}

sealed interface PerpsPortfolioAction : MviAction {
    data class SetSort(val value: PerpsSort) : PerpsPortfolioAction
    data class SetActive(val active: Boolean) : PerpsPortfolioAction
    data class RowVisible(val symbol: String, val visible: Boolean) : PerpsPortfolioAction
    data object Retry : PerpsPortfolioAction
    data class AuthorizeDeposit(val context: Context) : PerpsPortfolioAction
    data object DismissFundingOutcome : PerpsPortfolioAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsPortfolioFeature(
    private val repository: PerpsRepository,
    private val livePriceStore: PerpsLivePrices,
    private val portfolioStore: PerpsPortfolioStore,
    private val fundingRecovery: PerpsFundingRecovery,
    private val depositService: PerpsDepositService,
    private val mcAccountRepository: McAccountRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsPortfolioViewState, PerpsPortfolioAction>(), PerpsPortfolioViewState {

    private val portfolioLive = portfolioStore.liveSubscription()

    private val snapshot = portfolioStore.snapshot
        .cacheState(initialValue = null)

    private val refreshRequests = merge(
        actions.on<PerpsPortfolioAction.Retry>(),
        actions.on<PerpsPortfolioAction.SetActive>().filter { it.active },
    )
        .onEach { portfolioStore.refresh() }
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
                fundingRecovery.observe(walletId).flatMapLatest { records -> fundingStatus(records) }
            }
        }
        .cacheState(initialValue = null)

    init {
        mainScope.launch {
            actions.on<PerpsPortfolioAction.AuthorizeDeposit>().collect { authorizeDeposit(it.context) }
        }
        mainScope.launch {
            actions.on<PerpsPortfolioAction.DismissFundingOutcome>().collect { dismissFundingOutcome() }
        }
    }

    private suspend fun dismissFundingOutcome() {
        val walletId = snapshot.value?.walletId ?: return
        try {
            withContext(Async.Io) { depositService.dismissOutcomes(walletId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e, "perps funding: dismissing deposit outcomes failed for $walletId")
        }
    }

    private suspend fun authorizeDeposit(context: Context) {
        val walletId = snapshot.value?.walletId ?: return
        try {
            val wallet = passcodeManager.unlockMultichainVault(context) { coder ->
                mcAccountRepository.getCryptoWallet(walletId, coder)
                    ?: throw IllegalStateException("No multichain wallet for $walletId")
            }
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
        val confirmedUntil = records
            .filter { it.stage.isSucceeded }
            .maxOfOrNull { it.updatedAt + CONFIRMED_PILL_MS }
        val now = System.currentTimeMillis()
        if (confirmedUntil != null && confirmedUntil > now) {
            emit(PerpsFundingStatus.CONFIRMED)
            delay(confirmedUntil - now)
        }
        emit(outcomeStatus(records))
    }

    private fun outcomeStatus(records: List<PerpsFundingRecord>): PerpsFundingStatus? {
        val lastSucceededAt = records
            .filter { it.stage.isSucceeded }
            .maxOfOrNull { it.updatedAt }
            ?: 0L
        val outcomes = records.filter { it.updatedAt > lastSucceededAt }
        val failed = outcomes.filter { it.stage == PerpsDepositStage.FAILED }.maxByOrNull { it.updatedAt }
        return when {
            failed?.kind == PerpsFundingKind.WITHDRAW -> PerpsFundingStatus.WITHDRAW_FAILED
            failed != null -> PerpsFundingStatus.FAILED
            outcomes.any { it.stage == PerpsDepositStage.REFUNDED && it.kind == PerpsFundingKind.WITHDRAW } ->
                PerpsFundingStatus.WITHDRAW_FAILED

            outcomes.any { it.stage == PerpsDepositStage.REFUNDED } -> PerpsFundingStatus.REFUNDED
            else -> null
        }
    }

    override fun onCleared() {
        portfolioLive.close()
        super.onCleared()
    }

    private companion object {
        const val CONFIRMED_PILL_MS = 6_000L
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
    )
}

/*
 *   PerpsPortfolioStore.snapshot ──► snapshot?
 *
 *                     Retry ──┐
 *   SetActive [active only] ──┴──► refreshRequests? ╌► store.refresh()
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
 */
