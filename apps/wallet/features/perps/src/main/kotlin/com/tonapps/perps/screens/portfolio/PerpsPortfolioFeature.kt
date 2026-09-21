package com.tonapps.perps.screens.portfolio

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsPortfolio
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.perpsMarketsPager
import com.tonapps.perps.data.perpsTicker
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import java.math.BigDecimal

interface PerpsPortfolioViewState : MviViewState {
    val portfolio: StateFlow<PerpsPortfolio?>
    val portfolioLoading: StateFlow<Boolean>
    val portfolioError: StateFlow<PerpsError?>

    val sort: StateFlow<PerpsSort>
    val exploreMarkets: Flow<PagingData<PerpMarket>>
    val livePrices: StateFlow<Map<String, BigDecimal>>
}

sealed interface PerpsPortfolioAction : MviAction {
    data class SetSort(val value: PerpsSort) : PerpsPortfolioAction
    data class SetActive(val active: Boolean) : PerpsPortfolioAction
    data class RowVisible(val symbol: String, val visible: Boolean) : PerpsPortfolioAction
    data object Retry : PerpsPortfolioAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsPortfolioFeature(
    private val repository: PerpsRepository,
    private val livePriceStore: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
    accountRefresh: PerpsAccountRefresh,
) : GraphViewModel<PerpsPortfolioViewState, PerpsPortfolioAction>(), PerpsPortfolioViewState {

    private data class PortfolioContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
    )

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    private val portfolioAttempt = merge(
        actions.on<PerpsPortfolioAction.Retry>().map { },
        accountRefresh.events,
    )
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val active = actions
        .on<PerpsPortfolioAction.SetActive>()
        .map { it.active }
        .distinctUntilChanged()
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

    private val portfolioState = combine(
        wallet,
        portfolioAttempt,
        transform = { wallet, attempt ->
            val walletId = wallet?.id ?: return@combine null
            PortfolioContext(
                walletId = walletId,
                isMultichain = wallet.type == WalletType.Multichain,
                attempt = attempt,
            )
        }
    )
        .filterNotNull()
        .distinctUntilChanged()
        .transformStateLatest { context ->
            if (context.isMultichain) {
                repository.getPortfolio(context.walletId)
            } else {
                KResult.Err<PerpsPortfolio, PerpsError>(PerpsError.NoAccount)
            }
        }
        .cacheStateWithLoading()

    override val sort = actions
        .on<PerpsPortfolioAction.SetSort>()
        .map { it.value }
        .cacheState(initialValue = PerpsSort.VOLUME)

    override val exploreMarkets = sort
        .flatMapLatest { sort -> perpsMarketsPager(repository, "", PerpsMarketFilter.ALL, sort).flow }
        .cachedIn(viewModelScope)

    private val positionIcons = portfolioState
        .mapStateDataValueOrNull()
        .filterNotNull()
        .mapResultValueOrNull()
        .filterNotNull()
        .map { result -> result.value.positions.mapTo(mutableSetOf(), PerpsPosition::marketIndex) }
        .distinctUntilChanged()
        .transformStateLatest { indices -> repository.getMarketsByIndices(indices) }
        .mapStateDataValueOrNull()
        .filterNotNull()
        .mapResultValueOrNull()
        .filterNotNull()
        .runningFold(emptyMap<Int, String?>()) { icons, markets ->
            icons + markets.value.associate { it.marketIndex to it.iconUrl }
        }
        .cacheState(initialValue = emptyMap<Int, String?>())

    override val portfolio = combine(
        portfolioState.map { state ->
            val result = state.state
            when {
                result is KResult.Ok -> result.value
                (result as? KResult.Err)?.value == PerpsError.NoAccount -> EMPTY_PORTFOLIO
                else -> null
            }
        },
        positionIcons,
        transform = { portfolio, icons ->
            portfolio?.copy(
                positions = portfolio.positions.map { it.copy(iconUrl = icons[it.marketIndex]) },
            )
        }
    )
        .cacheState(initialValue = null)

    override val portfolioLoading = combine(
        portfolioState,
        portfolio,
        transform = { state, value -> state is KState.Loading && value == null }
    )
        .cacheState(initialValue = true)

    override val portfolioError = portfolioState
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { error -> error?.value?.takeIf { it != PerpsError.NoAccount } }
        .cacheState(initialValue = null)

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

    private companion object {
        val EMPTY_PORTFOLIO = PerpsPortfolio(balance = null, positions = emptyList())
    }
}

/*
 *          selectedTonWalletFlow ──► wallet? ──┐
 *                                              ├──► [distinct, latest] portfolioState
 *   Retry, refresh ──► [fold] portfolioAttempt ┘     (multichain only, else NoAccount)
 *
 *   SetSort ──► sort ──► [latest] exploreMarkets (paged)
 *
 *   portfolioState [dataOrNull, okOrNull] ──► [distinct] position indices
 *                                              └──► [latest] markets ──► [fold] positionIcons ──┐
 *                                                                                               │
 *    portfolioState [carried, okOrNoAccount] ──────────────────────────────────────────────────┬┴──► portfolio?
 *                   portfolioState [loading] ──┬──► portfolioLoading
 *                                  portfolio? ─┘
 *   portfolioState [dataOrNull, errOrNull] ──► portfolioError?
 *
 *   RowVisible ──► [fold] visibleSymbols ──┐
 *                    SetActive ──► active ─┴──► [fold] tickers ──► livePrices
 */
