package com.tonapps.perps.screens.withdraw

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.perps.funding.PerpsConfirmRequests
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
import com.tonapps.perps.data.tokenInputDecimals
import com.tonapps.perps.data.withdrawableBalance
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.PerpsWithdrawDestination
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class PerpsWithdrawSummary(
    val amountUnits: BigInteger,
    val receiveAmount: BigDecimal?,
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
    val inputInToken: StateFlow<Boolean>
    val summary: StateFlow<PerpsWithdrawSummary?>
    val withdrawInFlight: StateFlow<Boolean>
}

sealed interface PerpsWithdrawAction : MviAction {
    data class SetAmount(val value: String) : PerpsWithdrawAction
    data class SelectDestination(val assetId: String) : PerpsWithdrawAction
    data object Max : PerpsWithdrawAction
    data object ToggleInput : PerpsWithdrawAction
    data object Reload : PerpsWithdrawAction
}

private data class WithdrawInput(val text: String, val inToken: Boolean)

class PerpsWithdrawFeature(
    private val service: PerpsWithdrawService,
    private val portfolioStore: PerpsPortfolioStore,
    unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsWithdrawViewState, PerpsWithdrawAction>(), PerpsWithdrawViewState {

    private val walletId = unifiedAccountRepository.selectedTonWalletFlow
        .map { wallet -> wallet?.takeIf { it.supportsPerps }?.id }
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
        loaded.firstOrNull { it.assetId == assetId } ?: loaded.firstOrNull { it.isUsdcEthereum }
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

    private val input = MutableStateFlow(WithdrawInput(text = "", inToken = false))

    override val amountInput = input.map { it.text }.cacheState(initialValue = "")

    override val inputInToken = input.map { it.inToken }.cacheState(initialValue = false)

    override val summary = combine(input, availableBalance, limits, selectedDestination) { entered, balance, limit, destination ->
        summarize(entered, balance.state, destination, destination?.let { limit?.minimumFor(it) })
    }
        .cacheState(initialValue = null)

    init {
        stateScope.launch {
            actions.collect { action ->
                when (action) {
                    is PerpsWithdrawAction.SetAmount -> input.update { it.copy(text = action.value) }
                    PerpsWithdrawAction.Max -> availableBalance.value.state?.let { balance ->
                        input.update { it.copy(text = maxText(balance, it.inToken)) }
                    }
                    PerpsWithdrawAction.ToggleInput -> input.update { it.toggled(selectedDestination.value) }
                    is PerpsWithdrawAction.SelectDestination,
                    PerpsWithdrawAction.Reload -> Unit
                }
            }
        }
        stateScope.launch {
            var previous: PerpsWithdrawDestination? = null
            selectedDestination
                .filterNotNull()
                .distinctUntilChangedBy { it.assetId }
                .collect { next ->
                    input.update { it.retargeted(previous, next) }
                    previous = next
                }
        }
    }

    fun confirmRequest(amountUnits: BigInteger, destinationAssetId: String): ConfirmRequest? {
        val wallet = walletId.value ?: return null
        return PerpsConfirmRequests.withdraw(wallet, amountUnits, destinationAssetId)
    }

    private fun summarize(
        entered: WithdrawInput,
        balance: BigDecimal?,
        destination: PerpsWithdrawDestination?,
        minAmountUnits: BigInteger?,
    ): PerpsWithdrawSummary? {
        balance ?: return null
        val amount = (entered.text.toBigDecimalOrNull() ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val price = destination?.usdPrice
        val usd = when {
            !entered.inToken -> amount
            price == null -> BigDecimal.ZERO
            else -> amount.multiply(price)
        }
        val exact = usd.floorToUnits()
        val receiveAmount = when {
            destination == null || price == null -> null
            entered.inToken -> amount
            else -> exact.divide(price, destination.receiveDecimals, RoundingMode.DOWN)
        }
        return PerpsWithdrawSummary(
            amountUnits = exact.unscaledValue(),
            receiveAmount = receiveAmount,
            insufficient = exact > balance,
            minAmountUnits = minAmountUnits,
        )
    }

    private fun maxText(balance: BigDecimal, inToken: Boolean): String {
        val destination = selectedDestination.value
        val price = destination?.usdPrice
        return if (inToken && destination != null && price != null) {
            formatPlainDecimal(balance.divide(price, destination.receiveInputDecimals, RoundingMode.DOWN))
        } else {
            formatPlainDecimal(balance.floorToUnits())
        }
    }

    private fun WithdrawInput.toggled(destination: PerpsWithdrawDestination?): WithdrawInput {
        val price = destination?.usdPrice ?: return copy(inToken = false)
        val entered = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            ?: return WithdrawInput(text = "", inToken = !inToken)
        val converted = if (inToken) {
            val exact = entered.multiply(price)
            val usd = exact.setScale(USD_INPUT_SCALE, RoundingMode.HALF_UP)
            val available = availableBalance.value.state?.floorToUnits()
            if (available != null && exact.floorToUnits() <= available && usd > available) {
                available
            } else {
                usd
            }
        } else {
            entered.divide(price, destination.receiveInputDecimals, RoundingMode.DOWN)
        }
        return WithdrawInput(text = formatPlainDecimal(converted), inToken = !inToken)
    }

    private fun WithdrawInput.retargeted(previous: PerpsWithdrawDestination?, next: PerpsWithdrawDestination): WithdrawInput {
        if (!inToken || previous == null) {
            return this
        }
        val entered = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return this
        val usd = previous.usdPrice?.let(entered::multiply)
        val nextPrice = next.usdPrice
        return when {
            usd == null -> WithdrawInput(text = "", inToken = nextPrice != null)
            nextPrice == null -> WithdrawInput(text = formatPlainDecimal(usd.setScale(USD_INPUT_SCALE, RoundingMode.DOWN)), inToken = false)
            else -> copy(text = formatPlainDecimal(usd.divide(nextPrice, next.receiveInputDecimals, RoundingMode.DOWN)))
        }
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

    private companion object {
        const val USD_INPUT_SCALE = 2
    }
}

internal val PerpsWithdrawDestination.usdPrice: BigDecimal?
    get() = source?.price?.takeIf { it.signum() > 0 }

internal val PerpsWithdrawDestination.receiveDecimals: Int
    get() = source?.decimals ?: PerpsFundingAmount.USDC_DECIMALS

internal val PerpsWithdrawDestination.receiveInputDecimals: Int
    get() = tokenInputDecimals(receiveDecimals)
