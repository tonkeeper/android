package com.tonapps.deposit.multicoin.screens.confirm

import android.content.Context
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Prerequisite
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessSender
import com.tonapps.log.L
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRoute
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
private const val BPS_IN_WHOLE = 10_000

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
 * Confirm feature for swaps. Adds the quote refresh timer and slippage selection on top of
 * [com.tonapps.deposit.multicoin.screens.confirm.base.BaseTransactionConfirmFeature]; an EVM approve is sent as its own transaction ahead of the swap.
 */
class SwapConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    private val swapRepo: SwapRepository,
    passcodeManager: PasscodeManager,
    confirmResults: ConfirmResultListener,
    feeBuilder: TxFeeBuilder,
    gaslessSender: GaslessSender,
    settingsRepository: SettingsRepository,
    private val legacyAccounts: LegacySwapAccounts,
) : BaseTransactionConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    passcodeManager = passcodeManager,
    confirmResults = confirmResults,
    feeBuilder = feeBuilder,
    gaslessSender = gaslessSender,
    settingsRepository = settingsRepository,
) {

    // A legacy wallet the backend never registered: it swaps on its own TON and TRON addresses, signs
    // with their keys and asks the swap API without a wallet id. Null for a multichain wallet.
    private val legacyWallet = bgScope.async(start = CoroutineStart.LAZY) {
        legacyAccounts.find(request.data.walletId)
    }

    private val swapQuoteTick = MutableStateFlow(0)
    val swapQuoteCountdown = MutableStateFlow(0f)

    // Slippage options for the swap's source chain; null while loading or for chains without config.
    val slippage: StateFlow<SwapSlippage?> = flow {
        emit(loadSlippage())
    }.cacheState(initialValue = null)

    // Selected slippage in bps, seeded from the swap screen and mutable here; changing it re-quotes.
    val selectedSlippageBps = MutableStateFlow((request.type as? ConfirmType.Swap)?.slippageBps)

    // An unlimited allowance spares the approve on the next swaps of the token; chosen at signing.
    val isUnlimitedApprove = MutableStateFlow(false)

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
        .quoteSelectedFee()

    init {
        mainScope.launch {
            combine(sendingState, confirmationError, sendData) { sending, error, data ->
                sending == SendingState.None && error == null && data == null
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        runSwapQuoteTimer()
                    }
                }
        }
    }

    fun selectSlippage(bps: Int) {
        if (txLocked) {
            return
        }
        selectedSlippageBps.tryEmit(bps)
    }

    fun setUnlimitedApprove(isEnabled: Boolean) {
        if (txLocked) {
            return
        }
        isUnlimitedApprove.tryEmit(isEnabled)
    }

    override fun signableApproval(tx: Transaction, main: Transaction): Transaction {
        if (!isUnlimitedApprove.value) {
            return tx
        }
        val approve = tx as? Transaction.Call ?: return tx
        val swap = main as? Transaction.Swap ?: return tx
        return provider.buildApproval(swap, approve.data)?.txMax ?: tx
    }

    // The swap screen underneath closes itself and opens History on this signal.
    override fun onSendSucceeded() {
        swapRepo.notifySwapCompleted()
    }

    override suspend fun unlockSigner(context: Context, pendingTx: PendingTransaction): SigningWallet {
        val legacy = legacyWallet.await() ?: return super.unlockSigner(context, pendingTx)

        val confirmed = passcodeManager.confirmation(context, context.getString(Localization.app_name))
        if (!confirmed) {
            throw CancellationException("Passcode dismissed")
        }

        return legacyAccounts.signingKeys(legacy)
    }

    // Prepared only after the wallet resolved, so the answer is in by the time anything is sent.
    override fun reportedWalletId(pendingTx: PendingTransaction): String? {
        return when (resolvedLegacyWallet()) {
            null -> super.reportedWalletId(pendingTx)
            else -> null
        }
    }

    override fun notifyTransactionSent(pendingTx: PendingTransaction) {
        val legacy = resolvedLegacyWallet() ?: return super.notifyTransactionSent(pendingTx)
        legacyAccounts.onTransactionSent(legacy)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resolvedLegacyWallet(): LegacySwapWallet? {
        return legacyWallet.takeIf { it.isCompleted }?.getCompleted()
    }

    private suspend fun registeredWalletId(): String? {
        return request.data.walletId.takeIf { legacyWallet.await() == null }
    }

    private suspend fun findAccount(assetId: String, forceRefresh: Boolean = false): AccountWithDetails? {
        val legacy = legacyWallet.await()
            ?: return accountRepo.findAccount(request.data.walletId, assetId, forceRefresh = forceRefresh)
        return legacyAccounts.findAccount(legacy, assetId, refresh = forceRefresh)
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

    // The slippage config is served per registered wallet; a legacy swap quotes with the server default.
    private suspend fun loadSlippage(): SwapSlippage? = withContext(Async.Io) {
        val walletId = registeredWalletId() ?: return@withContext null
        val account = accountRepo.findAccount(walletId, request.data.assetId)
        account?.let { swapRepo.getSlippage(walletId, it.asset.value.chain) }
    }

    private suspend fun prepareSwap(trade: ConfirmType.Swap, ctx: SwapContext): PendingTransaction? {
        val walletId = request.data.walletId
        val wallet = legacyWallet.await()?.toMcWallet()
            ?: accountRepo.getWallet(walletId)
            ?: throw IllegalStateException("Can't find wallet by id")
        val account = findAccount(request.data.assetId, forceRefresh = true) ?: throw IllegalStateException("Can't find account")
        val destination = findAccount(trade.destinationAssetId) ?: throw IllegalStateException("Can't find destination account")

        val asset = account.asset.value
        val energy = when (asset) {
            is Asset.Coin -> asset
            is Asset.Token, is Asset.Battery -> asset.chain.toAsset()
        }

        val energyAccount = findAccount(energy.id) ?: throw IllegalStateException("Can't find energy account by id")

        val prepared = loadRoute(trade, ctx, account, destination, asset.chain) ?: return null
        val route = prepared.route
        val buyBaseAmount = route.buyBaseAmount ?: prepared.quote.buyBaseAmount
        val quote = prepared.quote.copy(
            providerTxId = route.providerRouteId,
            buyBaseAmount = buyBaseAmount,
            minimumBuyBaseAmount = minOf(route.minimumBuyBaseAmount ?: prepared.quote.minimumBuyBaseAmountAt(buyBaseAmount), buyBaseAmount),
            priceImpactBps = prepared.quote.priceImpactBpsAt(buyBaseAmount),
        )
        val messages = route.mainMessages
        val batched = messages.size > 1

        val operation = route.toOperation(asset.chain)
        val swap = swapTransactions(
            operation = operation,
            messages = messages,
            account = account.value,
            destination = destination.value,
            isMax = trade.isMax == true && !batched,
        )
        val approvals = swap.approvals
        val transactions = swap.messages
        val transaction = transactions.first()
        val messagesAmount = transactions.fold(BigInteger.ZERO) { total, tx -> total + tx.amount }
        val attachedEnergy = transactions.fold(BigInteger.ZERO) { total, tx -> total + tx.attachedEnergy() }

        val payloadFee = operation.payloads.last().fee
        val (feeResult, nonceResult) = withContext(Async.Io) {
            val feeTask = when {
                payloadFee != null -> null
                approvals.isEmpty() -> async { provider.estimateBatchFee(transactions.map { it.asEnergyMessage(energy) }) }
                else -> async { provider.estimateFee(transaction) }
            }
            val nonceTask = async { provider.getNonce(account.value) }
            feeTask?.await() to nonceTask.await()
        }

        val prerequisitesResult = prerequisites(operation, approvals)
        val prerequisites = prerequisitesResult.value.orEmpty()

        // Use provider Fee or calculate on our own
        val feeError = (feeResult?.error ?: prerequisitesResult.error)
            ?.also { L.e(it) }
            ?.let(::parseChainError)
        val mainFee = payloadFee ?: feeResult?.value
        val fee = mainFee?.withPrerequisites(prerequisites)

        // Calculate Nonce
        val nonceError = nonceResult.error
            ?.also { L.e(it) }
            ?.let(::parseNodeError)

        // On `exact` the sell amount is baked into the provider calldata, so trimming it
        // here desyncs `value` from the calldata and the router reverts. Check the balance,
        // but never shrink: report InsufficientBalance instead.
        val exactCalldata = messages.first().isExactCalldata
        val fixedAmount = exactCalldata || batched

        val reservationResult = reserveNativeGas(
            account = transaction.account,
            energy = energyAccount.value,
            amount = messagesAmount,
            isMax = transaction.isMax && !fixedAmount,
            fee = fee?.withAttachedEnergy(attachedEnergy),
            policy = if (fixedAmount) {
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

        // A coin source pays its messages out of the same balance, and a TON route attaches forward
        // gas to every part on top of the amount typed: no fee method covers that.
        val spentAmount = messagesAmount.takeIf { asset is Asset.Coin && it > trade.sourceAmount }
        val balanceError = ConfirmationError.InsufficientBalance
            .takeIf { (spentAmount ?: trade.sourceAmount) > account.unitBalance.value }

        val feeState = feeBuilder.buildSwap(
            account = account,
            energy = energyAccount,
            walletId = walletId,
            transactions = transactions,
            approvals = approvals,
            estimated = fee,
            attachedEnergy = attachedEnergy,
            shrinkToFit = transaction.isMax && !fixedAmount,
        )

        val batteryPayable = feeState.hasBatteryOption

        val shrunkTransaction = if (fixedAmount) {
            transaction
        } else {
            reservationResult.getOrNull()
                ?.let {
                    val isMax = transaction.isMax || it.isAmountAdjusted
                    transaction.copy(amount = it.amount, isMax = isMax)
                }
                ?: transaction
        }
        val adjustedTransaction = if (batteryPayable) {
            transaction
        } else {
            shrunkTransaction
        }

        val chainKitError = feeError ?: nonceError ?: reservationError.takeUnless { batteryPayable }

        confirmationError.tryEmit(balanceError ?: feeConfirmationError(feeState, chainKitError, nonceError))
        L.d("Nonce: ${nonceResult.value}")

        val signing = if (batched) {
            Signing.Tx(value = transaction.copy(isMax = trade.isMax == true), batchToSign = transactions)
        } else {
            Signing.Tx(adjustedTransaction, walletPaid = shrunkTransaction.takeIf { batteryPayable })
        }

        return PendingTransaction(
            request = request,
            wallet = wallet,
            account = account,
            fee = feeState.copy(error = chainKitError),
            nonce = nonceResult.value,
            signing = signing,
            spentAmount = spentAmount,
            destination = destination,
            prerequisites = prerequisites,
            signingFee = mainFee.takeIf { prerequisites.isNotEmpty() },
            quote = quote,
            rateLabel = swapRepo.formatRateLabel(quote, asset, destination.asset.value),
        )
    }

    // Each its own transaction ahead of the swap: the route's fee where it quotes one.
    private suspend fun prerequisites(operation: Operation, approvals: List<Transaction>): ChainRes<List<Prerequisite>> {
        val items = approvals.mapIndexed { index, tx ->
            val fee = operation.payloads[index].fee ?: run {
                val estimated = withContext(Async.Io) { provider.estimateFee(tx) }
                estimated.value ?: return ChainRes.err(estimated.error ?: ChainError.InternalError("No fee for $tx"))
            }
            Prerequisite(tx, fee)
        }
        return ChainRes.ok(items)
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
            PreparedRoute(quote = quote, route = swapRepo.prepareRoute(quote, chain, registeredWalletId()))
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
            PreparedRoute(quote = quote, route = swapRepo.prepareRoute(quote, chain, registeredWalletId()))
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
        val result = swapRepo.fetchQuote(
            sell = account,
            buy = destination,
            amount = trade.sourceAmount,
            slippageBps = ctx.slippageBps,
            walletId = registeredWalletId(),
        )
        return (result as? SwapQuoteResult.Found)?.quote
            ?: throw IllegalStateException("No swap route available")
    }
}

private val Transaction.Swap.attachesEnergy: Boolean
    get() = account.chain.isTvm && account.asset is Asset.Token && !data.isNullOrBlank()

private fun Transaction.Swap.attachedEnergy(): BigInteger {
    return if (attachesEnergy) {
        amount
    } else {
        BigInteger.ZERO
    }
}

private fun Transaction.Swap.asEnergyMessage(energy: Asset): Transaction.Swap {
    if (!attachesEnergy) {
        return this
    }
    return copy(account = account.copy(asset = energy), isMax = false)
}

private fun SwapQuote.minimumBuyBaseAmountAt(receiveBaseAmount: BigInteger): BigInteger {
    if (!buyBaseAmount.isPositive) {
        return minimumBuyBaseAmount
    }
    return minimumBuyBaseAmount * receiveBaseAmount / buyBaseAmount
}

private fun SwapQuote.priceImpactBpsAt(receiveBaseAmount: BigInteger): Int? {
    val bps = priceImpactBps ?: return null
    if (!buyBaseAmount.isPositive) {
        return bps
    }
    return (BigInteger.fromInt(BPS_IN_WHOLE + bps) * receiveBaseAmount / buyBaseAmount).intValue(exactRequired = false) - BPS_IN_WHOLE
}

private fun Fee.withAttachedEnergy(attachedEnergy: BigInteger): Fee {
    if (!attachedEnergy.isPositive) {
        return this
    }
    return Fee.Value(asset = asset, amount = TxFeeLogic.requiredEnergy(this, attachedEnergy))
}

private fun Fee.withPrerequisites(prerequisites: List<Prerequisite>): Fee {
    if (prerequisites.isEmpty()) {
        return this
    }
    return Fee.Value(asset = asset, amount = prerequisites.fold(amount) { total, item -> total + item.fee.amount })
}
