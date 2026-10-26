package com.tonapps.perps.screens.common

import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.anyOfFlows
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.trade.PerpsPlanFailure
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsPositionBounds
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import java.math.BigDecimal

data class PerpsPositionFormGate(
    val isMultichain: Boolean,
    val error: PerpsError?,
    val detail: PerpsPositionDetail?,
    val enabled: Boolean,
    val loading: Boolean,
)

abstract class PerpsPositionFormFeature<State : MviViewState, Action : MviAction>(
    val marketIndex: Int,
    val symbol: String,
    private val repository: PerpsRepository,
    private val adapter: PerpsPlannerAdapter,
    livePrices: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
    protected val settingsRepository: SettingsRepository,
    isRetry: (Action) -> Boolean,
    isEnabled: (PerpsTradingFlags) -> Boolean,
) : GraphViewModel<State, Action>() {

    private data class FormContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
    )

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    private val reloadAttempt = actions
        .filter { isRetry(it) }
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val context = combine(
        wallet,
        reloadAttempt,
        transform = { wallet, attempt ->
            val walletId = wallet?.id ?: return@combine null

            FormContext(
                walletId = walletId,
                isMultichain = wallet.type == WalletType.Multichain,
                attempt = attempt,
            )
        }
    )
        .filterNotNull()
        .distinctUntilChanged()

    private val isMultichain = context
        .map { it.isMultichain }
        .cacheState(initialValue = true)

    private val tradingState = context
        .transformStateLatest { context ->
            if (context.isMultichain) {
                adapter.tradingState(marketIndex, context.walletId)
            } else {
                KResult.Ok<PerpsTradingState, PerpsError>(PerpsTradingState.PUBLIC)
            }
        }
        .cacheStateWithLoading()

    private val trading = tradingState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    protected val rules: StateFlow<PerpsMarketRules?> = trading
        .map { it?.rules }
        .cacheState(initialValue = null)

    protected val flags: StateFlow<PerpsTradingFlags> = trading
        .map { it?.flags ?: PerpsTradingFlags.ALL_ENABLED }
        .cacheState(initialValue = PerpsTradingFlags.ALL_ENABLED)

    protected val availableBalance: StateFlow<BigDecimal?> = tradingState
        .mapStateDataValueOrNull()
        .map { it?.getOrNull()?.availableBalance }
        .cacheState(initialValue = null)

    private val tradingError = tradingState
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    private val tradingLoading = tradingState
        .isLoading()
        .cacheState(initialValue = true)

    private val positionState = context
        .transformStateLatest { context ->
            if (context.isMultichain) {
                repository.getOpenPosition(marketIndex, context.walletId)
            } else {
                KResult.Ok<PerpsPositionDetail?, PerpsError>(null)
            }
        }
        .cacheStateWithLoading()

    protected val detail: StateFlow<PerpsPositionDetail?> = positionState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    private val positionError = positionState
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    private val positionLoading = positionState
        .isLoading()
        .cacheState(initialValue = true)

    val position: StateFlow<PerpsOpenPosition?> = detail
        .map { it?.position }
        .cacheState(initialValue = null)

    private val livePrice = livePrices
        .observe(flowOf(setOf(perpsTicker(symbol))))
        .map { it[perpsTicker(symbol)] }
        .cacheState(initialValue = null)

    val mark: StateFlow<BigDecimal?> = combine(
        livePrice,
        rules,
        position,
        transform = { live, rules, position -> live ?: rules?.markPrice ?: position?.markPrice }
    )
        .cacheState(initialValue = null)

    val priceDecimals: StateFlow<Int> = rules
        .map { it?.priceDecimals ?: DEFAULT_PRICE_DECIMALS }
        .cacheState(initialValue = DEFAULT_PRICE_DECIMALS)

    private val marketState = context
        .transformStateLatest { context ->
            if (context.isMultichain) {
                adapter.reviewMarketState(context.walletId, marketIndex)
            } else {
                null
            }
        }
        .cacheStateWithLoading()

    private val marketStateError = marketState
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { failure ->
            when (val value = failure?.value) {
                null -> null
                is PerpsPlanFailure.Load -> value.error
                else -> PerpsError.Unknown
            }
        }
        .cacheState(initialValue = null)

    private val marketStateLoading = marketState
        .isLoading()
        .cacheState(initialValue = true)

    private val boundsResult: StateFlow<KResult<PerpsPositionBounds, Unit>?> = combine(
        marketState.mapStateDataValueOrNull(),
        mark,
        transform = { result, mark ->
            val state = result?.getOrNull()
            when {
                state == null || mark == null -> null
                else -> adapter.bounds(state, mark)
                    ?.let { KResult.Ok<PerpsPositionBounds, Unit>(it) }
                    ?: KResult.Err<PerpsPositionBounds, Unit>(Unit)
            }
        }
    )
        .cacheState(initialValue = null)

    protected val bounds: StateFlow<PerpsPositionBounds?> = boundsResult
        .map { it?.getOrNull() }
        .cacheState(initialValue = null)

    protected val boundsUnavailable: StateFlow<Boolean> = boundsResult
        .map { it?.isErr == true }
        .cacheState(initialValue = false)

    private val loading = anyOfFlows(tradingLoading, positionLoading, marketStateLoading)
        .cacheState(initialValue = true)

    private val formError = combine(
        tradingError,
        positionError,
        marketStateError,
        transform = { tradingError, positionError, marketStateError ->
            tradingError ?: positionError ?: marketStateError
        }
    )
        .cacheState(initialValue = null)

    protected val gate: Flow<PerpsPositionFormGate> = combine(
        isMultichain,
        formError,
        detail,
        combine(
            flags,
            loading,
            transform = { flags, loading -> isEnabled(flags) to loading }
        ),
        transform = { isMultichain, error, detail, (enabled, loading) ->
            PerpsPositionFormGate(
                isMultichain = isMultichain,
                error = error,
                detail = detail,
                enabled = enabled,
                loading = loading,
            )
        }
    )

    protected fun Flow<BigDecimal?>.toAvailableFormat(): StateFlow<String> =
        combine(this, settingsRepository.hiddenBalancesFlow) { available, _ ->
            maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(available ?: BigDecimal.ZERO) }
        }
            .cacheState(initialValue = maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(BigDecimal.ZERO) })

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
    }
}

/*
 *   selectedTonWalletFlow ──► wallet? ──┐
 *                                       ├──► [distinct] context ──┬──► isMultichain
 *      Retry ──► [fold] reloadAttempt ──┘                         ├──► [latest] tradingState
 *                                                                 ├──► [latest] positionState
 *                                                                 └──► [latest] marketState?
 *
 *   tradingState ──► trading? ──┬──► rules? ──► priceDecimals
 *                               └──► flags
 *   tradingState [dataOrNull] ──► availableBalance?
 *   tradingState [dataOrNull, errOrNull] ──► tradingError?
 *   tradingState [loading] ──► tradingLoading
 *
 *   positionState ──► detail? ──► position?
 *   positionState [dataOrNull, errOrNull] ──► positionError?
 *   positionState [loading] ──► positionLoading
 *
 *                  livePrice? ──┐
 *                      rules? ──┼──► mark?
 *                   position? ──┘
 *
 *   marketState? [dataOrNull, errOrNull] ──► marketStateError?
 *   marketState? [loading] ──► marketStateLoading
 *   marketState? [dataOrNull] ──┐
 *                       mark? ──┴──► boundsResult? ──┬──► bounds?
 *                                                    └──► boundsUnavailable
 *
 *      tradingLoading ──┐
 *     positionLoading ──┼─ [any] ──► loading
 *   marketStateLoading ─┘
 *
 *   tradingError? / positionError? / marketStateError? ──► formError?
 *
 *   isMultichain / formError? / detail? / flags / loading ──► gate
 */
