package com.tonapps.perps.data

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.extensions.isDebug
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsAccountTierUpgrader
import com.tonapps.perps.trade.legIndexes
import com.tonapps.perps.trade.readPortfolio
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class PerpsPortfolioSnapshot(
    val walletId: String,
    val loading: Boolean,
    val result: KResult<PerpsPortfolio, PerpsError>?,
    val lastGood: PerpsPortfolio?,
) {
    val isAccountMissing: Boolean = (result as? KResult.Err)?.value == PerpsError.NoAccount

    fun displayPortfolio(): PerpsPortfolio? = when (val current = result) {
        is KResult.Ok -> current.value
        is KResult.Err -> if (current.value == PerpsError.NoAccount) {
            PerpsPortfolio.EMPTY
        } else {
            lastGood
        }
        null -> lastGood
    }

    fun displayError(): PerpsError? {
        val error = (result as? KResult.Err)?.value ?: return null
        if (error == PerpsError.NoAccount || lastGood != null) {
            return null
        }
        return error
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsPortfolioStore(
    appContext: Context,
    private val repository: PerpsRepository,
    unifiedAccountRepository: UnifiedAccountRepository,
    accountRefresh: PerpsAccountRefresh,
    private val accountService: PerpsAccountService,
    private val tierUpgrader: PerpsAccountTierUpgrader,
    private val journal: PerpsOperationJournal,
) {

    private data class LoadContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
        val afterAccountChange: Boolean,
    )

    private val refreshSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val latest = MutableStateFlow<PerpsPortfolioSnapshot?>(null)
    private val markets = MutableStateFlow(emptyMap<Int, PerpMarket?>())
    private val liveSubscribers = MutableStateFlow(0)
    private val liveFailures = MutableStateFlow(0)
    private val emptyPollRefreshed = MutableStateFlow(false)
    private val liveRefreshMs = if (appContext.isDebug) {
        DEBUG_LIVE_REFRESH_MS
    } else {
        LIVE_REFRESH_MS
    }

    private val live = liveSubscribers.map { it > 0 }
        .distinctUntilChanged()
        .flatMapLatest { subscribed ->
            if (subscribed) {
                flowOf(true)
            } else {
                flow {
                    delay(LIVE_STOP_LINGER_MS)
                    emit(false)
                }
            }
        }
        .distinctUntilChanged()

    val snapshot: StateFlow<PerpsPortfolioSnapshot?> = combine(
        unifiedAccountRepository.selectedTonWalletFlow,
        merge(refreshSignal.map { false }, accountRefresh.events.map { true })
            .runningFold(0 to false) { (attempt, _), afterAccountChange -> attempt + 1 to afterAccountChange },
    ) { wallet, (attempt, afterAccountChange) ->
        wallet?.let { LoadContext(it.id, it.type == WalletType.Multichain, attempt, afterAccountChange) }
    }
        .filterNotNull()
        .distinctUntilChanged()
        .transformLatest { context ->
            val carried = latest.value?.takeIf { it.walletId == context.walletId }
            emit(
                PerpsPortfolioSnapshot(
                    walletId = context.walletId,
                    loading = true,
                    result = carried?.result,
                    lastGood = carried?.lastGood,
                )
            )
            val result = load(context)
            if (result is KResult.Ok) {
                liveFailures.value = 0
                if (result.value.positions.isEmpty()) {
                    emptyPollRefreshed.value = false
                }
            }
            val error = (result as? KResult.Err)?.value
            val lastGood = result.getOrNull() ?: carried?.lastGood?.takeIf {
                error != PerpsError.NoAccount
            }
            emit(
                PerpsPortfolioSnapshot(
                    walletId = context.walletId,
                    loading = false,
                    result = result,
                    lastGood = lastGood,
                )
            )
            // The only answer that stays true until something else changes; every other failure is
            // the backend being briefly unable to answer, and the poll is what asks it again.
            if (error == PerpsError.NoAccount) {
                return@transformLatest
            }
            emitAll(
                live.flatMapLatest { isLive ->
                    if (isLive) {
                        // Right after a trade /portfolio can still miss the fill, so idle needs a tick to confirm it.
                        val seed = (latest.value?.lastGood ?: lastGood).takeIf {
                            result is KResult.Ok && !context.afterAccountChange
                        }
                        pollPositions(context, seed)
                    } else {
                        emptyFlow()
                    }
                }
            )
        }
        .onEach { latest.value = it }
        .onStart<PerpsPortfolioSnapshot?> { emit(null) }
        .stateIn(Async.mainScope(), SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun refresh() {
        if (latest.value?.loading == true) {
            return
        }
        refreshSignal.tryEmit(Unit)
    }

    fun liveSubscription(): LiveSubscription = LiveSubscription()

    inner class LiveSubscription internal constructor() {

        private var active = false
        private var closed = false

        @Synchronized
        fun set(active: Boolean) {
            if (closed || this.active == active) {
                return
            }
            this.active = active
            val delta = if (active) {
                1
            } else {
                -1
            }
            liveSubscribers.update { it + delta }
        }

        @Synchronized
        fun close() {
            set(false)
            closed = true
        }
    }

    private suspend fun load(context: LoadContext): KResult<PerpsPortfolio, PerpsError> {
        if (!context.isMultichain) {
            return KResult.Err(PerpsError.NoAccount)
        }
        val result = accountService.readPortfolio(repository, context.walletId)
        val portfolio = result.getOrNull() ?: return result
        accountService.refreshRoToken(context.walletId, tokenRefused = portfolio.openOrders == null)
        tierUpgrader.ensurePlusInBackground(context.walletId)
        return KResult.Ok(withMargin(context.walletId, withCatalog(portfolio)))
    }

    private fun pollPositions(context: LoadContext, seed: PerpsPortfolio?): Flow<PerpsPortfolioSnapshot> = flow {
        var portfolio = seed
        while (portfolio == null || portfolio.positions.isNotEmpty() || portfolio.openOrders?.isEmpty() != true) {
            delay(liveRefreshMs shl liveFailures.value.coerceAtMost(LIVE_BACKOFF_MAX_SHIFT))
            val current = portfolio
            val fetched = load(context).getOrNull()
            if (fetched == null) {
                if (liveFailures.updateAndGet { it + 1 } == 1 && current != null) {
                    refresh()
                }
                continue
            }
            liveFailures.value = 0
            if (current != null && fetched.positions.isEmpty() && current.positions.isNotEmpty() && !emptyPollRefreshed.value) {
                emptyPollRefreshed.value = true
                refresh()
                continue
            }
            emptyPollRefreshed.value = fetched.positions.isEmpty() && emptyPollRefreshed.value
            portfolio = fetched
            emit(
                PerpsPortfolioSnapshot(
                    walletId = context.walletId,
                    loading = false,
                    result = KResult.Ok(fetched),
                    lastGood = fetched,
                )
            )
        }
    }

    private suspend fun withCatalog(portfolio: PerpsPortfolio): PerpsPortfolio {
        val indices = portfolio.positions.map { it.marketIndex } +
            portfolio.openOrders.orEmpty().map { it.marketIndex }
        val missing = indices.distinct().filter { it !in markets.value }
        if (missing.isNotEmpty()) {
            repository.getMarketsByIndices(missing).getOrNull()?.let { fetched ->
                markets.update { known ->
                    known + missing.associateWith { null } + fetched.associateBy { it.marketIndex }
                }
            }
        }
        val known = markets.value
        return portfolio.copy(
            positions = portfolio.positions.map { it.copy(iconUrl = known[it.marketIndex]?.iconUrl) },
            openOrders = portfolio.openOrders?.map { it.copy(iconUrl = known[it.marketIndex]?.iconUrl) },
        )
    }

    private suspend fun withMargin(walletId: String, portfolio: PerpsPortfolio): PerpsPortfolio {
        val orders = portfolio.openOrders?.takeIf { it.isNotEmpty() } ?: return portfolio
        val placements = if (orders.any { it.clientOrderIndex != null }) {
            placements(walletId)
        } else {
            emptyMap()
        }
        val positionLeverage = portfolio.positions
            .mapNotNull { position -> position.leverage?.let { position.marketIndex to it } }
            .toMap()
        val catalog = markets.value
        return portfolio.copy(
            openOrders = orders.map { order ->
                val placement = order.clientOrderIndex?.let { placements[order.marketIndex to it] }
                val leverage = placement?.leverage
                    ?: positionLeverage[order.marketIndex]
                    ?: catalog[order.marketIndex]?.maxLeverage?.takeIf { it > 0 }
                order.copy(
                    leverage = leverage,
                    margin = placement?.remainingBudget(order.remainingBase) ?: order.limitMargin(leverage),
                )
            },
        )
    }

    private suspend fun placements(walletId: String): Map<Pair<Int, Long>, PerpsOperationRecord> {
        val operations = journal.getOperations(walletId)
            .filter { it.kind == PerpsOperationKind.OPEN_LIMIT && it.leverage > 0 }
            .associateBy { it.operationId }
        return journal.getSteps(walletId).flatMap { step ->
            val operation = operations[step.operationId] ?: return@flatMap emptyList()
            step.legIndexes()
                .filter { it > LighterConstants.NilClientOrderIndex }
                .map { (operation.marketIndex to it) to operation }
        }.toMap()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val LIVE_REFRESH_MS = 10_000L
        const val DEBUG_LIVE_REFRESH_MS = 30_000L
        const val LIVE_BACKOFF_MAX_SHIFT = 4
        const val LIVE_STOP_LINGER_MS = 2_000L
    }
}

private const val MARGIN_SCALE = 2

private fun PerpsOperationRecord.remainingBudget(remainingBase: BigDecimal?): BigDecimal? {
    val budget = marginQuote.toBigDecimalOrNull() ?: return null
    val planned = expectedBaseSize?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return budget
    val remaining = remainingBase ?: return budget
    val share = remaining.divide(planned, MathContext.DECIMAL64).min(BigDecimal.ONE)
    return budget.multiply(share).setScale(MARGIN_SCALE, RoundingMode.HALF_UP)
}

private fun PerpsPortfolioOrder.limitMargin(leverage: Int?): BigDecimal? {
    val price = limitPrice ?: return null
    val base = remainingBase ?: return null
    val divisor = leverage ?: return null
    return base.multiply(price).divide(BigDecimal(divisor), MARGIN_SCALE, RoundingMode.HALF_UP)
}
