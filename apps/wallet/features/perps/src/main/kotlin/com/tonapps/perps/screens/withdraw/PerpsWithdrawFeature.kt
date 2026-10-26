package com.tonapps.perps.screens.withdraw

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.perps.funding.PerpsConfirmRequests
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KState
import com.tonapps.mvi.graph.map
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.withdrawableBalance
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class PerpsWithdrawSummary(
    val amountUnits: BigInteger,
    val amount: BigDecimal,
    val insufficient: Boolean,
    val minAmountUnits: BigInteger?,
) {
    val belowMinimum: Boolean
        get() = minAmountUnits != null && amountUnits.signum() > 0 && amountUnits < minAmountUnits

    val canReview: Boolean
        get() = amountUnits.signum() > 0 && !insufficient && !belowMinimum
}

interface PerpsWithdrawViewState : MviViewState {
    val available: StateFlow<KState<String?>>
    val destinations: StateFlow<KState<List<PerpsWithdrawDestinationUi>?>>
    val selected: StateFlow<PerpsWithdrawDestinationUi?>
    val amountInput: StateFlow<String>
    val summary: StateFlow<PerpsWithdrawSummary?>
    val withdrawInFlight: StateFlow<Boolean>
}

sealed interface PerpsWithdrawAction : MviAction {
    data class SetAmount(val value: String) : PerpsWithdrawAction
    data class SelectDestination(val assetId: String) : PerpsWithdrawAction
    data object Max : PerpsWithdrawAction
    data object Reload : PerpsWithdrawAction
}

class PerpsWithdrawFeature(
    private val service: PerpsWithdrawService,
    private val portfolioStore: PerpsPortfolioStore,
    unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsWithdrawViewState, PerpsWithdrawAction>(), PerpsWithdrawViewState {

    private val walletId = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
        .filterNotNull()
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    private val reloads = actions
        .on<PerpsWithdrawAction.Reload>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val loadContext = combine(walletId.filterNotNull(), reloads) { wallet, attempt -> wallet to attempt }
        .distinctUntilChanged()

    private val availableBalance = loadContext
        .transformStateLatest { (wallet, _) ->
            loadOrNull { service.available(wallet) }
                ?: portfolioStore.snapshot.value
                    ?.takeIf { it.walletId == wallet }
                    ?.lastGood
                    ?.takeIf { it.openOrders != null }
                    ?.balance
                    ?.withdrawableBalance()
        }
        .cacheStateWithLoading()

    override val available = combine(availableBalance, settingsRepository.hiddenBalancesFlow) { state, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        state.map { balance -> balance?.let { maskIf(hiddenBalance) { formatUsdAmount(it) } } }
    }
        .cacheState(initialValue = KState.Loading())

    private val loadedDestinations = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.destinations(wallet) } }
        .cacheStateWithLoading()

    override val destinations = combine(loadedDestinations, settingsRepository.hiddenBalancesFlow) { state, _ ->
        val hiddenBalance = settingsRepository.hiddenBalances
        state.map { destinations -> destinations?.map { it.toUi(hiddenBalance) } }
    }
        .cacheState(initialValue = KState.Loading())

    private val selectedAssetId = actions
        .on<PerpsWithdrawAction.SelectDestination>()
        .map { it.assetId }
        .cacheState(initialValue = null)

    private val selectedDestination = combine(loadedDestinations, selectedAssetId) { state, assetId ->
        val loaded = state.state.orEmpty()
        loaded.firstOrNull { it.assetId == assetId } ?: loaded.firstOrNull()
    }
        .cacheState(initialValue = null)

    override val selected = combine(selectedDestination, settingsRepository.hiddenBalancesFlow) { destination, _ ->
        destination?.toUi(settingsRepository.hiddenBalances)
    }
        .cacheState(initialValue = null)

    override val withdrawInFlight = loadContext
        .transformStateLatest { (wallet, _) -> loadOrNull { service.pendingWithdraw(wallet) != null } }
        .map { it.state == true }
        .cacheState(initialValue = false)

    private val limits = loadContext
        .transformStateLatest { loadOrNull { service.limits() } }
        .map { it.state }
        .cacheState(initialValue = null)

    private val input = MutableStateFlow("")

    override val amountInput: StateFlow<String> = input

    override val summary = combine(input, availableBalance, limits, selectedDestination) { text, balance, limit, destination ->
        summarize(text, balance.state, destination?.let { limit?.minimumFor(it) })
    }
        .cacheState(initialValue = null)

    init {
        stateScope.launch {
            actions.collect { action ->
                when (action) {
                    is PerpsWithdrawAction.SetAmount -> input.value = action.value
                    PerpsWithdrawAction.Max -> availableBalance.value.state?.let { input.value = formatPlainDecimal(it.floorToUnits()) }
                    is PerpsWithdrawAction.SelectDestination,
                    PerpsWithdrawAction.Reload -> Unit
                }
            }
        }
    }

    fun confirmRequest(amountUnits: BigInteger, destinationAssetId: String): ConfirmRequest? {
        val wallet = walletId.value ?: return null
        return PerpsConfirmRequests.withdraw(wallet, amountUnits, destinationAssetId)
    }

    private fun summarize(text: String, balance: BigDecimal?, minAmountUnits: BigInteger?): PerpsWithdrawSummary? {
        balance ?: return null
        val entered = text.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val exact = entered.max(BigDecimal.ZERO).floorToUnits()
        val units = exact.unscaledValue()
        return PerpsWithdrawSummary(
            amountUnits = units,
            amount = exact,
            insufficient = exact > balance,
            minAmountUnits = minAmountUnits,
        )
    }

    private fun BigDecimal.floorToUnits(): BigDecimal = setScale(PerpsFundingAmount.USDC_DECIMALS, RoundingMode.DOWN)

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
}
