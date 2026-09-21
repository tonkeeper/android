package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.engine.GaslessSender
import com.tonapps.log.L
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRoute
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val SWAP_QUOTE_INTERVAL_MS = 45_000L
private const val SWAP_QUOTE_TICK_MS = 250L

// What drives a swap re-preparation: the inactivity tick, a manual retry, and the chosen slippage.
private data class SwapContext(
    val tick: Int,
    val refresh: String?,
    val slippageBps: Int?,
)

private data class PreparedRoute(
    val quote: SwapQuote,
    val route: SwapRoute,
)

/**
 * Confirm feature for swaps. Adds the quote refresh timer, slippage selection and unlimited-approve
 * toggle on top of [BaseConfirmFeature].
 */
class SwapConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    private val swapRepo: SwapRepository,
    passcodeManager: PasscodeManager,
    wcRepository: WcRepository,
    feeBuilder: TxFeeBuilder,
    gaslessSender: GaslessSender,
    settingsRepository: SettingsRepository,
) : BaseConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    passcodeManager = passcodeManager,
    wcRepository = wcRepository,
    feeBuilder = feeBuilder,
    gaslessSender = gaslessSender,
    settingsRepository = settingsRepository,
) {

    private val swapQuoteTick = MutableStateFlow(0)
    val swapQuoteCountdown = MutableStateFlow(0f)

    // Slippage options for the swap's source chain; null while loading or for chains without config.
    val slippage: StateFlow<SwapSlippage?> = flow {
        emit(loadSlippage())
    }.cacheState(initialValue = null)

    // Selected slippage in bps, seeded from the swap screen and mutable here; changing it re-quotes.
    val selectedSlippageBps = MutableStateFlow((request.type as? ConfirmType.Swap)?.slippageBps)
    fun selectSlippage(bps: Int) {
        if (txLocked) {
            return
        }
        selectedSlippageBps.tryEmit(bps)
    }

    val isUnlimitedApprove = MutableStateFlow(false)
    override val unlimitedApproveEnabled: Boolean get() = isUnlimitedApprove.value
    fun setUnlimitedApprove(isEnabled: Boolean) {
        if (txLocked) {
            return
        }
        isUnlimitedApprove.tryEmit(isEnabled)
    }

    override val pendingTx = combine(refreshToken, swapQuoteTick, selectedSlippageBps)
    { refresh, tick, slippageBps -> SwapContext(tick = tick, refresh = refresh, slippageBps = slippageBps) }
        .mapLatestCatching(
            onError = { onPrepareFailed(it) },
            onFinally = { pendingLoader.tryEmit(false) },
        ) { ctx ->
            pendingLoader.tryEmit(true)
            prepareTracked {
                prepareSwap(request.type as ConfirmType.Swap, ctx)
            }
        }
        .keepLastPrepared()

    init {
        mainScope.launch {
            combine(sendingState, confirmationError, sendData) { sending, error, data ->
                sending == SendingState.None && error == null && data == null
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) runSwapQuoteTimer()
                }
        }

        bgScope.launch {
            // Once the swap is sent, tell the swap screen (a separate ViewModel underneath us) so it
            // can reset its amount while keeping the pair. Keyed on the sent state, not the reject
            // path, so only a successful swap clears the input.
            sendingState.collect { state ->
                if (state == SendingState.Done) {
                    swapRepo.notifySwapCompleted()
                }
            }
        }
    }

    private suspend fun runSwapQuoteTimer() {
        val totalTicks = (SWAP_QUOTE_INTERVAL_MS / SWAP_QUOTE_TICK_MS).toInt()
        while (!txLocked) {
            for (i in 0..totalTicks) {
                swapQuoteCountdown.tryEmit(1f - i.toFloat() / totalTicks)

                if (i < totalTicks) {
                    delay(SWAP_QUOTE_TICK_MS)
                }
            }

            if (txLocked) {
                return
            }

            swapQuoteTick.value += 1
        }
    }

    private suspend fun loadSlippage(): SwapSlippage? = withContext(Async.Io) {
        val account = accountRepo.findAccount(request.data.walletId, request.data.assetId)
        account?.let { swapRepo.getSlippage(request.data.walletId, it.asset.value.chain) }
    }

    private suspend fun prepareSwap(trade: ConfirmType.Swap, ctx: SwapContext): PendingTransaction? {
        val walletId = request.data.walletId
        val wallet = accountRepo.getWallet(walletId) ?: throw IllegalStateException("Can't find wallet by id")
        val account = accountRepo.findAccount(walletId, request.data.assetId, forceRefresh = true) ?: throw IllegalStateException("Can't find account")
        val destination = accountRepo.findAccount(walletId, trade.destinationAssetId) ?: throw IllegalStateException("Can't find destination account")

        val asset = account.asset.value
        val energy = when (asset) {
            is Asset.Coin -> asset
            is Asset.Token -> asset.chain.toAsset()
        }

        val energyAccount = accountRepo.findAccount(walletId, energy.id) ?: throw IllegalStateException("Can't find energy account by id")

        val prepared = loadRoute(trade, ctx, account, destination, asset.chain) ?: return null
        val quote = prepared.quote
        val route = prepared.route
        val payload = route.main

        val transaction = Transaction.Swap(
            account = account.value,
            amount = payload.amount,
            destination = destination.value,
            to = Address.force(payload.to, asset.chain),
            isMax = trade.isMax == true,
            data = payload.data,
            energy = energy,
        )

        val payloadFee = payload.fee
        val (feeResult, nonceResult) = withContext(Async.Io) {
            val feeTask = if (payloadFee == null) async { provider.estimateFee(transaction) } else null
            val nonceTask = async { provider.getNonce(account.value) }
            feeTask?.await() to nonceTask.await()
        }

        // Use provider Fee or calculate on our own
        val feeError = feeResult
            ?.error
            ?.also { L.e(it) }
            ?.let(::parseChainError)
        val fee = payloadFee ?: feeResult?.value

        // Calculate Nonce
        val nonceError = nonceResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)

        // On `exact` the sell amount is baked into the provider calldata, so trimming it
        // here desyncs `value` from the calldata and the router reverts. Check the balance,
        // but never shrink: report InsufficientBalance instead.
        val exactCalldata = payload.isExactCalldata

        val reservationResult = reserveNativeGas(
            account = transaction.account,
            energy = energyAccount.value,
            amount = transaction.amount,
            isMax = transaction.isMax && !exactCalldata,
            fee = fee,
            policy = if (exactCalldata) {
                GasReservePolicy.DrainOrError
            } else {
                GasReservePolicy.ShrinkToFit
            },
        )

        val reservationError = reservationResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)
            ?: reservationResult.value?.error
                ?.let { parseReservationError(it) }

        val balanceError = ConfirmationError.InsufficientBalance
            .takeIf { trade.sourceAmount > account.unitBalance.value }

        val feeState = feeBuilder.buildSwap(
            account = account,
            energy = energyAccount,
            walletId = walletId,
            transaction = transaction,
            estimated = fee,
        )

        val batteryPayable = feeState.hasBatteryOption

        val adjustedTransaction = if (batteryPayable || exactCalldata) {
            transaction
        } else {
            reservationResult.getOrNull()
                ?.let {
                    val isMax = transaction.isMax || it.isAmountAdjusted
                    transaction.copy(amount = it.amount, isMax = isMax)
                }
                ?: transaction
        }

        val approval = route.approval?.let {
            provider.buildApproval(adjustedTransaction, it.data, it.fee)
        }

        val chainKitError = feeError ?: nonceError ?: reservationError.takeUnless { batteryPayable }

        // The relayer pays the chain fee, so an empty gas account is not a reason to block the swap.
        val relayed = feeState.resolve(pickedFeeId.value)?.viaRelayer == true
        val confirmError = when {
            balanceError != null -> balanceError
            relayed -> nonceError
            else -> chainKitError
        }
        confirmationError.tryEmit(confirmError)
        L.d("Nonce: ${nonceResult.value}")

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = feeState.copy(error = chainKitError),
            nonce = nonceResult.value,
            signing = Signing.Tx(adjustedTransaction),
            destination = destination,
            approval = approval,
            quote = quote,
            rateLabel = swapRepo.formatRateLabel(quote, asset, destination.asset.value),
        )
    }

    private suspend fun loadRoute(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
        chain: Chain,
    ): PreparedRoute? {
        return try {
            val quote = snapshotQuote(trade, ctx) ?: fetchQuote(trade, ctx, account, destination)
            PreparedRoute(quote = quote, route = swapRepo.prepareRoute(quote, chain, account.data.walletId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            retryRoute(trade, ctx, account, destination, chain)
        }
    }

    private suspend fun retryRoute(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
        chain: Chain,
    ): PreparedRoute? {
        return try {
            val quote = fetchQuote(trade, ctx, account, destination)
            PreparedRoute(quote = quote, route = swapRepo.prepareRoute(quote, chain, account.data.walletId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            confirmationError.tryEmit(routeError(e))
            null
        }
    }

    private fun routeError(e: Throwable): ConfirmationError {
        return when (e) {
            is IOException -> ConfirmationError.NetworkError
            else -> ConfirmationError.Unknown
        }
    }

    private fun snapshotQuote(trade: ConfirmType.Swap, ctx: SwapContext): SwapQuote? {
        return trade.quote
            ?.takeIf { ctx.tick == 0 && ctx.refresh == null && ctx.slippageBps == trade.slippageBps }
            ?.let { snapshot ->
                SwapQuote(
                    routeId = snapshot.routeId,
                    sourceBaseAmount = trade.sourceAmount,
                    buyBaseAmount = snapshot.buyAmount,
                    minimumBuyBaseAmount = snapshot.minBuyAmount,
                    slippageBps = snapshot.slippageBps,
                    priceImpactBps = snapshot.priceImpactBps,
                    provider = SwapQuote.Provider.forceFrom(snapshot.provider),
                    providerTxId = snapshot.providerTxId,
                )
            }
    }

    private suspend fun fetchQuote(
        trade: ConfirmType.Swap,
        ctx: SwapContext,
        account: AccountWithDetails,
        destination: AccountWithDetails,
    ): SwapQuote {
        return swapRepo.fetchQuote(account, destination, trade.sourceAmount, ctx.slippageBps)
            ?: throw IllegalStateException("No swap route available")
    }
}
